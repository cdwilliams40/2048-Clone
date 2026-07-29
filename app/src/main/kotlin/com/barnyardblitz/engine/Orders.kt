package com.barnyardblitz.engine

import kotlin.math.roundToInt
import kotlin.random.Random

const val ORDER_SLOTS = 3

/** The regulars. [portrait] indexes the animal artwork; -1 is the player. */
data class Customer(val name: String, val portrait: Int, val lines: List<String>)

val CUSTOMERS: List<Customer> = listOf(
    Customer("Buttercup", 0, listOf(
        "Morning! The dairy ledger says I need these.",
        "Be a dear and sort me out?",
        "No rush. Well. Some rush.",
    )),
    Customer("Hamlet", 1, listOf(
        "You will NOT believe what I heard at the trough.",
        "Darling, I'm hosting. I need this yesterday.",
        "Trust me, this is for a very good cause.",
    )),
    Customer("Henrietta", 2, listOf(
        "Coop committee business. Very official.",
        "The girls are counting on this order.",
        "Chop chop, I've got eggs to inspect.",
    )),
    Customer("Woolliam", 3, listOf(
        "Sorry, is this a bad time? It's just a small thing.",
        "I hate to ask. I'll ask anyway.",
        "If it's no trouble. It might be trouble.",
    )),
    Customer("Drake", 4, listOf(
        "New in town, big plans. Start me off with these.",
        "Consider it an investment opportunity.",
        "Quack deal, take it or leave it.",
    )),
    Customer("Clementine", 5, listOf(
        "Back in my day we merged uphill. Both ways.",
        "Humour an old mare, would you?",
        "You remind me of your gran, you know.",
    )),
)

data class Request(val chain: String, val tier: Int, var quantity: Int = 1) {
    val label: String get() = Chains[chain].tierName(tier)

    fun toJson(): Map<String, Any?> = mapOf("chain" to chain, "tier" to tier, "qty" to quantity)

    companion object {
        fun fromJson(data: Map<String, Any?>): Request? {
            val chain = data.str("chain")
            if (!Chains.exists(chain)) return null
            return Request(
                chain,
                data.int("tier").coerceIn(0, MAX_TIER),
                data.int("qty", 1).coerceAtLeast(1),
            )
        }
    }
}

data class Order(
    val customer: String,
    val portrait: Int,
    val line: String,
    val requests: List<Request>,
    val coins: Int,
    val xp: Int,
) {
    fun filledBy(board: MergeBoard): Boolean =
        requests.all { board.has(it.chain, it.tier, it.quantity) }

    fun heldFor(request: Request, board: MergeBoard): Int =
        board.count(request.chain, request.tier) +
            board.storage.count { it.chain == request.chain && it.tier == request.tier }

    fun toJson(): Map<String, Any?> = mapOf(
        "customer" to customer, "portrait" to portrait, "line" to line,
        "coins" to coins, "xp" to xp,
        "requests" to requests.map { it.toJson() },
    )

    companion object {
        fun fromJson(data: Map<String, Any?>): Order? {
            val requests = data["requests"].asList().mapNotNull { raw ->
                @Suppress("UNCHECKED_CAST")
                (raw as? Map<String, Any?>)?.let { Request.fromJson(it) }
            }
            if (requests.isEmpty()) return null
            return Order(
                data.str("customer", "Buttercup"),
                data.int("portrait").coerceIn(0, CUSTOMERS.size - 1),
                data.str("line"),
                requests,
                data.int("coins").coerceAtLeast(1),
                data.int("xp").coerceAtLeast(1),
            )
        }
    }
}

/** Ask for tiers the player can plausibly reach at their level. */
private fun pickTier(level: Int, random: Random): Int {
    val ceiling = minOf(MAX_TIER, 1 + level / 3)
    val floor = maxOf(0, ceiling - 2)
    return random.nextInt(floor, ceiling + 1)
}

fun makeOrder(level: Int, random: Random): Order {
    val available = Chains.unlocked(level)
    val customer = CUSTOMERS[random.nextInt(CUSTOMERS.size)]
    val count = if (level < 3) 1 else listOf(1, 1, 2, 2, 3)[random.nextInt(5)]
    val requests = mutableListOf<Request>()
    repeat(count) {
        val chain = available[random.nextInt(available.size)]
        val tier = pickTier(level, random)
        val existing = requests.firstOrNull { it.chain == chain.key && it.tier == tier }
        if (existing != null) existing.quantity++ else requests.add(Request(chain.key, tier))
    }
    val worth = requests.sumOf { valueOf(it.tier) * it.quantity }
    return Order(
        customer = customer.name,
        portrait = customer.portrait,
        line = customer.lines[random.nextInt(customer.lines.size)],
        requests = requests,
        coins = maxOf(6, (worth * (1.5 + random.nextDouble() * 0.6)).roundToInt()),
        xp = maxOf(2, requests.sumOf { xpOf(it.tier) * it.quantity }),
    )
}

class OrderBook {
    val active: MutableList<Order> = mutableListOf()

    fun refill(level: Int, random: Random) {
        while (active.size < ORDER_SLOTS) active.add(makeOrder(level, random))
    }

    /** Hand over an order's items. Returns the completed order, or null. */
    fun deliver(index: Int, board: MergeBoard, level: Int, random: Random): Order? {
        val order = active.getOrNull(index) ?: return null
        if (!order.filledBy(board)) return null
        for (request in order.requests) {
            repeat(request.quantity) { board.take(request.chain, request.tier) }
        }
        active.removeAt(index)
        refill(level, random)
        return order
    }

    /** Replace an order the player never wants to fill. */
    fun skip(index: Int, level: Int, random: Random) {
        if (index in active.indices) {
            active.removeAt(index)
            refill(level, random)
        }
    }

    fun toJson(): Map<String, Any?> = mapOf("active" to active.map { it.toJson() })

    companion object {
        fun fromJson(data: Map<String, Any?>): OrderBook {
            val book = OrderBook()
            data["active"].asList().forEach { raw ->
                @Suppress("UNCHECKED_CAST")
                (raw as? Map<String, Any?>)?.let { map -> Order.fromJson(map)?.let(book.active::add) }
            }
            while (book.active.size > ORDER_SLOTS) book.active.removeAt(book.active.size - 1)
            return book
        }
    }
}
