"""Match-3 board rules for Barnyard Blitz.

This module is deliberately free of pygame so the rules can be unit tested
without a display. The renderer reads the grid and the returned result
objects to drive its animations.
"""

from __future__ import annotations

import random
from collections import Counter, deque
from dataclasses import dataclass, field
from enum import Enum
from typing import Iterator, Optional

from . import config

Cell = tuple[int, int]


class Power(Enum):
    """Special animals, in the spirit of Bejeweled's flame/star/hypercube."""

    NONE = "none"
    EGG = "egg"          # Golden Egg   - blasts the surrounding 3x3
    HAY = "hay"          # Hay Bale     - clears the whole row and column
    ROOSTER = "rooster"  # Prize Rooster- clears every animal of one kind


@dataclass
class Tile:
    kind: int
    power: Power = Power.NONE
    uid: int = 0

    @property
    def is_special(self) -> bool:
        return self.power is not Power.NONE


@dataclass
class Run:
    cells: list[Cell]
    horizontal: bool


@dataclass
class Cluster:
    """One or more runs of the same kind that share at least one cell."""

    kind: int
    runs: list[Run]
    cells: set[Cell]

    @property
    def longest(self) -> int:
        return max(len(r.cells) for r in self.runs)

    @property
    def is_corner(self) -> bool:
        return any(r.horizontal for r in self.runs) and any(
            not r.horizontal for r in self.runs
        )


