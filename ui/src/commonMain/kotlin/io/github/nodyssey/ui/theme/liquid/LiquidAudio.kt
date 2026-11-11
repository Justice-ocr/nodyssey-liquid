package io.github.nodyssey.ui.theme.liquid

import androidx.compose.runtime.Composable
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/** Optional adapter. Calls originate only from a user request, never from a system theme change. */
interface LiquidAudio {
    fun start(dark: Boolean, volume: Float)
    fun update(frame: LiquidFrame)
    fun stop(completed: Boolean)
    fun feedback(dark: Boolean, volume: Float)
    fun close()
}

@Composable
internal expect fun rememberLiquidAudio(): LiquidAudio

/** Local, original PCM synthesis: no recordings, remote assets, or audio permissions. */
object LiquidPcm {
    const val SAMPLE_RATE = 24000

    enum class Cue { POUR, SPLASH, FLOW, FINISH }

    fun synthesize(cue: Cue, dark: Boolean): ShortArray {
        val duration = when (cue) {
            Cue.POUR -> 0.18
            Cue.SPLASH -> 0.26
            Cue.FLOW -> 1.0
            Cue.FINISH -> 0.16
        }
        val samples = ShortArray((duration * SAMPLE_RATE).toInt())
        val random = Random(1729 + cue.ordinal)
        val pitch = if (dark) 0.74 else 1.18
        var filtered = 0.0
        for (i in samples.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val noise = random.nextDouble(-1.0, 1.0)
            filtered += 0.085 * (noise - filtered)
            val edge = minOf(1.0, t / 0.012, (duration - t) / 0.035).coerceAtLeast(0.0)
            val value = when (cue) {
                Cue.POUR -> (sin(2 * PI * pitch * (520 * t - 750 * t * t)) * exp(-25 * t) * 0.22 + filtered * exp(-14 * t) * 0.4) * edge

                Cue.SPLASH -> (filtered * 0.9 + sin(2 * PI * 170 * pitch * t) * 0.07) * exp(-15 * t) * edge

                // The integer-period tonal components and quiet low-pass noise conceal the loop seam.
                Cue.FLOW -> filtered * 0.28 * (0.75 + 0.25 * sin(2 * PI * 3 * t)) * edge

                Cue.FINISH -> sin(2 * PI * (if (dark) 270 else 520) * t) * exp(-30 * t) * 0.13 * edge
            }
            samples[i] = (tanh(value) * 32767).toInt().toShort()
        }
        return samples
    }
}

internal class SafeLiquidAudio(private val delegate: LiquidAudio) : LiquidAudio {
    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // A custom adapter is optional; its failures must not cancel a visual transition.
            runCatching { delegate.stop(false) }
        }
    }
    override fun start(dark: Boolean, volume: Float) = guard { delegate.start(dark, volume) }
    override fun update(frame: LiquidFrame) = guard { delegate.update(frame) }
    override fun stop(completed: Boolean) = guard { delegate.stop(completed) }
    override fun feedback(dark: Boolean, volume: Float) = guard { delegate.feedback(dark, volume) }
    override fun close() = guard { delegate.close() }
}

internal object SilentLiquidAudio : LiquidAudio {
    override fun start(dark: Boolean, volume: Float) = Unit
    override fun update(frame: LiquidFrame) = Unit
    override fun stop(completed: Boolean) = Unit
    override fun feedback(dark: Boolean, volume: Float) = Unit
    override fun close() = Unit
}
