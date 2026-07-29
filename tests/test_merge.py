"""Rules tests for the merge layer: board, economy, orders, story and saving.

Runs under pytest, or standalone with ``python tests/test_merge.py``.
"""

import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from barnyard.merge.board import (STORAGE_SLOTS, Item,  # noqa: E402
                                  MergeBoard)
from barnyard.merge.economy import Economy, xp_for_level  # noqa: E402
from barnyard.merge.items import CHAINS, MAX_TIER, value_of  # noqa: E402
from barnyard.merge.orders import OrderBook, make_order  # noqa: E402
from barnyard.merge.session import Session  # noqa: E402
from barnyard.merge.story import CHAPTERS, StoryProgress  # noqa: E402


def empty_board(rows=4, cols=4) -> MergeBoard:
    return MergeBoard(rows, cols)


# ------------------------------------------------------------------- the board
def test_matching_items_merge_into_the_next_tier():
    board = empty_board()
    board.put((0, 0), Item("eggs", 1))
    board.put((0, 1), Item("eggs", 1))
    result = board.drop((0, 0), (0, 1))
    assert result.kind == "merge"
    assert board.at((0, 0)) is None
    assert board.at((0, 1)).tier == 2


def test_mismatched_items_swap_instead_of_merging():
    board = empty_board()
    board.put((0, 0), Item("eggs", 1))
    board.put((0, 1), Item("eggs", 2))
    result = board.drop((0, 0), (0, 1))
    assert result.kind == "swap"
    assert board.at((0, 0)).tier == 2
    assert board.at((0, 1)).tier == 1


def test_dropping_onto_a_hole_moves_the_item():
    board = empty_board()
    board.put((0, 0), Item("crops", 0))
    result = board.drop((0, 0), (2, 2))
    assert result.kind == "move"
    assert board.at((0, 0)) is None
    assert board.at((2, 2)).chain == "crops"


def test_the_top_tier_cannot_merge_any_further():
    board = empty_board()
    board.put((0, 0), Item("eggs", MAX_TIER))
    board.put((0, 1), Item("eggs", MAX_TIER))
    assert board.drop((0, 0), (0, 1)).kind == "swap"


def test_generators_never_merge():
    board = empty_board()
    board.put((0, 0), Item("eggs", 0, "eggs_gen"))
    board.put((0, 1), Item("eggs", 0, "eggs_gen"))
    assert board.drop((0, 0), (0, 1)).kind == "swap"
    assert board.at((0, 1)).is_generator


def test_a_generator_drops_its_output_next_to_itself():
    board = empty_board()
    board.put((1, 1), Item("eggs", 0, "eggs_gen"))
    landed = board.tap((1, 1), random.Random(3))
    assert landed is not None
    assert abs(landed[0] - 1) + abs(landed[1] - 1) == 1, landed
    assert board.at(landed).chain == "eggs"
    assert not board.at(landed).is_generator


def test_a_full_board_refuses_new_output():
    board = empty_board(2, 2)
    board.put((0, 0), Item("eggs", 0, "eggs_gen"))
    for cell in ((0, 1), (1, 0), (1, 1)):
        board.put(cell, Item("eggs", 0))
    assert board.is_full
    assert board.tap((0, 0), random.Random(1)) is None


def test_selling_pays_the_item_value_and_spares_generators():
    board = empty_board()
    board.put((0, 0), Item("eggs", 3))
    board.put((0, 1), Item("eggs", 0, "eggs_gen"))
    assert board.sell((0, 0)) == value_of(3)
    assert board.at((0, 0)) is None
    assert board.sell((0, 1)) == 0
    assert board.at((0, 1)) is not None


def test_storage_round_trip():
    board = empty_board()
    board.put((0, 0), Item("wool", 2))
    assert board.to_storage((0, 0))
    assert board.at((0, 0)) is None
    assert len(board.storage) == 1
    assert board.from_storage(0)
    assert len(board.storage) == 0
    assert sum(1 for _c, _i in board.items()) == 1


def test_storage_is_capped():
    board = empty_board(8, 8)
    for i in range(STORAGE_SLOTS + 2):
        board.put((0, 0), Item("eggs", 0))
        stored = board.to_storage((0, 0))
        assert stored == (i < STORAGE_SLOTS)
    assert len(board.storage) == STORAGE_SLOTS


