package com.example.musicsmd.local

import com.example.musicsmd.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.model.SongSort
import com.example.musicsm.domain.model.sortedFor
import com.example.musicsmd.settings.SettingsStore
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.SongRow
import java.io.File
import javax.swing.JFileChooser

@Composable
fun LocalMusicScreen(
    manager: LocalMusicManager,
    settingsStore: SettingsStore,
    onSongClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs by manager.songs.collectAsState()
    val scanning by manager.isScanning.collectAsState()
    val status by manager.status.collectAsState()
    val settings by settingsStore.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SongSort.TITLE) }

    val visibleSongs = remember(songs, query, sort) {
        val filtered = if (query.isBlank()) songs else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                (it.album?.contains(query, ignoreCase = true) == true)
        }
        filtered.sortedFor(sort)
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Local music", style = MaterialTheme.typography.headlineSmall)
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (scanning) CircularProgressIndicator()
                OutlinedButton(onClick = { manager.rescan() }, enabled = !scanning) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Rescan")
                }
                Button(onClick = { chooseFolder()?.let(manager::addFolder) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Add folder")
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Folders", style = MaterialTheme.typography.titleMedium)
                if (settings.localMusicDirs.isEmpty()) {
                    Text("Add folders containing MP3, M4A, AAC, FLAC, OGG, OPUS or WAV files.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                settings.localMusicDirs.forEach { path ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(path, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { manager.removeFolder(path) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove folder")
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = visibleSongs.isNotEmpty(), onClick = { visibleSongs.firstOrNull()?.let { onSongClick(it, visibleSongs) } }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Play all")
            }
            OutlinedButton(enabled = visibleSongs.isNotEmpty(), onClick = {
                val queue = visibleSongs.shuffled()
                queue.firstOrNull()?.let { onSongClick(it, queue) }
            }) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Shuffle")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search local songs") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            AssistChip(onClick = { sort = nextSort(sort) }, label = { Text("Sort: ${sort.name.lowercase().replace('_', ' ')}") })
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
            items(visibleSongs, key = { it.id }) { song ->
                SongRow(song = song, onClick = { onSongClick(song, visibleSongs) })
            }
            if (visibleSongs.isEmpty()) {
                item {
                    Text(
                        if (songs.isEmpty()) "No local music indexed yet. Add a folder or rescan." else "No songs match your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

private fun chooseFolder(): File? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Add local music folder"
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun nextSort(current: SongSort): SongSort = when (current) {
    SongSort.DEFAULT -> SongSort.TITLE
    SongSort.TITLE -> SongSort.ARTIST
    SongSort.ARTIST -> SongSort.ALBUM
    SongSort.ALBUM -> SongSort.DURATION_SHORT
    SongSort.DURATION_SHORT -> SongSort.DURATION_LONG
    SongSort.DURATION_LONG -> SongSort.DEFAULT
}
