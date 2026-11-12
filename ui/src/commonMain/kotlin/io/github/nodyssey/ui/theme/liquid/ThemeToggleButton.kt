package io.github.nodyssey.ui.theme.liquid

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.liquid_switch_dark
import io.github.nodyssey.ui.resources.liquid_switch_light
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import org.jetbrains.compose.resources.stringResource

/** Null disables quick switching, e.g. while e-ink mode forces a light theme. */
val LocalQuickThemeChange = staticCompositionLocalOf<((Boolean) -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeToggleButton() {
    val dark = LocalPlazaDarkTheme.current
    val change = LocalQuickThemeChange.current
    val reveal = LocalLiquidReveal.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val label = stringResource(if (dark) Res.string.liquid_switch_light else Res.string.liquid_switch_dark)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            enabled = change != null && reveal?.busy != true,
            modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
            onClick = {
                if (change != null) {
                    val target = !dark
                    val source = Offset(bounds.center.x, bounds.bottom)
                    if (reveal == null) change(target) else reveal.request(target, source) { change(target) }
                }
            },
        ) {
            Icon(
                imageVector = if (dark) PlazaIcons.LightMode else PlazaIcons.DarkMode,
                contentDescription = label,
            )
        }
    }
}