def test_has_and_take_look_in_storage_too():
    board = empty_board()
    board.put((0, 0), Item("eggs", 2))
    board.storage.append(Item("eggs", 2))
    assert board.has("eggs", 2, 2)
    assert not board.has("eggs", 2, 3)
    assert board.take("eggs", 2) and board.take("eggs", 2)
    assert not board.take("eggs", 2)


# ---------------------------------------------------------------- the economy
def test_energy_trickles_back_up_to_the_cap():
    eco = Economy(energy=0)
    assert eco.tick(1.0) == 0
    gained = eco.tick(200.0)
    assert gained == 8, gained
    assert eco.energy == 8
    eco.energy = eco.energy_cap
    assert eco.tick(1000.0) == 0
    assert eco.energy == eco.energy_cap


def test_energy_is_spent_only_when_it_is_there():
    eco = Economy(energy=2)
    assert eco.spend_energy(2)
    assert eco.energy == 0
    assert not eco.spend_energy(1)
    assert eco.energy == 0


def test_levelling_consumes_xp_and_can_skip_several_levels():
    eco = Economy()
    assert eco.add_xp(0) == []
    levels = eco.add_xp(xp_for_level(1) * 40)
    assert levels == list(range(2, eco.level + 1))
    assert eco.xp < eco.xp_needed
    assert 0.0 <= eco.xp_fraction <= 1.0


def test_coins_cannot_go_negative():
    eco = Economy(coins=30)
    assert not eco.spend_coins(31)
    assert eco.coins == 30
    assert eco.spend_coins(30)
    assert eco.coins == 0


# ------------------------------------------------------------------- orders
def test_orders_only_ask_for_unlocked_chains():
    rng = random.Random(9)
    for _ in range(60):
        order = make_order(1, rng)
        for request in order.requests:
            assert CHAINS[request.chain].unlock_level <= 1
            assert 0 <= request.tier <= MAX_TIER
        assert order.coins > 0 and order.xp > 0


def test_delivering_consumes_the_items_and_refills_the_book():
    rng = random.Random(4)
    board = MergeBoard(6, 6)
    book = OrderBook()
    book.refill(1, rng)
    order = book.active[0]
    assert book.deliver(0, board, 1, rng) is None, "nothing to hand over yet"
    for request in order.requests:
        for _ in range(request.quantity):
            board.place(Item(request.chain, request.tier))
    assert order.filled_by(board)
    delivered = book.deliver(0, board, 1, rng)
    assert delivered is order
    assert len(book.active) == 3
    assert not any(True for _c, _i in board.items()), "items were consumed"


def test_skipping_an_order_replaces_it():
    rng = random.Random(2)
    book = OrderBook()
    book.refill(1, rng)
    first = book.active[0]
    book.skip(0, 1, rng)
    assert len(book.active) == 3
    assert book.active[0] is not first


# --------------------------------------------------------------------- story
def test_a_chapter_advances_once_every_task_is_done():
    story = StoryProgress()
    chapter = story.current
    assert chapter is CHAPTERS[0]
    for task in chapter.tasks:
        assert not story.chapter_complete
        assert story.complete(task)
        assert not story.complete(task), "a task completes only once"
    assert story.chapter_complete
    story.advance()
    assert story.current is CHAPTERS[1]


def test_the_story_ends_cleanly():
    story = StoryProgress()
    while not story.finished:
        for task in story.tasks():
            story.complete(task)
        story.advance()
    assert story.current is None
    assert story.tasks() == ()
    assert story.next_task() is None
    assert len(story.done) == story.total_tasks


# ------------------------------------------------------------------- session
def test_tapping_a_generator_costs_energy_and_makes_an_item():
    session = Session.new(random.Random(11))
    cell = next(c for c, i in session.board.items() if i.is_generator)
    before_energy = session.economy.energy
    before_items = sum(1 for _c, _i in session.board.items())
    assert session.tap(cell)
    cost = CHAINS["eggs"].generator.energy
    assert session.economy.energy == before_energy - cost
    assert sum(1 for _c, _i in session.board.items()) == before_items + 1


