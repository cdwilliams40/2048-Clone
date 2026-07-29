package com.barnyardblitz.engine

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun `round trips nested structures`() {
        val original = mapOf(
            "n" to 12, "d" to 1.5, "s" to "hi \"there\"\n", "b" to true, "z" to null,
            "list" to listOf(1, 2, mapOf("deep" to listOf<Any?>())),
        )
        val decoded = Json.decode(Json.encode(original)).asMap()
        assertEquals(12, decoded.int("n"))
        assertEquals(1.5, decoded.double("d"), 1e-9)
        assertEquals("hi \"there\"\n", decoded.str("s"))
        assertEquals(true, decoded["b"])
        assertNull(decoded["z"])
        assertEquals(3, decoded["list"].asList().size)
    }

    @Test
    fun `whole numbers do not gain a decimal point`() {
        assertEquals("""{"a":3}""", Json.encode(mapOf("a" to 3.0)))
    }

    @Test
    fun `malformed input is rejected`() {
        for (bad in listOf("{", "[1,]", "\"unterminated", "{\"a\" 1}", "nope", "{} extra")) {
            var threw = false
            try {
                Json.decode(bad)
            } catch (_: Exception) {
                threw = true
            }
            assertTrue("expected '$bad' to fail", threw)
        }
    }

    @Test
    fun `accessors survive wrong types`() {
        val map = mapOf<String, Any?>("n" to "abc", "list" to 4)
        assertEquals(7, map.int("n", 7))
        assertEquals(0, map["list"].asList().size)
        assertEquals("", map.str("missing"))
    }
}

class MergeBoardTest {

    private fun board() = MergeBoard(4, 4)

    @Test
    fun `matching items merge into the next tier`() {
        val b = board()
        b.put(Cell(0, 0), Item("eggs", 1))
        b.put(Cell(0, 1), Item("eggs", 1))
        val result = b.drop(Cell(0, 0), Cell(0, 1))
        assertEquals(DropKind.MERGE, result.kind)
        assertNull(b.at(Cell(0, 0)))
        assertEquals(2, b.at(Cell(0, 1))!!.tier)
    }

    @Test
    fun `mismatched items swap instead of merging`() {
        val b = board()
        b.put(Cell(0, 0), Item("eggs", 1))
        b.put(Cell(0, 1), Item("eggs", 2))
        assertEquals(DropKind.SWAP, b.drop(Cell(0, 0), Cell(0, 1)).kind)
        assertEquals(2, b.at(Cell(0, 0))!!.tier)
        assertEquals(1, b.at(Cell(0, 1))!!.tier)
    }

    @Test
    fun `dropping onto a hole moves the item`() {
        val b = board()
        b.put(Cell(0, 0), Item("crops", 0))
        assertEquals(DropKind.MOVE, b.drop(Cell(0, 0), Cell(2, 2)).kind)
        assertNull(b.at(Cell(0, 0)))
        assertEquals("crops", b.at(Cell(2, 2))!!.chain)
    }

    @Test
    fun `the top tier cannot merge any further`() {
        val b = board()
        b.put(Cell(0, 0), Item("eggs", MAX_TIER))
        b.put(Cell(0, 1), Item("eggs", MAX_TIER))
        assertEquals(DropKind.SWAP, b.drop(Cell(0, 0), Cell(0, 1)).kind)
    }

    @Test
    fun `generators never merge`() {
        val b = board()
        b.put(Cell(0, 0), Item("eggs", 0, "eggs_gen"))
        b.put(Cell(0, 1), Item("eggs", 0, "eggs_gen"))
        assertEquals(DropKind.SWAP, b.drop(Cell(0, 0), Cell(0, 1)).kind)
        assertTrue(b.at(Cell(0, 1))!!.isGenerator)
    }

    @Test
    fun `a generator drops its output beside itself`() {
        val b = board()
        b.put(Cell(1, 1), Item("eggs", 0, "eggs_gen"))
        val landed = b.tap(Cell(1, 1), Random(3))
        assertNotNull(landed)
        assertEquals(1, landed!!.manhattan(Cell(1, 1)))
        assertFalse(b.at(landed)!!.isGenerator)
    }

