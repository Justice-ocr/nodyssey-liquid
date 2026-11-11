package io.github.nodyssey.ui.theme.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Controlled theme requests. The callback is delivered once, on completion or interruption. */
class LiquidRevealController internal constructor() {
    var busy by mutableStateOf(false)
        internal set
    internal var pendingDark by mutableStateOf<Boolean?>(null)
    internal var run by mutableStateOf<LiquidRun?>(null)
    internal var handler: ((Boolean, Offset, () -> Unit) -> Unit)? = null
    internal var interrupt: (() -> Unit)? = null
    internal var audition: (() -> Unit)? = null

    fun request(dark: Boolean, sourceInRoot: Offset, onThemeChange: () -> Unit) {
        if (busy) return
        handler?.invoke(dark, sourceInRoot, onThemeChange) ?: onThemeChange()
    }

    fun previewSound() {
        if (!busy) audition?.invoke()
    }
}

internal class LiquidRun(val before: ImageBitmap, val origin: Offset) {
    var progress by mutableFloatStateOf(0f)
}

val LocalLiquidReveal = staticCompositionLocalOf<LiquidRevealController?> { null }

@Composable
internal expect fun rememberLiquidCaptureSupport(): () -> Boolean

/**
 * Single composition, two visual layers. Capture BEFORE retheming, then reveal the real rethemed
 * content through a liquid path. Material's transitions cannot express a spatial liquid mask, hence
 * this draw-phase implementation. Remove it if Compose gains an equivalent themed-content reveal.
 *
 * The immutable bitmap has no semantics, focus or events. The live content never leaves composition.
 * Native windows and external surfaces are excluded by the platform capability check.
 */
@Composable
fun LiquidThemeReveal(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = false,
    durationMillis: Int = 2600,
    soundEnabled: Boolean = false,
    volume: Float = 0.25f,
    audio: LiquidAudio? = null,
    content: @Composable (darkTheme: Boolean) -> Unit,
) {
    val controller = remember { LiquidRevealController() }
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val geometry = remember { LiquidGeometry() }
    val platformAudio = rememberLiquidAudio()
    val sound = remember(audio, platformAudio) { SafeLiquidAudio(audio ?: platformAudio) }
    val canCapture = rememberLiquidCaptureSupport()
    val latestDark by rememberUpdatedState(darkTheme)
    val latestEnabled by rememberUpdatedState(enabled && !reducedMotion)
    val latestSound by rememberUpdatedState(soundEnabled && volume > 0f)
    val latestVolume by rememberUpdatedState(volume.coerceIn(0f, 1f))
    val latestDuration by rememberUpdatedState(durationMillis.coerceIn(800, 8000))
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var job by remember { mutableStateOf<Job?>(null) }
    // The commit holder must survive recomposition, including the one caused by pendingDark.
    val commitHolder = remember { arrayOfNulls<() -> Unit>(1) }
    val generation = remember { longArrayOf(0L) }
    fun commitOnce() {
        val callback = commitHolder[0]
        commitHolder[0] = null
        callback?.invoke()
    }
    fun interrupt() {
        generation[0]++
        job?.cancel()
        sound.stop(false)
        controller.run = null
        controller.busy = false
        commitOnce()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    SideEffect {
        controller.interrupt = ::interrupt
        controller.handler = { target, source, callback ->
            if (target == (controller.pendingDark ?: latestDark)) {
                callback()
            } else {
                controller.busy = true
                val requestGeneration = ++generation[0]
                commitHolder[0] = callback
                job = scope.launch {
                    var completed = false
                    try {
                        val snapshot = if (latestEnabled && foreground && viewport != IntSize.Zero && canCapture()) {
                            try {
                                withTimeoutOrNull(700) { layer.toImageBitmap() }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                        controller.pendingDark = target
                        if (snapshot == null) {
                            if (latestSound && foreground) sound.feedback(target, latestVolume)
                        } else {
                            val run = LiquidRun(snapshot, source - rootOrigin)
                            controller.run = run
                            if (latestSound) sound.start(target, latestVolume)
                            val start = withFrameNanos { it }
                            while (true) {
                                val elapsed = withFrameNanos { it } - start
                                val frame = LiquidFrame.at(elapsed / (latestDuration * 1_000_000f))
                                run.progress = frame.progress // Read only in drawWithContent, not in composition.
                                sound.update(frame)
                                if (frame.finished) break
                            }
                            sound.stop(true)
                        }
                        completed = true
                    } finally {
                        if (generation[0] == requestGeneration) {
                            if (!completed) sound.stop(false)
                            controller.run = null
                            controller.busy = false
                            commitOnce()
                        }
                    }
                }
            }
        }
        controller.audition = {
            if (latestSound && foreground) {
                sound.feedback(!(controller.pendingDark ?: latestDark), latestVolume)
            }
        }
    }
    DisposableEffect(lifecycle, sound) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> foreground = true

                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    interrupt()
                }

                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            interrupt()
            sound.close()
            controller.handler = null
            controller.interrupt = null
            controller.audition = null
        }
    }
    LaunchedEffect(soundEnabled, volume) {
        // Muting also stops a short completion cue immediately. Never restart a consumed stage.
        sound.stop(false)
    }
    LaunchedEffect(enabled, reducedMotion) {
        if (!enabled || reducedMotion) controller.interrupt?.invoke()
    }
    LaunchedEffect(darkTheme, controller.pendingDark, controller.busy) {
        if (!controller.busy && controller.pendingDark != null) {
            if (darkTheme != controller.pendingDark) delay(2000)
            // A controlled caller that rejects a change must not leave a permanent local override.
            controller.pendingDark = null
        }
    }
    Box(
        modifier = modifier.fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .onSizeChanged {
                if (viewport != IntSize.Zero && viewport != it) controller.interrupt?.invoke()
                viewport = it
            }
            .onPreviewKeyEvent { controller.busy }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                val run = controller.run
                if (run == null) {
                    drawLayer(layer)
                } else {
                    drawImage(run.before)
                    clipPath(geometry.update(size, run.origin, run.progress)) { drawLayer(layer) }
                }
            },
    ) {
        CompositionLocalProvider(LocalLiquidReveal provides controller) {
            content(controller.pendingDark ?: darkTheme)
        }
        if (controller.busy) {
            // Freeze gestures for the short snapshot interval; scroll, IME and fixed elements cannot
            // drift relative to the captured pixels. There is still only one focus/semantics tree.
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                },
            )
        }
    }
}
