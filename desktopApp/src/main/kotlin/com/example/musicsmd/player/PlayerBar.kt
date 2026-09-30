package com.example.musicsmd.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
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
import com.example.musicsmd.ui.components.ArtworkImage

/** Bottom playback bar — desktop counterpart of the mobile app's mini player. Click anywhere
 * (except the buttons) to expand into [NowPlayingScreen]. */
@Composable
fun PlayerBar(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onExpand: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 4.dp) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(url = song.artworkUrl, size = 48.dp)
                Column(modifier = Modifier.weight(1f).clickable(onClick = onExpand).padding(start = 12.dp)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onToggleLike) {
                    Icon(
                        if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (state.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Controls(state = state, onTogglePlayPause = onTogglePlayPause, onNext = onNext, onPrevious = onPrevious)
                IconButton(onClick = onToggleQueue) { Icon(Icons.Filled.QueueMusic, contentDescription = "Queue") }
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
