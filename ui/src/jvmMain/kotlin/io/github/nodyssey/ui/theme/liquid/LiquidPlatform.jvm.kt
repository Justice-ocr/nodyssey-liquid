package io.github.nodyssey.ui.theme.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberLiquidAudio(): LiquidAudio = SilentLiquidAudio

@Composable
internal actual fun rememberLiquidCaptureSupport(): () -> Boolean = remember { { true } }
