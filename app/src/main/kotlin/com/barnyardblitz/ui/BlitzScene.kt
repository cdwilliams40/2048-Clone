package com.barnyardblitz.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.barnyardblitz.art.tileKey
import com.barnyardblitz.engine.Cell
import com.barnyardblitz.engine.MatchResult
import com.barnyardblitz.engine.Match3Board
import com.barnyardblitz.engine.Power
import com.barnyardblitz.engine.Sfx
import com.barnyardblitz.engine.Session
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val SWAP_TIME = 0.13f
private const val REVERT_TIME = 0.16f
private const val CLEAR_TIME = 0.22f
private const val FALL_TIME = 0.20f
private const val FINALE_STEP = 0.30f
private const val HINT_DELAY = 5.0f
private const val BLITZ_SECONDS = 60f
private const val POINTS_PER_TILE = 40
private const val MAX_CASCADE = 10

private val SPECIAL_BONUS = mapOf("egg" to 250, "hay" to 500, "rooster" to 1000)

private val SPECIAL_ROWS = listOf(
    "4 in a row" to "Golden Egg",
    "L or T shape" to "Hay Bale",
    "5 in a row" to "Prize Rooster",
)

/**
 * The match-3 round, now a minigame that pays out energy and coins for the farm.
 */
class BlitzScene(private val game: Game) : Scene {

    private enum class Screen { MENU, PLAYING, GAME_OVER }
    private enum class Phase { IDLE, SWAP, REVERT, CLEAR, FALL, SHUFFLE, FINALE }

    private val ui get() = game.ui
    private val layout get() = game.blitzLayout
    private val session get() = game.session

    private var state = Screen.MENU
    private var mode = "blitz"
    private var board = Match3Board(random = game.random)

    private var score = 0
    private var cascade = 0
    private var bestCascade = 0
    private var movesMade = 0
    private var timeLeft = BLITZ_SECONDS
    private var elapsed = 0f
    private var timeOver = false
    private var newRecord = false
    private var paused = false
    private var rewardEnergy = 0
    private var rewardCoins = 0

    private var phase = Phase.IDLE
    private var phaseT = 0f
    private var phaseLen = 0.0001f
    private val offsets = HashMap<Cell, Pair<Float, Float>>()
    private val dying = ArrayList<Pair<Cell, Int>>()
    private var selected: Cell? = null
    private var pendingA: Cell? = null
    private var pendingB: Cell? = null
    private var swapLegal = false
    private var idleTime = 0f
    private var hint: Pair<Cell, Cell>? = null
    private var banner = ""
    private var bannerTime = 0f
    private var nextTick = BLITZ_SECONDS.toInt()
    private var dragFrom: Cell? = null
    private var dragX = 0f
    private var dragY = 0f
    private var dragUsed = false

    private fun best(which: String): Int = session.bestBlitz[which] ?: 0

    override fun onEnter(argument: String?) {
        state = Screen.MENU
        rewardEnergy = 0
        rewardCoins = 0
        resetRound()
    }

    private fun resetRound() {
        board = Match3Board(random = game.random)
        score = 0
        cascade = 0
        bestCascade = 0
        movesMade = 0
        timeLeft = BLITZ_SECONDS
        elapsed = 0f
        timeOver = false
        newRecord = false
        paused = false
        phase = Phase.IDLE
        phaseT = 0f
        phaseLen = 0.0001f
        offsets.clear()
        dying.clear()
        selected = null
        pendingA = null
        pendingB = null
        idleTime = 0f
        hint = null
        banner = ""
        bannerTime = 0f
        nextTick = BLITZ_SECONDS.toInt()
        dragFrom = null
        dragUsed = false
        game.effects.clear()
    }

    private fun start(which: String) {
        mode = which
        resetRound()
        state = Screen.PLAYING
        game.sfx.play(Sfx.START)
    }

    private fun cellFrom(index: Int): Cell = Cell(index / layout.cols, index % layout.cols)

    private fun playable(): Boolean =
        state == Screen.PLAYING && !paused && !timeOver && phase == Phase.IDLE

