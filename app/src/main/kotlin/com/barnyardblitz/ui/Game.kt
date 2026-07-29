package com.barnyardblitz.ui

import android.graphics.Canvas
import android.graphics.RectF
import com.barnyardblitz.art.Sprites
import com.barnyardblitz.audio.SfxPlayer
import com.barnyardblitz.data.SaveStore
import com.barnyardblitz.engine.BOARD_COLS
import com.barnyardblitz.engine.BOARD_ROWS
import com.barnyardblitz.engine.MATCH_COLS
import com.barnyardblitz.engine.MATCH_ROWS
import com.barnyardblitz.engine.Session
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

private const val AUTOSAVE_SECONDS = 20f

/**
 * Holds everything the scenes share - the save session, artwork, audio, the
 * particle system - and owns the switch between the three screens.
 */
class Game(private val store: SaveStore, val sfx: SfxPlayer) {

    val random = Random(System.nanoTime())
    val ui = Ui()
    val effects = Effects(random)
    val sprites = Sprites()

    var session: Session = Session.load(store.read(), random)
        private set

    var width = 0
        private set
    var height = 0
        private set

    lateinit var farmLayout: FarmLayout
        private set
    lateinit var blitzLayout: BlitzLayout
        private set

    private val toasts = ArrayList<Toast>()
    private var saveTimer = 0f
    private var sizeReady = false

    val farmScene = FarmScene(this)
    val blitzScene = BlitzScene(this)
    val storyScene = StoryScene(this)

    var scene: Scene = farmScene
        private set

    /** True once a surface size has arrived; drawing before that is skipped. */
    val ready: Boolean get() = sizeReady

    fun resize(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        width = newWidth
        height = newHeight
        farmLayout = FarmLayout(newWidth.toFloat(), newHeight.toFloat(), BOARD_ROWS, BOARD_COLS)
        blitzLayout = BlitzLayout(newWidth.toFloat(), newHeight.toFloat(), MATCH_ROWS, MATCH_COLS)
        sprites.rebuild(
            width = newWidth,
            height = newHeight,
            tileSize = max(12, (blitzLayout.tile - 6f).roundToInt()),
            itemSize = max(14, (farmLayout.cell * 0.94f).roundToInt()),
            smallPortrait = max(20, (farmLayout.cell * 0.72f).roundToInt()),
            bigPortrait = max(48, (minOf(newWidth, newHeight) * 0.16f).roundToInt()),
            cloudTop = farmLayout.margin.roundToInt(),
        )
        val first = !sizeReady
        sizeReady = true
        farmScene.onLayout()
        blitzScene.onLayout()
        storyScene.onLayout()
        if (first) scene.onEnter()
    }

    // ------------------------------------------------------------------ scenes
    fun go(name: String, argument: String? = null) {
        save()
        scene = when (name) {
            "blitz" -> blitzScene
            "story" -> storyScene
            else -> farmScene
        }
        effects.clear()
        scene.onEnter(argument)
    }

    /** Set by MainActivity so the game can close the app from its own menus. */
    var onQuit: (() -> Unit)? = null

    fun quit() {
        save()
        onQuit?.invoke()
    }

    // ------------------------------------------------------------------ toasts
    fun toast(text: String, color: Int = Palette.CREAM) {
        toasts.add(Toast(text, color))
        while (toasts.size > 4) toasts.removeAt(0)
    }

    private fun pumpEvents() {
        for (event in session.drain()) {
            val color = when (event.kind) {
                "warn" -> Palette.WARN
                "coins", "blitz" -> Palette.GOLD
                "order" -> 0xFF7EC87E.toInt()
                "level", "unlock" -> 0xFFFFE278.toInt()
                "task" -> 0xFF92D0F0.toInt()
                else -> Palette.CREAM
            }
            toast(event.text, color)
        }
    }

    /** Snackbars stacked up from the button bar, clear of the playfield. */
    private fun drawToasts(canvas: Canvas) {
        if (toasts.isEmpty()) return
        val anchor = scene.let { current ->
            when (current) {
                is BlitzScene -> blitzLayout.bar?.top ?: (height - blitzLayout.margin)
                else -> farmLayout.bar.top
            }
        } - farmLayout.gap
        var y = anchor
        for (index in toasts.indices.reversed()) {
            val toast = toasts[index]
            val alpha = minOf(1f, toast.life / 0.5f)
            val size = ui.fs(17)
            val textWidth = ui.textWidth(toast.text, size, true)
            val plateH = ui.lineHeight(size, true) + ui.fs(12)
            val plateW = textWidth + ui.fs(26)
            val rect = RectF(width / 2f - plateW / 2f, y - plateH, width / 2f + plateW / 2f, y)
            ui.fill.color = withAlpha(0xFF2E241E.toInt(), 0.86f * alpha)
            canvas.drawRoundRect(rect, plateH / 2f, plateH / 2f, ui.fill)
            ui.text(
                canvas, toast.text, size, withAlpha(toast.color, alpha),
                rect.centerX(), rect.centerY(), Ui.Align.CENTER, bold = true, shadow = false,
            )
            y -= plateH + ui.fs(6)
        }
    }

    // -------------------------------------------------------------------- loop
    fun update(dt: Float) {
        session.tick(dt.toDouble())
        effects.update(dt)
        scene.update(dt)
        pumpEvents()
        var i = toasts.size - 1
        while (i >= 0) {
            toasts[i].life -= dt
            if (toasts[i].life <= 0f) toasts.removeAt(i)
            i--
        }
        saveTimer += dt
        if (saveTimer >= AUTOSAVE_SECONDS) save()
    }

    fun draw(canvas: Canvas) {
        if (!sizeReady) return
        ui.resetFrame()
        val dx = effects.offsetX()
        val dy = effects.offsetY()
        canvas.save()
        canvas.translate(dx, dy)
        sprites.background?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        scene.draw(canvas)
        drawToasts(canvas)
        canvas.restore()
    }

    // ------------------------------------------------------------------- input
    fun onDown(x: Float, y: Float) {
        if (sizeReady) scene.onDown(x, y)
    }

    fun onMove(x: Float, y: Float) {
        if (sizeReady) scene.onMove(x, y)
    }

    fun onUp(x: Float, y: Float) {
        if (sizeReady) scene.onUp(x, y)
    }

    /** Returns true when the game handled it; false means "leave the app". */
    fun onBack(): Boolean {
        if (!sizeReady) return false
        if (scene.onBack()) return true
        if (scene !== farmScene) {
            go("farm")
            return true
        }
        return false
    }

    // -------------------------------------------------------------------- save
    fun save() {
        store.write(session)
        saveTimer = 0f
    }

    fun restart() {
        session = Session.new(random)
        save()
        go("farm")
    }
}
