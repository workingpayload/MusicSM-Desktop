package com.example.musicsmd.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsmd.settings.RepeatMode
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFillStrong

/** Slide-in editable queue with MusicSM's glass treatment and playback-mode indicators. */
@Composable
fun QueuePanel(
    state: PlaybackUiState,
    onClose: () -> Unit,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier.fillMaxHeight().width(380.dp).padding(vertical = 12.dp, horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        tint = GlassFillStrong,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    ModeIndicators(state)
                }
                IconButton(onClick = onClearUpcoming, enabled = state.queueIndex + 1 < state.queue.size) {
                    Icon(Icons.Filled.ClearAll, contentDescription = "Clear upcoming", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close queue", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.currentSong != null && state.queueIndex in state.queue.indices) {
                    item { Header("Now playing") }
                    item {
                        QueueRow(
                            index = state.queueIndex,
                            song = state.queue[state.queueIndex],
                            isCurrent = true,
                            canMoveUp = state.queueIndex > 0,
                            canMoveDown = state.queueIndex < state.queue.lastIndex,
                            onSelect = onSelect,
                            onRemove = onRemove,
                            onMove = onMove,
                        )
                    }
                }
                val upcomingStart = (state.queueIndex + 1).coerceAtLeast(0)
                if (upcomingStart < state.queue.size) {
                    item { Header("Up next") }
                    items(state.queue.size - upcomingStart) { offset ->
                        val index = upcomingStart + offset
                        QueueRow(
                            index = index,
                            song = state.queue[index],
                            isCurrent = false,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.queue.lastIndex,
                            onSelect = onSelect,
                            onRemove = onRemove,
                            onMove = onMove,
                        )
                    }
                }
                if (state.queueIndex > 0) {
                    item { Header("Played") }
                    items(state.queueIndex) { index ->
                        QueueRow(
                            index = index,
                            song = state.queue[index],
                            isCurrent = false,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.queue.lastIndex,
                            onSelect = onSelect,
                            onRemove = onRemove,
                            onMove = onMove,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeIndicators(state: PlaybackUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.shuffle) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Coral, modifier = Modifier.size(14.dp))
            Text("Shuffle", style = MaterialTheme.typography.labelSmall, color = Coral)
        }
        if (state.repeatMode != RepeatMode.OFF) {
            Icon(
                if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = null,
                tint = Coral,
                modifier = Modifier.size(14.dp),
            )
            Text(if (state.repeatMode == RepeatMode.ONE) "Repeat one" else "Repeat all", style = MaterialTheme.typography.labelSmall, color = Coral)
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Coral,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun QueueRow(
    index: Int,
    song: com.example.musicsm.domain.model.Song,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(url = song.artworkUrl, size = 42.dp, shape = RoundedCornerShape(10.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) Coral else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onMove(index, index - 1) }, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = { onMove(index, index + 1) }, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = { onRemove(index) }, enabled = !isCurrent) {
            Icon(Icons.Filled.Close, contentDescription = "Remove")
        }
    }
}