def test_tapping_without_energy_warns_and_changes_nothing():
    session = Session.new(random.Random(12))
    session.economy.energy = 0
    cell = next(c for c, i in session.board.items() if i.is_generator)
    before = sum(1 for _c, _i in session.board.items())
    assert not session.tap(cell)
    assert sum(1 for _c, _i in session.board.items()) == before
    assert any(e.kind == "warn" for e in session.drain())


def test_levelling_up_puts_the_new_generator_on_the_board():
    session = Session.new(random.Random(13))
    assert {i.chain for _c, i in session.board.items() if i.is_generator} \
        == {"eggs"}
    session._gain_xp(50000)
    chains = {i.chain for _c, i in session.board.items() if i.is_generator}
    assert chains == set(CHAINS), chains
    assert any(e.kind == "unlock" for e in session.events)


def test_a_task_is_paid_for_in_coins():
    session = Session.new(random.Random(14))
    task = session.story.tasks()[0]
    session.economy.coins = task.cost - 1
    assert not session.complete_task(task)
    assert not session.story.is_done(task)
    session.economy.coins = task.cost
    assert session.complete_task(task)
    assert session.story.is_done(task)
    assert session.economy.coins == 0


def test_blitz_pays_out_energy_and_coins():
    session = Session.new(random.Random(15))
    session.economy.energy = 0
    energy, coins = session.claim_blitz("blitz", 24000)
    assert energy > 0 and coins > 0
    assert session.economy.energy == energy
    assert session.best_blitz["blitz"] == 24000
    session.claim_blitz("blitz", 100)
    assert session.best_blitz["blitz"] == 24000, "a worse run must not count"


def test_a_session_survives_a_save_load_round_trip():
    session = Session.new(random.Random(16))
    session._gain_xp(4000)
    session.economy.coins = 12345
    session.story.complete(session.story.tasks()[0])
    session.best_blitz["blitz"] = 9876
    cell = next(c for c, i in session.board.items() if not i.is_generator)
    session.board.to_storage(cell)

    clone = Session.from_dict(json.loads(json.dumps(session.to_dict())),
                              random.Random(17))
    assert clone.economy.coins == 12345
    assert clone.economy.level == session.economy.level
    assert clone.story.done == session.story.done
    assert clone.best_blitz == session.best_blitz
    assert len(clone.board.storage) == len(session.board.storage)
    assert clone.to_dict()["board"] == session.to_dict()["board"]


def test_a_corrupt_save_does_not_crash_the_loader():
    for junk in ({}, {"board": {"cells": [[{"chain": "nope"}]]}},
                 {"economy": {"coins": "lots"}}, {"orders": {"active": [{}]}}):
        try:
            session = Session.from_dict(junk, random.Random(1))
        except (KeyError, TypeError, ValueError):
            continue  # save.load() catches these and starts a new farm
        assert session.economy.coins >= 0
        assert len(session.orders.active) == 3


def test_a_long_play_session_stays_consistent():
    """Grind taps, merges, deliveries and tasks; nothing may go negative."""
    rng = random.Random(21)
    session = Session.new(rng)
    for step in range(4000):
        session.economy.tick(3.0)
        gens = [c for c, i in session.board.items() if i.is_generator]
        if gens and not session.board.is_full:
            session.tap(rng.choice(gens))
        pairs = {}
        for cell, item in list(session.board.items()):
            if item.is_generator or item.tier >= MAX_TIER:
                continue
            key = (item.chain, item.tier)
            if key in pairs:
                session.drop(pairs.pop(key), cell)
            else:
                pairs[key] = cell
        for index in range(len(session.orders.active) - 1, -1, -1):
            session.deliver(index)
        task = session.story.next_task()
        if task is not None and session.economy.can_afford(task.cost):
            session.complete_task(task)
            if session.story.chapter_complete:
                session.advance_chapter()
        assert session.economy.coins >= 0
        assert session.economy.energy >= 0
        assert len(session.orders.active) == 3
        session.events.clear()
    assert session.economy.level > 1
    assert session.story.chapter > 0, "the story should have progressed"


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
