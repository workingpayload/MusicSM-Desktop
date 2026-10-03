package com.example.musicsmd.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.Lavender
import com.example.musicsmd.ui.theme.Teal
import java.util.concurrent.TimeUnit

/**
 * Apple Music-style scrubber, ported from the mobile app: a rounded capsule with no thumb that
 * thickens while dragging, filled with a coral/lavender/teal gradient that flows while playing.
 *
 * @param progress current fraction (0..1).
 * @param durationMs total duration for the time labels; pass 0 with [showLabels] = false for
 *   non-time bars such as volume.
 * @param onSeek called with the target fraction when the user taps or finishes dragging.
 */
@Composable
fun AppleSeekBar(
    progress: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    showLabels: Boolean = true,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    // The gesture handlers outlive recompositions; they must call the latest onSeek, whose target
    // depends on the current song's duration, not the one captured when they were set up.
    val currentOnSeek by rememberUpdatedState(onSeek)
    val shown = (if (dragging) dragFraction else progress).coerceIn(0f, 1f)

    val trackHeight by animateDpAsState(if (dragging) 9.dp else 5.dp, label = "seekHeight")
    val trackColor = Color.White.copy(alpha = if (dragging) 0.30f else 0.22f)

    val infinite = rememberInfiniteTransition(label = "seekHue")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "seekPhase",
    )
    val hueColors = listOf(Coral, Lavender, Teal, Lavender, Coral)
    val idleColors = listOf(Coral, Lavender)

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            // The whole 24 dp row takes clicks and drags, not just the thin capsule drawn in it.
            val input = Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    detectTapGestures { offset -> currentOnSeek((offset.x / widthPx).coerceIn(0f, 1f)) }
                }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragging = false
                            currentOnSeek(dragFraction)
                        },
                        onDragCancel = { dragging = false },
                    )
                }
            val fillBrush = if (playing) {
                val shift = phase * widthPx
                Brush.linearGradient(
                    colors = hueColors,
                    start = Offset(shift - widthPx, 0f),
                    end = Offset(shift, 0f),
                    tileMode = TileMode.Mirror,
                )
            } else {
                Brush.horizontalGradient(idleColors)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(trackColor),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shown)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(fillBrush),
                )
            }
            Box(input)
        }

        if (showLabels) {
            val elapsed = (shown * durationMs).toLong()
            val remaining = (durationMs - elapsed).coerceAtLeast(0L)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formatDuration(elapsed),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    text = "-" + formatDuration(remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
