package com.barnyardblitz.engine

import kotlin.random.Random

/**
 * Match-3 rules for the Blitz minigame.
 *
 * Pure logic: the renderer reads [grid] and animates from the result objects
 * the mutating calls hand back.
 */
const val MATCH_ROWS = 8
const val MATCH_COLS = 8
const val MATCH_KINDS = 6
const val RUN_FOR_EGG = 4
const val RUN_FOR_ROOSTER = 5

/** Special critters, in the spirit of Bejeweled's flame/star/hypercube. */
enum class Power {
    NONE,
    /** Golden Egg: blasts the surrounding 3x3. */
    EGG,
    /** Hay Bale: clears the whole row and column. */
    HAY,
    /** Prize Rooster: clears every animal of one kind. */
    ROOSTER;

    val key: String get() = name.lowercase()
}

data class Tile(val kind: Int, val power: Power = Power.NONE, val uid: Int = 0) {
    val isSpecial: Boolean get() = power != Power.NONE
}

data class Run(val cells: List<Cell>, val horizontal: Boolean)

class Cluster(val kind: Int, val runs: MutableList<Run>, val cells: MutableSet<Cell>) {
    val longest: Int get() = runs.maxOf { it.cells.size }

    /** True for an L or T shape: a horizontal and a vertical run that meet. */
    val isCorner: Boolean
        get() = runs.any { it.horizontal } && runs.any { !it.horizontal }
}

data class SpecialSpawn(val cell: Cell, val kind: Int, val power: Power)

data class Effect(val name: String, val cell: Cell)

class MatchResult(
    val cleared: Map<Cell, Tile> = emptyMap(),
    val specials: List<SpecialSpawn> = emptyList(),
    val effects: List<Effect> = emptyList(),
    val clusters: List<Cluster> = emptyList(),
) {
    val isEmpty: Boolean get() = cleared.isEmpty()

    /** A reasonable spot to anchor a score popup. */
    val focus: Cell?
        get() = specials.firstOrNull()?.cell
            ?: clusters.firstOrNull()?.cells?.sortedWith(compareBy({ it.row }, { it.col }))
                ?.let { it[it.size / 2] }
            ?: cleared.keys.minWithOrNull(compareBy({ it.row }, { it.col }))
}

data class FallMove(val col: Int, val fromRow: Int, val toRow: Int)

data class Spawn(val col: Int, val row: Int, val height: Int)

