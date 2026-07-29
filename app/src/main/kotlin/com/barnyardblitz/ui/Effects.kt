package com.barnyardblitz.ui

import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Particles, floating score popups and screen shake. */
class Effects(private val random: Random = Random.Default) {

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val size: Float, val color: Int,
    )

    private class Popup(
        var x: Float, var y: Float, val text: String, val color: Int,
        var life: Float, val maxLife: Float, val size: Float,
    )

    private val particles = ArrayList<Particle>()
    private val popups = ArrayList<Popup>()
    var shake: Float = 0f
        private set

    fun clear() {
        particles.clear()
        popups.clear()
        shake = 0f
    }

    fun burst(x: Float, y: Float, color: Int, count: Int = 12, speed: Float = 260f) {
        repeat(count) {
            val angle = random.nextDouble(0.0, Math.PI * 2).toFloat()
            val mag = (0.35f + random.nextFloat() * 0.65f) * speed
            particles.add(
                Particle(
                    x, y,
                    Math.cos(angle.toDouble()).toFloat() * mag,
                    Math.sin(angle.toDouble()).toFloat() * mag - 60f,
                    0.35f + random.nextFloat() * 0.35f, 0.7f,
                    3f + random.nextFloat() * 4f, color,
                ),
            )
        }
    }

    fun ring(x: Float, y: Float, color: Int, count: Int = 22, speed: Float = 420f) {
        for (i in 0 until count) {
            val angle = (Math.PI * 2 * i / count).toFloat()
            particles.add(
                Particle(
                    x, y,
                    Math.cos(angle.toDouble()).toFloat() * speed,
                    Math.sin(angle.toDouble()).toFloat() * speed,
                    0.45f, 0.45f, 4f + random.nextFloat() * 4f, color,
                ),
            )
        }
    }

    fun feathers(x: Float, y: Float, count: Int = 16) {
        val palette = intArrayOf(0xFFFFFAEB.toInt(), 0xFFFAE296.toInt(), 0xFFF09E2A.toInt())
        repeat(count) {
            particles.add(
                Particle(
                    x, y,
                    -160f + random.nextFloat() * 320f, -260f + random.nextFloat() * 200f,
                    0.6f + random.nextFloat() * 0.5f, 1.1f,
                    4f + random.nextFloat() * 4f, palette[random.nextInt(palette.size)],
                ),
            )
        }
    }

    fun popup(x: Float, y: Float, text: String, color: Int, size: Float) {
        popups.add(Popup(x, y, text, color, 0.9f, 0.9f, size))
        // A short stack: past three or four, labels just overlap each other.
        while (popups.size > 4) popups.removeAt(0)
    }

    fun kick(amount: Float) {
        shake = minOf(14f, shake + amount)
    }

    fun update(dt: Float) {
        var i = particles.size - 1
        while (i >= 0) {
            val particle = particles[i]
            particle.life -= dt
            if (particle.life <= 0f) {
                particles.removeAt(i)
            } else {
                particle.vy += 900f * dt
                particle.vx *= 0.98f
                particle.x += particle.vx * dt
                particle.y += particle.vy * dt
            }
            i--
        }
        var j = popups.size - 1
        while (j >= 0) {
            val popup = popups[j]
            popup.life -= dt
            if (popup.life <= 0f) popups.removeAt(j) else popup.y -= 46f * dt
            j--
        }
        shake = maxOf(0f, shake - 44f * dt)
    }

    fun offsetX(): Float = if (shake <= 0.1f) 0f else (random.nextInt(3) - 1) * shake
    fun offsetY(): Float = if (shake <= 0.1f) 0f else (random.nextInt(3) - 1) * shake

    fun draw(canvas: Canvas, ui: Ui) {
        for (particle in particles) {
            val alpha = (particle.life / particle.maxLife).coerceIn(0f, 1f)
            val size = maxOf(2f, particle.size * (0.4f + 0.6f * alpha))
            ui.fill.color = withAlpha(particle.color, alpha)
            canvas.drawRoundRect(
                RectF(particle.x - size, particle.y - size, particle.x + size, particle.y + size),
                size / 2f, size / 2f, ui.fill,
            )
        }
        for (popup in popups) {
            val t = popup.life / popup.maxLife
            val alpha = minOf(1f, t * 2.2f)
            ui.text(
                canvas, popup.text, popup.size, withAlpha(popup.color, alpha),
                popup.x, popup.y, Ui.Align.CENTER, bold = true,
            )
        }
    }

    /** Used by tests and the view to know whether a redraw is still needed. */
    val busy: Boolean get() = particles.isNotEmpty() || popups.isNotEmpty() || shake > 0.1f
}

/** A short message that slides up from the button bar. */
class Toast(val text: String, val color: Int) {
    var life: Float = 2.4f
    val maxLife: Float = 2.4f
}

/** Shared by every scene. */
interface Scene {
    fun onEnter(argument: String? = null) {}
    fun onLayout() {}
    fun update(dt: Float) {}
    fun draw(canvas: Canvas)
    fun onDown(x: Float, y: Float) {}
    fun onMove(x: Float, y: Float) {}
    fun onUp(x: Float, y: Float) {}
    /** Return true when the scene consumed the back gesture. */
    fun onBack(): Boolean = false
}

internal fun easeOut(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    val inv = 1f - clamped
    return 1f - inv * inv * inv
}

internal fun easeIn(t: Float): Float = t.coerceIn(0f, 1f).let { it * it }

internal fun formatCoins(value: Int): String {
    if (abs(value) < 1000) return value.toString()
    val text = value.toString()
    val out = StringBuilder()
    var count = 0
    for (i in text.length - 1 downTo 0) {
        out.append(text[i])
        count++
        if (count % 3 == 0 && i > 0 && text[i - 1].isDigit()) out.append(',')
    }
    return out.reverse().toString()
}

internal fun Float.roundToIntSafe(): Int = if (isNaN()) 0 else roundToInt()
