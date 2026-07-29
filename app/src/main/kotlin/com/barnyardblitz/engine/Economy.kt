package com.barnyardblitz.engine

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Energy, coins and levelling - the pacing layer.
 *
 * Energy is the scarce resource: generators cost it and it trickles back on a
 * timer, which is what makes a session finite. A round of Blitz is the other
 * way to top it up.
 */
const val ENERGY_CAP = 60
const val SECONDS_PER_ENERGY = 25.0
const val START_ENERGY = 40
const val START_COINS = 50

/** XP needed to go from [level] to the next one. */
fun xpForLevel(level: Int): Int = (70.0 * level.toDouble().pow(1.35)).roundToInt()

class Economy(
    energy: Int = START_ENERGY,
    coins: Int = START_COINS,
    level: Int = 1,
    xp: Int = 0,
) {
    var energy: Int = energy
        private set
    var coins: Int = coins
        private set
    var level: Int = level
        private set
    var xp: Int = xp
        private set

    val energyCap: Int = ENERGY_CAP
    private var regen: Double = 0.0

    val energyFull: Boolean get() = energy >= energyCap

    val secondsToNextEnergy: Double
        get() = if (energyFull) 0.0 else (SECONDS_PER_ENERGY - regen).coerceAtLeast(0.0)

    /** Advance the regen timer. Returns how much energy trickled in. */
    fun tick(deltaSeconds: Double): Int {
        if (energyFull) {
            regen = 0.0
            return 0
        }
        regen += deltaSeconds
        var gained = 0
        while (regen >= SECONDS_PER_ENERGY && !energyFull) {
            regen -= SECONDS_PER_ENERGY
            energy++
            gained++
        }
        if (energyFull) regen = 0.0
        return gained
    }

    fun canSpend(amount: Int): Boolean = energy >= amount

    fun spendEnergy(amount: Int): Boolean {
        if (!canSpend(amount)) return false
        energy -= amount
        return true
    }

    /** Top up towards the cap. Returns how much actually landed. */
    fun addEnergy(amount: Int): Int {
        val before = energy
        energy = minOf(energyCap, energy + amount)
        return energy - before
    }

    fun canAfford(cost: Int): Boolean = coins >= cost

    fun spendCoins(cost: Int): Boolean {
        if (!canAfford(cost)) return false
        coins -= cost
        return true
    }

    fun addCoins(amount: Int) {
        if (amount > 0) coins += amount
    }

    val xpNeeded: Int get() = xpForLevel(level)

    val xpFraction: Float get() = (xp.toFloat() / maxOf(1, xpNeeded)).coerceIn(0f, 1f)

    /** Add XP and return the levels reached, if any. */
    fun addXp(amount: Int): List<Int> {
        if (amount > 0) xp += amount
        val reached = mutableListOf<Int>()
        while (xp >= xpNeeded) {
            xp -= xpNeeded
            level++
            reached.add(level)
        }
        return reached
    }

    fun toJson(): Map<String, Any?> = mapOf(
        "energy" to energy, "coins" to coins,
        "level" to level, "xp" to xp, "regen" to regen,
    )

    companion object {
        fun fromJson(data: Map<String, Any?>): Economy {
            val eco = Economy(
                energy = data.int("energy", START_ENERGY).coerceAtLeast(0),
                coins = data.int("coins", START_COINS).coerceAtLeast(0),
                level = data.int("level", 1).coerceAtLeast(1),
                xp = data.int("xp", 0).coerceAtLeast(0),
            )
            eco.regen = data.double("regen", 0.0).coerceAtLeast(0.0)
            return eco
        }
    }
}
