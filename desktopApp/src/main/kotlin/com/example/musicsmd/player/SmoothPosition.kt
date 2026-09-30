package com.example.musicsmd.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import com.example.musicsmd.ui.components.AppleSeekBar
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

/**
 * Interpolates playback position between libVLC's coarse (~300ms) position ticks. Ported from the
 * mobile app.
 *
 * While playing, it advances once per frame using the frame clock, re-anchoring to the real
 * [positionMs] every time a new tick arrives (which corrects any drift and handles seeks). This
 * gives a smooth progress bar and frame-accurate lyric highlighting instead of visible steps.
 */
@Composable
fun rememberSmoothPosition(
    positionMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
    speed: Float = 1f,
): Long {
    var smooth by remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying, durationMs, speed) {
        smooth = positionMs
        if (isPlaying) {
            val anchorPos = positionMs
            val anchorFrame = withFrameMillis { it }
            while (isActive) {
                val now = withFrameMillis { it }
                val next = anchorPos + ((now - anchorFrame) * speed).toLong()
                smooth = if (durationMs > 0) next.coerceIn(0L, durationMs) else next.coerceAtLeast(0L)
            }
        }
    }
    return smooth
}

/**
 * The scrubber wired to the player's [positionFlow]. The position is collected AND the per-frame
 * interpolated value is read here, in this leaf composable — never in the caller's scope — so the
 * per-frame recomposition stays confined to the seek bar instead of re-running Now Playing.
 */
@Composable
fun SmoothSeekBar(
    positionFlow: StateFlow<Long>,
    isPlaying: Boolean,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
) {
    val position by positionFlow.collectAsState()
    val smooth = rememberSmoothPosition(position, isPlaying, durationMs, speed)
    val progress = if (durationMs > 0) (smooth.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    AppleSeekBar(
        progress = progress,
        durationMs = durationMs,
        onSeek = onSeek,
        modifier = modifier,
        playing = isPlaying,
    )
}
