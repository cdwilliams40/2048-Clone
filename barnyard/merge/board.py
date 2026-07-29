"""The merge grid: generators, merging, storage and selling.

Pure logic so it can be unit tested headlessly. The view reads ``cells`` and
animates from the small result objects the mutating calls return.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Iterator, Optional

from .items import CHAINS, MAX_TIER, value_of

Cell = tuple[int, int]

ROWS = 8
COLS = 7
STORAGE_SLOTS = 8


@dataclass
class Item:
    chain: str
    tier: int = 0
    gen_key: str | None = None   # set when this item is a generator

    @property
    def is_generator(self) -> bool:
        return self.gen_key is not None

    @property
    def value(self) -> int:
        return value_of(self.tier)

    @property
    def name(self) -> str:
        chain = CHAINS[self.chain]
        return chain.generator.name if self.is_generator \
            else chain.tier_name(self.tier)

    def matches(self, other: "Item") -> bool:
        return (not self.is_generator and not other.is_generator
                and self.chain == other.chain and self.tier == other.tier
                and self.tier < MAX_TIER)

    def to_dict(self) -> dict:
        data = {"chain": self.chain, "tier": self.tier}
        if self.gen_key:
            data["gen"] = self.gen_key
        return data

    @classmethod
    def from_dict(cls, data: dict) -> "Item":
        return cls(data["chain"], int(data.get("tier", 0)), data.get("gen"))


@dataclass
class DropResult:
    """What a drag from one cell to another did."""

    kind: str                       # "merge" | "move" | "swap" | "none"
    item: Optional[Item] = None     # the resulting item for a merge
    at: Optional[Cell] = None


@dataclass
class MergeBoard:
    rows: int = ROWS
    cols: int = COLS
    cells: list[list[Optional[Item]]] = field(default_factory=list)
    storage: list[Item] = field(default_factory=list)

    def __post_init__(self):
        if not self.cells:
            self.cells = [[None] * self.cols for _ in range(self.rows)]

    # ------------------------------------------------------------------ basics
    def in_bounds(self, cell: Cell) -> bool:
        r, c = cell
        return 0 <= r < self.rows and 0 <= c < self.cols

    def at(self, cell: Cell) -> Optional[Item]:
        if not self.in_bounds(cell):
            return None
        return self.cells[cell[0]][cell[1]]

    def put(self, cell: Cell, item: Optional[Item]) -> None:
        self.cells[cell[0]][cell[1]] = item

    def all_cells(self) -> Iterator[Cell]:
        for r in range(self.rows):
            for c in range(self.cols):
                yield (r, c)

    def items(self) -> Iterator[tuple[Cell, Item]]:
        for cell in self.all_cells():
            item = self.at(cell)
            if item is not None:
                yield cell, item

    def free_cells(self) -> list[Cell]:
        return [cell for cell in self.all_cells() if self.at(cell) is None]

    @property
    def is_full(self) -> bool:
        return not self.free_cells()

    def count(self, chain: str, tier: int) -> int:
        return sum(1 for _cell, item in self.items()
                   if item.chain == chain and item.tier == tier
                   and not item.is_generator)

    # ------------------------------------------------------------------ layout
    def nearest_free(self, origin: Cell) -> Optional[Cell]:
        """The empty cell closest to ``origin``, so output lands nearby."""
        free = self.free_cells()
        if not free:
            return None
        return min(free, key=lambda c: (abs(c[0] - origin[0])
                                        + abs(c[1] - origin[1]), c))

    def place(self, item: Item, near: Optional[Cell] = None) -> Optional[Cell]:
        cell = self.nearest_free(near) if near else None
        if cell is None:
            free = self.free_cells()
            if not free:
                return None
            cell = free[0]
        self.put(cell, item)
        return cell

    def add_generator(self, chain_key: str,
                      rng: random.Random | None = None) -> Optional[Cell]:
        gen = CHAINS[chain_key].generator
        return self.place(Item(chain_key, 0, gen.key))

    # ------------------------------------------------------------------ actions
    def tap(self, cell: Cell, rng: random.Random) -> Optional[Cell]:
        """Run a generator. Returns where the new item landed, or None.

        Energy is the caller's business; this only knows about space.
        """
        item = self.at(cell)
        if item is None or not item.is_generator:
            return None
        gen = CHAINS[item.chain].generator
        tier = 1 if rng.random() < gen.bonus_chance else 0
        return self.place(Item(item.chain, tier), near=cell)

    def drop(self, src: Cell, dst: Cell) -> DropResult:
        """Drag ``src`` onto ``dst``: merge, move into a hole, or swap."""
        if src == dst or not (self.in_bounds(src) and self.in_bounds(dst)):
            return DropResult("none")
        a = self.at(src)
        if a is None:
            return DropResult("none")
        b = self.at(dst)
        if b is None:
            self.put(dst, a)
            self.put(src, None)
            return DropResult("move", a, dst)
        if a.matches(b):
            merged = Item(a.chain, a.tier + 1)
            self.put(dst, merged)
            self.put(src, None)
            return DropResult("merge", merged, dst)
        # Generators and mismatched items simply trade places.
        self.put(dst, a)
        self.put(src, b)
        return DropResult("swap", a, dst)

    def sell(self, cell: Cell) -> int:
        item = self.at(cell)
        if item is None or item.is_generator:
            return 0
        self.put(cell, None)
        return item.value

    def take(self, chain: str, tier: int) -> bool:
        """Remove one matching item, preferring the board over storage."""
        for cell, item in self.items():
            if (not item.is_generator and item.chain == chain
                    and item.tier == tier):
                self.put(cell, None)
                return True
        for i, item in enumerate(self.storage):
            if item.chain == chain and item.tier == tier:
                self.storage.pop(i)
                return True
        return False

    def has(self, chain: str, tier: int, quantity: int = 1) -> bool:
        found = self.count(chain, tier)
        found += sum(1 for item in self.storage
                     if item.chain == chain and item.tier == tier)
        return found >= quantity

    # ----------------------------------------------------------------- storage
    def to_storage(self, cell: Cell) -> bool:
        item = self.at(cell)
        if item is None or item.is_generator:
            return False
        if len(self.storage) >= STORAGE_SLOTS:
            return False
        self.storage.append(item)
        self.put(cell, None)
        return True

    def from_storage(self, index: int, cell: Optional[Cell] = None) -> bool:
        if not 0 <= index < len(self.storage):
            return False
        target = cell if cell is not None and self.at(cell) is None \
            else self.nearest_free((self.rows // 2, self.cols // 2))
        if target is None:
            return False
        self.put(target, self.storage.pop(index))
        return True

    # -------------------------------------------------------------------- save
    def to_dict(self) -> dict:
        return {
            "rows": self.rows,
            "cols": self.cols,
            "cells": [[item.to_dict() if item else None for item in row]
                      for row in self.cells],
            "storage": [item.to_dict() for item in self.storage],
        }

    @classmethod
    def from_dict(cls, data: dict) -> "MergeBoard":
        rows = int(data.get("rows", ROWS))
        cols = int(data.get("cols", COLS))
        board = cls(rows, cols)
        for r, row in enumerate(data.get("cells", [])[:rows]):
            for c, cell in enumerate(row[:cols]):
                if cell and cell.get("chain") in CHAINS:
                    board.cells[r][c] = Item.from_dict(cell)
        board.storage = [Item.from_dict(d) for d in data.get("storage", [])
                         if d.get("chain") in CHAINS][:STORAGE_SLOTS]
        return board
