package com.barnyardblitz.ui

import android.graphics.Canvas
import android.graphics.RectF
import com.barnyardblitz.art.GENERATOR_TIER
import com.barnyardblitz.art.itemKey
import com.barnyardblitz.engine.Cell
import com.barnyardblitz.engine.Chains
import com.barnyardblitz.engine.DropKind
import com.barnyardblitz.engine.Item
import com.barnyardblitz.engine.MAX_TIER
import com.barnyardblitz.engine.STORAGE_SLOTS
import com.barnyardblitz.engine.Sfx
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DRAG_THRESHOLD = 0.25f

/**
 * The merge yard - the game's home screen.
 *
 * Tap a generator to spend energy on a new item, drag matching items together
 * to climb the chain, and hand the results to the customers along the top.
 */
class FarmScene(private val game: Game) : Scene {

    private val ui get() = game.ui
    private val layout get() = game.farmLayout
    private val session get() = game.session

    private var selected: Cell? = null
    private var dragCell: Cell? = null
    private var dragX = 0f
    private var dragY = 0f
    private var originX = 0f
    private var originY = 0f
    private var dragging = false
    private val pop = HashMap<Cell, Float>()
    private var storageOpen = false
    private var detail = -1
    private var elapsed = 0f

    override fun onEnter(argument: String?) {
        selected = null
        detail = -1
        storageOpen = false
        clearDrag()
        session.orders.refill(session.economy.level, session.random)
        val chapter = session.story.current
        if (chapter != null && chapter.key !in session.story.seenIntro) {
            game.go("story", "intro")
        }
    }

    private fun clearDrag() {
        dragCell = null
        dragging = false
    }

    private fun cellFrom(index: Int): Cell = Cell(index / layout.cols, index % layout.cols)

    // ------------------------------------------------------------------- input
    override fun onDown(x: Float, y: Float) {
        if (detail >= 0) {
            pressDetail(x, y)
            return
        }
        if (storageOpen) {
            pressStorage(x, y)
            return
        }
        for (key in listOf("story", "blitz", "storage", "quit")) {
            if (ui.hit(key, x, y)) {
                pressButton(key)
                return
            }
        }
        if (selected != null) {
            if (ui.hit("sell", x, y)) {
                session.sell(selected!!)
                game.sfx.play(Sfx.COIN)
                selected = null
                return
            }
            if (ui.hit("store", x, y)) {
                session.store(selected!!)
                selected = null
                return
            }
        }
        for (i in session.orders.active.indices) {
            if (ui.hit("order$i", x, y)) {
                detail = i
                return
            }
        }
        val index = layout.cellAt(x, y)
        if (index < 0) {
            selected = null
            return
        }
        val cell = cellFrom(index)
        if (session.board.at(cell) == null) {
            selected = null
            return
        }
        dragCell = cell
        originX = x
        originY = y
        dragX = x
        dragY = y
        dragging = false
    }

    private fun pressButton(key: String) {
        when (key) {
            "story" -> game.go("story")
            "blitz" -> game.go("blitz")
            "storage" -> storageOpen = true
            "quit" -> game.quit()
        }
    }

    private fun pressStorage(x: Float, y: Float) {
        if (ui.hit("storage_close", x, y)) {
            storageOpen = false
            return
        }
        for (i in session.board.storage.indices) {
            if (ui.hit("slot$i", x, y)) {
                session.unstore(i)
                return
            }
        }
    }

    private fun pressDetail(x: Float, y: Float) {
        if (ui.hit("detail_close", x, y)) {
            detail = -1
        } else if (ui.hit("detail_deliver", x, y)) {
            if (session.deliver(detail) != null) {
                game.sfx.play(Sfx.ROOSTER)
                detail = -1
            }
        } else if (ui.hit("detail_skip", x, y)) {
            session.skipOrder(detail)
            detail = -1
        }
    }

    override fun onMove(x: Float, y: Float) {
        if (dragCell == null) return
        dragX = x
        dragY = y
        if (!dragging && max(abs(x - originX), abs(y - originY)) > layout.cell * DRAG_THRESHOLD) {
            dragging = true
            selected = null
        }
    }

