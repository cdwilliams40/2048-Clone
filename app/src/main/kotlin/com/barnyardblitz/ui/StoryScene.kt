package com.barnyardblitz.ui

import android.graphics.Canvas
import android.graphics.RectF
import com.barnyardblitz.engine.CHAPTERS
import com.barnyardblitz.engine.Line
import com.barnyardblitz.engine.Sfx
import com.barnyardblitz.engine.Task
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Chapters, renovation tasks and the dialogue scenes between them. */
class StoryScene(private val game: Game) : Scene {

    private val ui get() = game.ui
    private val layout get() = game.farmLayout
    private val session get() = game.session

    private var lines: List<Line> = emptyList()
    private var lineIndex = 0
    private var returnToFarm = false
    private var elapsed = 0f

    private val inDialogue: Boolean get() = lineIndex < lines.size

    override fun onEnter(argument: String?) {
        elapsed = 0f
        lines = emptyList()
        lineIndex = 0
        returnToFarm = false
        val story = session.story
        val chapter = story.current ?: return
        if (chapter.key !in story.seenIntro) {
            story.seenIntro.add(chapter.key)
            play(chapter.intro, backToFarm = argument == "intro")
        }
    }

    private fun play(next: List<Line>, backToFarm: Boolean = false) {
        lines = next
        lineIndex = 0
        returnToFarm = backToFarm
    }

    // ------------------------------------------------------------------- input
    override fun onDown(x: Float, y: Float) {
        if (inDialogue) {
            advance()
            return
        }
        if (ui.hit("back", x, y)) {
            game.go("farm")
            return
        }
        session.story.tasks().forEachIndexed { i, task ->
            if (ui.hit("task$i", x, y)) {
                startTask(task)
                return
            }
        }
    }

    override fun onBack(): Boolean {
        if (inDialogue) {
            advance()
            return true
        }
        return false
    }

    private fun advance() {
        lineIndex++
        if (inDialogue) return
        lines = emptyList()
        lineIndex = 0
        if (returnToFarm) {
            returnToFarm = false
            game.go("farm")
        }
    }

    private fun startTask(task: Task) {
        val story = session.story
        if (story.isDone(task)) return
        if (!session.economy.canAfford(task.cost)) {
            game.toast("Not enough coins yet", Palette.WARN)
            game.sfx.play(Sfx.INVALID)
            return
        }
        session.completeTask(task)
        game.sfx.play(Sfx.HAY)
        game.effects.kick(6f)
        game.save()
        if (story.chapterComplete) {
            val chapter = story.current
            val outro = chapter?.outro ?: emptyList()
            chapter?.let { story.seenOutro.add(it.key) }
            session.advanceChapter()
            val next = story.current
            if (next != null) {
                story.seenIntro.add(next.key)
                play(outro + next.intro)
            } else {
                play(outro, backToFarm = true)
            }
        }
    }

    override fun update(dt: Float) {
        elapsed += dt
    }

    // -------------------------------------------------------------------- draw
    override fun draw(canvas: Canvas) {
        ui.scale = layout.scale
        drawChapter(canvas)
        if (inDialogue) drawDialogue(canvas)
    }

    private fun drawChapter(canvas: Canvas) {
        val m = layout.margin
        val story = session.story
        val header = RectF(m, m, layout.w - m, m + max(56f, min(layout.h * 0.12f, layout.fs(86))))
        ui.plank(canvas, header, Palette.BARN_RED)

        val chapter = story.current
        val title = chapter?.title ?: "Hollow Creek Farm"
        val blurb = chapter?.blurb ?: "Every chapter finished. Gran would be proud."
        val step = if (chapter == null) {
            "${CHAPTERS.size} of ${CHAPTERS.size}"
        } else {
            "Chapter ${story.chapter + 1} of ${CHAPTERS.size}"
        }
        // Reserve the coin counter's width before fitting the title, or a long
        // chapter name runs straight through it.
        val coins = "${formatCoins(session.economy.coins)} coins"
        val coinsWidth = ui.textWidth(coins, layout.fs(18), true)
        ui.text(canvas, coins, layout.fs(18), Palette.GOLD, header.right - layout.fs(14), header.top + layout.fs(22), Ui.Align.RIGHT, bold = true)
        val titleRoom = header.width() - layout.fs(28) - coinsWidth - layout.fs(12)
        ui.textFitted(canvas, title, layout.fs(27), Palette.CREAM, header.left + layout.fs(14), header.top + layout.fs(24), titleRoom, bold = true)
        ui.text(canvas, step, layout.fs(15), Palette.GOLD, header.left + layout.fs(14), header.top + layout.fs(50))

        var y = header.bottom + layout.gap * 2
        y += ui.wrapped(canvas, blurb, layout.fs(17), Palette.INK, m + layout.fs(6), y, layout.w - 2 * m - layout.fs(12))
        y += layout.gap

        val barH = max(44f, layout.h * 0.07f)
        val back = RectF(m, layout.h - m - barH, layout.w - m, layout.h - m)
        val tasks = story.tasks()
        val available = back.top - layout.gap - y
        if (tasks.isNotEmpty()) {
            // Size the card to its text rather than to the screen, or a tall
            // phone gives every task a near-empty slab.
            val cardH = max(70f, min(layout.fs(86), available / tasks.size - layout.gap))
            // Sit the jobs right under the blurb; a centred block on a tall
            // phone strands them in the middle of nowhere.
            val top = y + layout.gap
            tasks.forEachIndexed { i, task ->
                drawTask(canvas, i, RectF(m, top + i * (cardH + layout.gap), layout.w - m, top + i * (cardH + layout.gap) + cardH), task)
            }
        } else {
            ui.text(canvas, "The farm is finished. Gran would be proud.", layout.fs(20), Palette.INK, layout.w / 2f, y + available / 2f, Ui.Align.CENTER, bold = true)
        }
        ui.button(canvas, "back", back, "Back to the yard", Palette.WOOD_DARK, 20)
    }

