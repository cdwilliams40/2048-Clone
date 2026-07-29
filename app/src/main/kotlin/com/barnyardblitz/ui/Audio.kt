package com.barnyardblitz.ui

/**
 * Sound the scenes can ask for.
 *
 * Keeping this an interface stops the UI layer depending on android.media, so
 * the whole drawing stack can be exercised off-device.
 */
interface Audio {
    val muted: Boolean

    fun play(name: String, volume: Float = 1f)

    /** Returns the new muted state. */
    fun toggleMute(): Boolean

    /** Does nothing; used by harnesses and when audio cannot be opened. */
    object Silent : Audio {
        override var muted: Boolean = false
            private set

        override fun play(name: String, volume: Float) = Unit

        override fun toggleMute(): Boolean {
            muted = !muted
            return muted
        }
    }
}
