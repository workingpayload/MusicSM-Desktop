package com.example.musicsmd.downloads

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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.FailedDownload
import com.example.musicsmd.settings.SettingsStore
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.SongRow
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

@Composable
fun DownloadsScreen(
    manager: DownloadManager,
    settingsStore: SettingsStore,
    onSongClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active by manager.activeDownloads.collectAsState()
    val failed by manager.failedDownloads.collectAsState()
    val downloaded by manager.downloads().collectAsState(initial = emptyList())
    val settings by settingsStore.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(downloaded, active) {
        bytes = manager.storageUsedBytes()
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Downloads", style = MaterialTheme.typography.headlineSmall)
                Text("${downloaded.size} songs · ${formatBytes(bytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { runCatching { Desktop.getDesktop().open(File(settings.downloadsDir).apply { mkdirs() }) } }) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Open folder")
            }
        }

        if (active.isNotEmpty()) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Active downloads", style = MaterialTheme.typography.titleMedium)
                    active.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                                Text("${(item.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { manager.cancel(item.song.id) }) {
                                Icon(Icons.Filled.Cancel, contentDescription = "Cancel")
                            }
                        }
                    }
                }
            }
        }

        if (failed.isNotEmpty()) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Failed", style = MaterialTheme.typography.titleMedium)
                    failed.forEach { item -> FailedDownloadRow(item, onRetry = { scope.launch { manager.retry(item.song.id) } }) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = downloaded.isNotEmpty(), onClick = { downloaded.firstOrNull()?.let { onSongClick(it, downloaded) } }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Play all")
            }
            OutlinedButton(enabled = downloaded.isNotEmpty(), onClick = {
                val queue = downloaded.shuffled()
                queue.firstOrNull()?.let { onSongClick(it, queue) }
            }) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Shuffle")
            }
            OutlinedButton(enabled = downloaded.isNotEmpty(), onClick = { scope.launch { manager.deleteAll() } }) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Delete all")
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(downloaded, key = { it.id }) { song ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    SongRow(song = song, onClick = { onSongClick(song, downloaded) }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { manager.delete(song.id) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete download")
                    }
                }
            }
            if (downloaded.isEmpty() && active.isEmpty()) {
                item { Text("Downloaded songs will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
private fun FailedDownloadRow(item: FailedDownload, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, contentDescription = "Retry") }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}
