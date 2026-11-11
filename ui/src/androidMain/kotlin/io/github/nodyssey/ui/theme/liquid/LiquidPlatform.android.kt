package io.github.nodyssey.ui.theme.liquid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun rememberLiquidAudio(): LiquidAudio = remember { AndroidLiquidAudio() }

@Composable
internal actual fun rememberLiquidCaptureSupport(): () -> Boolean {
    val view = LocalView.current
    return remember(view) { { !hasExternalSurface(view.rootView) } }
}

private fun hasExternalSurface(view: View): Boolean {
    if (!view.isShown) return false
    if (view is SurfaceView || view is TextureView || view is WebView) return true
    return view is ViewGroup && (0 until view.childCount).any { hasExternalSurface(view.getChildAt(it)) }
}

/** Static PCM tracks are small; marker callbacks release one-shots, not a per-frame audio worker. */
private class AndroidLiquidAudio : LiquidAudio {
    private val tracks = mutableListOf<AudioTrack>()
    private val handler = Handler(Looper.getMainLooper())
    private var flow: AudioTrack? = null
    private var dark = false
    private var volume = 0f
    private var active = false
    private var impacted = false

    override fun start(dark: Boolean, volume: Float) {
        stop(false)
        this.dark = dark
        this.volume = volume.coerceIn(0f, 1f)
        active = this.volume > 0f
        impacted = false
        if (active) safely { play(LiquidPcm.Cue.POUR) }
    }

    override fun update(frame: LiquidFrame) {
        if (!active) return
        safely {
            if (frame.impact && !impacted) {
                impacted = true
                play(LiquidPcm.Cue.SPLASH)
            }
            if (frame.filling && flow == null) flow = play(LiquidPcm.Cue.FLOW, loop = true)
            flow?.setVolume(volume * 0.65f * minOf(1f, (1f - frame.progress) * 9f))
        }
    }

    override fun stop(completed: Boolean) {
        val finish = completed && active
        active = false
        flow = null
        tracks.toList().forEach(::release)
        if (finish) safely { play(LiquidPcm.Cue.FINISH) }
    }

    override fun feedback(dark: Boolean, volume: Float) {
        stop(false)
        this.dark = dark
        this.volume = volume.coerceIn(0f, 1f)
        if (this.volume > 0f) safely { play(LiquidPcm.Cue.FINISH) }
    }

    override fun close() = stop(false)

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {
            // Audio device loss or an unavailable output cannot prevent theme persistence.
            stop(false)
        }
    }

    private fun play(cue: LiquidPcm.Cue, loop: Boolean = false): AudioTrack {
        val pcm = LiquidPcm.synthesize(cue, dark)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(LiquidPcm.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        tracks += track
        check(track.write(pcm, 0, pcm.size) == pcm.size)
        track.setVolume(volume)
        if (loop) {
            track.setLoopPoints(0, pcm.size, -1)
        } else {
            track.notificationMarkerPosition = pcm.size
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack) = release(track)
                    override fun onPeriodicNotification(track: AudioTrack) = Unit
                },
                handler,
            )
        }
        track.play()
        return track
    }

    private fun release(track: AudioTrack) {
        if (!tracks.remove(track)) return
        runCatching { track.setPlaybackPositionUpdateListener(null) }
        runCatching { track.pause() }
        runCatching { track.flush() }
        track.release()
    }
}
