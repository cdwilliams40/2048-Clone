package com.barnyardblitz.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.pow
import kotlin.math.sin

/**
 * Procedurally synthesised sound effects.
 *
 * Waveforms are built here as raw 16-bit mono PCM so the app ships no audio
 * files; the Android layer only has to hand the arrays to an AudioTrack.
 */
object Sfx {
    const val RATE = 22050

    enum class Wave { SINE, SQUARE, TRIANGLE }

    data class Note(val freq: Double, val startMs: Int, val lengthMs: Int)

    const val SWAP = "swap"
    const val INVALID = "invalid"
    const val SELECT = "select"
    const val EGG = "egg"
    const val HAY = "hay"
    const val ROOSTER = "rooster"
    const val COIN = "coin"
    const val SHUFFLE = "shuffle"
    const val TICK = "tick"
    const val START = "start"
    const val OVER = "over"

    private val scale = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.51)

    /** Matches climb the scale as a cascade builds, like Blitz does. */
    fun matchName(cascade: Int): String = "match${cascade.coerceIn(0, scale.size - 1)}"

    private fun envelope(i: Int, n: Int, attack: Double = 0.02): Double {
        val a = maxOf(1, (n * attack).toInt())
        if (i < a) return i.toDouble() / a
        return (1.0 - (i - a).toDouble() / maxOf(1, n - a)).pow(1.6)
    }

    fun render(notes: List<Note>, volume: Double = 0.35, wave: Wave = Wave.SINE): ShortArray {
        val totalMs = notes.maxOf { it.startMs + it.lengthMs }
        val n = RATE * totalMs / 1000
        val samples = DoubleArray(n)
        for (note in notes) {
            val first = RATE * note.startMs / 1000
            val count = RATE * note.lengthMs / 1000
            for (i in 0 until count) {
                val index = first + i
                if (index >= n) break
                val phase = 2.0 * PI * note.freq * (i.toDouble() / RATE)
                var value = sin(phase)
                when (wave) {
                    Wave.SQUARE -> value = if (value > 0) 1.0 else -1.0
                    Wave.TRIANGLE -> value = 2.0 / PI * asin(value.coerceIn(-1.0, 1.0))
                    Wave.SINE -> Unit
                }
                samples[index] += value * envelope(i, count)
            }
        }
        val peak = maxOf(1.0, samples.maxOfOrNull { abs(it) } ?: 1.0)
        return ShortArray(n) { i ->
            ((samples[i] / peak).coerceIn(-1.0, 1.0) * volume * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Every effect, built once. Keys are the constants above plus `match0..7`. */
    fun buildAll(): Map<String, ShortArray> {
        val out = LinkedHashMap<String, ShortArray>()
        out[SWAP] = render(listOf(Note(660.0, 0, 60), Note(880.0, 30, 70)), 0.22)
        out[INVALID] = render(listOf(Note(150.0, 0, 110), Note(120.0, 60, 120)), 0.25, Wave.SQUARE)
        out[SELECT] = render(listOf(Note(1046.0, 0, 45)), 0.16)
        for (level in scale.indices) {
            val base = scale[level]
            out["match$level"] = render(
                listOf(Note(base, 0, 130), Note(base * 1.5, 45, 150)), 0.30,
            )
        }
        out[EGG] = render(
            listOf(Note(300.0, 0, 240), Note(180.0, 40, 260), Note(90.0, 90, 300)),
            0.38, Wave.TRIANGLE,
        )
        out[HAY] = render(
            listOf(Note(880.0, 0, 90), Note(620.0, 60, 120), Note(440.0, 130, 200)), 0.34,
        )
        out[ROOSTER] = render(
            listOf(Note(523.0, 0, 120), Note(659.0, 90, 120), Note(784.0, 180, 140), Note(1046.0, 270, 260)),
            0.40,
        )
        out[COIN] = render(listOf(Note(1046.0, 0, 60), Note(1318.0, 45, 90)), 0.26)
        out[SHUFFLE] = render(
            listOf(Note(392.0, 0, 110), Note(523.0, 90, 110), Note(659.0, 180, 160)), 0.30,
        )
        out[TICK] = render(listOf(Note(1200.0, 0, 45)), 0.20)
        out[START] = render(
            listOf(Note(523.0, 0, 130), Note(659.0, 120, 130), Note(784.0, 240, 130), Note(1046.0, 360, 280)),
            0.38,
        )
        out[OVER] = render(
            listOf(Note(784.0, 0, 200), Note(587.0, 180, 220), Note(392.0, 380, 420)),
            0.38, Wave.TRIANGLE,
        )
        return out
    }
}
