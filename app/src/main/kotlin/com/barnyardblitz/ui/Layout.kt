package com.barnyardblitz.ui

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun clamp(value: Int, low: Int, high: Int) = max(low, min(high, value))

private fun rect(left: Float, top: Float, width: Float, height: Float) =
    RectF(left, top, left + width, top + height)

/**
 * Geometry for the merge yard: top bar, orders, board, button bar.
 *
 * Landscape keeps the orders and buttons in a side panel; portrait puts the
 * orders on top and the buttons along the bottom, with the board biased low
 * into thumb reach.
 */
class FarmLayout(val w: Float, val h: Float, val rows: Int, val cols: Int) {

    companion object { const val REF_CELL = 70f }

    val portrait = h > w * 1.08f
    val margin = clamp((min(w, h) * 0.022f).roundToInt(), 8, 30).toFloat()
    val gap = max(6f, margin * 0.7f)

    val topbar: RectF
    val orders: RectF
    val bar: RectF
    val board: RectF
    val cell: Float
    val scale: Float
    val orderCards: List<RectF>
    val buttons: LinkedHashMap<String, RectF> = LinkedHashMap()

    /** Empty space above the board in portrait, used for the chain legend. */
    val info: RectF?

    init {
        val topH = clamp((h * (if (portrait) 0.082f else 0.11f)).roundToInt(), 44, 116).toFloat()
        topbar = rect(margin, margin, w - 2 * margin, topH)

        val area: RectF
        if (portrait) {
            val ordersH = clamp((h * 0.15f).roundToInt(), 96, 250).toFloat()
            val barH = clamp((h * 0.078f).roundToInt(), 46, 116).toFloat()
            orders = rect(margin, topbar.bottom + gap, w - 2 * margin, ordersH)
            bar = rect(margin, h - margin - barH, w - 2 * margin, barH)
            area = RectF(margin, orders.bottom + gap, w - margin, bar.top - gap)
            orderCards = splitRow(orders, 3)
        } else {
            val panelW = clamp((w * 0.32f).roundToInt(), 200, 380).toFloat()
            area = RectF(margin, topbar.bottom + gap, w - margin - gap - panelW, h - margin)
            val panel = RectF(area.right + gap, area.top, area.right + gap + panelW, area.bottom)
            orders = RectF(panel.left, panel.top, panel.right, panel.top + panel.height() * 0.66f)
            bar = RectF(panel.left, orders.bottom + gap, panel.right, panel.bottom)
            orderCards = splitColumn(orders, 3)
        }

        cell = max(24f, min(area.width() / cols, area.height() / rows).toInt().toFloat())
        val boardW = cell * cols
        val boardH = cell * rows
        if (portrait) {
            // A tall phone leaves slack the 7-wide board cannot use; push most
            // of it above so the grid sits within thumb reach.
            val slack = max(0f, area.height() - boardH)
            board = rect(area.left + (area.width() - boardW) / 2f, area.top + slack * 0.62f, boardW, boardH)
            val above = board.top - area.top
            val inset = w * 0.08f
            info = if (above > gap * 3) {
                RectF(margin + inset, area.top, w - margin - inset, board.top - gap)
            } else null
        } else {
            board = rect(
                area.left + (area.width() - boardW) / 2f,
                area.top + (area.height() - boardH) / 2f, boardW, boardH,
            )
            info = null
        }

        scale = (cell / REF_CELL).coerceIn(0.55f, 3.0f)
        buildButtons()
    }

    fun fs(base: Int): Float = max(9f, base * scale)

    private fun splitRow(area: RectF, count: Int): List<RectF> {
        val pad = max(3f, gap * 0.4f)
        val each = (area.width() - pad * (count - 1)) / count
        return (0 until count).map { rect(area.left + it * (each + pad), area.top, each, area.height()) }
    }

    private fun splitColumn(area: RectF, count: Int): List<RectF> {
        val pad = max(3f, gap * 0.5f)
        val each = (area.height() - pad * (count - 1)) / count
        return (0 until count).map { rect(area.left, area.top + it * (each + pad), area.width(), each) }
    }

    private fun buildButtons() {
        val keys = listOf("story", "blitz", "storage", "quit")
        val rects = if (portrait) {
            splitRow(bar, 4)
        } else {
            val pad = max(4f, bar.height() * 0.05f)
            val cw = (bar.width() - pad) / 2f
            val ch = (bar.height() - pad) / 2f
            (0 until 4).map {
                rect(bar.left + (it % 2) * (cw + pad), bar.top + (it / 2) * (ch + pad), cw, ch)
            }
        }
        keys.forEachIndexed { i, key -> buttons[key] = rects[i] }
    }

    fun cellRect(row: Int, col: Int): RectF =
        rect(board.left + col * cell, board.top + row * cell, cell, cell)

    /** Returns row*cols+col, or -1 when the point is off the board. */
    fun cellAt(x: Float, y: Float): Int {
        val col = ((x - board.left) / cell).toInt()
        val row = ((y - board.top) / cell).toInt()
        if (x < board.left || y < board.top || row !in 0 until rows || col !in 0 until cols) return -1
        return row * cols + col
    }

    fun centreCard(widthFrac: Float, heightFrac: Float): RectF {
        val cw = min(w * widthFrac, w - 2 * margin)
        val ch = min(h * heightFrac, h - 2 * margin)
        return rect((w - cw) / 2f, (h - ch) / 2f, cw, ch)
    }
}

/**
 * Geometry for the Blitz minigame: header, square board and read-out cards.
 */
class BlitzLayout(val w: Float, val h: Float, val rows: Int, val cols: Int) {

