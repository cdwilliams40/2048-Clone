package com.barnyardblitz.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.barnyardblitz.engine.Chain
import com.barnyardblitz.engine.Chains
import com.barnyardblitz.engine.MAX_TIER
import com.barnyardblitz.engine.Motif
import com.barnyardblitz.engine.Power
import com.barnyardblitz.ui.Palette
import com.barnyardblitz.ui.shade
import com.barnyardblitz.ui.withAlpha
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Every pixel the game shows is drawn here at run time - the animals, the merge
 * items, the portraits and the scenery - so the APK ships no image assets.
 *
 * Sprites are rendered once into bitmaps at the current cell size and cached;
 * only the background and item art get rebuilt when the screen size changes.
 */
private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

private fun p(): Paint = paint.apply {
    reset()
    isAntiAlias = true
    style = Paint.Style.FILL
    shader = null
}

private fun oval(c: Canvas, color: Int, cx: Float, cy: Float, w: Float, h: Float) {
    val q = p()
    q.color = color
    c.drawOval(RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), q)
}

private fun poly(c: Canvas, color: Int, points: List<Pair<Float, Float>>) {
    val path = Path()
    points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
    path.close()
    val q = p()
    q.color = color
    c.drawPath(path, q)
}

private fun box(c: Canvas, color: Int, rect: RectF, radius: Float = 0f) {
    val q = p()
    q.color = color
    if (radius > 0f) c.drawRoundRect(rect, radius, radius, q) else c.drawRect(rect, q)
}

private fun outline(c: Canvas, color: Int, rect: RectF, width: Float, radius: Float = 0f) {
    val q = p()
    q.color = color
    q.style = Paint.Style.STROKE
    q.strokeWidth = width
    if (radius > 0f) c.drawRoundRect(rect, radius, radius, q) else c.drawRect(rect, q)
}

private fun line(c: Canvas, color: Int, x1: Float, y1: Float, x2: Float, y2: Float, width: Float) {
    val q = p()
    q.color = color
    q.style = Paint.Style.STROKE
    q.strokeWidth = width
    c.drawLine(x1, y1, x2, y2, q)
}

// --------------------------------------------------------------------- animals
private fun cow(c: Canvas, s: Float, body: Int, accent: Int) {
    oval(c, shade(body, -0.35f), 0.20f * s, 0.34f * s, 0.20f * s, 0.24f * s)
    oval(c, shade(body, -0.35f), 0.80f * s, 0.34f * s, 0.20f * s, 0.24f * s)
    oval(c, 0xFFEEE2C4.toInt(), 0.30f * s, 0.19f * s, 0.13f * s, 0.16f * s)
    oval(c, 0xFFEEE2C4.toInt(), 0.70f * s, 0.19f * s, 0.13f * s, 0.16f * s)
    oval(c, body, 0.5f * s, 0.50f * s, 0.72f * s, 0.66f * s)
    oval(c, 0xFF3A3230.toInt(), 0.32f * s, 0.36f * s, 0.26f * s, 0.24f * s)
    oval(c, accent, 0.5f * s, 0.68f * s, 0.42f * s, 0.30f * s)
    oval(c, shade(accent, -0.3f), 0.42f * s, 0.68f * s, 0.07f * s, 0.09f * s)
    oval(c, shade(accent, -0.3f), 0.58f * s, 0.68f * s, 0.07f * s, 0.09f * s)
    oval(c, 0xFF201C1A.toInt(), 0.34f * s, 0.44f * s, 0.09f * s, 0.10f * s)
    oval(c, 0xFF201C1A.toInt(), 0.66f * s, 0.44f * s, 0.09f * s, 0.10f * s)
}

