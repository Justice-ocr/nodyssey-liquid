package io.github.nodyssey.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.PreferenceStoreScope
import io.github.nodyssey.data.TitleKeywordStore
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.model.TitleKeywordKind
import io.github.plaza.core.AppClock
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class TitleKeywordsScreenTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val preferences = PreferenceStoreScope("radar-boards")

    private val settings by lazy { SettingsRepository(preferences.dataStore) }
    private lateinit var database: NodeSeekDatabase

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `confirming multiple boards preserves a retired selection until explicitly cleared`() {
        showRadar(listOf("tech", "retired"))
        openPicker()
        option("技术").assertIsOn()
        option("retired（不在当前版块列表）").assertIsOn()
        option("交易").assertIsOff().performClick()
        compose.onNodeWithText("确定").performClick()

        compose.waitUntilAtLeastOneExists(hasText("技术, retired, 交易"), timeoutMillis = 5_000)
        assertEquals(listOf("tech", "retired", "trade"), savedSlugs())

        openPicker()
        option("全部版块").performClick()
        compose.onNodeWithText("确定").performClick()
        compose.waitUntilAtLeastOneExists(hasText("全部版块") and hasClickAction(), timeoutMillis = 5_000)
        assertEquals(emptyList<String>(), savedSlugs())
    }

    @Test
    fun `cancelling discards the draft instead of changing what the radar monitors`() {
        showRadar(listOf("tech"))
        openPicker()
        option("技术").performClick()
        option("交易").performClick()
        compose.onNodeWithText("取消").performClick()

        openPicker()
        option("技术").assertIsOn()
        option("交易").assertIsOff()
        assertEquals(listOf("tech"), savedSlugs())
    }

    private fun showRadar(savedSlugs: List<String>) {
        runBlocking { settings.setAlertCategorySlugs(savedSlugs) }
        val clock = AppClock.System
        val boards = CategoryRepository(
            client = object : JsonSource {
                override suspend fun getJson(path: String, referer: String): String =
                    """{"success":true,"data":[{"key":"tech","cn_text":"技术"},{"key":"trade","cn_text":"交易"}]}"""
            },
            boardDao = database.boardDao(),
            clock = clock,
        )
        val factory = viewModelFactory {
            initializer {
                TitleKeywordsViewModel(TitleKeywordKind.ALERT, TitleKeywordStore(database.titleKeywordDao(), clock), settings, boards)
            }
        }
        compose.setContent { PlazaTheme { TitleKeywordsRoute(viewModel(factory = factory), onBack = {}) } }
        compose.waitUntilAtLeastOneExists(hasText("技术", substring = true), timeoutMillis = 5_000)
    }

    private fun openPicker() = compose.onNode(hasText("监控版块") and hasClickAction()).performScrollTo().performClick()

    private fun option(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()) and hasClickAction())

    private fun savedSlugs() = runBlocking { settings.settings.first().alertCategorySlugs }
}
