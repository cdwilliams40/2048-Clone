"""Energy, coins and levelling - the pacing layer.

Energy is the scarce resource: generators cost it and it trickles back on a
timer, which is what makes a session finite. Playing a round of Blitz is the
other way to top it up.
"""

from __future__ import annotations

from dataclasses import dataclass

ENERGY_CAP = 60
SECONDS_PER_ENERGY = 25.0
START_ENERGY = 40
START_COINS = 50


def xp_for_level(level: int) -> int:
    """XP needed to go from ``level`` to the next one."""
    return int(round(70 * level ** 1.35))


@dataclass
class Economy:
    energy: int = START_ENERGY
    energy_cap: int = ENERGY_CAP
    coins: int = START_COINS
    level: int = 1
    xp: int = 0
    _regen: float = 0.0

    # ------------------------------------------------------------------ energy
    @property
    def energy_full(self) -> bool:
        return self.energy >= self.energy_cap

    @property
    def seconds_to_next_energy(self) -> float:
        if self.energy_full:
            return 0.0
        return max(0.0, SECONDS_PER_ENERGY - self._regen)

    def tick(self, dt: float) -> int:
        """Advance the regen timer. Returns how much energy trickled in."""
        if self.energy_full:
            self._regen = 0.0
            return 0
        self._regen += dt
        gained = 0
        while self._regen >= SECONDS_PER_ENERGY and not self.energy_full:
            self._regen -= SECONDS_PER_ENERGY
            self.energy += 1
            gained += 1
        if self.energy_full:
            self._regen = 0.0
        return gained

    def can_spend(self, amount: int) -> bool:
        return self.energy >= amount

    def spend_energy(self, amount: int) -> bool:
        if not self.can_spend(amount):
            return False
        self.energy -= amount
        return True

    def add_energy(self, amount: int) -> int:
        """Top up, allowing overflow past the cap from bonuses."""
        before = self.energy
        self.energy = min(self.energy_cap, self.energy + amount)
        return self.energy - before

    # ------------------------------------------------------------------- coins
    def can_afford(self, cost: int) -> bool:
        return self.coins >= cost

    def spend_coins(self, cost: int) -> bool:
        if not self.can_afford(cost):
            return False
        self.coins -= cost
        return True

    def add_coins(self, amount: int) -> None:
        self.coins += max(0, amount)

    # -------------------------------------------------------------------- xp
    @property
    def xp_needed(self) -> int:
        return xp_for_level(self.level)

    @property
    def xp_fraction(self) -> float:
        return min(1.0, self.xp / max(1, self.xp_needed))

    def add_xp(self, amount: int) -> list[int]:
        """Add XP and return the list of levels reached, if any."""
        self.xp += max(0, amount)
        reached = []
        while self.xp >= self.xp_needed:
            self.xp -= self.xp_needed
            self.level += 1
            reached.append(self.level)
        return reached

    # ------------------------------------------------------------------- save
    def to_dict(self) -> dict:
        return {"energy": self.energy, "coins": self.coins,
                "level": self.level, "xp": self.xp, "regen": self._regen}

    @classmethod
    def from_dict(cls, data: dict) -> "Economy":
        eco = cls()
        eco.energy = max(0, int(data.get("energy", START_ENERGY)))
        eco.coins = max(0, int(data.get("coins", START_COINS)))
        eco.level = max(1, int(data.get("level", 1)))
        eco.xp = max(0, int(data.get("xp", 0)))
        eco._regen = float(data.get("regen", 0.0))
        return eco