    // ------------------------------------------------------------------- input
    override fun onDown(x: Float, y: Float) {
        when (state) {
            Screen.MENU -> {
                if (ui.hit("start_blitz", x, y)) start("blitz")
                else if (ui.hit("start_relaxed", x, y)) start("relaxed")
                else if (ui.hit("to_farm", x, y)) game.go("farm")
                return
            }
            Screen.GAME_OVER -> {
                if (ui.hit("again", x, y)) start(mode)
                else if (ui.hit("to_farm", x, y)) game.go("farm")
                return
            }
            Screen.PLAYING -> Unit
        }
        if (onControl(x, y)) return
        if (!playable()) return
        val index = layout.cellAt(x, y)
        if (index < 0) {
            selected = null
            return
        }
        val cell = cellFrom(index)
        dragFrom = cell
        dragX = x
        dragY = y
        dragUsed = false
        val current = selected
        if (current != null && Match3Board.adjacent(current, cell)) {
            attemptSwap(current, cell)
            dragFrom = null
        } else if (current == cell) {
            selected = null
        } else {
            selected = cell
            game.sfx.play(Sfx.SELECT)
        }
    }

    private fun onControl(x: Float, y: Float): Boolean {
        if (ui.hit("pause", x, y)) {
            paused = !paused
            return true
        }
        if (ui.hit("restart", x, y)) {
            start(mode)
            return true
        }
        if (ui.hit("sound", x, y)) {
            say(if (game.sfx.toggleMute()) "Sound off" else "Sound on")
            return true
        }
        if (ui.hit("back", x, y)) {
            state = Screen.MENU
            return true
        }
        return false
    }

    /** Swipe a critter towards its neighbour - the natural phone gesture. */
    override fun onMove(x: Float, y: Float) {
        val from = dragFrom ?: return
        if (dragUsed || !playable()) return
        val dx = x - dragX
        val dy = y - dragY
        val threshold = layout.tile * 0.42f
        if (max(abs(dx), abs(dy)) < threshold) return
        val target = if (abs(dx) > abs(dy)) {
            Cell(from.row, from.col + if (dx > 0) 1 else -1)
        } else {
            Cell(from.row + if (dy > 0) 1 else -1, from.col)
        }
        if (board.inBounds(target)) {
            dragUsed = true
            attemptSwap(from, target)
        }
        dragFrom = null
    }

    override fun onUp(x: Float, y: Float) {
        dragFrom = null
    }

    override fun onBack(): Boolean {
        if (state != Screen.MENU) {
            state = Screen.MENU
            return true
        }
        return false
    }

    private fun attemptSwap(a: Cell, b: Cell) {
        swapLegal = board.swapIsLegal(a, b)
        board.swap(a, b)
        pendingA = a
        pendingB = b
        selected = null
        hint = null
        idleTime = 0f
        setSwapOffsets(a, b)
        setPhase(Phase.SWAP, SWAP_TIME)
        game.sfx.play(Sfx.SWAP)
    }

    private fun setSwapOffsets(a: Cell, b: Cell) {
        val ra = layout.tileRect(a.row, a.col)
        val rb = layout.tileRect(b.row, b.col)
        offsets.clear()
        offsets[a] = (rb.left - ra.left) to (rb.top - ra.top)
        offsets[b] = (ra.left - rb.left) to (ra.top - rb.top)
    }

    // ------------------------------------------------------------------ phases
    private fun setPhase(next: Phase, length: Float) {
        phase = next
        phaseT = 0f
        phaseLen = max(0.0001f, length)
    }

    private fun advance(dt: Float) {
        if (phase == Phase.IDLE) {
            if (timeOver) {
                say("Last hurrah!")
                setPhase(Phase.FINALE, FINALE_STEP)
                return
            }
            idleTime += dt
            if (hint == null && idleTime > HINT_DELAY) hint = board.findHint()
            return
        }
        phaseT += dt
        if (phaseT < phaseLen) return
        val finished = phase
        phaseT = 0f
        when (finished) {
            Phase.SWAP -> afterSwap()
            Phase.REVERT -> {
                offsets.clear()
                pendingA = null
                pendingB = null
                setPhase(Phase.IDLE, 0f)
            }
            Phase.CLEAR -> {
                dying.clear()
                collapse()
            }
            Phase.FALL -> {
                offsets.clear()
                resolveOrSettle()
            }
            Phase.SHUFFLE -> {
                offsets.clear()
                setPhase(Phase.IDLE, 0f)
            }
            Phase.FINALE -> finaleStep()
            Phase.IDLE -> Unit
        }
    }

