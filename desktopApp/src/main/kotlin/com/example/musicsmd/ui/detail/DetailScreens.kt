package com.example.musicsmd.ui.detail

import com.example.musicsmd.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.SongRow
import com.example.musicsmd.ui.components.accentColorFor
import com.example.musicsmd.ui.components.rememberDominantColorState
import com.example.musicsmd.ui.theme.AppBackground
import com.example.musicsmd.ui.theme.GlassFill
import com.example.musicsmd.ui.theme.asDeepTint
import com.example.musicsmd.ui.theme.isHueless

/** Shared header + song-list layout for Album/Artist/Playlist detail screens. */
@Composable
private fun DetailScaffold(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    songs: List<Song>,
    isLiked: (String) -> Boolean,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    isLoading: Boolean,
    onDownloadAll: ((List<Song>) -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    val accent = rememberDominantColorState(url = artworkUrl ?: songs.firstOrNull()?.artworkUrl, fallback = accentColorFor(title))
    // Palette tokens are composable reads, so they are hoisted out of the draw lambda. A cover with
    // no usable hue is left alone rather than deepened: deepening grey only makes a muddier grey.
    val backdrop = AppBackground
    val tint by remember(backdrop) {
        derivedStateOf {
            val raw = accent.value
            if (raw.isHueless()) backdrop else raw.asDeepTint()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Held flat over the top half, then eased out over the bottom, as on mobile: a
                // single flat fill leaves a seam where the cover the tint came from stops and the
                // plain track list starts; easing it turns that line into a deliberate wash.
                drawRect(
                    Brush.verticalGradient(
                        0.0f to tint,
                        0.5f to tint,
                        1.0f to backdrop,
                    ),
                )
            }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface) }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        } else {
            GlassPanel(shape = RoundedCornerShape(20.dp), tint = GlassFill) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
                    ArtworkImage(url = artworkUrl, size = 160.dp, shape = RoundedCornerShape(16.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (songs.isNotEmpty()) {
                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onSongClick(songs.first(), songs) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play")
                                }
                                if (onDownloadAll != null) {
                                    OutlinedButton(onClick = { onDownloadAll(songs) }) {
                                        Icon(Icons.Filled.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Download all")
                                    }
                                }
                                if (onShare != null) {
                                    OutlinedButton(onClick = onShare) {
                                        Icon(Icons.Filled.Share, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarPadding.current)) {
                itemsIndexed(songs) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onSongClick(song, songs) },
                        isLiked = isLiked(song.id),
                        onToggleLike = { onToggleLike(song) },
                        trailingIndex = index + 1,
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(
    album: Album?,
    isLoading: Boolean,
    isLiked: (String) -> Boolean,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onDownloadAll: (List<Song>) -> Unit,
) {
    DetailScaffold(
        title = album?.title ?: "",
        subtitle = album?.let { "${it.artist}${it.year?.let { y -> " · $y" } ?: ""}" },
        artworkUrl = album?.artworkUrl,
        songs = album?.songs ?: emptyList(),
        isLiked = isLiked,
        onBack = onBack,
        onSongClick = onSongClick,
        onToggleLike = onToggleLike,
        isLoading = isLoading,
        onDownloadAll = onDownloadAll,
    )
}

@Composable
fun ArtistDetailScreen(
    artist: Artist?,
    isLoading: Boolean,
    isLiked: (String) -> Boolean,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onDownloadAll: (List<Song>) -> Unit,
) {
    DetailScaffold(
        title = artist?.name ?: "",
        subtitle = artist?.subscribers,
        artworkUrl = artist?.artworkUrl,
        songs = artist?.topSongs ?: emptyList(),
        isLiked = isLiked,
        onBack = onBack,
        onSongClick = onSongClick,
        onToggleLike = onToggleLike,
        isLoading = isLoading,
        onDownloadAll = onDownloadAll,
    )
}

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    isLoading: Boolean,
    isLiked: (String) -> Boolean,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onDownloadAll: (List<Song>) -> Unit,
    onSharePlaylist: (Playlist) -> Unit,
) {
    DetailScaffold(
        title = playlist?.name ?: "",
        subtitle = playlist?.let { "${it.songs.size} songs" },
        artworkUrl = playlist?.artworkUrl,
        songs = playlist?.songs ?: emptyList(),
        isLiked = isLiked,
        onBack = onBack,
        onSongClick = onSongClick,
        onToggleLike = onToggleLike,
        isLoading = isLoading,
        onDownloadAll = onDownloadAll,
        onShare = playlist?.takeIf { it.isLocal && it.songs.isNotEmpty() }?.let { { onSharePlaylist(it) } },
    )
}