@dataclass
class MatchResult:
    """Everything the view needs to animate one clearing step."""

    cleared: dict[Cell, Tile] = field(default_factory=dict)
    specials: list[tuple[Cell, int, Power]] = field(default_factory=list)
    effects: list[tuple[str, Cell]] = field(default_factory=list)
    clusters: list[Cluster] = field(default_factory=list)

    def __bool__(self) -> bool:
        return bool(self.cleared)

    @property
    def focus(self) -> Optional[Cell]:
        """A reasonable spot to anchor a score popup."""
        if self.specials:
            return self.specials[0][0]
        if self.clusters:
            cells = sorted(self.clusters[0].cells)
            return cells[len(cells) // 2]
        if self.cleared:
            return sorted(self.cleared)[0]
        return None


class Board:
    def __init__(self, rows=config.ROWS, cols=config.COLS, kinds=config.KINDS,
                 rng: random.Random | None = None):
        self.rows = rows
        self.cols = cols
        self.kinds = kinds
        self.rng = rng or random.Random()
        self._uid = 0
        self.grid: list[list[Optional[Tile]]] = [
            [None] * cols for _ in range(rows)
        ]
        self.reset()

    # ------------------------------------------------------------------ basics
    def in_bounds(self, cell: Cell) -> bool:
        r, c = cell
        return 0 <= r < self.rows and 0 <= c < self.cols

    def at(self, cell: Cell) -> Optional[Tile]:
        r, c = cell
        if not self.in_bounds(cell):
            return None
        return self.grid[r][c]

    def put(self, cell: Cell, tile: Optional[Tile]) -> None:
        r, c = cell
        self.grid[r][c] = tile

    def cells(self) -> Iterator[Cell]:
        for r in range(self.rows):
            for c in range(self.cols):
                yield (r, c)

    def make_tile(self, kind: Optional[int] = None,
                  power: Power = Power.NONE) -> Tile:
        self._uid += 1
        if kind is None:
            kind = self.rng.randrange(self.kinds)
        return Tile(kind, power, self._uid)

    # ------------------------------------------------------------------- setup
    def reset(self) -> None:
        """Deal a fresh board with no free matches but at least one move."""
        for _ in range(200):
            self._deal()
            if not self.find_runs() and self.find_hint():
                return
        # Extremely unlikely; keep whatever we dealt rather than spinning.

    def _deal(self) -> None:
        for r in range(self.rows):
            for c in range(self.cols):
                banned = set()
                if c >= 2 and self.grid[r][c - 1].kind == self.grid[r][c - 2].kind:
                    banned.add(self.grid[r][c - 1].kind)
                if r >= 2 and self.grid[r - 1][c].kind == self.grid[r - 2][c].kind:
                    banned.add(self.grid[r - 1][c].kind)
                choices = [k for k in range(self.kinds) if k not in banned]
                self.grid[r][c] = self.make_tile(self.rng.choice(choices))

    # ----------------------------------------------------------------- matches
    def find_runs(self) -> list[Run]:
        runs: list[Run] = []
        for r in range(self.rows):
            c = 0
            while c < self.cols:
                run_end = self._run_end(r, c, 0, 1)
                if run_end - c >= 3:
                    runs.append(Run([(r, x) for x in range(c, run_end)], True))
                c = max(run_end, c + 1)
        for c in range(self.cols):
            r = 0
            while r < self.rows:
                run_end = self._run_end(r, c, 1, 0)
                if run_end - r >= 3:
                    runs.append(Run([(y, c) for y in range(r, run_end)], False))
                r = max(run_end, r + 1)
        return runs

    def _run_end(self, r: int, c: int, dr: int, dc: int) -> int:
        """Index just past the end of the same-kind run starting at (r, c)."""
        start = self.grid[r][c]
        pos = r if dr else c
        if start is None:
            return pos
        limit = self.rows if dr else self.cols
        nxt = pos + 1
        while nxt < limit:
            tile = self.grid[r + dr * (nxt - pos)][c + dc * (nxt - pos)]
            if tile is None or tile.kind != start.kind:
                break
            nxt += 1
        return nxt

    def find_clusters(self) -> list[Cluster]:
        """Group runs that overlap, so an L/T shape counts as one match."""
        runs = self.find_runs()
        clusters: list[Cluster] = []
        for run in runs:
            cells = set(run.cells)
            merged: list[Cluster] = []
            for cluster in clusters:
                if cluster.cells & cells:
                    merged.append(cluster)
            if merged:
                head = merged[0]
                for other in merged[1:]:
                    head.runs.extend(other.runs)
                    head.cells |= other.cells
                    clusters.remove(other)
                head.runs.append(run)
                head.cells |= cells
            else:
                kind = self.grid[run.cells[0][0]][run.cells[0][1]].kind
                clusters.append(Cluster(kind, [run], cells))
        return clusters

    # ----------------------------------------------------------------- clearing
    def resolve_matches(self, prefer: Optional[Cell] = None) -> MatchResult:
        """Clear every current match, chaining any specials caught in the blast.

        ``prefer`` is the cell the player moved into; when a special is earned
        it is created there so the reward lands where the player was looking.
        The grid is mutated immediately: cleared cells become ``None`` and any
        newly earned specials are placed. Callers animate from the result.
        """
        clusters = self.find_clusters()
        if not clusters:
            return MatchResult()

        result = MatchResult(clusters=clusters)
        seeds: set[Cell] = set()
        for cluster in clusters:
            seeds |= cluster.cells
            power = self._power_for(cluster)
            if power is not Power.NONE:
                result.specials.append(
                    (self._spawn_cell(cluster, prefer), cluster.kind, power)
                )

        cleared, effects = self.detonate(seeds)
        result.cleared = cleared
        result.effects = effects
        for cell in cleared:
            self.put(cell, None)
        for cell, kind, power in result.specials:
            self.put(cell, self.make_tile(kind, power))
        return result

    def _power_for(self, cluster: Cluster) -> Power:
        if cluster.longest >= config.RUN5_LEN:
            return Power.ROOSTER
        if cluster.is_corner:
            return Power.HAY
        if cluster.longest == config.RUN4_LEN:
            return Power.EGG
        return Power.NONE

    def _spawn_cell(self, cluster: Cluster, prefer: Optional[Cell]) -> Cell:
        if prefer is not None and prefer in cluster.cells:
            return prefer
        if cluster.is_corner:
            horiz = {c for r in cluster.runs if r.horizontal for c in r.cells}
            vert = {c for r in cluster.runs if not r.horizontal for c in r.cells}
            shared = horiz & vert
            if shared:
                return sorted(shared)[0]
        longest = max(cluster.runs, key=lambda r: len(r.cells))
        return longest.cells[len(longest.cells) // 2]

    def detonate(self, seeds, trigger_kind: Optional[int] = None
                 ) -> tuple[dict[Cell, Tile], list[tuple[str, Cell]]]:
        """Expand ``seeds`` into every cell that should clear, chaining specials.

        Returns the cleared tiles (still in place) plus the special effects that
        fired, in the order they fired, for the particle layer.
        """
        cleared: dict[Cell, Tile] = {}
        effects: list[tuple[str, Cell]] = []
        queue: deque[Cell] = deque(seeds)
        while queue:
            cell = queue.popleft()
            if cell in cleared:
                continue
            tile = self.at(cell)
            if tile is None:
                continue
            cleared[cell] = tile
            r, c = cell
            if tile.power is Power.EGG:
                effects.append(("egg", cell))
                for rr in range(r - 1, r + 2):
                    for cc in range(c - 1, c + 2):
                        if self.in_bounds((rr, cc)):
                            queue.append((rr, cc))
            elif tile.power is Power.HAY:
                effects.append(("hay", cell))
                queue.extend((r, cc) for cc in range(self.cols))
                queue.extend((rr, c) for rr in range(self.rows))
            elif tile.power is Power.ROOSTER:
                effects.append(("rooster", cell))
                kind = trigger_kind
                if kind is None:
                    kind = self.most_common_kind(skip=cleared.keys())
                if kind is not None:
                    queue.extend(
                        other for other in self.cells()
                        if (t := self.at(other)) and t.kind == kind
                        and t.power is not Power.ROOSTER
                    )
        return cleared, effects

    def most_common_kind(self, skip=()) -> Optional[int]:
        skip = set(skip)
        counts = Counter(
            t.kind for cell in self.cells()
            if cell not in skip and (t := self.at(cell)) and not t.is_special
        )
        return counts.most_common(1)[0][0] if counts else None

    def activate_rooster(self, rooster: Cell, other: Cell) -> MatchResult:
        """Fire a Prize Rooster that was swapped onto ``other``."""
        target = self.at(other)
        partner = self.at(rooster)
        trigger_kind = None
        seeds = {rooster}
        if target is not None and target.power is Power.ROOSTER:
            # Two roosters: the whole barnyard goes up.
            seeds = set(self.cells())
        elif target is not None:
            trigger_kind = target.kind
            seeds.add(other)
        cleared, effects = self.detonate(seeds, trigger_kind)
        if partner is not None:
            effects.insert(0, ("rooster", rooster))
        for cell in cleared:
            self.put(cell, None)
        return MatchResult(cleared=cleared, effects=effects)

    def detonate_all_specials(self) -> MatchResult:
        """End-of-round hurrah: set off one special still sitting on the board."""
        for cell in self.cells():
            tile = self.at(cell)
            if tile is not None and tile.is_special:
                cleared, effects = self.detonate({cell})
                for done in cleared:
                    self.put(done, None)
                return MatchResult(cleared=cleared, effects=effects)
        return MatchResult()

    # ------------------------------------------------------------------ moving
    def swap(self, a: Cell, b: Cell) -> None:
        ar, ac = a
        br, bc = b
        self.grid[ar][ac], self.grid[br][bc] = self.grid[br][bc], self.grid[ar][ac]

    @staticmethod
    def adjacent(a: Cell, b: Cell) -> bool:
        return abs(a[0] - b[0]) + abs(a[1] - b[1]) == 1

    def swap_is_legal(self, a: Cell, b: Cell) -> bool:
        if not (self.in_bounds(a) and self.in_bounds(b) and self.adjacent(a, b)):
            return False
        ta, tb = self.at(a), self.at(b)
        if ta is None or tb is None:
            return False
        if ta.power is Power.ROOSTER or tb.power is Power.ROOSTER:
            return True
        self.swap(a, b)
        ok = self._matches_at(a) or self._matches_at(b)
        self.swap(a, b)
        return ok

    def _matches_at(self, cell: Cell) -> bool:
        tile = self.at(cell)
        if tile is None:
            return False
        r, c = cell
        for dr, dc in ((0, 1), (1, 0)):
            length = 1
            for sign in (1, -1):
                step = 1
                while True:
                    probe = (r + dr * step * sign, c + dc * step * sign)
                    other = self.at(probe)
                    if other is None or other.kind != tile.kind:
                        break
                    length += 1
                    step += 1
            if length >= 3:
                return True
        return False

    def find_hint(self) -> Optional[tuple[Cell, Cell]]:
        for cell in self.cells():
            for other in ((cell[0], cell[1] + 1), (cell[0] + 1, cell[1])):
                if self.in_bounds(other) and self.swap_is_legal(cell, other):
                    return cell, other
        return None

    def has_moves(self) -> bool:
        return self.find_hint() is not None

    def collapse(self) -> tuple[list[tuple[int, int, int]],
                                list[tuple[int, int, int]]]:
        """Drop tiles into holes and refill from above.

        Returns ``(moves, spawns)`` where a move is ``(col, from_row, to_row)``
        and a spawn is ``(col, row, height)`` with ``height`` counting how far
        above the board the new tile starts.
        """
        moves: list[tuple[int, int, int]] = []
        spawns: list[tuple[int, int, int]] = []
        for c in range(self.cols):
            write = self.rows - 1
            for r in range(self.rows - 1, -1, -1):
                tile = self.grid[r][c]
                if tile is None:
                    continue
                if write != r:
                    self.grid[write][c] = tile
                    self.grid[r][c] = None
                    moves.append((c, r, write))
                write -= 1
            for r in range(write, -1, -1):
                self.grid[r][c] = self.make_tile()
                spawns.append((c, r, write - r + 1))
        return moves, spawns

    def shuffle(self) -> None:
        """Re-deal the existing animals until a playable board falls out."""
        tiles = [self.at(cell) for cell in self.cells()]
        for _ in range(100):
            self.rng.shuffle(tiles)
            for cell, tile in zip(self.cells(), tiles):
                self.put(cell, tile)
            if not self.find_runs() and self.has_moves():
                return
        self.reset()