private fun pig(c: Canvas, s: Float, body: Int, accent: Int) {
    poly(c, shade(body, -0.2f), listOf(0.24f * s to 0.28f * s, 0.20f * s to 0.06f * s, 0.44f * s to 0.20f * s))
    poly(c, shade(body, -0.2f), listOf(0.76f * s to 0.28f * s, 0.80f * s to 0.06f * s, 0.56f * s to 0.20f * s))
    oval(c, body, 0.5f * s, 0.52f * s, 0.76f * s, 0.70f * s)
    oval(c, accent, 0.5f * s, 0.66f * s, 0.36f * s, 0.28f * s)
    oval(c, shade(accent, -0.4f), 0.43f * s, 0.66f * s, 0.07f * s, 0.10f * s)
    oval(c, shade(accent, -0.4f), 0.57f * s, 0.66f * s, 0.07f * s, 0.10f * s)
    oval(c, 0xFF2C2022.toInt(), 0.35f * s, 0.42f * s, 0.09f * s, 0.10f * s)
    oval(c, 0xFF2C2022.toInt(), 0.65f * s, 0.42f * s, 0.09f * s, 0.10f * s)
}

private fun chicken(c: Canvas, s: Float, body: Int, accent: Int) {
    listOf(0.38f, 0.50f, 0.62f).forEachIndexed { i, x ->
        val hh = if (i == 1) 0.20f else 0.15f
        oval(c, accent, x * s, (0.22f - hh * 0.35f) * s, 0.15f * s, hh * s)
    }
    oval(c, body, 0.5f * s, 0.52f * s, 0.68f * s, 0.64f * s)
    poly(c, 0xFFF09E2A.toInt(), listOf(0.50f * s to 0.56f * s, 0.86f * s to 0.62f * s, 0.50f * s to 0.72f * s))
    oval(c, shade(accent, -0.1f), 0.54f * s, 0.78f * s, 0.12f * s, 0.16f * s)
    oval(c, 0xFF2C221E.toInt(), 0.40f * s, 0.46f * s, 0.11f * s, 0.12f * s)
    oval(c, Palette.WHITE, 0.42f * s, 0.44f * s, 0.04f * s, 0.04f * s)
}

private fun sheep(c: Canvas, s: Float, body: Int, accent: Int) {
    listOf(
        Triple(0.28f, 0.36f, 0.20f), Triple(0.72f, 0.36f, 0.20f), Triple(0.5f, 0.26f, 0.22f),
        Triple(0.24f, 0.62f, 0.20f), Triple(0.76f, 0.62f, 0.20f), Triple(0.5f, 0.60f, 0.30f),
    ).forEach { (cx, cy, rr) -> oval(c, body, cx * s, cy * s, rr * 2 * s, rr * 1.9f * s) }
    oval(c, shade(accent, 0.05f), 0.26f * s, 0.52f * s, 0.16f * s, 0.12f * s)
    oval(c, shade(accent, 0.05f), 0.74f * s, 0.52f * s, 0.16f * s, 0.12f * s)
    oval(c, accent, 0.5f * s, 0.58f * s, 0.40f * s, 0.42f * s)
    oval(c, 0xFFFAFAFA.toInt(), 0.42f * s, 0.54f * s, 0.10f * s, 0.11f * s)
    oval(c, 0xFFFAFAFA.toInt(), 0.58f * s, 0.54f * s, 0.10f * s, 0.11f * s)
    oval(c, 0xFF1E1A1A.toInt(), 0.42f * s, 0.55f * s, 0.05f * s, 0.06f * s)
    oval(c, 0xFF1E1A1A.toInt(), 0.58f * s, 0.55f * s, 0.05f * s, 0.06f * s)
}

private fun duck(c: Canvas, s: Float, body: Int, accent: Int) {
    oval(c, shade(body, -0.14f), 0.5f * s, 0.16f * s, 0.15f * s, 0.17f * s)
    oval(c, body, 0.5f * s, 0.46f * s, 0.70f * s, 0.66f * s)
    oval(c, 0xFFD06C1A.toInt(), 0.5f * s, 0.735f * s, 0.48f * s, 0.25f * s)
    oval(c, accent, 0.5f * s, 0.715f * s, 0.46f * s, 0.20f * s)
    oval(c, 0xFFFFC468.toInt(), 0.5f * s, 0.665f * s, 0.40f * s, 0.10f * s)
    oval(c, 0xFFB05814.toInt(), 0.44f * s, 0.66f * s, 0.04f * s, 0.04f * s)
    oval(c, 0xFFB05814.toInt(), 0.56f * s, 0.66f * s, 0.04f * s, 0.04f * s)
    oval(c, 0xFF28221C.toInt(), 0.37f * s, 0.42f * s, 0.10f * s, 0.11f * s)
    oval(c, 0xFF28221C.toInt(), 0.63f * s, 0.42f * s, 0.10f * s, 0.11f * s)
    oval(c, Palette.WHITE, 0.39f * s, 0.40f * s, 0.04f * s, 0.04f * s)
    oval(c, Palette.WHITE, 0.65f * s, 0.40f * s, 0.04f * s, 0.04f * s)
}

