package io.github.nodyssey.ui.theme.liquid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.liquid_animation
import io.github.nodyssey.ui.resources.liquid_animation_hint
import io.github.nodyssey.ui.resources.liquid_listen
import io.github.nodyssey.ui.resources.liquid_sound
import io.github.nodyssey.ui.resources.liquid_sound_hint
import io.github.nodyssey.ui.resources.liquid_volume
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.GroupedRow
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun LiquidThemeOptions(
    enabled: Boolean,
    soundEnabled: Boolean,
    volume: Float,
    onEnabledChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    val controller = LocalLiquidReveal.current
    var slider by remember(volume) { mutableFloatStateOf(volume) }
    GroupedRow(
        title = stringResource(Res.string.liquid_animation),
        subtitle = stringResource(Res.string.liquid_animation_hint),
        checked = enabled,
        onCheckedChange = onEnabledChange,
        trailing = { GroupedListItemSwitch(checked = enabled) },
    )
    GroupedRow(
        title = stringResource(Res.string.liquid_sound),
        subtitle = stringResource(Res.string.liquid_sound_hint),
        checked = soundEnabled,
        onCheckedChange = onSoundChange,
        trailing = { GroupedListItemSwitch(checked = soundEnabled) },
    )
    if (soundEnabled) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row {
                Text(
                    stringResource(Res.string.liquid_volume, (slider * 100).roundToInt()),
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { controller?.previewSound() },
                    enabled = slider > 0f && controller?.busy == false,
                ) { Text(stringResource(Res.string.liquid_listen)) }
            }
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { onVolumeChange(slider) },
            )
        }
    }
}
