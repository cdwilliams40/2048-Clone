"""Customer orders - the loop that turns merged items into coins and XP."""

from __future__ import annotations

import random
from dataclasses import dataclass, field

from .board import MergeBoard
from .items import CHAINS, MAX_TIER, unlocked_chains, value_of, xp_of

ORDER_SLOTS = 3

# The regulars. ``kind`` indexes barnyard.config.ANIMALS for the portrait.
CUSTOMERS = [
    ("Buttercup", 0, ["Morning! The dairy ledger says I need these.",
                      "Be a dear and sort me out?",
                      "No rush. Well. Some rush."]),
    ("Hamlet", 1, ["You will NOT believe what I heard at the trough.",
                   "Darling, I'm hosting. I need this yesterday.",
                   "Trust me, this is for a very good cause."]),
    ("Henrietta", 2, ["Coop committee business. Very official.",
                      "The girls are counting on this order.",
                      "Chop chop, I've got eggs to inspect."]),
    ("Woolliam", 3, ["Sorry, is this a bad time? It's just a small thing.",
                     "I hate to ask. I'll ask anyway.",
                     "If it's no trouble. It might be trouble."]),
    ("Drake", 4, ["New in town, big plans. Start me off with these.",
                  "Consider it an investment opportunity.",
                  "Quack deal, take it or leave it."]),
    ("Clementine", 5, ["Back in my day we merged uphill. Both ways.",
                       "Humour an old mare, would you?",
                       "You remind me of your gran, you know."]),
]


@dataclass
class Request:
    chain: str
    tier: int
    quantity: int = 1

    @property
    def label(self) -> str:
        return CHAINS[self.chain].tier_name(self.tier)

    def to_dict(self) -> dict:
        return {"chain": self.chain, "tier": self.tier, "qty": self.quantity}

    @classmethod
    def from_dict(cls, data: dict) -> "Request":
        return cls(data["chain"], int(data["tier"]),
                   max(1, int(data.get("qty", 1))))


@dataclass
class Order:
    customer: str
    portrait: int
    line: str
    requests: list[Request] = field(default_factory=list)
    coins: int = 0
    xp: int = 0

    def filled_by(self, board: MergeBoard) -> bool:
        return all(board.has(r.chain, r.tier, r.quantity) for r in self.requests)

    def missing(self, board: MergeBoard) -> list[Request]:
        return [r for r in self.requests
                if not board.has(r.chain, r.tier, r.quantity)]

    def to_dict(self) -> dict:
        return {"customer": self.customer, "portrait": self.portrait,
                "line": self.line, "coins": self.coins, "xp": self.xp,
                "requests": [r.to_dict() for r in self.requests]}

    @classmethod
    def from_dict(cls, data: dict) -> "Order":
        return cls(data["customer"], int(data["portrait"]), data["line"],
                   [Request.from_dict(r) for r in data["requests"]],
                   int(data["coins"]), int(data["xp"]))


def _pick_tier(level: int, rng: random.Random) -> int:
    """Ask for tiers the player can plausibly reach at their level."""
    ceiling = min(MAX_TIER, 1 + level // 3)
    floor = max(0, ceiling - 2)
    return rng.randint(floor, ceiling)


def make_order(level: int, rng: random.Random) -> Order:
    available = unlocked_chains(level)
    name, portrait, lines = rng.choice(CUSTOMERS)
    count = 1 if level < 3 else rng.choice([1, 1, 2, 2, 3])
    requests: list[Request] = []
    for _ in range(count):
        chain = rng.choice(available)
        tier = _pick_tier(level, rng)
        for existing in requests:
            if existing.chain == chain.key and existing.tier == tier:
                existing.quantity += 1
                break
        else:
            requests.append(Request(chain.key, tier))

    worth = sum(value_of(r.tier) * r.quantity for r in requests)
    return Order(
        customer=name,
        portrait=portrait,
        line=rng.choice(lines),
        requests=requests,
        coins=max(6, int(round(worth * rng.uniform(1.5, 2.1)))),
        xp=max(2, sum(xp_of(r.tier) * r.quantity for r in requests)),
    )


@dataclass
class OrderBook:
    active: list[Order] = field(default_factory=list)

    def refill(self, level: int, rng: random.Random) -> None:
        while len(self.active) < ORDER_SLOTS:
            self.active.append(make_order(level, rng))

    def deliver(self, index: int, board: MergeBoard, level: int,
                rng: random.Random) -> Order | None:
        """Hand over an order's items. Returns the completed order, or None."""
        if not 0 <= index < len(self.active):
            return None
        order = self.active[index]
        if not order.filled_by(board):
            return None
        for request in order.requests:
            for _ in range(request.quantity):
                board.take(request.chain, request.tier)
        self.active.pop(index)
        self.refill(level, rng)
        return order

    def skip(self, index: int, level: int, rng: random.Random) -> None:
        """Replace an order the player never wants to fill."""
        if 0 <= index < len(self.active):
            self.active.pop(index)
            self.refill(level, rng)

    def to_dict(self) -> dict:
        return {"active": [o.to_dict() for o in self.active]}

    @classmethod
    def from_dict(cls, data: dict) -> "OrderBook":
        book = cls()
        for raw in data.get("active", []):
            try:
                order = Order.from_dict(raw)
            except (KeyError, TypeError, ValueError):
                continue
            if all(r.chain in CHAINS for r in order.requests):
                book.active.append(order)
        return book