private fun horse(c: Canvas, s: Float, body: Int, accent: Int) {
    poly(c, shade(body, -0.2f), listOf(0.28f * s to 0.26f * s, 0.26f * s to 0.05f * s, 0.44f * s to 0.18f * s))
    poly(c, shade(body, -0.2f), listOf(0.72f * s to 0.26f * s, 0.74f * s to 0.05f * s, 0.56f * s to 0.18f * s))
    oval(c, body, 0.5f * s, 0.50f * s, 0.60f * s, 0.78f * s)
    poly(
        c, accent,
        listOf(
            0.22f * s to 0.30f * s, 0.42f * s to 0.10f * s, 0.58f * s to 0.10f * s,
            0.78f * s to 0.30f * s, 0.62f * s to 0.24f * s, 0.5f * s to 0.30f * s, 0.38f * s to 0.24f * s,
        ),
    )
    oval(c, shade(body, 0.30f), 0.5f * s, 0.74f * s, 0.40f * s, 0.28f * s)
    oval(c, accent, 0.43f * s, 0.74f * s, 0.06f * s, 0.08f * s)
    oval(c, accent, 0.57f * s, 0.74f * s, 0.06f * s, 0.08f * s)
    oval(c, 0xFF1E1814.toInt(), 0.36f * s, 0.46f * s, 0.09f * s, 0.10f * s)
    oval(c, 0xFF1E1814.toInt(), 0.64f * s, 0.46f * s, 0.09f * s, 0.10f * s)
}

private fun rooster(c: Canvas, s: Float) {
    listOf(0.40f, 0.50f, 0.60f).forEach { x -> oval(c, 0xFFD03A32.toInt(), x * s, 0.16f * s, 0.16f * s, 0.22f * s) }
    oval(c, 0xFFFAF6EE.toInt(), 0.5f * s, 0.52f * s, 0.66f * s, 0.62f * s)
    poly(c, 0xFFF4A628.toInt(), listOf(0.50f * s to 0.54f * s, 0.88f * s to 0.61f * s, 0.50f * s to 0.72f * s))
    oval(c, 0xFFD03A32.toInt(), 0.55f * s, 0.80f * s, 0.14f * s, 0.18f * s)
    oval(c, 0xFF2C221E.toInt(), 0.40f * s, 0.46f * s, 0.12f * s, 0.13f * s)
    oval(c, Palette.WHITE, 0.42f * s, 0.44f * s, 0.045f * s, 0.045f * s)
}

/** The player's portrait: a face under a straw hat. */
private fun farmer(c: Canvas, s: Float) {
    val body = 0xFFF6E2C8.toInt()
    val hat = 0xFFE8BC60.toInt()
    oval(c, body, 0.5f * s, 0.56f * s, 0.62f * s, 0.62f * s)
    oval(c, 0xFF241E1A.toInt(), 0.38f * s, 0.54f * s, 0.08f * s, 0.09f * s)
    oval(c, 0xFF241E1A.toInt(), 0.62f * s, 0.54f * s, 0.08f * s, 0.09f * s)
    oval(c, 0xFFC68484.toInt(), 0.5f * s, 0.70f * s, 0.18f * s, 0.09f * s)
    oval(c, hat, 0.5f * s, 0.30f * s, 0.96f * s, 0.26f * s)
    oval(c, shade(hat, -0.18f), 0.5f * s, 0.235f * s, 0.56f * s, 0.30f * s)
}

private val animalDrawers: List<(Canvas, Float, Int, Int) -> Unit> =
    listOf(::cow, ::pig, ::chicken, ::sheep, ::duck, ::horse)

