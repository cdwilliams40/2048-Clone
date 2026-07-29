"""Merge chain definitions.

Every chain climbs the same six-step ladder - a single thing, a bundle of
them, a basket, a crate, a cart and finally a building - so the artwork can be
composed from one set of containers plus a per-chain motif, and so players can
read an unfamiliar item's tier at a glance.

Pure data: no pygame, no game state.
"""

from __future__ import annotations

from dataclasses import dataclass

MAX_TIER = 5  # six tiers, 0..5


@dataclass(frozen=True)
class Generator:
    key: str
    name: str
    energy: int          # energy per tap
    bonus_chance: float  # chance of producing a tier-1 item instead of tier-0


@dataclass(frozen=True)
class Chain:
    key: str
    name: str
    tiers: tuple[str, ...]
    motif: str           # which motif the artwork stamps on the container
    palette: tuple       # (base, accent) for the motif
    unlock_level: int
    generator: Generator

    def tier_name(self, tier: int) -> str:
        return self.tiers[max(0, min(tier, len(self.tiers) - 1))]


CHAINS: dict[str, Chain] = {}


def _chain(key, name, tiers, motif, palette, unlock, gen_name, energy,
           bonus=0.18) -> Chain:
    chain = Chain(key, name, tuple(tiers), motif, palette, unlock,
                  Generator(f"{key}_gen", gen_name, energy, bonus))
    CHAINS[key] = chain
    return chain


_chain(
    "eggs", "Eggs",
    ["Egg", "Egg Trio", "Egg Basket", "Egg Crate", "Egg Cart", "Henhouse"],
    "egg", ((250, 240, 214), (232, 196, 108)), 1, "Nest Box", 1,
)
_chain(
    "crops", "Crops",
    ["Corn Cob", "Corn Bundle", "Corn Basket", "Produce Crate",
     "Harvest Cart", "Grain Silo"],
    "corn", ((246, 206, 74), (126, 176, 84), ), 2, "Veg Patch", 1,
)
_chain(
    "milk", "Dairy",
    ["Milk Bottle", "Milk Trio", "Milk Churn", "Dairy Crate", "Milk Float",
     "Creamery"],
    "bottle", ((248, 250, 252), (96, 150, 206)), 4, "Milking Stall", 2,
)
_chain(
    "wool", "Wool",
    ["Wool Puff", "Wool Bundle", "Yarn Basket", "Wool Crate", "Wool Wagon",
     "Spinning Shed"],
    "wool", ((236, 232, 240), (168, 142, 200)), 6, "Shearing Post", 2,
)
_chain(
    "tools", "Tools",
    ["Nail", "Bolt Bundle", "Tool Bucket", "Tool Crate", "Tool Cart",
     "Workshop"],
    "tool", ((176, 182, 190), (196, 88, 62)), 8, "Tool Rack", 3,
)
_chain(
    "jam", "Preserves",
    ["Berry", "Berry Bunch", "Jam Jar", "Preserve Crate", "Bakery Cart",
     "Farm Kitchen"],
    "berry", ((188, 74, 132), (120, 168, 92)), 10, "Jam Pot", 3,
)

CHAIN_ORDER = tuple(CHAINS)


def value_of(tier: int) -> int:
    """Coin value of an item, growing fast enough to make merging worthwhile."""
    return int(round(4 * 2.6 ** tier))


def xp_of(tier: int) -> int:
    return max(1, value_of(tier) // 3)


def unlocked_chains(level: int) -> list[Chain]:
    return [c for c in CHAINS.values() if c.unlock_level <= level]


def chains_unlocked_at(level: int) -> list[Chain]:
    return [c for c in CHAINS.values() if c.unlock_level == level]