    @Test
    fun `a full board refuses new output`() {
        val b = MergeBoard(2, 2)
        b.put(Cell(0, 0), Item("eggs", 0, "eggs_gen"))
        listOf(Cell(0, 1), Cell(1, 0), Cell(1, 1)).forEach { b.put(it, Item("eggs", 0)) }
        assertTrue(b.isFull)
        assertNull(b.tap(Cell(0, 0), Random(1)))
    }

    @Test
    fun `selling pays the item value and spares generators`() {
        val b = board()
        b.put(Cell(0, 0), Item("eggs", 3))
        b.put(Cell(0, 1), Item("eggs", 0, "eggs_gen"))
        assertEquals(valueOf(3), b.sell(Cell(0, 0)))
        assertNull(b.at(Cell(0, 0)))
        assertEquals(0, b.sell(Cell(0, 1)))
        assertNotNull(b.at(Cell(0, 1)))
    }

    @Test
    fun `storage round trips and is capped`() {
        val b = MergeBoard(8, 8)
        b.put(Cell(0, 0), Item("wool", 2))
        assertTrue(b.toStorage(Cell(0, 0)))
        assertNull(b.at(Cell(0, 0)))
        assertTrue(b.fromStorage(0))
        assertEquals(0, b.storage.size)
        assertEquals(1, b.occupied().size)

        repeat(STORAGE_SLOTS + 2) { i ->
            b.put(Cell(0, 0), Item("eggs", 0))
            assertEquals(i < STORAGE_SLOTS, b.toStorage(Cell(0, 0)))
        }
        assertEquals(STORAGE_SLOTS, b.storage.size)
    }

    @Test
    fun `finds a mergeable pair and ignores what cannot merge`() {
        val b = board()
        assertNull("nothing to merge yet", b.findMergePair())
        b.put(Cell(0, 0), Item("eggs", 0, "eggs_gen"))
        b.put(Cell(0, 1), Item("eggs", 0, "eggs_gen"))
        assertNull("two generators are not a pair", b.findMergePair())
        b.put(Cell(1, 0), Item("eggs", MAX_TIER))
        b.put(Cell(1, 1), Item("eggs", MAX_TIER))
        assertNull("top tier cannot merge", b.findMergePair())
        b.put(Cell(2, 0), Item("wool", 2))
        b.put(Cell(2, 1), Item("eggs", 2))
        assertNull("different chains are not a pair", b.findMergePair())
        b.put(Cell(2, 2), Item("wool", 2))
        val pair = b.findMergePair()
        assertNotNull(pair)
        assertEquals(setOf(Cell(2, 0), Cell(2, 2)), setOf(pair!!.first, pair.second))
    }

    @Test
    fun `has and take look in storage too`() {
        val b = board()
        b.put(Cell(0, 0), Item("eggs", 2))
        b.storage.add(Item("eggs", 2))
        assertTrue(b.has("eggs", 2, 2))
        assertFalse(b.has("eggs", 2, 3))
        assertTrue(b.take("eggs", 2))
        assertTrue(b.take("eggs", 2))
        assertFalse(b.take("eggs", 2))
    }
}

class EconomyTest {

    @Test
    fun `energy trickles back up to the cap`() {
        val eco = Economy(energy = 0)
        assertEquals(0, eco.tick(1.0))
        assertEquals(8, eco.tick(200.0))
        assertEquals(8, eco.energy)
        eco.addEnergy(ENERGY_CAP)
        assertEquals(0, eco.tick(1000.0))
        assertEquals(ENERGY_CAP, eco.energy)
    }

    @Test
    fun `energy is spent only when it is there`() {
        val eco = Economy(energy = 2)
        assertTrue(eco.spendEnergy(2))
        assertEquals(0, eco.energy)
        assertFalse(eco.spendEnergy(1))
        assertEquals(0, eco.energy)
    }

    @Test
    fun `levelling consumes xp and can skip several levels`() {
        val eco = Economy()
        assertTrue(eco.addXp(0).isEmpty())
        val levels = eco.addXp(xpForLevel(1) * 40)
        assertEquals((2..eco.level).toList(), levels)
        assertTrue(eco.xp < eco.xpNeeded)
        assertTrue(eco.xpFraction in 0f..1f)
    }