// ------------------------------------------------------------------ blitz tiles
private fun pad(c: Canvas, s: Float, color: Int) {
    val r = s * 0.22f
    val inset = s * 0.05f
    val rect = RectF(inset, inset, s - inset, s - inset)
    box(c, shade(color, -0.35f), RectF(rect.left, rect.top + s * 0.03f, rect.right, rect.bottom + s * 0.03f), r)
    box(c, color, rect, r)
    box(
        c, shade(color, 0.22f),
        RectF(rect.left + s * 0.07f, rect.top + s * 0.06f, rect.right - s * 0.07f, rect.top + rect.height() * 0.42f),
        s * 0.16f,
    )
    outline(c, shade(color, 0.45f), rect, max(2f, s / 40f), r)
}

private fun rainbowPad(c: Canvas, s: Float) {
    val r = s * 0.22f
    val inset = s * 0.05f
    val rect = RectF(inset, inset, s - inset, s - inset)
    val hues = intArrayOf(
        0xFFE26A9E.toInt(), 0xFFEEAD34.toInt(), 0xFFFAE25A.toInt(),
        0xFF5EB292.toInt(), 0xFF5B7FB5.toInt(), 0xFF8064C8.toInt(),
    )
    c.save()
    val clip = Path()
    clip.addRoundRect(rect, r, r, Path.Direction.CW)
    c.clipPath(clip)
    val step = rect.height() / hues.size
    hues.forEachIndexed { i, color ->
        box(c, color, RectF(rect.left, rect.top + i * step, rect.right, rect.top + (i + 1) * step + 1f))
    }
    c.restore()
    outline(c, Palette.WHITE, rect, max(2f, s / 34f), r)
}

private fun eggOverlay(c: Canvas, s: Float) {
    oval(c, 0xFFFAE296.toInt(), 0.5f * s, 0.5f * s, 0.5f * s, 0.62f * s)
    oval(c, 0xFFF4C84A.toInt(), 0.5f * s, 0.52f * s, 0.44f * s, 0.56f * s)
    oval(c, 0xFFFFF6D6.toInt(), 0.42f * s, 0.38f * s, 0.14f * s, 0.18f * s)
    for (angle in 0 until 360 step 45) {
        val rad = Math.toRadians(angle.toDouble())
        oval(
            c, 0xFFFFF0B4.toInt(),
            (0.5f + cos(rad).toFloat() * 0.40f) * s, (0.5f + sin(rad).toFloat() * 0.40f) * s,
            0.07f * s, 0.07f * s,
        )
    }
}

private fun hayOverlay(c: Canvas, s: Float) {
    val band = s * 0.13f
    listOf(
        RectF(s * 0.05f, s * 0.5f - band / 2f, s * 0.95f, s * 0.5f + band / 2f),
        RectF(s * 0.5f - band / 2f, s * 0.05f, s * 0.5f + band / 2f, s * 0.95f),
    ).forEach { rect ->
        box(c, 0xFFE8C66C.toInt(), rect, band / 2f)
        outline(c, 0xFFC49A42.toInt(), rect, max(2f, s / 60f), band / 2f)
    }
    oval(c, 0xFFFFF6D6.toInt(), 0.5f * s, 0.5f * s, 0.18f * s, 0.18f * s)
}

// -------------------------------------------------------------------- merge art
private val ITEM_WOOD = 0xFFA6764A.toInt()
private val ITEM_WOOD_DARK = 0xFF7C5636.toInt()
private val ITEM_WOOD_LIGHT = 0xFFC69664.toInt()

