package com.barnyardblitz.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.barnyardblitz.engine.Sfx

/**
 * Plays the procedurally generated effects through AudioTrack.
 *
 * Waveforms are synthesised on a background thread at start-up; until they are
 * ready (and on any device where audio cannot be opened) every call is a no-op,
 * so sound never blocks or crashes the game.
 */
class SfxPlayer {

    private val tracks = HashMap<String, AudioTrack>()
    @Volatile private var ready = false

    var muted: Boolean = false
        private set

    /** Build every waveform. Safe to call from a worker thread. */
    fun warmUp() {
        if (ready) return
        val built = try {
            Sfx.buildAll()
        } catch (_: Throwable) {
            return
        }
        for ((name, samples) in built) {
            val track = createTrack(samples) ?: continue
            synchronized(tracks) { tracks[name] = track }
        }
        ready = true
    }

    private fun createTrack(samples: ShortArray): AudioTrack? = try {
        val bytes = samples.size * 2
        @Suppress("DEPRECATION")
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(Sfx.RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bytes,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        if (track.state != AudioTrack.STATE_NO_STATIC_DATA && track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            null
        } else {
            track.write(samples, 0, samples.size)
            track
        }
    } catch (_: Throwable) {
        null
    }

    fun play(name: String, volume: Float = 1f) {
        if (!ready || muted) return
        val track = synchronized(tracks) { tracks[name] } ?: return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.setVolume(volume.coerceIn(0f, 1f))
            track.reloadStaticData()
            track.play()
        } catch (_: Throwable) {
            // A device that refuses to play is not worth crashing over.
        }
    }

    /** Returns the new muted state. */
    fun toggleMute(): Boolean {
        muted = !muted
        return muted
    }

    fun release() {
        synchronized(tracks) {
            for (track in tracks.values) {
                try {
                    track.stop()
                } catch (_: Throwable) {
                    // ignore
                }
                track.release()
            }
            tracks.clear()
        }
        ready = false
    }
}