    private fun afterSwap() {
        offsets.clear()
        val a = pendingA ?: return
        val b = pendingB ?: return
        if (!swapLegal) {
            board.swap(a, b)
            setSwapOffsets(a, b)
            setPhase(Phase.REVERT, REVERT_TIME)
            game.sfx.play(Sfx.INVALID)
            return
        }
        movesMade++
        cascade = 0
        val ta = board.at(a)
        val tb = board.at(b)
        val result = when {
            ta != null && ta.power == Power.ROOSTER -> board.activateRooster(a, b)
            tb != null && tb.power == Power.ROOSTER -> board.activateRooster(b, a)
            else -> board.resolveMatches(prefer = b)
        }
        pendingA = null
        pendingB = null
        if (!result.isEmpty) beginClear(result) else setPhase(Phase.IDLE, 0f)
    }

    private fun resolveOrSettle() {
        val result = board.resolveMatches()
        if (!result.isEmpty) {
            beginClear(result)
            return
        }
        cascade = 0
        if (!board.hasMoves()) {
            board.shuffle()
            say("No moves - shuffling the barnyard!")
            game.sfx.play(Sfx.SHUFFLE)
            offsets.clear()
            board.allCells().forEach { offsets[it] = 0f to -layout.board.height() }
            setPhase(Phase.SHUFFLE, FALL_TIME * 1.6f)
            return
        }
        setPhase(Phase.IDLE, 0f)
        idleTime = 0f
    }

    private fun beginClear(result: MatchResult) {
        cascade++
        bestCascade = max(bestCascade, cascade)
        val multiplier = min(cascade, MAX_CASCADE)
        var gained = result.cleared.size * POINTS_PER_TILE * multiplier
        for (special in result.specials) gained += SPECIAL_BONUS[special.power.key] ?: 0

        for (effect in result.effects) {
            val rect = layout.tileRect(effect.cell.row, effect.cell.col)
            when (effect.name) {
                "egg" -> {
                    game.effects.ring(rect.centerX(), rect.centerY(), 0xFFFAD66E.toInt(), 26, 460f)
                    game.effects.kick(7f)
                }
                "hay" -> {
                    game.effects.ring(rect.centerX(), rect.centerY(), 0xFFF0E296.toInt(), 30, 560f)
                    game.effects.kick(9f)
                }
                else -> {
                    game.effects.feathers(rect.centerX(), rect.centerY(), 26)
                    game.effects.kick(12f)
                }
            }
            game.sfx.play(effect.name)
        }

        for ((cell, tile) in result.cleared) {
            val rect = layout.tileRect(cell.row, cell.col)
            game.effects.burst(rect.centerX(), rect.centerY(), Palette.ANIMALS[tile.kind].first, 7)
            dying.add(cell to tileKey(tile.kind, tile.power))
        }

        result.focus?.let { focus ->
            val rect = layout.tileRect(focus.row, focus.col)
            game.effects.popup(rect.centerX(), rect.centerY() - 6f, "+${formatCoins(gained)}", Palette.CREAM, layout.fs(26 + min(10, multiplier)))
            if (multiplier > 1) {
                game.effects.popup(rect.centerX(), rect.centerY() - layout.tile * 0.5f, "x$multiplier CHAIN", Palette.GOLD, layout.fs(22))
            }
        }
        for (special in result.specials) {
            say(
                when (special.power) {
                    Power.EGG -> "Golden Egg!"
                    Power.HAY -> "Hay Bale!"
                    else -> "Prize Rooster!"
                },
            )
        }

        score += gained
        game.sfx.play(Sfx.matchName(cascade - 1))
        game.effects.kick(2f + multiplier)
        setPhase(Phase.CLEAR, CLEAR_TIME)
    }

    private fun collapse() {
        val (moves, spawns) = board.collapse()
        offsets.clear()
        for (move in moves) {
            offsets[Cell(move.toRow, move.col)] = 0f to (move.fromRow - move.toRow) * layout.tile
        }
        for (spawn in spawns) {
            offsets[Cell(spawn.row, spawn.col)] = 0f to -spawn.height * layout.tile
        }
        setPhase(Phase.FALL, FALL_TIME)
    }

