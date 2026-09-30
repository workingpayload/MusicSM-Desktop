package com.example.musicsmd.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicsmd.ui.components.SongRow

/** Slide-in queue list, mirrors the mobile app's Queue screen. */
@Composable
fun QueuePanel(state: PlaybackUiState, onClose: () -> Unit, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxHeight().width(320.dp), tonalElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Queue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close queue") }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(state.queue) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onSelect(index) },
                        isPlaying = index == state.queueIndex,
                        trailingIndex = index + 1,
                    )
                }
            }
        }
    }
}