private fun motif(c: Canvas, chain: Chain, cx: Float, cy: Float, r: Float) {
    val base = chain.base or 0xFF000000.toInt()
    val accent = chain.accent or 0xFF000000.toInt()
    when (chain.motif) {
        Motif.EGG -> {
            oval(c, shade(base, -0.18f), cx, cy + r * 0.10f, r * 1.7f, r * 2.1f)
            oval(c, base, cx, cy, r * 1.7f, r * 2.1f)
            oval(c, shade(base, 0.5f), cx - r * 0.35f, cy - r * 0.45f, r * 0.55f, r * 0.7f)
        }
        Motif.CORN -> {
            poly(c, accent, listOf(cx - r * 0.95f to cy + r * 0.4f, cx - r * 0.3f to cy - r * 1.2f, cx - r * 0.1f to cy + r * 0.9f))
            poly(c, accent, listOf(cx + r * 0.95f to cy + r * 0.4f, cx + r * 0.3f to cy - r * 1.2f, cx + r * 0.1f to cy + r * 0.9f))
            oval(c, base, cx, cy, r * 1.15f, r * 2.1f)
            for (i in 0 until 3) oval(c, shade(base, -0.22f), cx, cy - r * 0.6f + i * r * 0.6f, r * 0.95f, r * 0.22f)
        }
        Motif.BOTTLE -> {
            box(c, base, RectF(cx - r * 0.62f, cy - r * 0.5f, cx + r * 0.62f, cy + r * 1.1f), r * 0.3f)
            box(c, base, RectF(cx - r * 0.3f, cy - r * 1.25f, cx + r * 0.3f, cy - r * 0.3f))
            box(c, accent, RectF(cx - r * 0.38f, cy - r * 1.45f, cx + r * 0.38f, cy - r * 1.07f), r * 0.14f)
            oval(c, shade(base, -0.25f), cx, cy + r * 0.55f, r * 1.15f, r * 0.4f)
        }
        Motif.WOOL -> {
            listOf(-0.55f to 0.05f, 0.55f to 0.05f, 0.0f to -0.45f, 0.0f to 0.35f).forEachIndexed { i, (dx, dy) ->
                val rr = if (i == 3) 0.75f else if (i == 2) 0.7f else 0.62f
                oval(c, base, cx + dx * r, cy + dy * r, rr * r * 2f, rr * r * 1.9f)
            }
            oval(c, shade(accent, 0.25f), cx - r * 0.2f, cy - r * 0.1f, r * 0.5f, r * 0.5f)
        }
        Motif.TOOL -> {
            box(c, accent, RectF(cx - r * 0.22f, cy - r * 0.2f, cx + r * 0.22f, cy + r * 1.3f), r * 0.18f)
            box(c, base, RectF(cx - r, cy - r * 1.25f, cx + r, cy - r * 0.4f), r * 0.24f)
            oval(c, shade(base, 0.4f), cx - r * 0.45f, cy - r * 1.05f, r * 0.6f, r * 0.28f)
        }
        Motif.BERRY -> {
            listOf(-0.45f to 0.3f, 0.45f to 0.3f, 0.0f to -0.15f).forEach { (dx, dy) ->
                oval(c, base, cx + dx * r, cy + dy * r, r * 1.15f, r * 1.15f)
                oval(c, shade(base, 0.35f), cx + dx * r - r * 0.2f, cy + dy * r - r * 0.22f, r * 0.32f, r * 0.32f)
            }
            poly(c, accent, listOf(cx to cy - r * 0.75f, cx + r * 0.85f to cy - r * 1.3f, cx + r * 0.2f to cy - r * 1.35f))
        }
    }
}

private fun itemShadow(c: Canvas, s: Float) {
    oval(c, withAlpha(0xFF000000.toInt(), 0.22f), 0.5f * s, 0.90f * s, 0.62f * s, 0.13f * s)
}

