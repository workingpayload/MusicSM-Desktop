package com.example.musicsmd.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

/** Bottom playback bar — desktop counterpart of the mobile app's mini player. */
@Composable
fun PlayerBar(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 4.dp) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
                Controls(state = state, onTogglePlayPause = onTogglePlayPause, onNext = onNext, onPrevious = onPrevious)
            }
            Slider(
                value = state.positionMs.toFloat(),
                valueRange = 0f..(state.durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { onSeek(it.toLong()) },
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(formatMs(state.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RowScope.Controls(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") }
    IconButton(onClick = onTogglePlayPause) {
        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
    }
    IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