    private fun finaleStep() {
        val result = board.detonateAllSpecials()
        if (result.isEmpty) {
            endRound()
            return
        }
        val bonus = result.cleared.size * POINTS_PER_TILE * 3
        score += bonus
        for (effect in result.effects) {
            val rect = layout.tileRect(effect.cell.row, effect.cell.col)
            game.effects.ring(rect.centerX(), rect.centerY(), Palette.GOLD, 24, 480f)
            game.sfx.play(effect.name)
        }
        for ((cell, tile) in result.cleared) {
            val rect = layout.tileRect(cell.row, cell.col)
            game.effects.burst(rect.centerX(), rect.centerY(), Palette.ANIMALS[tile.kind].first, 6)
        }
        result.cleared.keys.firstOrNull()?.let {
            val rect = layout.tileRect(it.row, it.col)
            game.effects.popup(rect.centerX(), rect.centerY(), "+${formatCoins(bonus)}", Palette.GOLD, layout.fs(30))
        }
        game.effects.kick(10f)
        setPhase(Phase.FINALE, FINALE_STEP)
    }

    private fun endRound() {
        newRecord = score > best(mode)
        // Blitz buys energy with skill; Relaxed is just for fun.
        if (mode == "blitz") {
            val (energy, coins) = session.claimBlitz(mode, score)
            rewardEnergy = energy
            rewardCoins = coins
        } else {
            rewardEnergy = 0
            rewardCoins = 0
            if (newRecord) session.bestBlitz[mode] = score
        }
        game.save()
        state = Screen.GAME_OVER
        game.sfx.play(Sfx.OVER)
    }

    private fun say(text: String) {
        banner = text
        bannerTime = 1.6f
    }

    // ------------------------------------------------------------------ update
    override fun update(dt: Float) {
        elapsed += dt
        bannerTime = max(0f, bannerTime - dt)
        if (state != Screen.PLAYING || paused) return
        if (mode == "blitz" && !timeOver) {
            timeLeft = max(0f, timeLeft - dt)
            if (timeLeft <= 10f && timeLeft.toInt() < nextTick) {
                nextTick = timeLeft.toInt()
                game.sfx.play(Sfx.TICK, 0.6f)
            }
            if (timeLeft <= 0f) timeOver = true
        }
        advance(dt)
    }

    private fun tileOffset(cell: Cell): Pair<Float, Float> {
        val base = offsets[cell] ?: return 0f to 0f
        val remaining = 1f - easeOut(phaseT / phaseLen)
        return base.first * remaining to base.second * remaining
    }

    // -------------------------------------------------------------------- draw
    override fun draw(canvas: Canvas) {
        ui.scale = layout.scale
        if (state == Screen.MENU) {
            drawMenu(canvas)
            return
        }
        drawBoard(canvas)
        drawHud(canvas)
        game.effects.draw(canvas, ui)
        drawBanner(canvas)
        if (paused) drawOverlay(canvas, "Paused", "Tap pause again to play on")
        if (state == Screen.GAME_OVER) drawGameOver(canvas)
    }

