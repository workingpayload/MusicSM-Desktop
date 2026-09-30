package com.example.musicsmd.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.player.LocalSongActions
import com.example.musicsmd.ui.theme.GlassFillStrong

/** A single song row shared by Home/Search/Library/Album/Artist/Playlist/Queue. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean? = null,
    onToggleLike: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    trailingIndex: Int? = null,
) {
    val actions = LocalSongActions.current
    var menuExpanded by remember { mutableStateOf(false) }
    var playlistsExpanded by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    val downloaded = remember(song.id, actions) { actions.isDownloaded(song.id) }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) menuExpanded = true
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (trailingIndex != null) {
                Text(
                    text = trailingIndex.toString(),
                    modifier = Modifier.width(28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ArtworkImage(url = song.artworkUrl, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
            }

            Row(modifier = Modifier.weight(1f)) {
                Column {
                    Text(
                        text = song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onToggleLike != null) {
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (isLiked == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isLiked == true) "Unlike" else "Like",
                        tint = if (isLiked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The menu is anchored to the ⋮ button so it opens beside it, not at the row's left edge.
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Song options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SongMenu(
                    expanded = menuExpanded,
                    playlistsExpanded = playlistsExpanded,
                    song = song,
                    isLiked = isLiked,
                    downloaded = downloaded,
                    onDismiss = { menuExpanded = false; playlistsExpanded = false },
                    onTogglePlaylists = { playlistsExpanded = !playlistsExpanded },
                    onCreatePlaylist = { showCreatePlaylist = true; menuExpanded = false; playlistsExpanded = false },
                    onToggleLike = onToggleLike,
                )
            }
        }
    }

    if (showCreatePlaylist) {
        NewPlaylistDialog(
            onCreate = { name ->
                actions.createPlaylistWith(song, name)
                showCreatePlaylist = false
            },
            onDismiss = { showCreatePlaylist = false },
        )
    }
}

@Composable
private fun SongMenu(
    expanded: Boolean,
    playlistsExpanded: Boolean,
    song: Song,
    isLiked: Boolean?,
    downloaded: Boolean,
    onDismiss: () -> Unit,
    onTogglePlaylists: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onToggleLike: (() -> Unit)?,
) {
    val actions = LocalSongActions.current
    // Local files have no YouTube id: radio, download, share and catalogue lookups don't apply.
    val isLocal = song.id.startsWith("local:")
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(GlassFillStrong, RoundedCornerShape(18.dp)).padding(vertical = 4.dp),
    ) {
        MenuItem(Icons.Filled.PlaylistPlay, "Play next") { actions.playNext(song); onDismiss() }
        MenuItem(Icons.Filled.QueueMusic, "Add to queue") { actions.addToQueue(song); onDismiss() }
        if (!isLocal) {
            MenuItem(Icons.Filled.QueueMusic, "Start radio") { actions.startRadio(song); onDismiss() }
        }
        MenuItem(Icons.Filled.PlaylistAdd, "Add to playlist  ›") { onTogglePlaylists() }
        DropdownMenu(
            expanded = playlistsExpanded,
            onDismissRequest = onTogglePlaylists,
            modifier = Modifier.background(GlassFillStrong, RoundedCornerShape(18.dp)).padding(vertical = 4.dp),
        ) {
            if (actions.playlists.isEmpty()) {
                DropdownMenuItem(text = { Text("No playlists yet") }, onClick = {}, enabled = false)
            } else {
                actions.playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { actions.addToPlaylist(song, playlist.id); onDismiss() },
                    )
                }
            }
            DropdownMenuItem(text = { Text("New playlist…") }, onClick = onCreatePlaylist)
        }
        if (!isLocal) {
            MenuItem(Icons.Filled.Person, "Go to artist", enabled = song.artist.isNotBlank()) { actions.goToArtist(song); onDismiss() }
            if (!song.album.isNullOrBlank()) {
                MenuItem(Icons.Filled.Album, "Go to album") { actions.goToAlbum(song); onDismiss() }
            }
            if (downloaded) {
                MenuItem(Icons.Filled.DownloadDone, "Remove download") { actions.removeDownload(song); onDismiss() }
            } else {
                MenuItem(Icons.Filled.Download, "Download") { actions.download(song); onDismiss() }
            }
            MenuItem(Icons.Filled.Share, "Share") { actions.share(song); onDismiss() }
        }
        if (onToggleLike != null) {
            MenuItem(
                icon = if (isLiked == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isLiked == true) "Unlike" else "Like",
                tintPrimary = isLiked == true,
            ) { onToggleLike(); onDismiss() }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tintPrimary: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (tintPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Playlist name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