    override fun onUp(x: Float, y: Float) {
        val source = dragCell ?: return
        val wasDragging = dragging
        clearDrag()
        if (!wasDragging) {
            tap(source)
            return
        }
        val index = layout.cellAt(x, y)
        if (index < 0) return
        val target = cellFrom(index)
        if (target == source) return
        val result = session.drop(source, target)
        when (result.kind) {
            DropKind.MERGE -> {
                val item = result.item ?: return
                pop[target] = 0f
                game.sfx.play(Sfx.matchName(min(7, item.tier + 1)))
                val rect = layout.cellRect(target.row, target.col)
                game.effects.burst(rect.centerX(), rect.centerY(), Chains[item.chain].base or 0xFF000000.toInt(), 10)
                game.effects.popup(rect.centerX(), rect.top, item.name, Palette.CREAM, layout.fs(18))
                if (item.tier == MAX_TIER) game.effects.kick(8f)
            }
            DropKind.MOVE, DropKind.SWAP -> game.sfx.play(Sfx.SWAP)
            DropKind.NONE -> Unit
        }
    }

    private fun tap(cell: Cell) {
        val item = session.board.at(cell) ?: return
        if (item.isGenerator) {
            val before = session.board.occupied().map { it.first }.toSet()
            if (session.tap(cell)) {
                game.sfx.play(Sfx.SELECT)
                session.board.occupied().forEach { (spot, _) -> if (spot !in before) pop[spot] = 0f }
            } else {
                game.sfx.play(Sfx.INVALID)
            }
            return
        }
        selected = if (selected == cell) null else cell
    }