    private fun drawMenu(canvas: Canvas) {
        val cx = layout.w / 2f
        val lines = listOf(
            "Line up three or more of the same critter.",
            "Match 4 for a Golden Egg, an L or T for a Hay Bale,",
            "5 in a row for a Prize Rooster that clears a species.",
            "A Blitz round pays out energy and coins for the farm.",
        )
        val gap = layout.fs(18)
        val headH = min(layout.h * 0.14f, layout.w * 0.26f)
        val barn = game.sprites.barn
        val barnH = (barn?.height ?: 0).toFloat()
        val rowH = layout.tile
        val btnH = max(40f, min(layout.h * 0.075f, layout.tile * 1.15f))
        val bestH = layout.fs(24)
        val helpH = lines.size * layout.fs(24)
        val total = headH + gap * 1.4f + barnH + gap + rowH + gap * 1.2f + btnH * 2 + bestH + gap * 2 + helpH
        var y = max(layout.margin, (layout.h - total) / 2f)

        val header = RectF(cx - min(layout.w - 2 * layout.margin, layout.w * 0.86f) / 2f, y, cx + min(layout.w - 2 * layout.margin, layout.w * 0.86f) / 2f, y + headH)
        ui.plank(canvas, header, Palette.BARN_RED)
        ui.text(canvas, "BARNYARD BLITZ", layout.fs(48), Palette.CREAM, cx, header.centerY() - header.height() * 0.14f, Ui.Align.CENTER, bold = true)
        ui.text(canvas, "the farm's energy minigame", layout.fs(19), Palette.GOLD, cx, header.centerY() + header.height() * 0.26f, Ui.Align.CENTER)
        y = header.bottom + gap * 1.4f

        barn?.let {
            canvas.drawBitmap(it, cx - it.width / 2f, y, null)
            y += barnH + gap
        }

        val step = layout.tile * 0.95f
        for (i in Palette.ANIMALS.indices) {
            val bitmap = game.sprites.tiles[tileKey(i, Power.NONE)] ?: continue
            val x = cx - Palette.ANIMALS.size * step / 2f + i * step
            val bob = sin(elapsed * 3f + i * 0.7f) * layout.tile * 0.08f
            canvas.drawBitmap(bitmap, null, RectF(x, y + bob, x + step * 0.95f, y + bob + step * 0.95f), null)
        }
        y += rowH + gap * 1.2f

        val bw = min(layout.w * 0.42f, layout.tile * 4.2f)
        ui.button(canvas, "start_blitz", RectF(cx - bw - layout.gap / 2f, y, cx - layout.gap / 2f, y + btnH), "Blitz  60s", Palette.BARN_RED, 24)
        ui.button(canvas, "start_relaxed", RectF(cx + layout.gap / 2f, y, cx + bw + layout.gap / 2f, y + btnH), "Relaxed", Palette.WOOD, 24)
        y += btnH * 1.1f
        ui.text(canvas, "best ${formatCoins(best("blitz"))}", layout.fs(18), Palette.INK, cx - (bw + layout.gap) / 2f, y + bestH / 2f, Ui.Align.CENTER)
        ui.text(canvas, "best ${formatCoins(best("relaxed"))}", layout.fs(18), Palette.INK, cx + (bw + layout.gap) / 2f, y + bestH / 2f, Ui.Align.CENTER)
        y += bestH + gap

        for (line in lines) {
            ui.text(canvas, line, layout.fs(17), Palette.INK, cx, y + layout.fs(12), Ui.Align.CENTER, shadow = false)
            y += layout.fs(24)
        }
        y += layout.fs(10)
        val backW = min(layout.w * 0.5f, layout.tile * 5f)
        val backY = min(y, layout.h - layout.margin - btnH)
        ui.button(canvas, "to_farm", RectF(cx - backW / 2f, backY, cx + backW / 2f, backY + btnH), "Back to the farm", Palette.WOOD_DARK, 22)
    }

