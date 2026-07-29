package com.barnyardblitz.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
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

private val GRASS_BED = 0xFFB6CE9A.toInt()
private val PAD_LIGHT = 0xFFD2E4BC.toInt()
private val PAD_DARK = 0xFFC6DAAE.toInt()
private val PAD_RIM = 0xFFE2EFD4.toInt()
private const val SPAWN_FLIGHT = 0.26f

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

    /** Items still flying out of the generator that produced them. */
    private val flying = HashMap<Cell, Pair<Cell, Float>>()
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
                session.board.occupied().forEach { (spot, _) ->
                    if (spot !in before) {
                        // Fly the new item out of the generator so the source of
                        // it is never in doubt.
                        flying[spot] = cell to 0f
                        pop[spot] = 0f
                    }
                }
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

        val landed = flying.entries.filter { it.value.second + dt >= SPAWN_FLIGHT }.map { it.key }
        flying.entries.forEach { it.setValue(it.value.first to it.value.second + dt) }
        landed.forEach { flying.remove(it) }
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
        val pad = bar.width() * 0.025f

        // Level badge: a filled ring reads as progress at a glance and frees
        // the width the old bar was eating.
        val ringR = bar.height() * 0.32f
        val ringX = bar.left + pad + ringR
        val ringY = bar.centerY()
        ui.stroke.strokeWidth = max(3f, ringR * 0.26f)
        ui.stroke.color = 0xFF7E2E2A.toInt()
        canvas.drawCircle(ringX, ringY, ringR, ui.stroke)
        ui.fill.color = shade(Palette.BARN_RED, -0.22f)
        canvas.drawCircle(ringX, ringY, ringR - ui.stroke.strokeWidth * 0.6f, ui.fill)
        drawRingArc(canvas, ringX, ringY, ringR, eco.xpFraction)
        ui.text(canvas, "${eco.level}", layout.fs(21), Palette.CREAM, ringX, ringY, Ui.Align.CENTER, bold = true)

        pill(canvas, bar.left + bar.width() * 0.44f, bar.centerY(), Palette.GOLD, formatCoins(eco.coins))

        val energyX = bar.left + bar.width() * 0.74f
        // The energy pill glows when it is full, so a wasted refill is obvious.
        val energyColor = if (eco.energyFull) 0xFF9BE0FF.toInt() else 0xFF7EC8F0.toInt()
        pill(canvas, energyX, bar.centerY() - (if (eco.energyFull) 0f else layout.fs(5)), energyColor, "${eco.energy}/${eco.energyCap}")
        if (!eco.energyFull) {
            ui.text(
                canvas, "+1 in ${eco.secondsToNextEnergy.toInt()}s", layout.fs(13), withAlpha(Palette.CREAM, 0.8f),
                energyX + layout.topbar.height() * 0.22f * 1.35f, bar.centerY() + layout.fs(13), shadow = false,
            )
        }
    }

    private fun drawRingArc(canvas: Canvas, cx: Float, cy: Float, radius: Float, fraction: Float) {
        if (fraction <= 0.001f) return
        val sweep = 360f * fraction.coerceIn(0f, 1f)
        ui.stroke.color = Palette.GOLD
        ui.stroke.strokeWidth = max(3f, radius * 0.26f)
        canvas.drawArc(
            RectF(cx - radius, cy - radius, cx + radius, cy + radius),
            -90f, sweep, false, ui.stroke,
        )
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

            // A ready order gets a warm face and a gentle breathing outline, so
            // it pulls the eye without shouting.
            val face = if (ready) 0xFFE4F6DC.toInt() else Palette.CREAM
            ui.plank(canvas, card, face)
            if (ready) {
                val pulse = (sin(elapsed * 3.4f + i) + 1f) / 2f
                ui.stroke.color = withAlpha(Palette.GREEN, 0.45f + 0.4f * pulse)
                ui.stroke.strokeWidth = max(2f, layout.fs(3))
                val r = max(6f, min(24f * layout.scale, min(card.width(), card.height()) * 0.18f))
                canvas.drawRoundRect(card, r, r, ui.stroke)
            }

            val pad = layout.fs(8)
            val portraitSize = min(card.height() - pad * 2f, card.width() * 0.40f)
            game.sprites.portraitSmall[order.portrait]?.let {
                val top = card.top + pad
                canvas.drawBitmap(it, null, RectF(card.left + pad, top, card.left + pad + portraitSize, top + portraitSize), null)
            }

            val textLeft = card.left + pad + portraitSize + layout.fs(6)
            val textRoom = card.right - pad - textLeft
            ui.textFitted(canvas, order.customer, layout.fs(15), Palette.INK, textLeft, card.top + pad + layout.fs(9), textRoom, bold = true, shadow = false)

            // What they want, at a size you can actually identify.
            val icon = max(16f, min(card.height() * 0.40f, layout.cell * 0.72f))
            var x = textLeft
            val y = card.top + pad + layout.fs(20)
            for (request in order.requests.take(3)) {
                val chainIndex = Chains.all.indexOfFirst { it.key == request.chain }
                game.sprites.items[itemKey(chainIndex, request.tier)]?.let {
                    canvas.drawBitmap(it, null, RectF(x, y, x + icon, y + icon), null)
                }
                if (request.quantity > 1) {
                    val bx = x + icon * 0.74f
                    val by = y + icon * 0.74f
                    ui.fill.color = Palette.BARN_RED
                    canvas.drawCircle(bx, by, layout.fs(9), ui.fill)
                    ui.text(canvas, "${request.quantity}", layout.fs(11), Palette.CREAM, bx, by, Ui.Align.CENTER, bold = true, shadow = false)
                }
                x += icon * 0.86f
            }

            // Reward as a coin pill rather than a bare number.
            val coinY = card.bottom - pad - layout.fs(8)
            val coinR = layout.fs(9)
            ui.fill.color = Palette.GOLD
            canvas.drawCircle(card.left + pad + coinR, coinY, coinR, ui.fill)
            ui.stroke.color = shade(Palette.GOLD, -0.3f)
            ui.stroke.strokeWidth = max(1f, coinR / 5f)
            canvas.drawCircle(card.left + pad + coinR, coinY, coinR, ui.stroke)
            ui.text(canvas, formatCoins(order.coins), layout.fs(15), Palette.INK, card.left + pad + coinR * 2.5f, coinY, bold = true, shadow = false)

            if (ready) {
                ui.text(canvas, "READY", layout.fs(13), Palette.GREEN, card.right - pad, coinY, Ui.Align.RIGHT, bold = true, shadow = false)
            }
        }
    }

    private fun drawBoard(canvas: Canvas) {
        val frame = RectF(layout.board)
        frame.inset(-layout.gap * 0.9f, -layout.gap * 0.9f)
        val frameRadius = max(10f, layout.gap * 1.3f)
        ui.plank(canvas, frame, Palette.WOOD, frameRadius)
        drawFencePosts(canvas, frame)

        // The grass bed under the cells, so the gaps between pads read as
        // ground rather than as background showing through.
        val bedRadius = layout.cell * 0.18f
        ui.fill.color = GRASS_BED
        canvas.drawRoundRect(layout.board, bedRadius, bedRadius, ui.fill)

        val inset = layout.cell * 0.045f
        val padRadius = layout.cell * 0.20f
        for (r in 0 until layout.rows) {
            for (c in 0 until layout.cols) {
                val rect = layout.cellRect(r, c)
                rect.inset(inset, inset)
                ui.fill.color = if ((r + c) % 2 == 0) PAD_LIGHT else PAD_DARK
                canvas.drawRoundRect(rect, padRadius, padRadius, ui.fill)
                // A lit top edge gives each pad a little depth.
                ui.stroke.color = PAD_RIM
                ui.stroke.strokeWidth = max(1f, layout.cell * 0.018f)
                canvas.drawLine(
                    rect.left + padRadius, rect.top + ui.stroke.strokeWidth * 0.5f,
                    rect.right - padRadius, rect.top + ui.stroke.strokeWidth * 0.5f, ui.stroke,
                )
            }
        }
        drawBoardShading(canvas, bedRadius)

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

    /** Little posts along the top and bottom rails of the yard fence. */
    private fun drawFencePosts(canvas: Canvas, frame: RectF) {
        val postW = layout.cell * 0.16f
        val postH = frame.height() * 0.055f
        val count = layout.cols
        for (i in 0 until count) {
            val x = layout.board.left + layout.cell * (i + 0.5f)
            for (y in listOf(frame.top + postH * 0.35f, frame.bottom - postH * 1.35f)) {
                val rect = RectF(x - postW / 2f, y, x + postW / 2f, y + postH)
                ui.fill.color = shade(Palette.WOOD, 0.16f)
                canvas.drawRoundRect(rect, postW * 0.4f, postW * 0.4f, ui.fill)
            }
        }
    }

    /** A soft gradient at the top and bottom of the bed, so it reads as a dip. */
    private fun drawBoardShading(canvas: Canvas, radius: Float) {
        val depth = layout.cell * 0.5f
        canvas.save()
        val clip = android.graphics.Path()
        clip.addRoundRect(layout.board, radius, radius, android.graphics.Path.Direction.CW)
        canvas.clipPath(clip)
        ui.fill.shader = LinearGradient(
            0f, layout.board.top, 0f, layout.board.top + depth,
            withAlpha(0xFF3E5A2A.toInt(), 0.18f), withAlpha(0xFF3E5A2A.toInt(), 0f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(RectF(layout.board.left, layout.board.top, layout.board.right, layout.board.top + depth), ui.fill)
        ui.fill.shader = LinearGradient(
            0f, layout.board.bottom - depth, 0f, layout.board.bottom,
            withAlpha(0xFF3E5A2A.toInt(), 0f), withAlpha(0xFF3E5A2A.toInt(), 0.16f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(RectF(layout.board.left, layout.board.bottom - depth, layout.board.right, layout.board.bottom), ui.fill)
        ui.fill.shader = null
        canvas.restore()
    }

    private fun spriteKey(item: Item): Int {
        val index = Chains.all.indexOfFirst { it.key == item.chain }
        return itemKey(index, if (item.isGenerator) GENERATOR_TIER else item.tier)
    }

    private fun drawItem(canvas: Canvas, cell: Cell, item: Item) {
        val rect = layout.cellRect(cell.row, cell.col)
        var cx = rect.centerX()
        var cy = rect.centerY()
        var scale = 1f
        pop[cell]?.let { scale = 1f + 0.34f * sin((it / 0.34f) * Math.PI.toFloat()) }
        var alpha = 255
        if (item.isGenerator) {
            val ready = session.economy.canSpend(Chains[item.chain].generator.energy) && !session.board.isFull
            if (ready) {
                // A breathing halo says "this is the thing you tap".
                val pulse = (sin(elapsed * 2.6f + cell.col) + 1f) / 2f
                ui.fill.color = withAlpha(0xFFFFF0A8.toInt(), 0.16f + 0.18f * pulse)
                canvas.drawCircle(cx, cy, layout.cell * (0.46f + 0.05f * pulse), ui.fill)
                scale *= 1f + 0.025f * sin(elapsed * 4f + cell.row + cell.col)
            } else {
                alpha = 140
            }
        }
        flying[cell]?.let { (from, t) ->
            // Arc out of the generator, shrinking in from small.
            val progress = easeOut((t / SPAWN_FLIGHT).coerceIn(0f, 1f))
            val src = layout.cellRect(from.row, from.col)
            cx = src.centerX() + (rect.centerX() - src.centerX()) * progress
            cy = src.centerY() + (rect.centerY() - src.centerY()) * progress - sin(progress * Math.PI.toFloat()) * layout.cell * 0.28f
            scale *= 0.45f + 0.55f * progress
        }
        val bitmap = game.sprites.items[spriteKey(item)] ?: return
        val size = layout.cell * 0.94f * scale
        val dst = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
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

    /** Fills the gap a tall phone leaves above the board with real progress. */
    private fun drawLegend(canvas: Canvas, area: RectF) {
        if (area.height() < layout.fs(96)) return
        val height = min(area.height() - layout.gap, layout.fs(112))
        val card = RectF(area.left, area.centerY() - height / 2f, area.right, area.centerY() + height / 2f)
        ui.plank(canvas, card, Palette.CREAM)

        val pad = layout.fs(12)
        val chapter = session.story.current
        val title = chapter?.title ?: "Hollow Creek Farm"
        ui.text(canvas, title, layout.fs(19), Palette.BARN_RED, card.left + pad, card.top + pad + layout.fs(10), bold = true, shadow = false)

        val next = session.story.nextTask()
        val line = when {
            next == null -> "The farm is finished."
            session.economy.canAfford(next.cost) -> "Ready: ${next.title}"
            else -> "Next: ${next.title}"
        }
        val lineColor = if (next != null && session.economy.canAfford(next.cost)) Palette.GREEN else Palette.INK_SOFT
        ui.text(canvas, line, layout.fs(15), lineColor, card.left + pad, card.top + pad + layout.fs(32), shadow = false)
        if (next != null) {
            ui.text(canvas, formatCoins(next.cost), layout.fs(15), Palette.INK, card.right - pad, card.top + pad + layout.fs(32), Ui.Align.RIGHT, bold = true, shadow = false)
        }

        // How much of the whole story is done. An unlabelled empty bar reads
        // as a bug on a fresh save, so the count goes beside it.
        val done = session.story.done.size
        val total = session.story.totalTasks
        val label = "$done / $total jobs"
        val labelWidth = ui.textWidth(label, layout.fs(13), false) + layout.fs(8)
        ui.text(canvas, label, layout.fs(13), Palette.INK_SOFT, card.right - pad, card.bottom - pad - layout.fs(4), Ui.Align.RIGHT, shadow = false)
        val meter = RectF(card.left + pad, card.bottom - pad - layout.fs(8), card.right - pad - labelWidth, card.bottom - pad - layout.fs(1))
        ui.meter(canvas, meter, session.progressFraction, Palette.BARN_RED)
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

        // Measure the card before drawing it. A fixed height left a big empty
        // pool under a one-item order, which made the panel feel unfinished.
        val cardW = min(layout.w * 0.88f, layout.w - 2 * layout.margin)
        val innerW = cardW * 0.80f
        val quote = "\"${order.line}\""
        val quoteLines = ui.wrapLines(quote, layout.fs(17), innerW)
        val portraitSize = min(layout.h * 0.10f, layout.w * 0.24f)
        val icon = max(36f, min(layout.cell * 0.95f, layout.w * 0.17f))
        val buttonH = max(44f, layout.fs(50))
        val content = portraitSize + layout.fs(34) +
            quoteLines.size * ui.lineHeight(layout.fs(17)) * 1.1f + layout.fs(14) +
            icon + layout.fs(26) + layout.fs(30) + layout.fs(16) + buttonH
        val cardH = (content * 1.22f).coerceIn(layout.h * 0.34f, layout.h * 0.82f)

        val card = RectF(
            (layout.w - cardW) / 2f, (layout.h - cardH) / 2f,
            (layout.w + cardW) / 2f, (layout.h + cardH) / 2f,
        )
        val inner = ui.panel(canvas, card)

        var y = inner.top
        game.sprites.portraitBig[order.portrait]?.let {
            canvas.drawBitmap(it, null, RectF(inner.centerX() - portraitSize / 2f, y, inner.centerX() + portraitSize / 2f, y + portraitSize), null)
        }
        y += portraitSize + layout.fs(6)
        ui.textFitted(canvas, order.customer, layout.fs(26), Palette.BARN_RED, inner.centerX(), y + layout.fs(13), inner.width(), Ui.Align.CENTER, bold = true)
        y += layout.fs(30)

        val quoteStep = ui.lineHeight(layout.fs(17)) * 1.1f
        quoteLines.forEachIndexed { i, line ->
            ui.text(canvas, line, layout.fs(17), Palette.INK_SOFT, inner.centerX(), y + quoteStep * (i + 0.5f), Ui.Align.CENTER, shadow = false)
        }
        y += quoteLines.size * quoteStep + layout.fs(14)

        // What they want, big enough to recognise without squinting.
        val span = order.requests.size * icon + (order.requests.size - 1) * layout.fs(14)
        var x = inner.centerX() - span / 2f
        for (request in order.requests) {
            val chainIndex = Chains.all.indexOfFirst { it.key == request.chain }
            game.sprites.items[itemKey(chainIndex, request.tier)]?.let {
                canvas.drawBitmap(it, null, RectF(x, y, x + icon, y + icon), null)
            }
            val have = order.heldFor(request, session.board)
            val enough = have >= request.quantity
            ui.text(
                canvas, "${min(have, request.quantity)}/${request.quantity}", layout.fs(16),
                if (enough) Palette.GREEN else Palette.BARN_RED,
                x + icon / 2f, y + icon + layout.fs(11), Ui.Align.CENTER, bold = true, shadow = false,
            )
            x += icon + layout.fs(14)
        }
        y += icon + layout.fs(26)

        ui.text(canvas, Chains[order.requests[0].chain].tierName(order.requests[0].tier), layout.fs(15), Palette.INK_SOFT, inner.centerX(), y, Ui.Align.CENTER, shadow = false)
        y += layout.fs(22)
        ui.text(canvas, "${formatCoins(order.coins)} coins   ${order.xp} xp", layout.fs(19), Palette.INK, inner.centerX(), y, Ui.Align.CENTER, bold = true)

        // One row of actions: the main one, the way out, and a quiet skip.
        val by = inner.bottom - buttonH
        val skipW = inner.width() * 0.20f
        val mainW = (inner.width() - skipW - layout.gap * 2f) / 2f
        ui.button(canvas, "detail_deliver", RectF(inner.left, by, inner.left + mainW, by + buttonH), "Deliver", 0xFF56A052.toInt(), 19, order.filledBy(session.board))
        ui.button(canvas, "detail_close", RectF(inner.left + mainW + layout.gap, by, inner.left + mainW * 2 + layout.gap, by + buttonH), "Close", Palette.WOOD_DARK, 19)
        ui.button(canvas, "detail_skip", RectF(inner.right - skipW, by, inner.right, by + buttonH), "Skip", Palette.WOOD, 16)
    }

}