private fun container(c: Canvas, s: Float, chain: Chain, tier: Int) {
    when (tier) {
        0 -> motif(c, chain, 0.5f * s, 0.52f * s, 0.21f * s)
        1 -> {
            motif(c, chain, 0.32f * s, 0.62f * s, 0.145f * s)
            motif(c, chain, 0.68f * s, 0.62f * s, 0.145f * s)
            motif(c, chain, 0.50f * s, 0.36f * s, 0.155f * s)
            box(c, 0xFFC45C54.toInt(), RectF(0.20f * s, 0.66f * s, 0.80f * s, 0.735f * s), 0.04f * s)
        }
        2 -> {
            motif(c, chain, 0.36f * s, 0.44f * s, 0.125f * s)
            motif(c, chain, 0.64f * s, 0.44f * s, 0.125f * s)
            val body = RectF(0.20f * s, 0.50f * s, 0.80f * s, 0.84f * s)
            val path = Path()
            path.addRoundRect(body, floatArrayOf(0f, 0f, 0f, 0f, 0.2f * s, 0.2f * s, 0.2f * s, 0.2f * s), Path.Direction.CW)
            val q = p(); q.color = ITEM_WOOD; c.drawPath(path, q)
            for (i in 1 until 4) {
                val y = body.top + body.height() * i / 4f
                line(c, shade(ITEM_WOOD, -0.22f), body.left + 2f, y, body.right - 2f, y, max(2f, s * 0.018f))
            }
            box(c, ITEM_WOOD_LIGHT, RectF(0.16f * s, 0.47f * s, 0.84f * s, 0.56f * s), 0.045f * s)
        }
        3 -> {
            motif(c, chain, 0.35f * s, 0.34f * s, 0.115f * s)
            motif(c, chain, 0.65f * s, 0.34f * s, 0.115f * s)
            val body = RectF(0.16f * s, 0.42f * s, 0.84f * s, 0.84f * s)
            box(c, ITEM_WOOD, body, 0.045f * s)
            outline(c, shade(ITEM_WOOD, -0.28f), body, max(2f, s * 0.028f), 0.045f * s)
            for (i in 1 until 3) {
                val y = body.top + body.height() * i / 3f
                line(c, shade(ITEM_WOOD, -0.2f), body.left, y, body.right, y, max(2f, s * 0.022f))
            }
            line(c, ITEM_WOOD_LIGHT, body.left, body.bottom, body.right, body.top, max(2f, s * 0.022f))
        }
        4 -> {
            motif(c, chain, 0.38f * s, 0.28f * s, 0.105f * s)
            motif(c, chain, 0.63f * s, 0.28f * s, 0.105f * s)
            val body = RectF(0.14f * s, 0.36f * s, 0.86f * s, 0.68f * s)
            box(c, ITEM_WOOD, body, 0.04f * s)
            outline(c, shade(ITEM_WOOD, -0.28f), body, max(2f, s * 0.026f), 0.04f * s)
            line(c, ITEM_WOOD_DARK, 0.84f * s, 0.52f * s, 0.96f * s, 0.40f * s, max(3f, s * 0.035f))
            listOf(0.30f, 0.70f).forEach { x ->
                oval(c, 0xFF3E342E.toInt(), x * s, 0.75f * s, 0.23f * s, 0.23f * s)
                oval(c, 0xFF968A80.toInt(), x * s, 0.75f * s, 0.10f * s, 0.10f * s)
            }
        }
        else -> {
            val base = shade(chain.accent or 0xFF000000.toInt(), -0.05f)
            poly(c, shade(base, -0.25f), listOf(0.08f * s to 0.42f * s, 0.5f * s to 0.14f * s, 0.92f * s to 0.42f * s))
            box(c, base, RectF(0.16f * s, 0.42f * s, 0.84f * s, 0.86f * s))
            val door = RectF(0.40f * s, 0.58f * s, 0.60f * s, 0.86f * s)
            box(c, ITEM_WOOD_DARK, door)
            outline(c, Palette.CREAM, door, max(2f, s * 0.018f))
            box(c, Palette.CREAM, RectF(0.22f * s, 0.46f * s, 0.78f * s, 0.57f * s), 0.03f * s)
            motif(c, chain, 0.5f * s, 0.515f * s, 0.042f * s)
            listOf(0.24f, 0.68f).forEach { x ->
                box(c, Palette.CREAM, RectF(x * s, 0.62f * s, (x + 0.09f) * s, 0.72f * s))
            }
        }
    }
}

/** A signpost hut, deliberately unlike any tier so it reads as a source. */
private fun generator(c: Canvas, s: Float, chain: Chain) {
    box(c, ITEM_WOOD_DARK, RectF(0.44f * s, 0.52f * s, 0.56f * s, 0.88f * s), 0.03f * s)
    val board = RectF(0.12f * s, 0.20f * s, 0.88f * s, 0.60f * s)
    box(c, shade(chain.accent or 0xFF000000.toInt(), -0.1f), board, 0.09f * s)
    box(
        c, Palette.CREAM,
        RectF(board.left + s * 0.045f, board.top + s * 0.045f, board.right - s * 0.045f, board.bottom - s * 0.045f),
        0.06f * s,
    )
    motif(c, chain, 0.5f * s, 0.40f * s, 0.115f * s)
    listOf(200, 250, 290, 340).forEach { angle ->
        val rad = Math.toRadians(angle.toDouble())
        oval(
            c, 0xFFFFF0B0.toInt(),
            (0.5f + cos(rad).toFloat() * 0.46f) * s, (0.40f + sin(rad).toFloat() * 0.34f) * s,
            0.05f * s, 0.05f * s,
        )
    }
}