    @Test
    fun `coins cannot go negative`() {
        val eco = Economy(coins = 30)
        assertFalse(eco.spendCoins(31))
        assertEquals(30, eco.coins)
        assertTrue(eco.spendCoins(30))
        assertEquals(0, eco.coins)
    }
}

class OrdersTest {

    @Test
    fun `orders only ask for unlocked chains`() {
        val random = Random(9)
        repeat(60) {
            val order = makeOrder(1, random)
            for (request in order.requests) {
                assertTrue(Chains[request.chain].unlockLevel <= 1)
                assertTrue(request.tier in 0..MAX_TIER)
            }
            assertTrue(order.coins > 0)
            assertTrue(order.xp > 0)
        }
    }

    @Test
    fun `delivering consumes the items and refills the book`() {
        val random = Random(4)
        val board = MergeBoard(6, 6)
        val book = OrderBook()
        book.refill(1, random)
        val order = book.active[0]
        assertNull("nothing to hand over yet", book.deliver(0, board, 1, random))
        for (request in order.requests) {
            repeat(request.quantity) { board.place(Item(request.chain, request.tier)) }
        }
        assertTrue(order.filledBy(board))
        assertEquals(order, book.deliver(0, board, 1, random))
        assertEquals(ORDER_SLOTS, book.active.size)
        assertTrue(board.occupied().isEmpty())
    }

    @Test
    fun `skipping an order replaces it`() {
        val random = Random(2)
        val book = OrderBook()
        book.refill(1, random)
        val first = book.active[0]
        book.skip(0, 1, random)
        assertEquals(ORDER_SLOTS, book.active.size)
        assertFalse(book.active[0] === first)
    }
}

class StoryTest {

    @Test
    fun `a chapter advances once every task is done`() {
        val story = StoryProgress()
        val chapter = story.current!!
        assertEquals(CHAPTERS[0], chapter)
        for (task in chapter.tasks) {
            assertFalse(story.chapterComplete)
            assertTrue(story.complete(task))
            assertFalse("a task completes only once", story.complete(task))
        }
        assertTrue(story.chapterComplete)
        story.advance()
        assertEquals(CHAPTERS[1], story.current)
    }

    @Test
    fun `the story ends cleanly`() {
        val story = StoryProgress()
        while (!story.finished) {
            story.tasks().forEach { story.complete(it) }
            story.advance()
        }
        assertNull(story.current)
        assertTrue(story.tasks().isEmpty())
        assertNull(story.nextTask())
        assertEquals(story.totalTasks, story.done.size)
    }
}

class SessionTest {

    @Test
    fun `tapping a generator costs energy and makes an item`() {
        val session = Session.new(Random(11))
        val cell = session.board.occupied().first { it.second.isGenerator }.first
        val energyBefore = session.economy.energy
        val itemsBefore = session.board.occupied().size
        assertTrue(session.tap(cell))
        assertEquals(energyBefore - Chains["eggs"].generator.energy, session.economy.energy)
        assertEquals(itemsBefore + 1, session.board.occupied().size)
    }

    @Test
    fun `tapping without energy warns and changes nothing`() {
        val session = Session.new(Random(12))
        session.economy.spendEnergy(session.economy.energy)
        val cell = session.board.occupied().first { it.second.isGenerator }.first
        val before = session.board.occupied().size
        assertFalse(session.tap(cell))
        assertEquals(before, session.board.occupied().size)
        assertTrue(session.drain().any { it.kind == "warn" })
    }

    @Test
    fun `levelling up puts the new generator on the board`() {
        val session = Session.new(Random(13))
        assertEquals(setOf("eggs"), generatorChains(session))
        // A big delivery-sized XP dump should unlock everything.
        session.completeTaskForTest(50_000)
        assertEquals(Chains.all.map { it.key }.toSet(), generatorChains(session))
    }

    private fun generatorChains(session: Session): Set<String> =
        session.board.occupied().filter { it.second.isGenerator }.map { it.second.chain }.toSet()

    @Test
    fun `a task is paid for in coins`() {
        val session = Session.new(Random(14))
        val task = session.story.tasks()[0]
        session.economy.addCoins(task.cost - 1 - session.economy.coins)
        assertFalse(session.completeTask(task))
        assertFalse(session.story.isDone(task))
        session.economy.addCoins(1)
        assertTrue(session.completeTask(task))
        assertTrue(session.story.isDone(task))
    }