class Match3Board(
    val rows: Int = MATCH_ROWS,
    val cols: Int = MATCH_COLS,
    val kinds: Int = MATCH_KINDS,
    private val random: Random = Random.Default,
) {
    private val grid: Array<Array<Tile?>> = Array(rows) { arrayOfNulls<Tile>(cols) }
    private var uid = 0

    init {
        reset()
    }

    fun inBounds(cell: Cell): Boolean = cell.row in 0 until rows && cell.col in 0 until cols

    fun at(cell: Cell): Tile? = if (inBounds(cell)) grid[cell.row][cell.col] else null

    fun put(cell: Cell, tile: Tile?) {
        if (inBounds(cell)) grid[cell.row][cell.col] = tile
    }

    fun allCells(): List<Cell> = (0 until rows).flatMap { r -> (0 until cols).map { c -> Cell(r, c) } }

    fun makeTile(kind: Int = random.nextInt(kinds), power: Power = Power.NONE): Tile =
        Tile(kind, power, ++uid)

    // ------------------------------------------------------------------- setup
    /** Deal a fresh board with no free matches but at least one legal move. */
    fun reset() {
        repeat(200) {
            deal()
            if (findRuns().isEmpty() && findHint() != null) return
        }
    }

    private fun deal() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val banned = mutableSetOf<Int>()
                if (c >= 2 && grid[r][c - 1]!!.kind == grid[r][c - 2]!!.kind) {
                    banned.add(grid[r][c - 1]!!.kind)
                }
                if (r >= 2 && grid[r - 1][c]!!.kind == grid[r - 2][c]!!.kind) {
                    banned.add(grid[r - 1][c]!!.kind)
                }
                val choices = (0 until kinds).filter { it !in banned }
                grid[r][c] = makeTile(choices[random.nextInt(choices.size)])
            }
        }
    }

    // ----------------------------------------------------------------- matches
    fun findRuns(): List<Run> {
        val runs = mutableListOf<Run>()
        for (r in 0 until rows) {
            var c = 0
            while (c < cols) {
                val end = runEnd(r, c, 0, 1)
                if (end - c >= 3) runs.add(Run((c until end).map { Cell(r, it) }, true))
                c = maxOf(end, c + 1)
            }
        }
        for (c in 0 until cols) {
            var r = 0
            while (r < rows) {
                val end = runEnd(r, c, 1, 0)
                if (end - r >= 3) runs.add(Run((r until end).map { Cell(it, c) }, false))
                r = maxOf(end, r + 1)
            }
        }
        return runs
    }

    /** Index just past the end of the same-kind run starting at (r, c). */
    private fun runEnd(r: Int, c: Int, dr: Int, dc: Int): Int {
        val start = grid[r][c]
        val pos = if (dr != 0) r else c
        if (start == null) return pos
        val limit = if (dr != 0) rows else cols
        var next = pos + 1
        while (next < limit) {
            val step = next - pos
            val tile = grid[r + dr * step][c + dc * step]
            if (tile == null || tile.kind != start.kind) break
            next++
        }
        return next
    }

    /** Group runs that overlap, so an L or T shape counts as one match. */
    fun findClusters(): List<Cluster> {
        val clusters = mutableListOf<Cluster>()
        for (run in findRuns()) {
            val cells = run.cells.toSet()
            val touching = clusters.filter { it.cells.any { cell -> cell in cells } }
            if (touching.isEmpty()) {
                val kind = at(run.cells.first())!!.kind
                clusters.add(Cluster(kind, mutableListOf(run), cells.toMutableSet()))
            } else {
                val head = touching.first()
                for (other in touching.drop(1)) {
                    head.runs.addAll(other.runs)
                    head.cells.addAll(other.cells)
                    clusters.remove(other)
                }
                head.runs.add(run)
                head.cells.addAll(cells)
            }
        }
        return clusters
    }

    // ---------------------------------------------------------------- clearing
    /**
     * Clear every current match, chaining any specials caught in the blast.
     *
     * [prefer] is the cell the player moved into; a special earned by the match
     * is created there so the reward lands where they were looking.
     */
    fun resolveMatches(prefer: Cell? = null): MatchResult {
        val clusters = findClusters()
        if (clusters.isEmpty()) return MatchResult()

        val seeds = mutableSetOf<Cell>()
        val specials = mutableListOf<SpecialSpawn>()
        for (cluster in clusters) {
            seeds.addAll(cluster.cells)
            val power = powerFor(cluster)
            if (power != Power.NONE) {
                specials.add(SpecialSpawn(spawnCell(cluster, prefer), cluster.kind, power))
            }
        }

        val (cleared, effects) = detonate(seeds)
        cleared.keys.forEach { put(it, null) }
        specials.forEach { put(it.cell, makeTile(it.kind, it.power)) }
        return MatchResult(cleared, specials, effects, clusters)
    }

    private fun powerFor(cluster: Cluster): Power = when {
        cluster.longest >= RUN_FOR_ROOSTER -> Power.ROOSTER
        cluster.isCorner -> Power.HAY
        cluster.longest == RUN_FOR_EGG -> Power.EGG
        else -> Power.NONE
    }

    private fun spawnCell(cluster: Cluster, prefer: Cell?): Cell {
        if (prefer != null && prefer in cluster.cells) return prefer
        if (cluster.isCorner) {
            val horizontal = cluster.runs.filter { it.horizontal }.flatMap { it.cells }.toSet()
            val vertical = cluster.runs.filter { !it.horizontal }.flatMap { it.cells }.toSet()
            val shared = horizontal intersect vertical
            shared.minWithOrNull(compareBy({ it.row }, { it.col }))?.let { return it }
        }
        val longest = cluster.runs.maxByOrNull { it.cells.size }!!
        return longest.cells[longest.cells.size / 2]
    }

    /**
     * Expand [seeds] into every cell that should clear, chaining specials.
     * Returns the cleared tiles (still in place) and the effects that fired.
     */
    fun detonate(
        seeds: Collection<Cell>,
        triggerKind: Int? = null,
    ): Pair<Map<Cell, Tile>, List<Effect>> {
        val cleared = LinkedHashMap<Cell, Tile>()
        val effects = mutableListOf<Effect>()
        val queue = ArrayDeque(seeds)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell in cleared) continue
            val tile = at(cell) ?: continue
            cleared[cell] = tile
            when (tile.power) {
                Power.EGG -> {
                    effects.add(Effect("egg", cell))
                    for (r in cell.row - 1..cell.row + 1) {
                        for (c in cell.col - 1..cell.col + 1) {
                            val probe = Cell(r, c)
                            if (inBounds(probe)) queue.add(probe)
                        }
                    }
                }
                Power.HAY -> {
                    effects.add(Effect("hay", cell))
                    (0 until cols).forEach { queue.add(Cell(cell.row, it)) }
                    (0 until rows).forEach { queue.add(Cell(it, cell.col)) }
                }
                Power.ROOSTER -> {
                    effects.add(Effect("rooster", cell))
                    val kind = triggerKind ?: mostCommonKind(cleared.keys)
                    if (kind != null) {
                        allCells().forEach { other ->
                            val t = at(other)
                            if (t != null && t.kind == kind && t.power != Power.ROOSTER) {
                                queue.add(other)
                            }
                        }
                    }
                }
                Power.NONE -> Unit
            }
        }
        return cleared to effects
    }

    fun mostCommonKind(skip: Set<Cell> = emptySet()): Int? {
        val counts = IntArray(kinds)
        var any = false
        for (cell in allCells()) {
            if (cell in skip) continue
            val tile = at(cell) ?: continue
            if (tile.isSpecial) continue
            counts[tile.kind]++
            any = true
        }
        if (!any) return null
        return counts.indices.maxByOrNull { counts[it] }
    }

    /** Fire a Prize Rooster that was swapped onto [other]. */
    fun activateRooster(rooster: Cell, other: Cell): MatchResult {
        val target = at(other)
        var triggerKind: Int? = null
        var seeds: Collection<Cell> = setOf(rooster)
        if (target != null && target.power == Power.ROOSTER) {
            seeds = allCells()  // two roosters: the whole barnyard goes up
        } else if (target != null) {
            triggerKind = target.kind
            seeds = setOf(rooster, other)
        }
        val (cleared, effects) = detonate(seeds, triggerKind)
        cleared.keys.forEach { put(it, null) }
        return MatchResult(cleared, emptyList(), listOf(Effect("rooster", rooster)) + effects)
    }

    /** End-of-round hurrah: set off one special still sitting on the board. */
    fun detonateAllSpecials(): MatchResult {
        for (cell in allCells()) {
            val tile = at(cell) ?: continue
            if (!tile.isSpecial) continue
            val (cleared, effects) = detonate(setOf(cell))
            cleared.keys.forEach { put(it, null) }
            return MatchResult(cleared, emptyList(), effects)
        }
        return MatchResult()
    }

    // ------------------------------------------------------------------ moving
    fun swap(a: Cell, b: Cell) {
        val tileA = at(a)
        put(a, at(b))
        put(b, tileA)
    }

    fun swapIsLegal(a: Cell, b: Cell): Boolean {
        if (!inBounds(a) || !inBounds(b) || !adjacent(a, b)) return false
        val ta = at(a) ?: return false
        val tb = at(b) ?: return false
        if (ta.power == Power.ROOSTER || tb.power == Power.ROOSTER) return true
        swap(a, b)
        val ok = matchesAt(a) || matchesAt(b)
        swap(a, b)
        return ok
    }

    private fun matchesAt(cell: Cell): Boolean {
        val tile = at(cell) ?: return false
        for ((dr, dc) in listOf(0 to 1, 1 to 0)) {
            var length = 1
            for (sign in listOf(1, -1)) {
                var step = 1
                while (true) {
                    val probe = Cell(cell.row + dr * step * sign, cell.col + dc * step * sign)
                    val other = at(probe)
                    if (other == null || other.kind != tile.kind) break
                    length++
                    step++
                }
            }
            if (length >= 3) return true
        }
        return false
    }

    fun findHint(): Pair<Cell, Cell>? {
        for (cell in allCells()) {
            for (other in listOf(Cell(cell.row, cell.col + 1), Cell(cell.row + 1, cell.col))) {
                if (inBounds(other) && swapIsLegal(cell, other)) return cell to other
            }
        }
        return null
    }

    fun hasMoves(): Boolean = findHint() != null

    /**
     * Drop tiles into holes and refill from above. [Spawn.height] counts how far
     * above the board a new tile starts, so the view can animate it in.
     */
    fun collapse(): Pair<List<FallMove>, List<Spawn>> {
        val moves = mutableListOf<FallMove>()
        val spawns = mutableListOf<Spawn>()
        for (c in 0 until cols) {
            var write = rows - 1
            for (r in rows - 1 downTo 0) {
                val tile = grid[r][c] ?: continue
                if (write != r) {
                    grid[write][c] = tile
                    grid[r][c] = null
                    moves.add(FallMove(c, r, write))
                }
                write--
            }
            for (r in write downTo 0) {
                grid[r][c] = makeTile()
                spawns.add(Spawn(c, r, write - r + 1))
            }
        }
        return moves to spawns
    }

    /** Re-deal the existing animals until a playable board falls out. */
    fun shuffle() {
        val tiles = allCells().mapNotNull { at(it) }.toMutableList()
        repeat(100) {
            tiles.shuffle(random)
            allCells().forEachIndexed { index, cell -> put(cell, tiles.getOrNull(index)) }
            if (findRuns().isEmpty() && hasMoves()) return
        }
        reset()
    }

    companion object {
        fun adjacent(a: Cell, b: Cell): Boolean = a.manhattan(b) == 1
    }
}
