package com.example.musicsmd.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsmd.audio.OutputPickerPopup
import com.example.musicsmd.settings.RepeatMode
import com.example.musicsmd.ui.components.AppleSeekBar
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFillStrong

/** Floating frosted mini-player — desktop counterpart of the mobile app's [MiniPlayer]/glass bar.
 * Click anywhere (except the buttons) to expand into [NowPlayingScreen]. */
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
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onSelectAudioOutput: (String) -> Unit,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return
    var showOutputPicker by remember { mutableStateOf(false) }
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tint = GlassFillStrong,
        liquid = true,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(url = song.artworkUrl, size = 48.dp, shape = RoundedCornerShape(12.dp))
                Column(modifier = Modifier.weight(1f).clickable(onClick = onExpand).padding(start = 12.dp)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggleLike) {
                    Icon(
                        if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (state.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onRefreshAudioOutputs(); showOutputPicker = true }) {
                    Icon(Icons.Filled.Speaker, contentDescription = "Audio output", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Controls(
                    state = state,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                )
                IconButton(onClick = onShowLyrics) { Icon(Icons.Filled.Lyrics, contentDescription = "Lyrics", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onToggleQueue) { Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            AppleSeekBar(
                progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                durationMs = state.durationMs,
                onSeek = { fraction -> onSeek((fraction * state.durationMs).toLong()) },
                playing = state.isPlaying,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (showOutputPicker) {
            OutputPickerPopup(
                devices = state.audioOutputDevices,
                selectedDeviceId = state.audioOutputDevice,
                onRefresh = onRefreshAudioOutputs,
                onSelect = onSelectAudioOutput,
                onDismiss = { showOutputPicker = false },
            )
        }
    }
}

@Composable
private fun RowScope.Controls(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onToggleShuffle) {
        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffle) Coral else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = tint) }
    IconButton(onClick = onTogglePlayPause) {
        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = tint)
    }
    IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = tint) }
    IconButton(onClick = onCycleRepeat) {
        Icon(
            if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = "Repeat",
            tint = if (state.repeatMode != RepeatMode.OFF) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
