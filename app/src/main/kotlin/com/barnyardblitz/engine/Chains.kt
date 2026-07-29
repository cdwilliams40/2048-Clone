package com.barnyardblitz.engine

import kotlin.math.roundToInt

/**
 * Merge chain definitions.
 *
 * Every chain climbs the same six-step ladder - a single thing, a bundle, a
 * basket, a crate, a cart and finally a building - so the artwork composes from
 * one set of containers plus a per-chain motif, and a player can read an
 * unfamiliar item's tier from its silhouette.
 */
const val MAX_TIER = 5

/** Which motif the artwork stamps onto the container. */
enum class Motif { EGG, CORN, BOTTLE, WOOL, TOOL, BERRY }

data class GeneratorDef(
    val key: String,
    val name: String,
    /** Energy per tap. */
    val energy: Int,
    /** Chance of producing a tier-1 item instead of tier-0. */
    val bonusChance: Double,
)

data class Chain(
    val key: String,
    val displayName: String,
    val tiers: List<String>,
    val motif: Motif,
    /** Base and accent colours as 0xRRGGBB, used by the item artwork. */
    val base: Int,
    val accent: Int,
    /**
     * Colour of this chain's crates and carts. Chosen per chain rather than
     * derived from [accent], because two chains can share an accent (crops and
     * preserves are both green) and their containers must never look alike.
     */
    val wood: Int,
    val unlockLevel: Int,
    val generator: GeneratorDef,
) {
    fun tierName(tier: Int): String = tiers[tier.coerceIn(0, tiers.size - 1)]
}

object Chains {
    val all: List<Chain> = listOf(
        chain(
            "eggs", "Eggs",
            listOf("Egg", "Egg Trio", "Egg Basket", "Egg Crate", "Egg Cart", "Henhouse"),
            Motif.EGG, 0xFAF0D6, 0xE8C46C, 0xC08A46, 1, "Nest Box", 1,
        ),
        chain(
            "crops", "Crops",
            listOf("Corn Cob", "Corn Bundle", "Corn Basket", "Produce Crate", "Harvest Cart", "Grain Silo"),
            Motif.CORN, 0xF6CE4A, 0x7EB054, 0x6E9A46, 2, "Veg Patch", 1,
        ),
        chain(
            "milk", "Dairy",
            listOf("Milk Bottle", "Milk Trio", "Milk Churn", "Dairy Crate", "Milk Float", "Creamery"),
            Motif.BOTTLE, 0xF8FAFC, 0x6096CE, 0x5286BE, 4, "Milking Stall", 2,
        ),
        chain(
            "wool", "Wool",
            listOf("Wool Puff", "Wool Bundle", "Yarn Basket", "Wool Crate", "Wool Wagon", "Spinning Shed"),
            Motif.WOOL, 0xECE8F0, 0xA88EC8, 0x9078B8, 6, "Shearing Post", 2,
        ),
        chain(
            "tools", "Tools",
            listOf("Nail", "Bolt Bundle", "Tool Bucket", "Tool Crate", "Tool Cart", "Workshop"),
            Motif.TOOL, 0xB0B6BE, 0xC4583E, 0x8A8F98, 8, "Tool Rack", 3,
        ),
        chain(
            "jam", "Preserves",
            listOf("Berry", "Berry Bunch", "Jam Jar", "Preserve Crate", "Bakery Cart", "Farm Kitchen"),
            Motif.BERRY, 0xBC4A84, 0x78A85C, 0xA8506E, 10, "Jam Pot", 3,
        ),
    )

    private val byKey: Map<String, Chain> = all.associateBy { it.key }

    operator fun get(key: String): Chain =
        byKey[key] ?: throw IllegalArgumentException("unknown chain '$key'")

    fun exists(key: String): Boolean = key in byKey

    fun unlocked(level: Int): List<Chain> = all.filter { it.unlockLevel <= level }

    fun unlockedAt(level: Int): List<Chain> = all.filter { it.unlockLevel == level }

    private fun chain(
        key: String, name: String, tiers: List<String>, motif: Motif,
        base: Int, accent: Int, wood: Int, unlock: Int, generatorName: String, energy: Int,
    ) = Chain(
        key, name, tiers, motif, base, accent, wood, unlock,
        GeneratorDef("${key}_gen", generatorName, energy, 0.18),
    )
}

/** Coin value of an item, growing fast enough to make merging worthwhile. */
fun valueOf(tier: Int): Int = (4.0 * Math.pow(2.6, tier.toDouble())).roundToInt()

fun xpOf(tier: Int): Int = maxOf(1, valueOf(tier) / 3)