    @Test
    fun `merges are counted and survive a save`() {
        val session = Session.new(Random(31))
        assertEquals(0, session.merges)
        val pair = session.board.findMergePair()
        assertNotNull("a fresh farm starts with mergeable eggs", pair)
        session.drop(pair!!.first, pair.second)
        assertEquals(1, session.merges)
        val clone = Session.load(Json.encode(session.toJson()), Random(32))
        assertEquals(1, clone.merges)
    }

    @Test
    fun `blitz pays out energy and coins`() {
        val session = Session.new(Random(15))
        session.economy.spendEnergy(session.economy.energy)
        val (energy, coins) = session.claimBlitz("blitz", 24000)
        assertTrue(energy > 0)
        assertTrue(coins > 0)
        assertEquals(energy, session.economy.energy)
        assertEquals(24000, session.bestBlitz["blitz"])
        session.claimBlitz("blitz", 100)
        assertEquals("a worse run must not count", 24000, session.bestBlitz["blitz"])
    }

    @Test
    fun `a session survives a save load round trip`() {
        val session = Session.new(Random(16))
        session.completeTaskForTest(4000)
        session.economy.addCoins(12345)
        session.story.complete(session.story.tasks()[0])
        session.bestBlitz["blitz"] = 9876
        val cell = session.board.occupied().first { !it.second.isGenerator }.first
        session.board.toStorage(cell)

        val text = Json.encode(session.toJson())
        val clone = Session.load(text, Random(17))
        assertEquals(session.economy.coins, clone.economy.coins)
        assertEquals(session.economy.level, clone.economy.level)
        assertEquals(session.story.done, clone.story.done)
        assertEquals(session.bestBlitz, clone.bestBlitz)
        assertEquals(session.board.storage.size, clone.board.storage.size)
        assertEquals(
            Json.encode(session.board.toJson()),
            Json.encode(clone.board.toJson()),
        )
    }

    @Test
    fun `a corrupt save starts a new farm instead of crashing`() {
        for (junk in listOf(
            "", "not json", "{", "[]", "{}",
            """{"board":{"cells":[[{"chain":"nope"}]]}}""",
            """{"economy":{"coins":"lots"}}""",
            """{"orders":{"active":[{}]}}""",
        )) {
            val session = Session.load(junk, Random(1))
            assertTrue(session.economy.coins >= 0)
            assertEquals(ORDER_SLOTS, session.orders.active.size)
            assertTrue(session.board.occupied().any { it.second.isGenerator })
        }
    }

    @Test
    fun `a long play session stays consistent`() {
        val random = Random(21)
        val session = Session.new(random)
        repeat(4000) {
            session.economy.tick(3.0)
            val generators = session.board.occupied().filter { it.second.isGenerator }
            if (generators.isNotEmpty() && !session.board.isFull) {
                session.tap(generators[random.nextInt(generators.size)].first)
            }
            val pending = HashMap<Pair<String, Int>, Cell>()
            for ((cell, item) in session.board.occupied()) {
                if (item.isGenerator || item.tier >= MAX_TIER) continue
                val key = item.chain to item.tier
                val other = pending.remove(key)
                if (other != null) session.drop(other, cell) else pending[key] = cell
            }
            for (index in session.orders.active.indices.reversed()) session.deliver(index)
            val task = session.story.nextTask()
            if (task != null && session.economy.canAfford(task.cost)) {
                session.completeTask(task)
                if (session.story.chapterComplete) session.advanceChapter()
            }
            assertTrue(session.economy.coins >= 0)
            assertTrue(session.economy.energy >= 0)
            assertEquals(ORDER_SLOTS, session.orders.active.size)
            session.drain()
        }
        assertTrue("should have levelled up", session.economy.level > 1)
        assertTrue("the story should have progressed", session.story.chapter > 0)
    }
}

/** Test-only shortcut: award XP through the same path a delivery uses. */
private fun Session.completeTaskForTest(xp: Int) {
    val order = Order("Test", 0, "", listOf(Request("eggs", 0)), 0, xp)
    // Route through the public deliver path by staging the item first.
    board.place(Item("eggs", 0))
    orders.active.add(0, order)
    deliver(0)
}

