package com.example.musicsmd.home

import com.example.musicsmd.ui.components.ArtistCircle
import com.example.musicsmd.ui.components.AlbumCard
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.player.AppUiState
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.SongRow
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFill
import com.example.musicsmd.ui.theme.GlassFillStrong
import java.time.LocalTime

@Composable
fun HomeScreen(
    state: AppUiState,
    isLiked: (String) -> Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onLoadMoreHome: () -> Unit,
    onRetryHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Text(greeting(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Made from your recent plays, likes and MusicSM picks", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        when {
            state.isLoadingHome -> LoadingRow()
            state.homeFeed.sections.isEmpty() -> EmptyHome(onRetry = onRetryHome)
            else -> HomeShelves(
                sections = state.homeFeed.sections,
                canLoadMore = state.homeFeed.continuation != null,
                isLoadingMore = state.isLoadingMoreHome,
                onLoadMore = onLoadMoreHome,
                isLiked = isLiked,
                onSongClick = onSongClick,
                onToggleLike = onToggleLike,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
            )
        }

        state.error?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun HomeShelves(
    sections: List<HomeSection>,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    isLiked: (String) -> Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, canLoadMore, sections.size) {
        if (nearEnd && canLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 28.dp + LocalBottomBarPadding.current),
    ) {
        sections.forEachIndexed { index, section ->
            item(key = "title-$index-${section.title}") { ShelfTitle(section.title) }
            val songs = section.items.filterIsInstance<HomeItem.SongItem>().map { it.song }
            val cards = section.items.filterNot { it is HomeItem.SongItem }

            if (cards.isNotEmpty()) {
                item(key = "cards-$index-${section.title}") {
                    CardShelf(
                        items = cards,
                        onClick = { item ->
                            when (item) {
                                is HomeItem.AlbumItem -> onAlbumClick(item.album)
                                is HomeItem.ArtistItem -> onArtistClick(item.artist)
                                is HomeItem.PlaylistItem -> onPlaylistClick(item.playlist)
                                is HomeItem.SongItem -> Unit
                            }
                        },
                        card = { it.toCardInfo() },
                    )
                }
            }
            items(songs, key = { song -> "${section.title}-${song.id}" }) { song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song, songs) },
                    isLiked = isLiked(song.id),
                    onToggleLike = { onToggleLike(song) },
                )
            }
        }
        if (isLoadingMore) item(key = "loading-more") { LoadingRow() }
    }
}

@Composable
private fun ShelfTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun <T> CardShelf(items: List<T>, onClick: (T) -> Unit, card: (T) -> CardInfo) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items) { item ->
            val info = card(item)
            if (info.circular) {
                ArtistCircle(name = info.title, artworkUrl = info.artworkUrl, onClick = { onClick(item) })
            } else {
                AlbumCard(title = info.title, subtitle = info.subtitle, artworkUrl = info.artworkUrl, onClick = { onClick(item) })
            }
        }
    }
}

private data class CardInfo(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val circular: Boolean = false,
)

private fun HomeItem.toCardInfo(): CardInfo = when (this) {
    is HomeItem.AlbumItem -> CardInfo(album.title, album.artist, album.artworkUrl)
    is HomeItem.ArtistItem -> CardInfo(artist.name, artist.subscribers.orEmpty(), artist.artworkUrl, circular = true)
    is HomeItem.PlaylistItem -> CardInfo(
        playlist.name,
        if (playlist.songs.isEmpty()) "Playlist" else "Playlist · ${playlist.songs.size} songs",
        playlist.artworkUrl,
    )
    is HomeItem.SongItem -> CardInfo(song.title, song.artist, song.artworkUrl)
}

@Composable
private fun LoadingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyHome(onRetry: () -> Unit) {
    GlassPanel(shape = RoundedCornerShape(24.dp), tint = GlassFillStrong) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Couldn't load your home feed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Retry", color = Coral) }
        }
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