// -------------------------------------------------------------------- scenery
private fun hill(c: Canvas, color: Int, width: Float, height: Float, baseY: Float, amp: Float, phase: Float) {
    val path = Path()
    path.moveTo(0f, height)
    path.lineTo(0f, baseY)
    var x = 0f
    while (x <= width + 8f) {
        path.lineTo(x, baseY - sin(x / width * Math.PI.toFloat() * 1.5f + phase) * amp)
        x += 8f
    }
    path.lineTo(width, height)
    path.close()
    val q = p()
    q.color = color
    c.drawPath(path, q)
}

// -------------------------------------------------------------------- caching
private fun bitmap(size: Int, draw: (Canvas, Float) -> Unit): Bitmap {
    val bmp = Bitmap.createBitmap(max(1, size), max(1, size), Bitmap.Config.ARGB_8888)
    draw(Canvas(bmp), size.toFloat())
    return bmp
}

/** Key for a blitz tile: kind and power. */
fun tileKey(kind: Int, power: Power): Int = kind * 4 + power.ordinal

/** Key for a merge item: chain index and tier (6 means the generator). */
fun itemKey(chainIndex: Int, tier: Int): Int = chainIndex * 8 + tier

const val GENERATOR_TIER = 6

class Sprites {
    var tiles: Map<Int, Bitmap> = emptyMap()
        private set
    var items: Map<Int, Bitmap> = emptyMap()
        private set
    var portraitSmall: Map<Int, Bitmap> = emptyMap()
        private set
    var portraitBig: Map<Int, Bitmap> = emptyMap()
        private set
    var background: Bitmap? = null
        private set
    var barn: Bitmap? = null
        private set

    private var lastTileSize = -1
    private var lastItemSize = -1
    private var lastSmall = -1
    private var lastBig = -1
    private var lastBackground = -1 to -1

    fun rebuild(width: Int, height: Int, tileSize: Int, itemSize: Int, smallPortrait: Int, bigPortrait: Int, cloudTop: Int) {
        if (tileSize != lastTileSize) {
            tiles = buildTiles(tileSize)
            barn = buildBarn((tileSize * 2.9f).toInt(), (tileSize * 2.3f).toInt())
            lastTileSize = tileSize
        }
        if (itemSize != lastItemSize) {
            items = buildItems(itemSize)
            lastItemSize = itemSize
        }
        if (smallPortrait != lastSmall) {
            portraitSmall = buildPortraits(smallPortrait)
            lastSmall = smallPortrait
        }
        if (bigPortrait != lastBig) {
            portraitBig = buildPortraits(bigPortrait)
            lastBig = bigPortrait
        }
        if (lastBackground != width to height) {
            background = buildBackground(width, height, cloudTop)
            lastBackground = width to height
        }
    }

    private fun buildTiles(size: Int): Map<Int, Bitmap> {
        val out = HashMap<Int, Bitmap>()
        Palette.ANIMALS.forEachIndexed { kind, (padColor, body, accent) ->
            for (power in listOf(Power.NONE, Power.EGG, Power.HAY)) {
                out[tileKey(kind, power)] = bitmap(size) { c, s ->
                    pad(c, s, padColor)
                    animalDrawers[kind](c, s, body, accent)
                    when (power) {
                        Power.EGG -> eggOverlay(c, s)
                        Power.HAY -> hayOverlay(c, s)
                        else -> Unit
                    }
                }
            }
        }
        val roosterTile = bitmap(size) { c, s ->
            rainbowPad(c, s)
            rooster(c, s)
        }
        Palette.ANIMALS.indices.forEach { out[tileKey(it, Power.ROOSTER)] = roosterTile }
        return out
    }

