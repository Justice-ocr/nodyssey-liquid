package io.github.nodyssey.ui.theme.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// This Android feature remains source-compatible with the existing iOS shell. A native audio
// adapter can be supplied to LiquidThemeReveal; the default is deliberately silent on iOS.
@Composable
internal actual fun rememberLiquidAudio(): LiquidAudio = SilentLiquidAudio

@Composable
internal actual fun rememberLiquidCaptureSupport(): () -> Boolean = remember { { false } }
