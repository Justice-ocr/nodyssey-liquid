package io.github.nodyssey.ui.theme.liquid

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeToggleButtonTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `quick switching inverts the rendered theme both ways without discarding a draft`() {
        val committed = mutableListOf<Boolean>()
        compose.setContent {
            // Start dark even if the host OS is light: the button must invert the app's theme.
            var dark by remember { mutableStateOf(true) }
            LiquidThemeReveal(darkTheme = dark, reducedMotion = true) { renderedDark ->
                PlazaTheme(darkTheme = renderedDark) {
                    CompositionLocalProvider(
                        LocalQuickThemeChange provides { target ->
                            committed += target
                            dark = target
                        },
                    ) {
                        var draft by remember { mutableStateOf("initial draft") }
                        Column {
                            ThemeToggleButton()
                            TextField(value = draft, onValueChange = { draft = it })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("initial draft").performTextReplacement("unsent reply")
        compose.onNodeWithContentDescription("切换到浅色模式").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("unsent reply").assertTextEquals("unsent reply")
        compose.onNodeWithContentDescription("切换到深色模式").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("unsent reply").assertTextEquals("unsent reply")
        assertEquals(listOf(false, true), committed)
    }
}
