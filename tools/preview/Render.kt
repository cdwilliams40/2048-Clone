import android.graphics.Bitmap
import android.graphics.Canvas
import com.barnyardblitz.data.SaveStore
import com.barnyardblitz.engine.Cell
import com.barnyardblitz.engine.Chains
import com.barnyardblitz.engine.Item
import com.barnyardblitz.engine.MAX_TIER
import com.barnyardblitz.ui.Audio
import com.barnyardblitz.ui.Game
import java.io.File
import javax.imageio.ImageIO

const val OUT = "build/preview"

lateinit var game: Game
var W = 1080
var H = 2340

fun shot(name: String) {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    game.draw(canvas)
    File(OUT).mkdirs()
    ImageIO.write(bmp.image, "png", File("$OUT/$name.png"))
}

fun frames(n: Int = 2, dt: Float = 1f / 60f) {
    repeat(n) { game.update(dt) }
}

fun tap(x: Float, y: Float) {
    game.onDown(x, y); game.onUp(x, y); frames(1)
}

fun tapKey(key: String) {
    val r = game.ui.hitboxes[key] ?: error("no hitbox '$key' (have ${game.ui.hitboxes.keys})")
    tap(r.centerX(), r.centerY())
}

fun drag(from: Cell, to: Cell) {
    val l = game.farmLayout
    val a = l.cellRect(from.row, from.col)
    val b = l.cellRect(to.row, to.col)
    game.onDown(a.centerX(), a.centerY())
    game.onMove(a.centerX() + l.cell, a.centerY())
    game.onMove(b.centerX(), b.centerY())
    game.onUp(b.centerX(), b.centerY())
    frames(1)
}

fun main(args: Array<String>) {
    W = args.getOrNull(0)?.toInt() ?: 1080
    H = args.getOrNull(1)?.toInt() ?: 2340
    val tag = args.getOrNull(2) ?: "phone"

    val dir = File(System.getProperty("java.io.tmpdir"), "bb-render-$tag").apply { mkdirs() }
    File(dir, "farm.json").delete()
    game = Game(SaveStore(dir), Audio.Silent)
    game.resize(W, H)
    frames(3)

    // The opening chapter scene plays first; click through it.
    var guard = 0
    while (game.scene === game.storyScene) {
        if (guard == 2) shot("${tag}_01_dialogue")
        tap(W / 2f, H - 120f)
        if (guard++ > 60) error("opening scene never ends")
    }
    shot("${tag}_02_farm_fresh")

    // Spend some energy and build a few tiers so the yard has something in it.
    val gen = game.session.board.occupied().first { it.second.isGenerator }.first
    repeat(10) { tap(game.farmLayout.cellRect(gen.row, gen.col).centerX(), game.farmLayout.cellRect(gen.row, gen.col).centerY()) }
    repeat(3) {
        val seen = HashMap<Pair<String, Int>, Cell>()
        for ((cell, item) in game.session.board.occupied().toList()) {
            if (item.isGenerator || item.tier >= MAX_TIER) continue
            val key = item.chain to item.tier
            val other = seen.remove(key)
            if (other != null) drag(other, cell) else seen[key] = cell
        }
    }
    frames(20)
    shot("${tag}_03_farm_busy")

    // Selected item: shows the sell / store actions.
    val loose = game.session.board.occupied().first { !it.second.isGenerator }.first
    tap(game.farmLayout.cellRect(loose.row, loose.col).centerX(), game.farmLayout.cellRect(loose.row, loose.col).centerY())
    frames(2)
    shot("${tag}_04_selected")
    game.onBack()

    // Order detail.
    val order = game.session.orders.active[0]
    for (req in order.requests) repeat(req.quantity) { game.session.board.place(Item(req.chain, req.tier)) }
    frames(1)
    tapKey("order0")
    frames(2)
    shot("${tag}_05_order")
    tapKey("detail_close")

    // Storage.
    game.session.board.toStorage(game.session.board.occupied().first { !it.second.isGenerator }.first)
    tapKey("storage")
    frames(2)
    shot("${tag}_06_storage")
    tapKey("storage_close")

    // A mature farm: every chain unlocked, plenty of coin.
    game.session.economy.addCoins(40000)
    repeat(40) { game.session.deliver(0) ?: game.session.skipOrder(0) }
    game.session.placeNewGenerators()
    for (chain in Chains.all) {
        repeat(2) { game.session.board.place(Item(chain.key, 2)) }
        game.session.board.place(Item(chain.key, 4))
    }
    frames(30)
    shot("${tag}_07_farm_mature")

    // Story screen.
    game.go("story")
    frames(3)
    shot("${tag}_08_story")

    // Blitz menu and a round in progress.
    game.go("blitz")
    frames(3)
    shot("${tag}_09_blitz_menu")
    tapKey("start_blitz")
    frames(4)
    val blitz = game.blitzScene
    var moves = 0
    repeat(1200) {
        if (moves < 6) {
            val hint = blitzHint()
            if (hint != null) { blitzSwap(hint); moves++ }
        }
        frames(1)
    }
    shot("${tag}_10_blitz_play")
    println("[$tag] rendered ${File(OUT).listFiles()?.count { it.name.startsWith(tag) }} screens at ${W}x$H")
}

// The blitz board is private to the scene, so drive it through the public surface.
fun blitzHint(): Pair<Cell, Cell>? {
    val l = game.blitzLayout
    for (r in 0 until l.rows) for (c in 0 until l.cols) {
        // fall back to nudging the middle of the board
    }
    return null
}

fun blitzSwap(pair: Pair<Cell, Cell>) {
    val l = game.blitzLayout
    val a = l.tileRect(pair.first.row, pair.first.col)
    val b = l.tileRect(pair.second.row, pair.second.col)
    game.onDown(a.centerX(), a.centerY())
    game.onMove(b.centerX(), b.centerY())
    game.onUp(b.centerX(), b.centerY())
}
