package com.barnyardblitz.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The farm palette, as ARGB ints. */
object Palette {
    val SKY_TOP = 0xFF96CDEB.toInt()
    val SKY_BOTTOM = 0xFFCEE9F0.toInt()
    val FIELD = 0xFF7CB060.toInt()
    val FIELD_DARK = 0xFF689A50.toInt()
    val BARN_RED = 0xFFA63A34.toInt()
    val BARN_RED_DARK = 0xFF802A26.toInt()
    val WOOD = 0xFF7A5438.toInt()
    val WOOD_DARK = 0xFF5C3E29.toInt()
    val WOOD_LIGHT = 0xFF966C4A.toInt()
    val CREAM = 0xFFF7F0E0.toInt()
    val INK = 0xFF302620.toInt()
    val INK_SOFT = 0xFF605044.toInt()
    val GOLD = 0xFFF0BE3E.toInt()
    val WHITE = 0xFFFFFFFF.toInt()

    val CELL_LIGHT = 0xFFCEE0BA.toInt()
    val CELL_DARK = 0xFFC2D6AC.toInt()
    val CELL_EDGE = 0xFFA8C094.toInt()

    val BLITZ_CELL_LIGHT = 0xFFA8CD8A.toInt()
    val BLITZ_CELL_DARK = 0xFF98BF7C.toInt()

    val GREEN = 0xFF3A8C42.toInt()
    val WARN = 0xFFE8943E.toInt()

    /** Pad, body and accent colour for each of the six animals. */
    val ANIMALS = listOf(
        Triple(0xFF5B7FB5.toInt(), 0xFFF6F4EE.toInt(), 0xFFE896A5.toInt()), // cow
        Triple(0xFFE26A9E.toInt(), 0xFFF49AC1.toInt(), 0xFFD6709B.toInt()), // pig
        Triple(0xFFEEAD34.toInt(), 0xFFFFF6D6.toInt(), 0xFFCE3E34.toInt()), // chicken
        Triple(0xFF5EB292.toInt(), 0xFFEEEAE2.toInt(), 0xFF5C504C.toInt()), // sheep
        Triple(0xFF8064C8.toInt(), 0xFFFCD64C.toInt(), 0xFFF08E2E.toInt()), // duck
        Triple(0xFFC4583E.toInt(), 0xFFA26A3E.toInt(), 0xFF4E3222.toInt()), // horse
    )
}

