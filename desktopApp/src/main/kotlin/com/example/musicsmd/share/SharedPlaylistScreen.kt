package com.example.musicsmd.share

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.share.PlaylistShareCodec
import com.example.musicsm.domain.share.SharedPlaylist
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.SongRow
import kotlinx.coroutines.launch

@Composable
fun SharedPlaylistScreen(
    payload: String,
    libraryRepository: LibraryRepository,
    onBack: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlist = remember(payload) { decodeSharedPlaylist(payload) }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (playlist == null) {
        Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("That shared playlist link is invalid or expired.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) { Text("Back to library") }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtworkImage(url = playlist.songs.firstOrNull()?.artworkUrl, size = 112.dp)
                    Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                        Text("Shared playlist", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text(playlist.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.songs.size} songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = playlist.songs.isNotEmpty(),
                                onClick = { playlist.songs.firstOrNull()?.let { onPlay(it, playlist.songs) } },
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Play all")
                            }
                            OutlinedButton(
                                enabled = !saving,
                                onClick = {
                                    scope.launch {
                                        saving = true
                                        error = null
                                        runCatching {
                                            val id = libraryRepository.createPlaylist(playlist.name)
                                            playlist.songs.forEach { libraryRepository.addToPlaylist(id, it) }
                                            id
                                        }.onSuccess(onOpenPlaylist)
                                            .onFailure { error = it.message ?: "Couldn't save playlist" }
                                        saving = false
                                    }
                                },
                            ) {
                                if (saving) CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Filled.LibraryAdd, contentDescription = null)
                                Text("Save to library")
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        itemsIndexed(playlist.songs, key = { index, _ -> index }) { index, song ->
            SongRow(song = song, onClick = { onPlay(song, playlist.songs) }, trailingIndex = index + 1)
        }
    }
}

private fun decodeSharedPlaylist(raw: String): SharedPlaylist? {
    val text = raw.trim()
    val payload = PlaylistShareCodec.payloadFromUrl(text) ?: text
    return PlaylistShareCodec.decode(payload)
}
