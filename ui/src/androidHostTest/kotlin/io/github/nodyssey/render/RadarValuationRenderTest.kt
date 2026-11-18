package io.github.nodyssey.render

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.TitleAlertRule
import io.github.nodyssey.data.valuation.RenewalCycle
import io.github.nodyssey.data.valuation.ValuationCurrency
import io.github.nodyssey.data.valuation.VpsValuationEngine
import io.github.nodyssey.data.valuation.VpsValuationInput
import io.github.nodyssey.data.valuation.VpsValuationOutcome
import io.github.nodyssey.model.TitleKeywordKind
import io.github.nodyssey.ui.settings.TitleKeywordsScreen
import io.github.nodyssey.ui.settings.TitleKeywordsUiState
import io.github.nodyssey.ui.tools.VpsValuationScreen
import io.github.nodyssey.ui.tools.VpsValuationUiState
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class RadarValuationRenderTest {
    @get:Rule val compose = createComposeRule()

    @Before fun onlyWhenRendering() = assumeRendering()

    @Test fun radarLight() = radar(false)

    @Test fun radarDark() = radar(true)

    @Test fun valuationLight() = valuation(false)

    @Test fun valuationDark() = valuation(true)

    private fun radar(dark: Boolean) {
        compose.setContent {
            PlazaTheme(darkTheme = dark) {
                TitleKeywordsScreen(
                    state = TitleKeywordsUiState(
                        kind = TitleKeywordKind.ALERT,
                        alertRules = listOf(TitleAlertRule("VPS", false), TitleAlertRule("KVM|LXC", true)),
                        boards = listOf(
                            Board("daily", "日常", null),
                            Board("tech", "技术", null),
                            Board("info", "情报", null),
                            Board("review", "测评", null),
                            Board("trade", "交易", null),
                            Board("carpool", "拼车", null),
                            Board("promotion", "推广", null),
                        ),
                        categorySlugs = listOf("tech", "trade"),
                    ),
                    onBack = {}, onInputChange = {}, onAdd = {}, onRemove = {}, onRemoveAlertRule = {},
                    onRegexChange = {}, onCategorySlugsChange = {}, onExcludedWordsChange = {},
                )
            }
        }
        val palette = if (dark) "dark" else "light"
        compose.onRoot().captureRender("radar-$palette")
        compose.onNodeWithText("监控版块").performScrollTo().performClick()
        compose.onNode(isDialog()).captureRender("radar-boards-$palette")
    }

    private fun valuation(dark: Boolean) {
        val input = VpsValuationInput(ValuationCurrency.CNY, "120", LocalDate(2026, 12, 1), RenewalCycle.MONTH)
        val result = (VpsValuationEngine.calculate(input, LocalDate(2026, 11, 16)) as VpsValuationOutcome.Success).result
        compose.setContent {
            PlazaTheme(darkTheme = dark) {
                VpsValuationScreen(
                    state = VpsValuationUiState(renewalPrice = input.renewalPrice, expiresOn = input.expiresOn.toString(), result = result),
                    onBack = {}, onCurrency = {}, onCycle = {}, onRenewalPrice = {}, onExpiry = {}, onAskingPrice = {}, onTransferFee = {},
                    onBrokerPercent = {}, onFeePayer = {}, onManualRate = {}, onApplyManualRate = {}, onClearManualRate = {}, onRefreshRate = {}, onInsertMarkdown = null,
                )
            }
        }
        compose.onRoot().captureRender(if (dark) "valuation-dark" else "valuation-light")
    }
}