    override fun onBack(): Boolean {
        if (storageOpen || detail >= 0) {
            storageOpen = false
            detail = -1
            return true
        }
        if (selected != null) {
            selected = null
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ update
    override fun update(dt: Float) {
        elapsed += dt
        val done = pop.entries.filter { it.value + dt > 0.34f }.map { it.key }
        pop.entries.forEach { it.setValue(it.value + dt) }
        done.forEach { pop.remove(it) }
    }

    // -------------------------------------------------------------------- draw
    override fun draw(canvas: Canvas) {
        ui.scale = layout.scale
        drawTopBar(canvas)
        drawOrders(canvas)
        drawBoard(canvas)
        layout.info?.let { drawLegend(canvas, it) }
        drawBar(canvas)
        game.effects.draw(canvas, ui)
        if (storageOpen) drawStorage(canvas) else if (detail >= 0) drawDetail(canvas)
    }

    private fun drawTopBar(canvas: Canvas) {
        val bar = layout.topbar
        val eco = session.economy
        ui.plank(canvas, bar, Palette.BARN_RED)
        val pad = bar.width() * 0.02f

        ui.text(canvas, "Lv ${eco.level}", layout.fs(22), Palette.CREAM, bar.left + pad, bar.top + bar.height() * 0.32f, bold = true)
        ui.meter(
            canvas,
            RectF(bar.left + pad, bar.top + bar.height() * 0.60f, bar.left + pad + bar.width() * 0.26f, bar.top + bar.height() * 0.60f + max(5f, bar.height() * 0.14f)),
            eco.xpFraction, Palette.GOLD, 0xFF782E2A.toInt(),
        )

        pill(canvas, bar.left + bar.width() * 0.48f, bar.centerY(), Palette.GOLD, formatCoins(eco.coins))
        pill(canvas, bar.left + bar.width() * 0.76f, bar.centerY(), 0xFF7EC8F0.toInt(), "${eco.energy}/${eco.energyCap}")
        if (!eco.energyFull) {
            ui.text(
                canvas, "+1 in ${eco.secondsToNextEnergy.toInt()}s", layout.fs(13), Palette.CREAM,
                bar.right - pad, bar.bottom - bar.height() * 0.18f, Ui.Align.RIGHT, shadow = false,
            )
        }
    }

    private fun pill(canvas: Canvas, x: Float, y: Float, color: Int, text: String) {
        val radius = max(8f, layout.topbar.height() * 0.22f)
        ui.fill.color = color
        canvas.drawCircle(x, y, radius, ui.fill)
        ui.stroke.color = shade(color, -0.3f)
        ui.stroke.strokeWidth = max(2f, radius / 6f)
        canvas.drawCircle(x, y, radius, ui.stroke)
        ui.text(canvas, text, layout.fs(21), Palette.CREAM, x + radius * 1.35f, y, bold = true)
    }

    private fun drawOrders(canvas: Canvas) {
        layout.orderCards.forEachIndexed { i, card ->
            val order = session.orders.active.getOrNull(i) ?: return@forEachIndexed
            val ready = order.filledBy(session.board)
            ui.hitboxes["order$i"] = RectF(card)
            ui.plank(canvas, card, if (ready) 0xFFE2F4DC.toInt() else Palette.CREAM)

            val portraitSize = min(card.height() - layout.fs(22), card.width() * 0.42f)
            game.sprites.portraitSmall[order.portrait]?.let {
                val dst = RectF(card.left + layout.fs(6), card.top + layout.fs(6), card.left + layout.fs(6) + portraitSize, card.top + layout.fs(6) + portraitSize)
                canvas.drawBitmap(it, null, dst, null)
            }
            val textLeft = card.left + layout.fs(6) + portraitSize + layout.fs(4)
            ui.text(canvas, order.customer, layout.fs(14), Palette.INK, textLeft, card.top + layout.fs(14), bold = true, shadow = false)

            val icon = max(14f, min(card.height() * 0.34f, layout.cell * 0.62f))
            var x = textLeft
            val y = card.top + layout.fs(26)
            for (request in order.requests.take(3)) {
                game.sprites.items[itemKey(Chains.all.indexOfFirst { it.key == request.chain }, request.tier)]?.let {
                    canvas.drawBitmap(it, null, RectF(x, y, x + icon, y + icon), null)
                }
                if (request.quantity > 1) {
                    ui.text(canvas, "x${request.quantity}", layout.fs(12), Palette.INK, x + icon * 0.72f, y + icon * 0.8f, bold = true, shadow = false)
                }
                x += icon * 0.92f
            }
            ui.text(canvas, formatCoins(order.coins), layout.fs(15), Palette.BARN_RED, card.left + layout.fs(8), card.bottom - layout.fs(14), bold = true, shadow = false)
            if (ready) {
                ui.text(canvas, "READY", layout.fs(14), Palette.GREEN, card.right - layout.fs(8), card.bottom - layout.fs(14), Ui.Align.RIGHT, bold = true, shadow = false)
            }
        }
    }

    private fun drawBoard(canvas: Canvas) {
        val frame = RectF(layout.board)
        frame.inset(-layout.gap / 2f, -layout.gap / 2f)
        ui.plank(canvas, frame, Palette.WOOD, max(8f, layout.gap))

        for (r in 0 until layout.rows) {
            for (c in 0 until layout.cols) {
                val rect = layout.cellRect(r, c)
                ui.fill.color = if ((r + c) % 2 == 0) Palette.CELL_LIGHT else Palette.CELL_DARK
                canvas.drawRect(rect, ui.fill)
                ui.stroke.color = Palette.CELL_EDGE
                ui.stroke.strokeWidth = 1f
                canvas.drawRect(rect, ui.stroke)
            }
        }

        for ((cell, item) in session.board.occupied()) {
            if (dragging && cell == dragCell) continue
            drawItem(canvas, cell, item)
        }

        selected?.let { cell ->
            val rect = layout.cellRect(cell.row, cell.col)
            rect.inset(3f, 3f)
            val pulse = (sin(elapsed * 9f) + 1f) / 2f
            ui.stroke.color = Palette.WHITE
            ui.stroke.strokeWidth = max(2f, layout.cell * 0.05f + pulse * 3f)
            canvas.drawRoundRect(rect, layout.cell * 0.18f, layout.cell * 0.18f, ui.stroke)
            drawActions(canvas, cell)
        }

        val source = dragCell
        if (dragging && source != null) {
            val item = session.board.at(source)
            if (item != null) {
                val index = layout.cellAt(dragX, dragY)
                if (index >= 0) {
                    val target = cellFrom(index)
                    val other = session.board.at(target)
                    if (target != source && other != null && item.matches(other)) {
                        val rect = layout.cellRect(target.row, target.col)
                        ui.fill.color = withAlpha(0xFFFFF6AA.toInt(), 0.59f)
                        canvas.drawRoundRect(rect, layout.cell * 0.18f, layout.cell * 0.18f, ui.fill)
                    }
                }
                val size = layout.cell * 1.07f
                game.sprites.items[spriteKey(item)]?.let {
                    canvas.drawBitmap(it, null, RectF(dragX - size / 2f, dragY - size / 2f, dragX + size / 2f, dragY + size / 2f), null)
                }
            }
        }
    }

    private fun spriteKey(item: Item): Int {
        val index = Chains.all.indexOfFirst { it.key == item.chain }
        return itemKey(index, if (item.isGenerator) GENERATOR_TIER else item.tier)
    }

    private fun drawItem(canvas: Canvas, cell: Cell, item: Item) {
        val rect = layout.cellRect(cell.row, cell.col)
        var scale = 1f
        pop[cell]?.let { scale = 1f + 0.34f * sin((it / 0.34f) * Math.PI.toFloat()) }
        var alpha = 255
        if (item.isGenerator) {
            if (session.economy.canSpend(Chains[item.chain].generator.energy)) {
                scale *= 1f + 0.025f * sin(elapsed * 4f + cell.row + cell.col)
            } else {
                alpha = 140
            }
        }
        val bitmap = game.sprites.items[spriteKey(item)] ?: return
        val size = layout.cell * 0.94f * scale
        val dst = RectF(rect.centerX() - size / 2f, rect.centerY() - size / 2f, rect.centerX() + size / 2f, rect.centerY() + size / 2f)
        if (alpha == 255) {
            canvas.drawBitmap(bitmap, null, dst, null)
        } else {
            ui.fill.alpha = alpha
            canvas.drawBitmap(bitmap, null, dst, ui.fill)
            ui.fill.alpha = 255
        }
    }

    /** Sell / store buttons for the selected item. */
    private fun drawActions(canvas: Canvas, cell: Cell) {
        val item = session.board.at(cell) ?: return
        if (item.isGenerator) return
        val width = layout.board.width() * 0.46f
        val height = max(36f, layout.cell * 0.62f)
        val total = width * 2 + layout.gap
        val x = layout.board.centerX() - total / 2f
        var y = layout.board.bottom + layout.gap
        if (y + height > layout.h - layout.margin) y = layout.board.bottom - height - layout.gap
        ui.button(canvas, "sell", RectF(x, y, x + width, y + height), "Sell  ${formatCoins(item.value)}", Palette.GOLD, 18, true, Palette.INK)
        ui.button(
            canvas, "store", RectF(x + width + layout.gap, y, x + width * 2 + layout.gap, y + height),
            "Store", Palette.WOOD_DARK, 18, session.board.storage.size < STORAGE_SLOTS,
        )
    }

    /** Fills the gap a tall phone leaves above the board. */
    private fun drawLegend(canvas: Canvas, area: RectF) {
        if (area.height() < layout.fs(90)) return
        var y = area.top + layout.fs(12)
        ui.text(canvas, "HOLLOW CREEK FARM", layout.fs(18), Palette.INK, area.centerX(), y, Ui.Align.CENTER, bold = true)
        y += layout.fs(30)
        val chapter = session.story.current
        val subtitle = chapter?.title ?: "Every chapter finished"
        ui.text(canvas, subtitle, layout.fs(16), Palette.INK_SOFT, area.centerX(), y, Ui.Align.CENTER, shadow = false)
        y += layout.fs(28)
        val next = session.story.nextTask()
        if (next != null && y + layout.fs(20) < area.bottom) {
            val afford = session.economy.canAfford(next.cost)
            ui.text(
                canvas, "Next: ${next.title}  ${formatCoins(next.cost)}", layout.fs(15),
                if (afford) Palette.BARN_RED else Palette.INK_SOFT,
                area.centerX(), y, Ui.Align.CENTER, shadow = false,
            )
        }
    }

    private fun drawBar(canvas: Canvas) {
        val labels = mapOf(
            "story" to "Story",
            "blitz" to "Blitz",
            "storage" to "Store ${session.board.storage.size}/$STORAGE_SLOTS",
            "quit" to "Quit",
        )
        val colors = mapOf(
            "story" to Palette.BARN_RED, "blitz" to Palette.WOOD_DARK,
            "storage" to Palette.WOOD_DARK, "quit" to Palette.WOOD_DARK,
        )
        for ((key, rect) in layout.buttons) {
            ui.button(canvas, key, rect, labels.getValue(key), colors.getValue(key), 17)
        }
        val task = session.story.nextTask()
        if (task != null && session.economy.canAfford(task.cost)) {
            val rect = layout.buttons.getValue("story")
            ui.fill.color = 0xFF56BE60.toInt()
            canvas.drawCircle(rect.right - layout.fs(8), rect.top + layout.fs(8), max(5f, layout.fs(7)), ui.fill)
        }
    }

    private fun drawStorage(canvas: Canvas) {
        ui.veil(canvas, layout.w, layout.h)
        val card = layout.centreCard(0.86f, 0.5f)
        val inner = ui.panel(canvas, card)
        ui.text(canvas, "Storage", layout.fs(28), Palette.BARN_RED, inner.centerX(), inner.top + layout.fs(18), Ui.Align.CENTER, bold = true)

        val cols = 4
        val size = min(inner.width() / cols - layout.gap, inner.height() * 0.30f)
        val top = inner.top + layout.fs(46)
        for (i in 0 until STORAGE_SLOTS) {
            val row = i / cols
            val col = i % cols
            val rect = RectF(inner.left + col * (size + layout.gap), top + row * (size + layout.gap), inner.left + col * (size + layout.gap) + size, top + row * (size + layout.gap) + size)
            ui.fill.color = 0xFFD6CEBA.toInt()
            canvas.drawRoundRect(rect, size * 0.16f, size * 0.16f, ui.fill)
            val item = session.board.storage.getOrNull(i) ?: continue
            ui.hitboxes["slot$i"] = RectF(rect)
            game.sprites.items[spriteKey(item)]?.let { canvas.drawBitmap(it, null, rect, null) }
        }
        ui.text(canvas, "Tap an item to send it back to the yard", layout.fs(15), Palette.INK_SOFT, inner.centerX(), inner.bottom - layout.fs(58), Ui.Align.CENTER, shadow = false)
        val buttonH = max(38f, layout.fs(44))
        ui.button(
            canvas, "storage_close",
            RectF(inner.centerX() - inner.width() * 0.2f, inner.bottom - buttonH, inner.centerX() + inner.width() * 0.2f, inner.bottom),
            "Close", Palette.WOOD_DARK, 20,
        )
    }

    private fun drawDetail(canvas: Canvas) {
        val order = session.orders.active.getOrNull(detail)
        if (order == null) {
            detail = -1
            return
        }
        ui.veil(canvas, layout.w, layout.h)
        val card = layout.centreCard(0.88f, 0.58f)
        val inner = ui.panel(canvas, card)

        val portrait = game.sprites.portraitBig[order.portrait]
        var y = inner.top
        if (portrait != null) {
            val size = min(portrait.width.toFloat(), inner.height() * 0.22f)
            canvas.drawBitmap(portrait, null, RectF(inner.centerX() - size / 2f, y, inner.centerX() + size / 2f, y + size), null)
            y += size + layout.fs(6)
        }
        ui.text(canvas, order.customer, layout.fs(26), Palette.BARN_RED, inner.centerX(), y + layout.fs(14), Ui.Align.CENTER, bold = true)
        y += layout.fs(32)
        y += ui.wrapped(canvas, "\"${order.line}\"", layout.fs(17), Palette.INK_SOFT, inner.left, y, inner.width())
        y += layout.fs(10)

        val icon = max(28f, min(layout.cell * 0.78f, inner.height() * 0.18f))
        var x = inner.centerX() - (order.requests.size * (icon + layout.fs(10))) / 2f
        for (request in order.requests) {
            game.sprites.items[itemKey(Chains.all.indexOfFirst { it.key == request.chain }, request.tier)]?.let {
                canvas.drawBitmap(it, null, RectF(x, y, x + icon, y + icon), null)
            }
            val have = order.heldFor(request, session.board)
            ui.text(
                canvas, "${min(have, request.quantity)}/${request.quantity}", layout.fs(15),
                if (have >= request.quantity) Palette.GREEN else Palette.BARN_RED,
                x + icon / 2f, y + icon + layout.fs(12), Ui.Align.CENTER, bold = true, shadow = false,
            )
            x += icon + layout.fs(10)
        }
        y += icon + layout.fs(32)
        ui.text(canvas, "Reward  ${formatCoins(order.coins)} coins   ${order.xp} xp", layout.fs(18), Palette.INK, inner.centerX(), y, Ui.Align.CENTER, bold = true)

        val bw = inner.width() * 0.30f
        val bh = max(38f, layout.fs(46))
        val by = inner.bottom - bh
        ui.button(canvas, "detail_deliver", RectF(inner.centerX() - bw - layout.gap, by, inner.centerX() - layout.gap, by + bh), "Deliver", 0xFF56A052.toInt(), 19, order.filledBy(session.board))
        ui.button(canvas, "detail_close", RectF(inner.centerX(), by, inner.centerX() + bw, by + bh), "Close", Palette.WOOD_DARK, 19)
        ui.button(canvas, "detail_skip", RectF(inner.right - bw / 2f, by - bh - layout.gap, inner.right, by - layout.gap), "Skip", Palette.WOOD, 16)
    }
}