    private fun buildItems(size: Int): Map<Int, Bitmap> {
        val out = HashMap<Int, Bitmap>()
        Chains.all.forEachIndexed { index, chain ->
            for (tier in 0..MAX_TIER) {
                out[itemKey(index, tier)] = bitmap(size) { c, s ->
                    itemShadow(c, s)
                    container(c, s, chain, tier)
                }
            }
            out[itemKey(index, GENERATOR_TIER)] = bitmap(size) { c, s ->
                itemShadow(c, s)
                generator(c, s, chain)
            }
        }
        return out
    }

    /** A round headshot for dialogue and order cards. Kind -1 is the player. */
    private fun buildPortraits(size: Int): Map<Int, Bitmap> {
        val out = HashMap<Int, Bitmap>()
        for (kind in -1 until Palette.ANIMALS.size) {
            out[kind] = bitmap(size) { c, s ->
                val q = p()
                q.color = Palette.CREAM
                c.drawCircle(s / 2f, s / 2f, s / 2f, q)
                c.save()
                val clip = Path()
                clip.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW)
                c.clipPath(clip)
                if (kind < 0) {
                    farmer(c, s)
                } else {
                    val (_, body, accent) = Palette.ANIMALS[kind]
                    animalDrawers[kind](c, s, body, accent)
                }
                c.restore()
                val ring = p()
                ring.color = Palette.WOOD
                ring.style = Paint.Style.STROKE
                ring.strokeWidth = max(2f, s / 28f)
                c.drawCircle(s / 2f, s / 2f, s / 2f - ring.strokeWidth / 2f, ring)
            }
        }
        return out
    }

    private fun buildBackground(width: Int, height: Int, cloudTop: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val sky = p()
        sky.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Palette.SKY_TOP, Palette.SKY_BOTTOM, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sky)

        // Clouds stay below cloudTop so none pokes into the margin above the bar.
        listOf(
            Triple(width * 0.18f, cloudTop + 36f, 1.0f),
            Triple(width * 0.62f, cloudTop + 26f, 0.7f),
            Triple(width * 0.88f, cloudTop + 32f, 0.85f),
        ).forEach { (cx, cy, scale) ->
            listOf(
                Triple(-26f, 6f, 20f), Triple(0f, -6f, 27f),
                Triple(26f, 4f, 22f), Triple(8f, 10f, 20f),
            ).forEach { (dx, dy, rr) ->
                oval(c, 0xFFFCFCFF.toInt(), cx + dx * scale, cy + dy * scale, rr * 2 * scale, rr * 1.7f * scale)
            }
        }
        hill(c, shade(Palette.FIELD, 0.18f), width.toFloat(), height.toFloat(), height * 0.52f, 34f, 0.4f)
        hill(c, Palette.FIELD, width.toFloat(), height.toFloat(), height * 0.66f, 26f, 2.2f)
        hill(c, Palette.FIELD_DARK, width.toFloat(), height.toFloat(), height * 0.86f, 18f, 4.0f)
        return bmp
    }

    private fun buildBarn(width: Int, height: Int): Bitmap {
        val w = max(1, width).toFloat()
        val h = max(1, height).toFloat()
        val bmp = Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        poly(c, Palette.BARN_RED_DARK, listOf(w * 0.06f to h * 0.40f, w * 0.5f to h * 0.06f, w * 0.94f to h * 0.40f))
        box(c, Palette.BARN_RED, RectF(w * 0.12f, h * 0.38f, w * 0.88f, h * 0.94f))
        val door = RectF(w * 0.36f, h * 0.56f, w * 0.64f, h * 0.94f)
        box(c, Palette.WOOD_DARK, door)
        val strokeWidth = max(2f, w * 0.012f)
        line(c, Palette.CREAM, door.left, door.top, door.right, door.bottom, strokeWidth)
        line(c, Palette.CREAM, door.right, door.top, door.left, door.bottom, strokeWidth)
        outline(c, Palette.CREAM, door, strokeWidth)
        listOf(0.20f, 0.72f).forEach { x ->
            val win = RectF(w * x, h * 0.48f, w * (x + 0.09f), h * 0.60f)
            box(c, Palette.CREAM, win)
            outline(c, Palette.WOOD_DARK, win, strokeWidth * 0.6f)
        }
        return bmp
    }
}