    private fun drawBoard(canvas: Canvas) {
        ui.plank(canvas, layout.frame, Palette.WOOD, max(8f, layout.framePad * 2f))

        val bedRadius = layout.tile * 0.16f
        ui.fill.color = 0xFF8FB874.toInt()
        canvas.drawRoundRect(layout.board, bedRadius, bedRadius, ui.fill)
        val inset = layout.tile * 0.04f
        val padRadius = layout.tile * 0.18f
        for (r in 0 until layout.rows) {
            for (c in 0 until layout.cols) {
                val rect = layout.tileRect(r, c)
                rect.inset(inset, inset)
                ui.fill.color = if ((r + c) % 2 == 0) Palette.BLITZ_CELL_LIGHT else Palette.BLITZ_CELL_DARK
                canvas.drawRoundRect(rect, padRadius, padRadius, ui.fill)
            }
        }
        drawBoardShading(canvas, bedRadius)

        canvas.save()
        canvas.clipRect(layout.board)

        hint?.let { (a, b) ->
            if (phase == Phase.IDLE) {
                val pulse = (sin(elapsed * 7f) + 1f) / 2f
                for (cell in listOf(a, b)) {
                    ui.fill.color = withAlpha(0xFFFFF6BE.toInt(), (70f + 90f * pulse) / 255f)
                    canvas.drawRoundRect(layout.tileRect(cell.row, cell.col), layout.tile * 0.18f, layout.tile * 0.18f, ui.fill)
                }
            }
        }

        for (cell in board.allCells()) {
            val tile = board.at(cell) ?: continue
            val (ox, oy) = tileOffset(cell)
            val rect = layout.tileRect(cell.row, cell.col)
            var size = layout.tile - 6f
            if (tile.power == Power.ROOSTER) size *= 1f + 0.05f * sin(elapsed * 6f)
            val cxp = rect.centerX() + ox
            val cyp = rect.centerY() + oy
            game.sprites.tiles[tileKey(tile.kind, tile.power)]?.let {
                canvas.drawBitmap(it, null, RectF(cxp - size / 2f, cyp - size / 2f, cxp + size / 2f, cyp + size / 2f), null)
            }
        }

        selected?.let { cell ->
            val rect = layout.tileRect(cell.row, cell.col)
            rect.inset(2f, 2f)
            val pulse = (sin(elapsed * 9f) + 1f) / 2f
            ui.stroke.color = Palette.WHITE
            ui.stroke.strokeWidth = max(2f, layout.tile * 0.05f + pulse * 3f)
            canvas.drawRoundRect(rect, layout.tile * 0.2f, layout.tile * 0.2f, ui.stroke)
        }

        val progress = if (phase == Phase.CLEAR) (phaseT / phaseLen).coerceIn(0f, 1f) else 0f
        for ((cell, key) in dying) {
            val scale = max(0.05f, 1f - easeIn(progress))
            val rect = layout.tileRect(cell.row, cell.col)
            val size = (layout.tile - 6f) * scale
            game.sprites.tiles[key]?.let {
                ui.fill.alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(
                    it, null,
                    RectF(rect.centerX() - size / 2f, rect.centerY() - size / 2f, rect.centerX() + size / 2f, rect.centerY() + size / 2f),
                    ui.fill,
                )
                ui.fill.alpha = 255
            }
        }
        canvas.restore()
    }

