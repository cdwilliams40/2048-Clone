package com.barnyardblitz.engine

import kotlin.random.Random

/**
 * The whole save-able game state, and the rules that tie the systems together.
 *
 * A Session owns the merge board, the economy, the order book and the story,
 * mediates every action that touches more than one of them, and queues short
 * [events] the UI drains for toasts.
 */
const val SAVE_VERSION = 1

data class GameEvent(val kind: String, val text: String)

class Session(val random: Random = Random.Default) {

    var board: MergeBoard = MergeBoard()
        private set
    var economy: Economy = Economy()
        private set
    var orders: OrderBook = OrderBook()
        private set
    var story: StoryProgress = StoryProgress()
        private set
    val bestBlitz: MutableMap<String, Int> = mutableMapOf()

    private val eventQueue: MutableList<GameEvent> = mutableListOf()

    val events: List<GameEvent> get() = eventQueue

    fun say(kind: String, text: String) {
        eventQueue.add(GameEvent(kind, text))
    }

    fun drain(): List<GameEvent> {
        val out = eventQueue.toList()
        eventQueue.clear()
        return out
    }

    fun tick(deltaSeconds: Double) {
        economy.tick(deltaSeconds)
    }

    // ---------------------------------------------------------------- actions
    /** Run the generator at [cell], paying its energy cost. */
    fun tap(cell: Cell): Boolean {
        val item = board.at(cell) ?: return false
        if (!item.isGenerator) return false
        val gen = Chains[item.chain].generator
        if (!economy.canSpend(gen.energy)) {
            say("warn", "Out of energy - play Blitz or wait for a refill")
            return false
        }
        if (board.isFull) {
            say("warn", "The yard is full - sell or store something")
            return false
        }
        economy.spendEnergy(gen.energy)
        return board.tap(cell, random) != null
    }

    fun drop(src: Cell, dst: Cell): DropResult {
        val result = board.drop(src, dst)
        if (result.kind == DropKind.MERGE && result.item != null) {
            gainXp(maxOf(1, result.item.tier * 2))
        }
        return result
    }

    fun sell(cell: Cell): Int {
        val coins = board.sell(cell)
        if (coins > 0) {
            economy.addCoins(coins)
            say("coins", "+$coins coins")
        }
        return coins
    }

    fun deliver(index: Int): Order? {
        val order = orders.deliver(index, board, economy.level, random) ?: return null
        economy.addCoins(order.coins)
        say("order", "${order.customer}: +${order.coins} coins")
        gainXp(order.xp)
        return order
    }

    fun skipOrder(index: Int) = orders.skip(index, economy.level, random)

    fun store(cell: Cell): Boolean {
        if (board.toStorage(cell)) return true
        say("warn", "Storage is full")
        return false
    }

    fun unstore(index: Int): Boolean {
        if (board.fromStorage(index)) return true
        say("warn", "No room in the yard")
        return false
    }

    // ------------------------------------------------------------------ story
    fun canStartTask(task: Task): Boolean =
        !story.isDone(task) && economy.canAfford(task.cost)

    fun completeTask(task: Task): Boolean {
        if (story.isDone(task) || !economy.spendCoins(task.cost)) return false
        story.complete(task)
        say("task", task.title)
        gainXp(maxOf(5, task.cost / 25))
        return true
    }

    fun advanceChapter() {
        story.advance()
        story.current?.let { say("chapter", it.title) }
    }

    // ------------------------------------------------------------------ blitz
    fun claimBlitz(mode: String, score: Int): Pair<Int, Int> {
        val (energy, coins) = blitzReward(score)
        val gained = economy.addEnergy(energy)
        economy.addCoins(coins)
        if (score > (bestBlitz[mode] ?: 0)) bestBlitz[mode] = score
        say("blitz", "+$gained energy, +$coins coins")
        return gained to coins
    }

    // ----------------------------------------------------------------- levels
    private fun gainXp(amount: Int) {
        for (level in economy.addXp(amount)) {
            say("level", "Level $level!")
            for (chain in Chains.unlockedAt(level)) {
                say("unlock", "${chain.generator.name} unlocked!")
            }
        }
        placeNewGenerators()
    }

    /** Give every unlocked chain a generator on the board. */
    fun placeNewGenerators() {
        val present = board.occupied().filter { it.second.isGenerator }
            .map { it.second.chain }.toSet()
        for (chain in Chains.unlocked(economy.level)) {
            if (chain.key in present) continue
            if (board.addGenerator(chain.key) == null) {
                say("warn", "No room for the ${chain.generator.name} yet")
                return
            }
        }
    }

    // ------------------------------------------------------------------- save
    fun toJson(): Map<String, Any?> = mapOf(
        "version" to SAVE_VERSION,
        "board" to board.toJson(),
        "economy" to economy.toJson(),
        "orders" to orders.toJson(),
        "story" to story.toJson(),
        "best_blitz" to bestBlitz,
    )

    val progressFraction: Float
        get() = story.done.size.toFloat() / maxOf(1, CHAPTERS.sumOf { it.tasks.size })

    companion object {
        /** Convert a Blitz score into (energy, coins). */
        fun blitzReward(score: Int): Pair<Int, Int> =
            minOf(30, 5 + score / 1200) to (20 + score / 30)

        fun new(random: Random = Random.Default): Session {
            val session = Session(random)
            session.board.addGenerator("eggs")
            repeat(6) { session.board.place(Item("eggs", 0)) }
            repeat(2) { session.board.place(Item("eggs", 1)) }
            session.orders.refill(session.economy.level, random)
            return session
        }

        fun fromJson(data: Map<String, Any?>, random: Random = Random.Default): Session {
            val session = Session(random)
            session.board = MergeBoard.fromJson(data["board"].asMap())
            session.economy = Economy.fromJson(data["economy"].asMap())
            session.orders = OrderBook.fromJson(data["orders"].asMap())
            session.story = StoryProgress.fromJson(data["story"].asMap())
            data["best_blitz"].asMap().forEach { (key, value) ->
                if (value is Number) session.bestBlitz[key] = value.toInt()
            }
            session.orders.refill(session.economy.level, random)
            session.placeNewGenerators()
            session.eventQueue.clear()
            return session
        }

        /** Load from serialised text, falling back to a fresh farm. */
        fun load(text: String?, random: Random = Random.Default): Session {
            if (text.isNullOrBlank()) return new(random)
            return try {
                fromJson(Json.decode(text).asMap(), random)
            } catch (_: Exception) {
                // A corrupt or older save must not brick the game.
                new(random)
            }
        }
    }
}
