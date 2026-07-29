"""The whole save-able game state, and the rules that tie the systems together.

A :class:`Session` owns the merge board, the economy, the order book and the
story, mediates every action that touches more than one of them, and queues
short ``events`` the UI drains for toasts and popups.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Any

from . import economy as eco_mod
from .board import Item, MergeBoard
from .economy import Economy
from .items import CHAINS, chains_unlocked_at, unlocked_chains
from .orders import OrderBook
from .story import CHAPTERS, StoryProgress, Task

SAVE_VERSION = 1


@dataclass
class Event:
    kind: str
    text: str
    payload: Any = None


@dataclass
class Session:
    board: MergeBoard = field(default_factory=MergeBoard)
    economy: Economy = field(default_factory=Economy)
    orders: OrderBook = field(default_factory=OrderBook)
    story: StoryProgress = field(default_factory=StoryProgress)
    best_blitz: dict[str, int] = field(default_factory=dict)
    events: list[Event] = field(default_factory=list)
    rng: random.Random = field(default_factory=random.Random)

    # -------------------------------------------------------------------- new
    @classmethod
    def new(cls, rng: random.Random | None = None) -> "Session":
        session = cls(rng=rng or random.Random())
        session.board.add_generator("eggs")
        for _ in range(6):
            session.board.place(Item("eggs", 0))
        for _ in range(2):
            session.board.place(Item("eggs", 1))
        session.orders.refill(session.economy.level, session.rng)
        return session

    def say(self, kind: str, text: str, payload: Any = None) -> None:
        self.events.append(Event(kind, text, payload))

    def drain(self) -> list[Event]:
        events, self.events = self.events, []
        return events

    # ------------------------------------------------------------------- tick
    def tick(self, dt: float) -> None:
        self.economy.tick(dt)

    # ---------------------------------------------------------------- actions
    def tap(self, cell) -> bool:
        """Run the generator at ``cell``, paying its energy cost."""
        item = self.board.at(cell)
        if item is None or not item.is_generator:
            return False
        gen = CHAINS[item.chain].generator
        if not self.economy.can_spend(gen.energy):
            self.say("warn", "Out of energy - play Blitz or wait for a refill")
            return False
        if self.board.is_full:
            self.say("warn", "The yard is full - sell or store something")
            return False
        self.economy.spend_energy(gen.energy)
        landed = self.board.tap(cell, self.rng)
        return landed is not None

    def drop(self, src, dst):
        result = self.board.drop(src, dst)
        if result.kind == "merge" and result.item is not None:
            self._gain_xp(max(1, result.item.tier * 2))
            self.say("merge", result.item.name, result)
        return result

    def sell(self, cell) -> int:
        coins = self.board.sell(cell)
        if coins:
            self.economy.add_coins(coins)
            self.say("coins", f"+{coins} coins")
        return coins

    def deliver(self, index: int):
        order = self.orders.deliver(index, self.board, self.economy.level,
                                    self.rng)
        if order is None:
            return None
        self.economy.add_coins(order.coins)
        self.say("order", f"{order.customer}: +{order.coins} coins")
        self._gain_xp(order.xp)
        return order

    def skip_order(self, index: int) -> None:
        self.orders.skip(index, self.economy.level, self.rng)

    def store(self, cell) -> bool:
        if self.board.to_storage(cell):
            return True
        self.say("warn", "Storage is full")
        return False

    def unstore(self, index: int) -> bool:
        if self.board.from_storage(index):
            return True
        self.say("warn", "No room in the yard")
        return False

    # ------------------------------------------------------------------ story
    def can_start_task(self, task: Task) -> bool:
        return (not self.story.is_done(task)
                and self.economy.can_afford(task.cost))

    def complete_task(self, task: Task) -> bool:
        if self.story.is_done(task) or not self.economy.spend_coins(task.cost):
            return False
        self.story.complete(task)
        self.say("task", task.title)
        self._gain_xp(max(5, task.cost // 25))
        return True

    def advance_chapter(self) -> None:
        self.story.advance()
        chapter = self.story.current
        if chapter is not None:
            self.say("chapter", chapter.title)

    # ------------------------------------------------------------------ blitz
    @staticmethod
    def blitz_reward(score: int) -> tuple[int, int]:
        """Convert a Blitz score into (energy, coins)."""
        return min(30, 5 + score // 1200), 20 + score // 30

    def claim_blitz(self, mode: str, score: int) -> tuple[int, int]:
        energy, coins = self.blitz_reward(score)
        gained = self.economy.add_energy(energy)
        self.economy.add_coins(coins)
        if score > self.best_blitz.get(mode, 0):
            self.best_blitz[mode] = score
        self.say("blitz", f"+{gained} energy, +{coins} coins")
        return gained, coins

    # ----------------------------------------------------------------- levels
    def _gain_xp(self, amount: int) -> None:
        for level in self.economy.add_xp(amount):
            self.say("level", f"Level {level}!")
            for chain in chains_unlocked_at(level):
                self.say("unlock", f"{chain.generator.name} unlocked!")
        self._place_new_generators()

    def _place_new_generators(self) -> None:
        """Give every unlocked chain a generator on the board."""
        present = {item.chain for _cell, item in self.board.items()
                   if item.is_generator}
        for chain in unlocked_chains(self.economy.level):
            if chain.key in present:
                continue
            if self.board.add_generator(chain.key) is None:
                self.say("warn",
                         f"No room for the {chain.generator.name} yet")
                return

    # ------------------------------------------------------------------- save
    def to_dict(self) -> dict:
        return {
            "version": SAVE_VERSION,
            "board": self.board.to_dict(),
            "economy": self.economy.to_dict(),
            "orders": self.orders.to_dict(),
            "story": self.story.to_dict(),
            "best_blitz": self.best_blitz,
        }

    @classmethod
    def from_dict(cls, data: dict,
                  rng: random.Random | None = None) -> "Session":
        session = cls(rng=rng or random.Random())
        session.board = MergeBoard.from_dict(data.get("board", {}))
        session.economy = Economy.from_dict(data.get("economy", {}))
        session.orders = OrderBook.from_dict(data.get("orders", {}))
        session.story = StoryProgress.from_dict(data.get("story", {}))
        best = data.get("best_blitz", {})
        session.best_blitz = {str(k): int(v) for k, v in best.items()
                              if isinstance(v, (int, float))}
        session.orders.refill(session.economy.level, session.rng)
        session._place_new_generators()
        session.events.clear()
        return session

    # ---------------------------------------------------------------- summary
    @property
    def progress_fraction(self) -> float:
        total = sum(len(c.tasks) for c in CHAPTERS)
        return len(self.story.done) / max(1, total)

    @property
    def energy_seconds(self) -> float:
        return eco_mod.SECONDS_PER_ENERGY
