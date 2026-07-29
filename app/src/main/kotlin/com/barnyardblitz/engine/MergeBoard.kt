package com.barnyardblitz.engine

import kotlin.math.abs
import kotlin.random.Random

const val BOARD_ROWS = 8
const val BOARD_COLS = 7
const val STORAGE_SLOTS = 8

/** A board coordinate. Rows run top to bottom. */
data class Cell(val row: Int, val col: Int) {
    fun manhattan(other: Cell): Int = abs(row - other.row) + abs(col - other.col)
}

data class Item(
    val chain: String,
    val tier: Int = 0,
    /** Non-null when this item is a generator rather than merchandise. */
    val genKey: String? = null,
) {
    val isGenerator: Boolean get() = genKey != null

    val value: Int get() = valueOf(tier)

    val name: String
        get() = if (isGenerator) Chains[chain].generator.name else Chains[chain].tierName(tier)

    fun matches(other: Item): Boolean =
        !isGenerator && !other.isGenerator &&
            chain == other.chain && tier == other.tier && tier < MAX_TIER

    fun toJson(): Map<String, Any?> = buildMap {
        put("chain", chain)
        put("tier", tier)
        if (genKey != null) put("gen", genKey)
    }

    companion object {
        fun fromJson(data: Map<String, Any?>): Item? {
            val chain = data.str("chain")
            if (!Chains.exists(chain)) return null
            return Item(chain, data.int("tier").coerceIn(0, MAX_TIER), data["gen"] as? String)
        }
    }
}

enum class DropKind { MERGE, MOVE, SWAP, NONE }

data class DropResult(val kind: DropKind, val item: Item? = null, val at: Cell? = null)

/** The merge grid, plus the off-board storage shelf. */
class MergeBoard(val rows: Int = BOARD_ROWS, val cols: Int = BOARD_COLS) {

    private val cells: Array<Array<Item?>> = Array(rows) { arrayOfNulls<Item>(cols) }
    val storage: MutableList<Item> = mutableListOf()

    fun inBounds(cell: Cell): Boolean =
        cell.row in 0 until rows && cell.col in 0 until cols

    fun at(cell: Cell): Item? = if (inBounds(cell)) cells[cell.row][cell.col] else null

    fun put(cell: Cell, item: Item?) {
        if (inBounds(cell)) cells[cell.row][cell.col] = item
    }

    fun allCells(): List<Cell> = (0 until rows).flatMap { r -> (0 until cols).map { c -> Cell(r, c) } }

    fun occupied(): List<Pair<Cell, Item>> = allCells().mapNotNull { cell ->
        at(cell)?.let { cell to it }
    }

    fun freeCells(): List<Cell> = allCells().filter { at(it) == null }

    val isFull: Boolean get() = freeCells().isEmpty()

    fun count(chain: String, tier: Int): Int = occupied().count { (_, item) ->
        !item.isGenerator && item.chain == chain && item.tier == tier
    }

    // ------------------------------------------------------------------ layout
    /** The empty cell closest to [origin], so generator output lands nearby. */
    fun nearestFree(origin: Cell): Cell? = freeCells().minWithOrNull(
        compareBy({ it.manhattan(origin) }, { it.row }, { it.col }),
    )

    fun place(item: Item, near: Cell? = null): Cell? {
        val cell = (near?.let { nearestFree(it) }) ?: freeCells().firstOrNull() ?: return null
        put(cell, item)
        return cell
    }

    fun addGenerator(chainKey: String): Cell? =
        place(Item(chainKey, 0, Chains[chainKey].generator.key))

    // ----------------------------------------------------------------- actions
    /**
     * Run the generator at [cell]. Energy is the caller's business; this only
     * knows about space. Returns where the new item landed.
     */
    fun tap(cell: Cell, random: Random): Cell? {
        val item = at(cell) ?: return null
        if (!item.isGenerator) return null
        val gen = Chains[item.chain].generator
        val tier = if (random.nextDouble() < gen.bonusChance) 1 else 0
        return place(Item(item.chain, tier), near = cell)
    }

    /** Drag [src] onto [dst]: merge, move into a hole, or trade places. */
    fun drop(src: Cell, dst: Cell): DropResult {
        if (src == dst || !inBounds(src) || !inBounds(dst)) return DropResult(DropKind.NONE)
        val a = at(src) ?: return DropResult(DropKind.NONE)
        val b = at(dst)
        if (b == null) {
            put(dst, a)
            put(src, null)
            return DropResult(DropKind.MOVE, a, dst)
        }
        if (a.matches(b)) {
            val merged = Item(a.chain, a.tier + 1)
            put(dst, merged)
            put(src, null)
            return DropResult(DropKind.MERGE, merged, dst)
        }
        put(dst, a)
        put(src, b)
        return DropResult(DropKind.SWAP, a, dst)
    }

    fun sell(cell: Cell): Int {
        val item = at(cell) ?: return 0
        if (item.isGenerator) return 0
        put(cell, null)
        return item.value
    }

    /** Remove one matching item, preferring the board over storage. */
    fun take(chain: String, tier: Int): Boolean {
        val hit = occupied().firstOrNull { (_, item) ->
            !item.isGenerator && item.chain == chain && item.tier == tier
        }
        if (hit != null) {
            put(hit.first, null)
            return true
        }
        val index = storage.indexOfFirst { it.chain == chain && it.tier == tier }
        if (index >= 0) {
            storage.removeAt(index)
            return true
        }
        return false
    }

    fun has(chain: String, tier: Int, quantity: Int = 1): Boolean {
        val found = count(chain, tier) + storage.count { it.chain == chain && it.tier == tier }
        return found >= quantity
    }

    // ----------------------------------------------------------------- storage
    fun toStorage(cell: Cell): Boolean {
        val item = at(cell) ?: return false
        if (item.isGenerator || storage.size >= STORAGE_SLOTS) return false
        storage.add(item)
        put(cell, null)
        return true
    }

    fun fromStorage(index: Int): Boolean {
        if (index !in storage.indices) return false
        val target = nearestFree(Cell(rows / 2, cols / 2)) ?: return false
        put(target, storage.removeAt(index))
        return true
    }

    // -------------------------------------------------------------------- save
    fun toJson(): Map<String, Any?> = mapOf(
        "rows" to rows,
        "cols" to cols,
        "cells" to (0 until rows).map { r ->
            (0 until cols).map { c -> cells[r][c]?.toJson() }
        },
        "storage" to storage.map { it.toJson() },
    )

    companion object {
        fun fromJson(data: Map<String, Any?>): MergeBoard {
            val board = MergeBoard(
                data.int("rows", BOARD_ROWS).coerceIn(1, 24),
                data.int("cols", BOARD_COLS).coerceIn(1, 24),
            )
            data["cells"].asList().forEachIndexed { r, rowValue ->
                if (r >= board.rows) return@forEachIndexed
                rowValue.asList().forEachIndexed { c, cellValue ->
                    if (c >= board.cols) return@forEachIndexed
                    val map = cellValue as? Map<*, *> ?: return@forEachIndexed
                    @Suppress("UNCHECKED_CAST")
                    Item.fromJson(map as Map<String, Any?>)?.let { board.put(Cell(r, c), it) }
                }
            }
            data["storage"].asList().forEach { raw ->
                if (board.storage.size >= STORAGE_SLOTS) return@forEach
                val map = raw as? Map<*, *> ?: return@forEach
                @Suppress("UNCHECKED_CAST")
                Item.fromJson(map as Map<String, Any?>)?.let { board.storage.add(it) }
            }
            return board
        }
    }
}
