package com.barnyardblitz

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.barnyardblitz.audio.SfxPlayer
import com.barnyardblitz.data.SaveStore
import com.barnyardblitz.ui.Game

/**
 * The whole game surface.
 *
 * The game is immediate-mode: everything is drawn from scratch each frame, so a
 * plain View that re-posts itself on the animation clock is a better fit (and a
 * far smaller dependency footprint) than a declarative UI toolkit.
 */
class GameView(context: Context, sfx: SfxPlayer) : View(context) {

    val game = Game(SaveStore(context), sfx)

    private var lastFrameNanos = 0L
    private var running = true

    init {
        isFocusable = true
        keepScreenOn = true
    }

    fun onResumed() {
        running = true
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    fun onPaused() {
        running = false
        game.save()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        game.resize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000_000.0f)
        lastFrameNanos = now
        // Clamp so a long pause (or a debugger breakpoint) cannot teleport the
        // simulation forward.
        game.update(dt.coerceIn(0f, 0.05f))
        game.draw(canvas)
        if (running) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> game.onDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> game.onMove(event.x, event.y)
            MotionEvent.ACTION_UP -> game.onUp(event.x, event.y)
            MotionEvent.ACTION_CANCEL -> game.onUp(event.x, event.y)
            else -> return false
        }
        return true
    }

    /** Returns true when the game consumed the gesture. */
    fun handleBack(): Boolean = game.onBack()
}
