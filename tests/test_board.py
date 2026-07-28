"""Rules tests for the Barnyard Blitz board.

Runs under pytest, or standalone with ``python tests/test_board.py``.
"""

import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from barnyard.board import Board, Power, Tile  # noqa: E402

C, P, K, S, D, H = range(6)


def make_board(layout, kinds=6):
    """Build a board from rows of single-character kind indices."""
    rows = [row.split() for row in layout.strip().splitlines()]
    board = Board(rows=len(rows), cols=len(rows[0]), kinds=kinds,
                  rng=random.Random(7))
    for r, row in enumerate(rows):
        for c, token in enumerate(row):
            board.grid[r][c] = board.make_tile(int(token))
    return board


LAYOUT = """
0 1 2 3 4 5 0 1
1 2 3 4 5 0 1 2
2 3 4 5 0 1 2 3
3 4 5 0 1 2 3 4
4 5 0 1 2 3 4 5
5 0 1 2 3 4 5 0
0 1 2 3 4 5 0 1
1 2 3 4 5 0 1 2
"""


def test_fresh_board_is_playable():
    for seed in range(25):
        board = Board(rng=random.Random(seed))
        assert not board.find_runs(), "a fresh deal must have no free matches"
        assert board.has_moves(), "a fresh deal must have at least one move"


def test_finds_horizontal_and_vertical_runs():
    board = make_board(LAYOUT)
    board.grid[3][2] = board.make_tile(0)
    board.grid[3][3] = board.make_tile(0)
    board.grid[3][4] = board.make_tile(0)
    runs = board.find_runs()
    assert len(runs) == 1
    assert runs[0].horizontal
    assert runs[0].cells == [(3, 2), (3, 3), (3, 4)]


def test_swap_is_legal_only_when_it_makes_a_match():
    board = make_board("""
1 0 2 3
0 1 3 2
1 2 0 3
2 3 1 0
""", kinds=4)
    assert not board.find_runs(), "the fixture starts with no matches"
    # Swapping (1,0)<->(1,1) lines up three 1s down column 0.
    assert board.swap_is_legal((1, 0), (1, 1))
    assert not board.swap_is_legal((0, 2), (0, 3))
    assert not board.swap_is_legal((0, 0), (2, 0)), "non-adjacent is illegal"
    assert board.at((1, 0)).kind == 0, "a legality check must not mutate"


def test_three_in_a_row_clears_without_a_special():
    board = make_board(LAYOUT)
    for c in (2, 3, 4):
        board.grid[3][c] = board.make_tile(0)
    result = board.resolve_matches()
    assert len(result.cleared) == 3
    assert result.specials == []
    assert all(board.at(cell) is None for cell in result.cleared)


def test_four_in_a_row_makes_a_golden_egg():
    board = make_board(LAYOUT)
    for c in (2, 3, 4, 5):
        board.grid[3][c] = board.make_tile(0)
    result = board.resolve_matches(prefer=(3, 4))
    assert len(result.specials) == 1
    cell, kind, power = result.specials[0]
    assert power is Power.EGG
    assert kind == 0
    assert cell == (3, 4), "the special lands where the player moved"
    assert board.at((3, 4)).power is Power.EGG


def test_five_in_a_row_makes_a_prize_rooster():
    board = make_board(LAYOUT)
    for c in range(1, 6):
        board.grid[3][c] = board.make_tile(0)
    result = board.resolve_matches()
    assert [p for _c, _k, p in result.specials] == [Power.ROOSTER]


def test_corner_match_makes_a_hay_bale():
    board = make_board("""
0 1 1 2
0 2 1 3
0 0 0 1
3 1 2 3
""", kinds=4)
    result = board.resolve_matches()
    powers = [p for _c, _k, p in result.specials]
    assert powers == [Power.HAY]
    assert (2, 0) in result.cleared and (0, 0) in result.cleared


def test_golden_egg_blasts_its_neighbourhood():
    board = make_board(LAYOUT)
    board.grid[4][4] = Tile(1, Power.EGG, uid=99)
    cleared, effects = board.detonate({(4, 4)})
    assert len(cleared) == 9
    assert ("egg", (4, 4)) in effects


def test_hay_bale_clears_row_and_column():
    board = make_board(LAYOUT)
    board.grid[2][5] = Tile(1, Power.HAY, uid=99)
    cleared, _effects = board.detonate({(2, 5)})
    assert len(cleared) == board.rows + board.cols - 1


def test_rooster_swap_clears_a_whole_species():
    board = make_board(LAYOUT)
    board.grid[0][0] = Tile(0, Power.ROOSTER, uid=99)
    target_kind = board.at((0, 1)).kind
    expected = sum(1 for cell in board.cells()
                   if (t := board.at(cell)) and t.kind == target_kind
                   and t.power is not Power.ROOSTER)
    result = board.activate_rooster((0, 0), (0, 1))
    assert len(result.cleared) == expected + 1  # every match, plus the rooster
    assert board.at((0, 0)) is None


def test_specials_chain_when_caught_in_a_blast():
    board = make_board(LAYOUT)
    board.grid[4][4] = Tile(1, Power.EGG, uid=1)
    board.grid[4][5] = Tile(2, Power.HAY, uid=2)
    cleared, effects = board.detonate({(4, 4)})
    names = [name for name, _cell in effects]
    assert "egg" in names and "hay" in names
    assert len(cleared) > 9, "the hay bale should widen the blast"


def test_collapse_drops_tiles_and_refills():
    board = make_board(LAYOUT)
    for r in (5, 6, 7):
        board.grid[r][2] = None
    top_uid = board.at((0, 2)).uid
    moves, spawns = board.collapse()
    assert all(board.at(cell) is not None for cell in board.cells())
    assert len(spawns) == 3
    assert board.at((3, 2)).uid == top_uid, "column 2 slid down three rows"
    assert (2, 0, 3) in moves


def test_shuffle_keeps_the_same_animals_and_stays_playable():
    board = make_board(LAYOUT)
    before = sorted(t.kind for t in
                    (board.at(cell) for cell in board.cells()))
    board.shuffle()
    after = sorted(t.kind for t in
                   (board.at(cell) for cell in board.cells()))
    assert before == after
    assert board.has_moves()
    assert not board.find_runs()


def test_a_full_random_game_never_wedges():
    """Play a few hundred moves; the board must always stay resolvable."""
    board = Board(rng=random.Random(1234))
    for _ in range(300):
        move = board.find_hint()
        if move is None:
            board.shuffle()
            move = board.find_hint()
        assert move is not None
        a, b = move
        board.swap(a, b)
        guard = 0
        while True:
            ta, tb = board.at(a), board.at(b)
            if ta is not None and ta.power is Power.ROOSTER:
                result = board.activate_rooster(a, b)
            elif tb is not None and tb.power is Power.ROOSTER:
                result = board.activate_rooster(b, a)
            else:
                result = board.resolve_matches(prefer=b)
            if not result:
                break
            board.collapse()
            guard += 1
            assert guard < 200, "cascades should terminate"
        assert all(board.at(cell) is not None for cell in board.cells())


def _run_standalone():
    tests = [(name, fn) for name, fn in sorted(globals().items())
             if name.startswith("test_") and callable(fn)]
    failures = 0
    for name, fn in tests:
        try:
            fn()
        except AssertionError as exc:
            failures += 1
            print(f"FAIL {name}: {exc}")
        else:
            print(f"ok   {name}")
    print(f"\n{len(tests) - failures}/{len(tests)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(_run_standalone())