    /** Matches the yard: a soft dip at the top and bottom of the bed. */
    private fun drawBoardShading(canvas: Canvas, radius: Float) {
        val depth = layout.tile * 0.5f
        canvas.save()
        val clip = Path()
        clip.addRoundRect(layout.board, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        ui.fill.shader = LinearGradient(
            0f, layout.board.top, 0f, layout.board.top + depth,
            withAlpha(0xFF3E5A2A.toInt(), 0.20f), withAlpha(0xFF3E5A2A.toInt(), 0f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(RectF(layout.board.left, layout.board.top, layout.board.right, layout.board.top + depth), ui.fill)
        ui.fill.shader = LinearGradient(
            0f, layout.board.bottom - depth, 0f, layout.board.bottom,
            withAlpha(0xFF3E5A2A.toInt(), 0f), withAlpha(0xFF3E5A2A.toInt(), 0.18f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(RectF(layout.board.left, layout.board.bottom - depth, layout.board.right, layout.board.bottom), ui.fill)
        ui.fill.shader = null
        canvas.restore()
    }

    private fun drawHud(canvas: Canvas) {
        ui.plank(canvas, layout.header, Palette.BARN_RED)
        val pad = layout.header.width() * 0.025f
        if (layout.portrait) {
            ui.text(canvas, "BARNYARD BLITZ", layout.fs(24), Palette.CREAM, layout.header.left + pad, layout.header.centerY(), bold = true)
            ui.text(canvas, formatCoins(score), layout.fs(30), Palette.GOLD, layout.header.right - pad * 1.6f, layout.header.centerY(), Ui.Align.RIGHT, bold = true)
        } else {
            ui.text(canvas, "BARNYARD BLITZ", layout.fs(34), Palette.CREAM, layout.header.left + pad, layout.header.centerY(), bold = true)
            val anchor = layout.header.right - layout.header.width() * 0.13f
            ui.text(canvas, formatCoins(score), layout.fs(40), Palette.GOLD, anchor, layout.header.centerY() - layout.fs(8), Ui.Align.CENTER, bold = true)
            ui.text(canvas, "SCORE", layout.fs(15), Palette.CREAM, anchor, layout.header.bottom - layout.fs(14), Ui.Align.CENTER)
        }

        layout.panel?.let { ui.plank(canvas, it, Palette.WOOD_LIGHT) }

        val timeCard = layout.cards[0]
        if (mode == "blitz") {
            val left = max(0f, timeLeft)
            val fraction = left / BLITZ_SECONDS
            val tone = when {
                fraction < 0.2f -> 0xFFC43E30.toInt()
                fraction < 0.5f -> 0xFFE8A834.toInt()
                else -> 0xFF6CB060.toInt()
            }
            ui.card(canvas, timeCard, "TIME", String.format("%.1f", left), if (left <= 10f) 0xFFC43E30.toInt() else Palette.INK, fraction, tone)
        } else {
            ui.card(canvas, timeCard, "TIME PLAYED", "${elapsed.toInt() / 60}:${(elapsed.toInt() % 60).toString().padStart(2, '0')}")
        }
        val chain = max(1, cascade)
        ui.card(canvas, layout.cards[1], "CHAIN", "x${min(chain, MAX_CASCADE)}", if (chain > 1) Palette.BARN_RED else Palette.INK_SOFT, min(1f, cascade.toFloat() / MAX_CASCADE))
        ui.card(canvas, layout.cards[2], "BEST", formatCoins(max(best(mode), score)))

        layout.info?.let { area ->
            if (area.height() > layout.fs(90)) drawInfo(canvas, area)
        }
        drawControls(canvas)
    }

    private fun drawInfo(canvas: Canvas, area: RectF) {
        val onWood = !layout.portrait
        if (onWood) {
            // Landscape draws this straight onto the wooden side panel.
            var y = area.top + layout.fs(10)
            ui.text(canvas, "${mode.uppercase()} MODE", layout.fs(18), Palette.CREAM, area.centerX(), y, Ui.Align.CENTER)
            y += layout.fs(28)
            for ((shape, name) in SPECIAL_ROWS) {
                if (y + layout.fs(20) > area.bottom) return
                ui.text(canvas, shape, layout.fs(16), Palette.CREAM, area.left, y, shadow = false)
                ui.text(canvas, name, layout.fs(16), Palette.GOLD, area.left + area.width() * 0.44f, y, shadow = false)
                y += layout.fs(22)
            }
            y += layout.fs(8)
            for (line in listOf("Moves  $movesMade", "Best chain  x$bestCascade")) {
                if (y + layout.fs(20) > area.bottom) return
                ui.text(canvas, line, layout.fs(16), Palette.CREAM, area.left, y, shadow = false)
                y += layout.fs(22)
            }
            return
        }

        // Portrait: a proper card, so the rules do not float loose on the sky.
        val height = min(area.height() - layout.gap, layout.fs(150))
        val card = RectF(area.left, area.centerY() - height / 2f, area.right, area.centerY() + height / 2f)
        ui.plank(canvas, card, Palette.CREAM)
        val pad = layout.fs(12)
        var y = card.top + pad + layout.fs(9)
        ui.text(canvas, "${mode.uppercase()} MODE", layout.fs(17), Palette.BARN_RED, card.left + pad, y, bold = true, shadow = false)
        ui.text(canvas, "moves $movesMade   best chain x$bestCascade", layout.fs(13), Palette.INK_SOFT, card.right - pad, y, Ui.Align.RIGHT, shadow = false)
        y += layout.fs(24)
        for ((shape, name) in SPECIAL_ROWS) {
            if (y + layout.fs(18) > card.bottom - pad) return
            ui.text(canvas, shape, layout.fs(15), Palette.INK, card.left + pad, y, shadow = false)
            ui.text(canvas, name, layout.fs(15), Palette.BARN_RED, card.left + card.width() * 0.46f, y, bold = true, shadow = false)
            y += layout.fs(21)
        }
    }

    private fun drawControls(canvas: Canvas) {
        val labels = mapOf(
            "pause" to if (paused) "Play" else "Pause",
            "restart" to "Restart",
            "sound" to if (game.sfx.muted) "Unmute" else "Mute",
            "back" to "Menu",
        )
        val colors = mapOf(
            "pause" to Palette.BARN_RED, "restart" to Palette.WOOD_DARK,
            "sound" to Palette.WOOD_DARK, "back" to Palette.WOOD_DARK,
        )
        for ((key, rect) in layout.buttons) {
            ui.button(canvas, key, rect, labels.getValue(key), colors.getValue(key), 18)
        }
    }

    private fun drawBanner(canvas: Canvas) {
        if (bannerTime <= 0f || banner.isEmpty()) return
        val alpha = min(1f, bannerTime / 0.4f)
        val size = layout.fs(28)
        val width = ui.textWidth(banner, size, true) + layout.fs(30)
        val height = ui.lineHeight(size, true) + layout.fs(16)
        val rect = RectF(
            layout.board.centerX() - width / 2f, layout.board.top + layout.tile * 0.45f - height / 2f,
            layout.board.centerX() + width / 2f, layout.board.top + layout.tile * 0.45f + height / 2f,
        )
        ui.fill.color = withAlpha(Palette.BARN_RED, 0.84f * alpha)
        canvas.drawRoundRect(rect, height * 0.3f, height * 0.3f, ui.fill)
        ui.text(canvas, banner, size, withAlpha(Palette.CREAM, alpha), rect.centerX(), rect.centerY(), Ui.Align.CENTER, bold = true)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        ui.veil(canvas, layout.w, layout.h, 0.67f)
        ui.text(canvas, title, layout.fs(50), Palette.CREAM, layout.w / 2f, layout.h / 2f - layout.fs(30), Ui.Align.CENTER, bold = true)
        ui.text(canvas, subtitle, layout.fs(21), Palette.GOLD, layout.w / 2f, layout.h / 2f + layout.fs(20), Ui.Align.CENTER)
        drawControls(canvas)
    }

    private fun drawGameOver(canvas: Canvas) {
        ui.veil(canvas, layout.w, layout.h, 0.73f)
        val card = layout.centreCard(if (layout.portrait) 0.86f else 0.58f, 0.58f)
        val inner = ui.panel(canvas, card)
        val cx = inner.centerX()

        ui.text(canvas, "That's all, folks!", layout.fs(34), Palette.BARN_RED, cx, inner.top + inner.height() * 0.10f, Ui.Align.CENTER, bold = true)
        ui.text(canvas, formatCoins(score), layout.fs(58), Palette.INK, cx, inner.top + inner.height() * 0.30f, Ui.Align.CENTER, bold = true)
        ui.text(canvas, "final score", layout.fs(17), Palette.INK_SOFT, cx, inner.top + inner.height() * 0.42f, Ui.Align.CENTER, shadow = false)
        if (newRecord) {
            val pulse = (sin(elapsed * 6f) + 1f) / 2f
            ui.text(canvas, "NEW BARN RECORD!", layout.fs(22), shade(Palette.GOLD, pulse * 0.3f), cx, inner.top + inner.height() * 0.53f, Ui.Align.CENTER, bold = true)
        } else {
            ui.text(canvas, "best  ${formatCoins(best(mode))}", layout.fs(19), Palette.INK_SOFT, cx, inner.top + inner.height() * 0.53f, Ui.Align.CENTER, shadow = false)
        }
        ui.text(canvas, "$movesMade moves    best chain x$bestCascade", layout.fs(17), Palette.INK_SOFT, cx, inner.top + inner.height() * 0.63f, Ui.Align.CENTER, shadow = false)
        if (rewardEnergy > 0 || rewardCoins > 0) {
            ui.text(canvas, "Earned  $rewardEnergy energy   ${formatCoins(rewardCoins)} coins", layout.fs(19), Palette.BARN_RED, cx, inner.top + inner.height() * 0.74f, Ui.Align.CENTER, bold = true)
        }

        val bw = inner.width() * 0.40f
        val bh = max(40f, inner.height() * 0.16f)
        val by = inner.bottom - bh
        ui.button(canvas, "again", RectF(cx - bw - layout.gap / 2f, by, cx - layout.gap / 2f, by + bh), "Play again", Palette.BARN_RED, 22)
        ui.button(canvas, "to_farm", RectF(cx + layout.gap / 2f, by, cx + bw + layout.gap / 2f, by + bh), "To the farm", Palette.WOOD_DARK, 22)
    }
}
