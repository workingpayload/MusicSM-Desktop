package com.example.musicsmd.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.musicsmd.ui.theme.GlassFill

/** Home/Search screen: search bar on top, then either home shelves or search results. */
@Composable
fun HomeScreen(
    state: AppUiState,
    isLiked: (String) -> Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onLoadMoreHome: () -> Unit,
    onRetryHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search songs, albums, artists…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = GlassFill,
                focusedContainerColor = GlassFill,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isSearching -> LoadingRow()
            state.query.isNotBlank() && !state.searchResults.isEmpty -> {
                val results = state.searchResults
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (results.albums.isNotEmpty()) {
                        item { ShelfTitle("Albums") }
                        item { CardShelf(items = results.albums, onClick = onAlbumClick) { CardInfo(it.title, it.artist, it.artworkUrl) } }
                    }
                    if (results.artists.isNotEmpty()) {
                        item { ShelfTitle("Artists") }
                        item {
                            CardShelf(items = results.artists, onClick = onArtistClick) {
                                CardInfo(it.name, it.subscribers.orEmpty(), it.artworkUrl, circular = true)
                            }
                        }
                    }
                    val songs = results.songs + results.videos
                    if (songs.isNotEmpty()) {
                        item { ShelfTitle("Songs") }
                        items(songs) { song ->
                            SongRow(
                                song = song,
                                onClick = { onSongClick(song, songs) },
                                isLiked = isLiked(song.id),
                                onToggleLike = { onToggleLike(song) },
                            )
                        }
                    }
                }
            }
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
            Text(text = error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
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
    // Keeps paging while the viewport isn't full, and again whenever the user nears the bottom.
    LaunchedEffect(nearEnd, canLoadMore, sections.size) {
        if (nearEnd && canLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        sections.forEachIndexed { index, section ->
            item(key = "title-$index") { ShelfTitle(section.title) }

            val songs = section.items.filterIsInstance<HomeItem.SongItem>().map { it.song }
            val cards = section.items.filterNot { it is HomeItem.SongItem }

            if (cards.isNotEmpty()) {
                item(key = "cards-$index") {
                    CardShelf(
                        items = cards,
                        onClick = { card ->
                            when (card) {
                                is HomeItem.AlbumItem -> onAlbumClick(card.album)
                                is HomeItem.ArtistItem -> onArtistClick(card.artist)
                                is HomeItem.PlaylistItem -> onPlaylistClick(card.playlist)
                                is HomeItem.SongItem -> Unit
                            }
                        },
                        card = { it.toCardInfo() },
                    )
                }
            }
            items(songs) { song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song, songs) },
                    isLiked = isLiked(song.id),
                    onToggleLike = { onToggleLike(song) },
                )
            }
        }
        if (isLoadingMore) {
            item(key = "loading-more") { LoadingRow() }
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
private fun ShelfTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun <T> CardShelf(items: List<T>, onClick: (T) -> Unit, card: (T) -> CardInfo) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { item ->
            val info = card(item)
            GlassPanel(
                modifier = Modifier.width(156.dp).clickableCard { onClick(item) },
                shape = RoundedCornerShape(16.dp),
                tint = GlassFill,
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ArtworkImage(
                        url = info.artworkUrl,
                        size = 140.dp,
                        shape = if (info.circular) RoundedCornerShape(70.dp) else RoundedCornerShape(10.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(info.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (info.subtitle.isNotBlank()) {
                        Text(
                            info.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableCard(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun LoadingRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun EmptyHome(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't load your home feed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
