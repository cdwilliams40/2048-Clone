package com.barnyardblitz

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.barnyardblitz.engine.Chains
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one check that needs a real device: that the app launches, keeps drawing,
 * takes a touch and writes its save. Everything else about the game is covered
 * by the JVM unit tests, which do not need an emulator.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    private fun waitForFrames(scenario: ActivityScenario<MainActivity>, count: Long) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            var drawn = 0L
            scenario.onActivity { drawn = it.gameView.game.framesDrawn }
            if (drawn >= count) return
            Thread.sleep(50)
        }
        throw AssertionError("the view never drew $count frames")
    }

    @Test
    fun launchesAndKeepsDrawing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForFrames(scenario, 30)
            scenario.onActivity {
                val game = it.gameView.game
                assertTrue("the surface should have a size", game.ready)
                assertTrue(game.width > 0 && game.height > 0)
            }
        }
    }

    @Test
    fun tappingTheGeneratorSpendsEnergyAndSaves() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "farm.json").delete()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForFrames(scenario, 30)

            // A fresh save opens on the chapter-one scene; click through it.
            for (attempt in 0 until 40) {
                var onFarm = false
                scenario.onActivity { activity ->
                    val game = activity.gameView.game
                    onFarm = game.scene === game.farmScene
                    if (!onFarm) game.onDown(game.width / 2f, game.height - 120f)
                    if (!onFarm) game.onUp(game.width / 2f, game.height - 120f)
                }
                if (onFarm) break
                Thread.sleep(30)
            }

            var energyBefore = 0
            var energyAfter = 0
            scenario.onActivity { activity ->
                val game = activity.gameView.game
                assertEquals("should be on the farm", game.farmScene, game.scene)
                val generator = game.session.board.occupied().first { it.second.isGenerator }
                energyBefore = game.session.economy.energy
                val rect = game.farmLayout.cellRect(generator.first.row, generator.first.col)
                game.onDown(rect.centerX(), rect.centerY())
                game.onUp(rect.centerX(), rect.centerY())
                energyAfter = game.session.economy.energy
            }
            assertEquals(
                "a generator tap costs its energy",
                energyBefore - Chains["eggs"].generator.energy, energyAfter,
            )

            scenario.onActivity { it.gameView.game.save() }
            val save = File(context.filesDir, "farm.json")
            assertTrue("the farm should have been written", save.exists())
            assertTrue(save.length() > 0)
            assertNotNull(save.readText())
        }
    }

    @Test
    fun survivesARotationAndAPauseResume() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForFrames(scenario, 20)
            scenario.recreate()
            waitForFrames(scenario, 20)
            scenario.onActivity { assertTrue(it.gameView.game.ready) }
        }
    }
}