class Match3Test {

    private fun boardOf(layout: String, kinds: Int = 6): Match3Board {
        val rows = layout.trim().lines().map { it.trim().split(" ") }
        val board = Match3Board(rows.size, rows[0].size, kinds, Random(7))
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, token ->
                board.put(Cell(r, c), board.makeTile(token.toInt()))
            }
        }
        return board
    }

    private val layout = """
        0 1 2 3 4 5 0 1
        1 2 3 4 5 0 1 2
        2 3 4 5 0 1 2 3
        3 4 5 0 1 2 3 4
        4 5 0 1 2 3 4 5
        5 0 1 2 3 4 5 0
        0 1 2 3 4 5 0 1
        1 2 3 4 5 0 1 2
    """

    @Test
    fun `a fresh board is playable`() {
        repeat(25) { seed ->
            val board = Match3Board(random = Random(seed))
            assertTrue("no free matches", board.findRuns().isEmpty())
            assertTrue("at least one move", board.hasMoves())
        }
    }

    @Test
    fun `finds a horizontal run`() {
        val board = boardOf(layout)
        listOf(2, 3, 4).forEach { board.put(Cell(3, it), board.makeTile(0)) }
        val runs = board.findRuns()
        assertEquals(1, runs.size)
        assertTrue(runs[0].horizontal)
        assertEquals(listOf(Cell(3, 2), Cell(3, 3), Cell(3, 4)), runs[0].cells)
    }

    @Test
    fun `swap is legal only when it makes a match`() {
        val board = boardOf(
            """
            1 0 2 3
            0 1 3 2
            1 2 0 3
            2 3 1 0
            """,
            kinds = 4,
        )
        assertTrue(board.findRuns().isEmpty())
        assertTrue(board.swapIsLegal(Cell(1, 0), Cell(1, 1)))
        assertFalse(board.swapIsLegal(Cell(0, 2), Cell(0, 3)))
        assertFalse("non-adjacent is illegal", board.swapIsLegal(Cell(0, 0), Cell(2, 0)))
        assertEquals("a legality check must not mutate", 0, board.at(Cell(1, 0))!!.kind)
    }

    @Test
    fun `three in a row clears without a special`() {
        val board = boardOf(layout)
        listOf(2, 3, 4).forEach { board.put(Cell(3, it), board.makeTile(0)) }
        val result = board.resolveMatches()
        assertEquals(3, result.cleared.size)
        assertTrue(result.specials.isEmpty())
        result.cleared.keys.forEach { assertNull(board.at(it)) }
    }

    @Test
    fun `four in a row makes a golden egg where the player moved`() {
        val board = boardOf(layout)
        listOf(2, 3, 4, 5).forEach { board.put(Cell(3, it), board.makeTile(0)) }
        val result = board.resolveMatches(prefer = Cell(3, 4))
        assertEquals(1, result.specials.size)
        assertEquals(Power.EGG, result.specials[0].power)
        assertEquals(Cell(3, 4), result.specials[0].cell)
        assertEquals(Power.EGG, board.at(Cell(3, 4))!!.power)
    }

    @Test
    fun `five in a row makes a prize rooster`() {
        val board = boardOf(layout)
        (1..5).forEach { board.put(Cell(3, it), board.makeTile(0)) }
        assertEquals(listOf(Power.ROOSTER), board.resolveMatches().specials.map { it.power })
    }

    @Test
    fun `a corner match makes a hay bale`() {
        val board = boardOf(
            """
            0 1 1 2
            0 2 1 3
            0 0 0 1
            3 1 2 3
            """,
            kinds = 4,
        )
        val result = board.resolveMatches()
        assertEquals(listOf(Power.HAY), result.specials.map { it.power })
        assertTrue(Cell(2, 0) in result.cleared)
        assertTrue(Cell(0, 0) in result.cleared)
    }

    @Test
    fun `a golden egg blasts its neighbourhood`() {
        val board = boardOf(layout)
        board.put(Cell(4, 4), Tile(1, Power.EGG, 99))
        val (cleared, effects) = board.detonate(setOf(Cell(4, 4)))
        assertEquals(9, cleared.size)
        assertTrue(effects.contains(Effect("egg", Cell(4, 4))))
    }

    @Test
    fun `a hay bale clears its row and column`() {
        val board = boardOf(layout)
        board.put(Cell(2, 5), Tile(1, Power.HAY, 99))
        val (cleared, _) = board.detonate(setOf(Cell(2, 5)))
        assertEquals(board.rows + board.cols - 1, cleared.size)
    }

    @Test
    fun `a rooster swap clears a whole species`() {
        val board = boardOf(layout)
        board.put(Cell(0, 0), Tile(0, Power.ROOSTER, 99))
        val targetKind = board.at(Cell(0, 1))!!.kind
        val expected = board.allCells().count { cell ->
            val tile = board.at(cell)
            tile != null && tile.kind == targetKind && tile.power != Power.ROOSTER
        }
        val result = board.activateRooster(Cell(0, 0), Cell(0, 1))
        assertEquals(expected + 1, result.cleared.size)
        assertNull(board.at(Cell(0, 0)))
    }

    @Test
    fun `specials chain when caught in a blast`() {
        val board = boardOf(layout)
        board.put(Cell(4, 4), Tile(1, Power.EGG, 1))
        board.put(Cell(4, 5), Tile(2, Power.HAY, 2))
        val (cleared, effects) = board.detonate(setOf(Cell(4, 4)))
        val names = effects.map { it.name }
        assertTrue(names.contains("egg"))
        assertTrue(names.contains("hay"))
        assertTrue("the hay bale widens the blast", cleared.size > 9)
    }

    @Test
    fun `collapse drops tiles and refills`() {
        val board = boardOf(layout)
        listOf(5, 6, 7).forEach { board.put(Cell(it, 2), null) }
        val topUid = board.at(Cell(0, 2))!!.uid
        val (moves, spawns) = board.collapse()
        board.allCells().forEach { assertNotNull(board.at(it)) }
        assertEquals(3, spawns.size)
        assertEquals("column 2 slid down three rows", topUid, board.at(Cell(3, 2))!!.uid)
        assertTrue(moves.contains(FallMove(2, 0, 3)))
    }

    @Test
    fun `shuffle keeps the same animals and stays playable`() {
        val board = boardOf(layout)
        val before = board.allCells().map { board.at(it)!!.kind }.sorted()
        board.shuffle()
        val after = board.allCells().map { board.at(it)!!.kind }.sorted()
        assertEquals(before, after)
        assertTrue(board.hasMoves())
        assertTrue(board.findRuns().isEmpty())
    }

    @Test
    fun `a full random game never wedges`() {
        val board = Match3Board(random = Random(1234))
        repeat(300) {
            var move = board.findHint()
            if (move == null) {
                board.shuffle()
                move = board.findHint()
            }
            assertNotNull(move)
            val (a, b) = move!!
            board.swap(a, b)
            var guard = 0
            while (true) {
                val ta = board.at(a)
                val tb = board.at(b)
                val result = when {
                    ta != null && ta.power == Power.ROOSTER -> board.activateRooster(a, b)
                    tb != null && tb.power == Power.ROOSTER -> board.activateRooster(b, a)
                    else -> board.resolveMatches(prefer = b)
                }
                if (result.isEmpty) break
                board.collapse()
                guard++
                assertTrue("cascades should terminate", guard < 200)
            }
            board.allCells().forEach { assertNotNull(board.at(it)) }
        }
    }
}

class SfxTest {

    @Test
    fun `every effect renders non-silent audio of a sane length`() {
        val all = Sfx.buildAll()
        assertTrue(all.containsKey(Sfx.ROOSTER))
        assertTrue(all.containsKey(Sfx.matchName(0)))
        assertTrue(all.containsKey(Sfx.matchName(99)))
        for ((name, samples) in all) {
            assertTrue("$name is empty", samples.isNotEmpty())
            assertTrue("$name is too long", samples.size < Sfx.RATE * 2)
            assertTrue("$name is silent", samples.any { it.toInt() != 0 })
            assertTrue("$name clips", samples.all { it > Short.MIN_VALUE })
        }
    }
}
