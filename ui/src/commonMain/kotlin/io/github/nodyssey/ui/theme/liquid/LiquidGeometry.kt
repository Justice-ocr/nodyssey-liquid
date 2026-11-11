package io.github.nodyssey.ui.theme.liquid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A clock shared by the reveal and an optional audio adapter; no delayed stage timers. */
data class LiquidFrame(val progress: Float, val impact: Boolean, val filling: Boolean, val finished: Boolean) {
    companion object {
        const val IMPACT = 0.22f
        const val FILL = 0.28f
        fun at(progress: Float): LiquidFrame {
            val p = progress.coerceIn(0f, 1f)
            return LiquidFrame(p, p >= IMPACT, p >= FILL && p < 1f, p >= 1f)
        }
    }
}

/** Reuses one non-zero-winding path. All sub-contours wind clockwise, so overlaps form a union. */
class LiquidGeometry {
    val path = Path()

    fun update(size: Size, source: Offset, progress: Float): Path {
        path.reset()
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return path
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) {
            path.addRect(Rect(0f, 0f, w, h), Path.Direction.Clockwise)
            return path
        }
        val x = source.x.coerceIn(0f, w)
        val y = source.y.coerceIn(0f, h)
        val scale = min(w, h) / 400f
        val fill = ((p - LiquidFrame.FILL) / (1f - LiquidFrame.FILL)).coerceIn(0f, 1f)
        val easedFill = fill * fill * (3f - 2f * fill)
        val surface = h + 20f * scale - (h + 55f * scale) * easedFill
        val amplitude = (5f + 10f * sin(fill * PI).toFloat()) * scale * min(1f, fill * 12f)
        // A spatially varying waterline: neither a rectangle nor a fading colour overlay.
        if (p >= LiquidFrame.IMPACT) {
            path.moveTo(0f, wave(0f, w, surface, amplitude, p))
            val segments = 64
            for (i in 1..segments) {
                val xx = w * i / segments
                path.lineTo(xx, wave(xx, w, surface, amplitude, p))
            }
            path.lineTo(w, h + 40f * scale)
            path.lineTo(0f, h + 40f * scale)
            path.close()
        }
        // Taper the stream as its source becomes submerged. A sampled centreline gives natural sway.
        val streamEnd = min(h, y + (h - y) * (p / LiquidFrame.IMPACT).coerceIn(0f, 1f))
        val streamBottom = min(streamEnd, max(y, surface + amplitude))
        val width = (5f + 2f * sin(p * 31f)) * scale * (1f - fill * 0.65f)
        if (surface > y && p > 0f) {
            val points = 36
            for (i in 0..points) {
                val t = i.toFloat() / points
                val yy = y + (streamBottom - y) * t
                val xx = x + sin(t * 9f - p * 26f) * 7f * scale * t
                val half = width * (0.65f + 0.3f * sin(t * 19f + p * 42f))
                if (i == 0) path.moveTo(xx + half, yy) else path.lineTo(xx + half, yy)
            }
            for (i in points downTo 0) {
                val t = i.toFloat() / points
                val yy = y + (streamBottom - y) * t
                val xx = x + sin(t * 9f - p * 26f) * 7f * scale * t
                val half = width * (0.65f + 0.3f * sin(t * 19f + p * 42f))
                path.lineTo(xx - half, yy)
            }
            path.close()
            for (i in 0..3) {
                val t = (p * 2.9f + i * 0.23f) % 1f
                val dy = y + (h - y) * t
                if (dy < surface - 9f * scale) {
                    ellipse(x + sin(p * 23f + i) * (14f + i * 5f) * scale, dy, 2.5f * scale, 4.5f * scale)
                }
            }
        }
        val splash = ((p - LiquidFrame.IMPACT) / 0.22f).coerceIn(0f, 1f)
        if (p >= LiquidFrame.IMPACT && splash < 1f) {
            for (i in 0..9) {
                val angle = PI * (0.1 + i * 0.8 / 9)
                val distance = (35f + (i % 3) * 18f) * scale * splash
                val dx = x + cos(angle).toFloat() * distance * 2.5f
                val dy = h - sin(angle).toFloat() * 190f * scale * splash + 170f * scale * splash * splash
                ellipse(dx, dy, (2f + i % 3) * scale * (1f - splash), (3f + i % 2) * scale * (1f - splash))
            }
            // Narrow displaced crests form the local impact ripple, also revealing real theme pixels.
            val ripple = 70f * scale * splash
            ellipse(x, min(h - 2f * scale, surface), ripple, 5f * scale * (1f - splash))
        }
        return path
    }

    private fun wave(x: Float, width: Float, base: Float, amplitude: Float, p: Float): Float =
        base + amplitude * (sin(x / width * 4f * PI.toFloat() + p * 24f) + 0.42f * sin(x / width * 9f * PI.toFloat() - p * 19f))

    private fun ellipse(x: Float, y: Float, rx: Float, ry: Float) {
        path.addOval(Rect(x - rx, y - ry, x + rx, y + ry), Path.Direction.Clockwise)
    }
}