    private fun drawTask(canvas: Canvas, index: Int, rect: RectF, task: Task) {
        val story = session.story
        val done = story.isDone(task)
        val affordable = session.economy.canAfford(task.cost)
        ui.plank(canvas, rect, if (done) 0xFFD6EED2.toInt() else Palette.CREAM)

        val pad = layout.fs(12)
        ui.text(canvas, task.title, layout.fs(21), Palette.INK, rect.left + pad, rect.top + pad + layout.fs(11), bold = true, shadow = false)
        ui.wrapped(canvas, task.detail, layout.fs(15), Palette.INK_SOFT, rect.left + pad, rect.top + pad + layout.fs(26), rect.width() * 0.58f)

        val bw = rect.width() * 0.28f
        val bh = min(rect.height() - pad * 2, max(36f, layout.fs(46)))
        val button = RectF(rect.right - bw - pad, rect.centerY() - bh / 2f, rect.right - pad, rect.centerY() + bh / 2f)
        if (done) {
            ui.text(canvas, "Done", layout.fs(20), Palette.GREEN, button.centerX(), button.centerY(), Ui.Align.CENTER, bold = true, shadow = false)
        } else {
            ui.button(
                canvas, "task$index", button, formatCoins(task.cost),
                if (affordable) Palette.GOLD else Palette.WOOD, 19, affordable,
                if (affordable) Palette.INK else Palette.CREAM,
            )
        }
    }

    private fun drawDialogue(canvas: Canvas) {
        val line = lines[lineIndex]
        ui.veil(canvas, layout.w, layout.h, 0.75f)

        // Measure first so the box is only as tall as the speech needs.
        val textWidth = (layout.w - 2 * layout.margin) * 0.86f
        val wrapped = ui.wrapLines(line.text, layout.fs(19), textWidth)
        val boxH = max(layout.fs(120), layout.fs(30) + wrapped.size * ui.lineHeight(layout.fs(19)) * 1.15f + layout.fs(48))
        val box = RectF(layout.margin, layout.h - layout.margin - boxH, layout.w - layout.margin, layout.h - layout.margin)
        val inner = ui.panel(canvas, box)

        game.sprites.portraitBig[line.portrait]?.let {
            val size = min(it.width.toFloat(), layout.h * 0.16f)
            canvas.drawBitmap(
                it, null,
                RectF(box.left + size * 0.2f, box.top - size * 0.72f, box.left + size * 1.2f, box.top + size * 0.28f),
                null,
            )
        }
        ui.text(canvas, line.speaker, layout.fs(22), Palette.BARN_RED, inner.left, inner.top + layout.fs(12), bold = true)
        ui.wrapped(canvas, line.text, layout.fs(19), Palette.INK, inner.left, inner.top + layout.fs(30), inner.width())

        val hint = if (lineIndex < lines.size - 1) "tap to continue" else "tap to finish"
        val pulse = 0.5f + 0.5f * abs((elapsed * 1.4f) % 2f - 1f)
        ui.text(canvas, hint, layout.fs(14), withAlpha(Palette.INK_SOFT, pulse), inner.right, inner.bottom, Ui.Align.RIGHT, shadow = false)
    }
}
