import com.barnyardblitz.ui.BlitzLayout
import com.barnyardblitz.ui.FarmLayout

private var failures = 0

fun check(condition: Boolean, message: String) {
    if (!condition) { println("FAIL $message"); failures++ }
}

fun main() {
    val sizes = listOf(
        1080 to 2340, 1080 to 1920, 720 to 1280, 1440 to 3120,
        2340 to 1080, 1280 to 720, 800 to 1280, 2560 to 1600,
        480 to 800, 1600 to 2560,
    )
    for ((w, h) in sizes) {
        val f = FarmLayout(w.toFloat(), h.toFloat(), 8, 7)
        check(f.cell > 8f, "farm ${w}x$h cell too small: ${f.cell}")
        check(f.board.right <= w + 1f && f.board.bottom <= h + 1f, "farm ${w}x$h board overflows")
        check(f.board.left >= -1f && f.board.top >= -1f, "farm ${w}x$h board off-screen")
        check(f.topbar.bottom < f.board.top, "farm ${w}x$h topbar overlaps board")
        check(f.buttons.size == 4, "farm ${w}x$h missing buttons")
        for ((key, r) in f.buttons) {
            check(r.width() > 8f && r.height() > 8f, "farm ${w}x$h button $key degenerate")
            check(r.right <= w + 1f && r.bottom <= h + 1f, "farm ${w}x$h button $key overflows")
        }
        check(f.orderCards.size == 3, "farm ${w}x$h order cards")
        f.orderCards.forEach { check(it.width() > 20f && it.height() > 20f, "farm ${w}x$h order card degenerate") }
        // every cell must round-trip through the hit test
        for (r in 0 until 8) for (c in 0 until 7) {
            val rect = f.cellRect(r, c)
            val index = f.cellAt(rect.centerX(), rect.centerY())
            check(index == r * 7 + c, "farm ${w}x$h cell($r,$c) hit test -> $index")
        }
        check(f.cellAt(-5f, -5f) == -1, "farm ${w}x$h off-board hit test")
        check(f.cellAt(w + 5f, h + 5f) == -1, "farm ${w}x$h off-board hit test 2")
        f.info?.let { check(it.height() > 0f && it.bottom <= f.board.top + 1f, "farm ${w}x$h info overlaps board") }

        val b = BlitzLayout(w.toFloat(), h.toFloat(), 8, 8)
        check(b.tile > 8f, "blitz ${w}x$h tile too small: ${b.tile}")
        check(b.board.right <= w + 1f && b.board.bottom <= h + 1f, "blitz ${w}x$h board overflows")
        check(b.header.bottom < b.frame.top, "blitz ${w}x$h header overlaps frame")
        check(b.cards.size == 3, "blitz ${w}x$h cards")
        b.cards.forEach { check(it.width() > 20f && it.height() > 10f, "blitz ${w}x$h card degenerate") }
        check(b.buttons.size == 4, "blitz ${w}x$h buttons")
        for ((key, r) in b.buttons) {
            check(r.width() > 8f && r.height() > 8f, "blitz ${w}x$h button $key degenerate")
            check(r.right <= w + 1f && r.bottom <= h + 1f, "blitz ${w}x$h button $key overflows")
        }
        for (r in 0 until 8) for (c in 0 until 8) {
            val rect = b.tileRect(r, c)
            check(b.cellAt(rect.centerX(), rect.centerY()) == r * 8 + c, "blitz ${w}x$h tile($r,$c) hit test")
        }
        val card = f.centreCard(0.86f, 0.5f)
        check(card.left >= 0f && card.right <= w.toFloat() + 1f, "farm ${w}x$h centre card overflows")
    }
    println(if (failures == 0) "layout geometry OK across ${sizes.size} screen sizes" else "$failures failures")
    if (failures > 0) System.exit(1)
}
