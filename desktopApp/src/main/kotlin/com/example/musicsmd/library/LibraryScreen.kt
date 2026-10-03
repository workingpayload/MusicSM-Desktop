package com.example.musicsmd.library

import com.example.musicsmd.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.share.decodeQrImage
import com.example.musicsmd.ui.theme.GlassFill
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** "Your Library": liked songs + local playlists (and the signed-in YouTube account's), mirrors the mobile app's Library screen. */
@Composable
fun LibraryScreen(
    likedSongs: List<Song>,
    playlists: List<Playlist>,
    youTubePlaylists: List<Playlist>,
    isLiked: (String) -> Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onImportClick: () -> Unit,
    onOpenSharedPlaylist: (String) -> Unit,
    onSharePlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showOpenSharedDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var sharedPayload by remember { mutableStateOf("") }
    var sharedError by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Your Library", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onImportClick) { Text("Import playlist") }
                TextButton(onClick = { showOpenSharedDialog = true }) { Text("Open shared playlist") }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New playlist")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showCreateDialog) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) onCreatePlaylist(newPlaylistName)
                    newPlaylistName = ""
                    showCreateDialog = false
                }) { Text("Create") }
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
            item {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clickable { if (likedSongs.isNotEmpty()) onSongClick(likedSongs.first(), likedSongs) },
                    shape = RoundedCornerShape(16.dp),
                    tint = GlassFill,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp),
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                        Column {
                            Text("Liked Songs", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text("${likedSongs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            items(playlists) { playlist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onPlaylistClick(playlist) }.padding(vertical = 8.dp),
                ) {
                    if (playlist.artworkUrl != null) {
                        ArtworkImage(url = playlist.artworkUrl, size = 40.dp)
                    } else {
                        Icon(Icons.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        Text("${playlist.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (playlist.isLocal && playlist.songs.isNotEmpty()) {
                        IconButton(onClick = { onSharePlaylist(playlist) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share playlist")
                        }
                    }
                }
            }

            if (youTubePlaylists.isNotEmpty()) {
                item {
                    Text(
                        "From YouTube Music",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(youTubePlaylists, key = { "yt:" + it.id }) { playlist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPlaylistClick(playlist) }.padding(vertical = 8.dp),
                    ) {
                        if (playlist.artworkUrl != null) {
                            ArtworkImage(url = playlist.artworkUrl, size = 40.dp)
                        } else {
                            Icon(Icons.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            Text("YouTube Music", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (likedSongs.isEmpty() && playlists.isEmpty() && youTubePlaylists.isEmpty()) {
                item {
                    Text(
                        "Songs you like and playlists you create will show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }

        if (showOpenSharedDialog) {
            AlertDialog(
                onDismissRequest = { showOpenSharedDialog = false },
                title = { Text("Open shared playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Paste a MusicSM shared playlist link or payload.")
                        OutlinedTextField(
                            value = sharedPayload,
                            onValueChange = { sharedPayload = it; sharedError = null },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 4,
                            label = { Text("Link or code") },
                        )
                        sharedError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val dialog = FileDialog(null as Frame?, "Scan QR from image", FileDialog.LOAD).apply { isVisible = true }
                            val selected = dialog.file
                            val dir = dialog.directory
                            if (selected != null && dir != null) {
                                val decoded = decodeQrImage(File(dir, selected))
                                if (decoded == null) sharedError = "No QR code found in that image" else sharedPayload = decoded
                            }
                        }) { Text("Scan QR image") }
                        Button(onClick = {
                            val payload = sharedPayload.trim()
                            if (payload.isBlank()) {
                                sharedError = "Paste a link or code first"
                            } else {
                                showOpenSharedDialog = false
                                onOpenSharedPlaylist(payload)
                            }
                        }) { Text("Open") }
                        TextButton(onClick = { showOpenSharedDialog = false }) { Text("Cancel") }
                    }
                },
            )
        }
    }
}
