package com.barnyardblitz

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import com.barnyardblitz.audio.SfxPlayer

/**
 * The single activity. There is one screen - the game surface - and the game
 * manages its own scenes inside it.
 */
class MainActivity : Activity() {

    /** Exposed for the instrumented smoke test. */
    internal lateinit var gameView: GameView
        private set
    private val sfx = SfxPlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = GameView(this, sfx)
        gameView.game.onQuit = { finish() }
        setContentView(gameView)

        // Synthesising every waveform takes a moment; keep it off the UI thread.
        Thread({ sfx.warmUp() }, "sfx-warmup").apply { isDaemon = true }.start()
    }

    override fun onResume() {
        super.onResume()
        gameView.onResumed()
    }

    override fun onPause() {
        // Flush the save before the system can drop us.
        gameView.onPaused()
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (gameView.handleBack()) return true
            gameView.game.save()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        sfx.release()
        super.onDestroy()
    }
}