/** Lighten (amount > 0) or darken (amount < 0) a colour. */
fun shade(color: Int, amount: Float): Int {
    val a = Color.alpha(color)
    fun ch(v: Int): Int = if (amount >= 0) {
        (v + (255 - v) * amount).roundToInt().coerceIn(0, 255)
    } else {
        (v * (1 + amount)).roundToInt().coerceIn(0, 255)
    }
    return Color.argb(a, ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
}

fun withAlpha(color: Int, alpha: Float): Int =
    Color.argb((alpha.coerceIn(0f, 1f) * 255).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

/**
 * Immediate-mode widget drawing plus the registry of tappable rectangles that
 * scenes hit-test against. One instance is shared by every scene.
 */
class Ui {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    val hitboxes = LinkedHashMap<String, RectF>()
    private val disabled = HashSet<String>()

    /** Scale factor applied to every font size; set from the active layout. */
    var scale: Float = 1f

    /** Which button is under the finger right now, so it can look pressed. */
    var pressedKey: String? = null

    fun resetFrame() {
        hitboxes.clear()
        disabled.clear()
        // pressedKey deliberately survives: it is owned by the touch sequence,
        // not by the frame.
    }

    fun fs(base: Int): Float = max(9f, base * scale)

    fun paintFor(size: Float, bold: Boolean): Paint {
        val p = if (bold) titlePaint else bodyPaint
        p.textSize = size
        p.textAlign = Paint.Align.LEFT
        return p
    }

    fun textWidth(text: String, size: Float, bold: Boolean): Float =
        paintFor(size, bold).measureText(text)

    fun hit(key: String, x: Float, y: Float): Boolean =
        key !in disabled && hitboxes[key]?.contains(x, y) == true

    /** The topmost enabled button under a point, if any. */
    fun hitAny(x: Float, y: Float): String? =
        hitboxes.entries.lastOrNull { it.key !in disabled && it.value.contains(x, y) }?.key

    // ---------------------------------------------------------------- widgets
    /** A wooden board with a capped drop shadow and a lit top edge. */
    fun plank(canvas: Canvas, rect: RectF, color: Int, radius: Float = -1f) {
        val short = min(rect.width(), rect.height())
        val r = if (radius >= 0f) radius else max(6f, min(24f * scale, short * 0.18f))
        val drop = max(2f, min(8f * scale, short * 0.05f))
        fill.color = shade(color, -0.4f)
        canvas.drawRoundRect(RectF(rect.left, rect.top + drop, rect.right, rect.bottom + drop), r, r, fill)
        fill.color = color
        canvas.drawRoundRect(rect, r, r, fill)
        stroke.color = shade(color, 0.22f)
        stroke.strokeWidth = max(1f, min(4f * scale, short * 0.025f))
        canvas.drawRoundRect(rect, r, r, stroke)
    }

    enum class Align { LEFT, CENTER, RIGHT }

    fun text(
        canvas: Canvas,
        value: String,
        size: Float,
        color: Int,
        x: Float,
        y: Float,
        align: Align = Align.LEFT,
        bold: Boolean = false,
        shadow: Boolean = true,
    ): Float {
        val paint = paintFor(size, bold)
        val width = paint.measureText(value)
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - width / 2f
            Align.RIGHT -> x - width
        }
        // y is the visual centre of the line, which is easier to lay out with.
        val baseline = y - (paint.descent() + paint.ascent()) / 2f
        if (shadow) {
            paint.color = withAlpha(0xFF2C221C.toInt(), 0.43f)
            canvas.drawText(value, left + 2f, baseline + 2f, paint)
        }
        paint.color = color
        canvas.drawText(value, left, baseline, paint)
        return width
    }

    fun lineHeight(size: Float, bold: Boolean = false): Float {
        val paint = paintFor(size, bold)
        return paint.descent() - paint.ascent()
    }

    /** Split [value] into lines that fit [width]. */
    fun wrapLines(value: String, size: Float, width: Float, bold: Boolean = false): List<String> {
        val paint = paintFor(size, bold)
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (word in value.split(" ")) {
            val probe = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(probe) <= width || current.isEmpty()) {
                current = StringBuilder(probe)
            } else {
                out.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    /** Draw wrapped text from the top-left; returns the height used. */
    fun wrapped(
        canvas: Canvas,
        value: String,
        size: Float,
        color: Int,
        left: Float,
        top: Float,
        width: Float,
        bold: Boolean = false,
    ): Float {
        val lines = wrapLines(value, size, width, bold)
        val step = lineHeight(size, bold) * 1.08f
        lines.forEachIndexed { i, line ->
            text(canvas, line, size, color, left, top + step * (i + 0.5f), Align.LEFT, bold, shadow = false)
        }
        return step * lines.size
    }

    /**
     * Draw [value] shrunk just enough to fit [maxWidth]. Names and titles vary
     * in length, and clipping one is worse than setting it a point smaller.
     */
    fun textFitted(
        canvas: Canvas,
        value: String,
        size: Float,
        color: Int,
        x: Float,
        y: Float,
        maxWidth: Float,
        align: Align = Align.LEFT,
        bold: Boolean = false,
        shadow: Boolean = true,
    ): Float {
        var fitted = size
        while (fitted > 8f && textWidth(value, fitted, bold) > maxWidth) fitted -= 1f
        return text(canvas, value, fitted, color, x, y, align, bold, shadow)
    }

    fun button(
        canvas: Canvas,
        key: String,
        rect: RectF,
        label: String,
        color: Int = Palette.BARN_RED,
        size: Int = 22,
        enabled: Boolean = true,
        textColor: Int = Palette.CREAM,
    ) {
        hitboxes[key] = RectF(rect)
        var face = color
        var ink = textColor
        if (!enabled) {
            disabled.add(key)
            face = shade(color, -0.35f)
            ink = shade(textColor, -0.35f)
        }
        // A pressed button sinks into its own shadow, which is the cheapest
        // possible "the tap landed" signal and costs no animation state.
        val pressed = enabled && key == pressedKey
        val target = if (pressed) RectF(rect.left, rect.top + fs(3), rect.right, rect.bottom + fs(3)) else rect
        plank(canvas, target, if (pressed) shade(face, -0.10f) else face)
        var fontSize = fs(size)
        // Shrink rather than overflow when a label is long for its button.
        val room = target.width() * 0.88f
        while (fontSize > 9f && textWidth(label, fontSize, true) > room) fontSize -= 1f
        text(canvas, label, fontSize, ink, target.centerX(), target.centerY(), Align.CENTER, bold = true)
    }

    fun meter(canvas: Canvas, rect: RectF, fraction: Float, color: Int, track: Int = 0xFFCEC4B0.toInt()) {
        val r = max(2f, rect.height() / 2f)
        fill.color = track
        canvas.drawRoundRect(rect, r, r, fill)
        val width = rect.width() * fraction.coerceIn(0f, 1f)
        if (width > 0.5f) {
            fill.color = color
            canvas.drawRoundRect(RectF(rect.left, rect.top, rect.left + width, rect.bottom), r, r, fill)
        }
    }

    fun card(
        canvas: Canvas,
        rect: RectF,
        title: String,
        value: String,
        valueColor: Int = Palette.INK,
        bar: Float? = null,
        barColor: Int = Palette.GOLD,
        face: Int = Palette.CREAM,
    ) {
        plank(canvas, rect, face)
        text(canvas, title, fs(14), Palette.INK_SOFT, rect.centerX(), rect.top + rect.height() * 0.22f, Align.CENTER, shadow = false)
        text(canvas, value, fs(30), valueColor, rect.centerX(), rect.top + rect.height() * 0.58f, Align.CENTER, bold = true)
        if (bar != null) {
            meter(
                canvas,
                RectF(
                    rect.left + rect.width() * 0.10f,
                    rect.bottom - rect.height() * 0.24f,
                    rect.right - rect.width() * 0.10f,
                    rect.bottom - rect.height() * 0.24f + max(4f, rect.height() * 0.10f),
                ),
                bar, barColor,
            )
        }
    }

    fun veil(canvas: Canvas, width: Float, height: Float, alpha: Float = 0.68f) {
        fill.color = withAlpha(0xFF1E1814.toInt(), alpha)
        canvas.drawRect(0f, 0f, width, height, fill)
    }

    /** A framed dialog panel; returns the inner area to draw into. */
    fun panel(canvas: Canvas, rect: RectF, face: Int = Palette.CREAM, frame: Int = Palette.WOOD): RectF {
        plank(canvas, rect, frame)
        val inset = min(rect.width(), rect.height()) * 0.035f
        val inner = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        plank(canvas, inner, face)
        val pad = inset * 1.6f
        return RectF(inner.left + pad, inner.top + pad, inner.right - pad, inner.bottom - pad)
    }
}