    companion object { const val REF_TILE = 66f }

    val portrait = h > w * 1.08f
    val margin = clamp((min(w, h) * 0.025f).roundToInt(), 8, 34).toFloat()
    val gap = max(6f, margin * 0.7f)

    val header: RectF
    val frame: RectF
    val board: RectF
    val tile: Float
    val framePad: Float
    val scale: Float
    val cards: List<RectF>
    val panel: RectF?
    val bar: RectF?
    val info: RectF?
    val buttons: LinkedHashMap<String, RectF> = LinkedHashMap()
    private var buttonArea: RectF? = null

    init {
        if (portrait) {
            val headerH = clamp((h * 0.072f).roundToInt(), 40, 104).toFloat()
            val statsH = clamp((h * 0.088f).roundToInt(), 54, 132).toFloat()
            val barH = clamp((h * 0.078f).roundToInt(), 46, 116).toFloat()
            header = rect(margin, margin, w - 2 * margin, headerH)
            val stats = rect(margin, header.bottom + gap, w - 2 * margin, statsH)
            bar = rect(margin, h - margin - barH, w - 2 * margin, barH)
            panel = null
            cards = splitRow(stats, 3)

            val top = stats.bottom + gap
            val availH = bar!!.top - gap - top
            val side = min(availH, w - 2 * margin)
            framePad = max(5f, side * 0.022f)
            tile = max(16f, ((side - 2 * framePad) / cols).toInt().toFloat())
            val boardW = tile * cols
            val boardH = tile * rows
            val frameW = boardW + 2 * framePad
            val frameH = boardH + 2 * framePad
            val slack = max(0f, availH - frameH)
            frame = rect((w - frameW) / 2f, top + slack * 0.62f, frameW, frameH)
            board = rect(frame.left + framePad, frame.top + framePad, boardW, boardH)
            val above = frame.top - top
            val inset = w * 0.08f
            info = if (above > gap * 3) RectF(margin + inset, top, w - margin - inset, frame.top - gap) else null
        } else {
            val headerH = clamp((h * 0.115f).roundToInt(), 44, 104).toFloat()
            header = rect(margin, margin, w - 2 * margin, headerH)
            val top = header.bottom + gap
            val availH = h - top - margin
            val panelW = clamp((w * 0.30f).roundToInt(), 168, 330).toFloat()
            val side = max(96f, min(availH, w - 2 * margin - gap - panelW))
            framePad = max(5f, side * 0.022f)
            tile = max(16f, ((side - 2 * framePad) / cols).toInt().toFloat())
            val boardW = tile * cols
            val boardH = tile * rows
            val frameW = boardW + 2 * framePad
            val frameH = boardH + 2 * framePad
            val groupW = frameW + gap + panelW
            val left = max(margin, (w - groupW) / 2f)
            frame = rect(left, top + max(0f, (availH - frameH) / 2f), frameW, frameH)
            board = rect(frame.left + framePad, frame.top + framePad, boardW, boardH)
            panel = rect(frame.right + gap, frame.top, panelW, frame.height())
            bar = null

            val pad = max(6f, panel!!.width() * 0.055f)
            val inner = RectF(panel.left + pad, panel.top + pad, panel.right - pad, panel.bottom - pad)
            val cardH = inner.height() * 0.155f
            val cardGap = inner.height() * 0.026f
            cards = (0 until 3).map { rect(inner.left, inner.top + it * (cardH + cardGap), inner.width(), cardH) }
            val buttonsH = inner.height() * 0.20f
            info = RectF(
                inner.left, cards.last().bottom + cardGap,
                inner.right, max(cards.last().bottom + cardGap, inner.bottom - buttonsH - cardGap),
            )
            buttonArea = RectF(inner.left, inner.bottom - buttonsH, inner.right, inner.bottom)
        }
        scale = (tile / REF_TILE).coerceIn(0.55f, 3.2f)
        buildButtons()
    }

    fun fs(base: Int): Float = max(9f, base * scale)

    private fun splitRow(area: RectF, count: Int): List<RectF> {
        val pad = max(3f, gap * 0.4f)
        val each = (area.width() - pad * (count - 1)) / count
        return (0 until count).map { rect(area.left + it * (each + pad), area.top, each, area.height()) }
    }

    private fun buildButtons() {
        val keys = listOf("pause", "restart", "sound", "back")
        val rects = if (portrait) {
            splitRow(bar!!, 4)
        } else {
            val area = buttonArea!!
            val pad = max(4f, area.height() * 0.08f)
            val cw = (area.width() - pad) / 2f
            val ch = (area.height() - pad) / 2f
            (0 until 4).map {
                rect(area.left + (it % 2) * (cw + pad), area.top + (it / 2) * (ch + pad), cw, ch)
            }
        }
        keys.forEachIndexed { i, key -> buttons[key] = rects[i] }
    }

    fun tileRect(row: Int, col: Int): RectF =
        rect(board.left + col * tile, board.top + row * tile, tile, tile)

    fun cellAt(x: Float, y: Float): Int {
        val col = ((x - board.left) / tile).toInt()
        val row = ((y - board.top) / tile).toInt()
        if (x < board.left || y < board.top || row !in 0 until rows || col !in 0 until cols) return -1
        return row * cols + col
    }

    fun centreCard(widthFrac: Float, heightFrac: Float): RectF {
        val cw = min(w * widthFrac, w - 2 * margin)
        val ch = min(h * heightFrac, h - 2 * margin)
        return rect((w - cw) / 2f, (h - ch) / 2f, cw, ch)
    }
}
