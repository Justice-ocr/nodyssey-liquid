package io.github.nodyssey.ui.theme.liquid

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp")
class LiquidThemeRevealTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `reduced motion commits once without recreating form state or starting flow`() {
        var commits = 0
        val audio = RecordingAudio()
        compose.setContent {
            var dark by remember { mutableStateOf(false) }
            LiquidThemeReveal(darkTheme = dark, reducedMotion = true, audio = audio, soundEnabled = true) { renderedDark ->
                PlazaTheme(darkTheme = renderedDark) {
                    var draft by remember { mutableStateOf("initial draft") }
                    val reveal = LocalLiquidReveal.current!!
                    Column {
                        TextField(value = draft, onValueChange = { draft = it })
                        Button(onClick = {
                            reveal.request(!dark, Offset(100f, 80f)) {
                                commits++
                                dark = !dark
                            }
                        }) { Text("toggle") }
                    }
                }
            }
        }
        compose.onNodeWithText("initial draft").performTextReplacement("unsent reply")
        compose.onNodeWithText("toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("unsent reply").assertTextEquals("unsent reply")
        assertEquals(1, commits)
        assertEquals(0, audio.starts)
        assertFalse(audio.completed)
    }

    @Test
    fun `an interrupted request commits at most once and repeated clicks do not enqueue`() {
        var commits = 0
        lateinit var reveal: LiquidRevealController
        compose.setContent {
            var dark by remember { mutableStateOf(false) }
            LiquidThemeReveal(darkTheme = dark, durationMillis = 8000) { renderedDark ->
                PlazaTheme(darkTheme = renderedDark) {
                    reveal = LocalLiquidReveal.current!!
                    Text("content")
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            reveal.request(true, Offset(80f, 100f)) { commits++ }
            reveal.request(true, Offset(80f, 100f)) { commits++ }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            reveal.interrupt?.invoke()
            reveal.interrupt?.invoke()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, commits)
        assertFalse(reveal.busy)
    }

    private class RecordingAudio : LiquidAudio {
        var starts = 0
        var completed = false
        override fun start(dark: Boolean, volume: Float) {
            starts++
        }
        override fun update(frame: LiquidFrame) = Unit
        override fun stop(completed: Boolean) {
            this.completed = completed
        }
        override fun feedback(dark: Boolean, volume: Float) = Unit
        override fun close() = Unit
    }
}
