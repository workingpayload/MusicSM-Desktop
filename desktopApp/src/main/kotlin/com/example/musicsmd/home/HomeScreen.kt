package com.example.musicsmd.home

import com.example.musicsmd.ui.components.ArtistCircle
import com.example.musicsmd.ui.components.AlbumCard
import com.example.musicsmd.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.musicsmd.settings.HomeLayout
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.SongCard
import com.example.musicsmd.ui.components.SongRow
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFill
import com.example.musicsmd.ui.theme.GlassFillStrong
import com.example.musicsmd.ui.theme.OnAccent
import java.time.LocalTime

@Composable
fun HomeScreen(
    state: AppUiState,
    layout: HomeLayout,
    onLayoutChange: (HomeLayout) -> Unit,
    isLiked: (String) -> Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onLoadMoreHome: () -> Unit,
    onRetryHome: () -> Unit,
    onRefreshHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Made from your recent plays, likes and MusicSM picks", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RefreshButton(
                refreshing = state.isRefreshingHome,
                enabled = !state.isLoadingHome,
                onClick = onRefreshHome,
            )
            Spacer(Modifier.width(10.dp))
            LayoutToggle(layout = layout, onChange = onLayoutChange)
        }
        Spacer(Modifier.height(14.dp))

        when {
            state.isLoadingHome -> LoadingRow()
            state.homeFeed.sections.isEmpty() -> EmptyHome(onRetry = onRetryHome)
            else -> HomeShelves(
                sections = state.homeFeed.sections,
                generation = state.homeGeneration,
                layout = layout,
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
    generation: Int,
    layout: HomeLayout,
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
    // A refreshed feed starts from the top, not wherever the old one was scrolled to.
    var seenGeneration by remember { mutableIntStateOf(generation) }
    LaunchedEffect(generation) {
        if (generation != seenGeneration) {
            seenGeneration = generation
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 28.dp + LocalBottomBarPadding.current),
    ) {
        sections.forEachIndexed { index, section ->
            item(key = "title-$index-${section.title}") { ShelfTitle(section.title) }
            val songs = section.items.filterIsInstance<HomeItem.SongItem>().map { it.song }
            val openCard: (HomeItem) -> Unit = { item ->
                when (item) {
                    is HomeItem.AlbumItem -> onAlbumClick(item.album)
                    is HomeItem.ArtistItem -> onArtistClick(item.artist)
                    is HomeItem.PlaylistItem -> onPlaylistClick(item.playlist)
                    // Plays the shelf from this song, as a tapped card does on mobile.
                    is HomeItem.SongItem -> onSongClick(item.song, songs)
                }
            }

            if (layout == HomeLayout.CARDS) {
                // One horizontal shelf per section, in the feed's own order — mobile's Home.
                item(key = "shelf-$index-${section.title}") {
                    CardShelf(items = section.items, onClick = openCard) { item ->
                        SongCard(
                            song = item.song,
                            onClick = { openCard(item) },
                            isLiked = isLiked(item.song.id),
                            onToggleLike = { onToggleLike(item.song) },
                        )
                    }
                }
                return@forEachIndexed
            }

            val cards = section.items.filterNot { it is HomeItem.SongItem }
            if (cards.isNotEmpty()) {
                item(key = "cards-$index-${section.title}") {
                    CardShelf(items = cards, onClick = openCard, songCard = {})
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
private fun CardShelf(
    items: List<HomeItem>,
    onClick: (HomeItem) -> Unit,
    songCard: @Composable (HomeItem.SongItem) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items) { item ->
            if (item is HomeItem.SongItem) {
                songCard(item)
            } else {
                val info = item.toCardInfo()
                if (info.circular) {
                    ArtistCircle(name = info.title, artworkUrl = info.artworkUrl, onClick = { onClick(item) })
                } else {
                    AlbumCard(title = info.title, subtitle = info.subtitle, artworkUrl = info.artworkUrl, onClick = { onClick(item) })
                }
            }
        }
    }
}

/** List / Cards switch, styled like Search's filter chips. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RefreshButton(refreshing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TooltipArea(tooltip = { Tooltip("Refresh (F5)") }) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(GlassFill)
                .clickable(enabled = enabled && !refreshing, onClickLabel = "Refresh Home", onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Coral)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (refreshing) "Refreshing" else "Refresh",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tooltip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GlassFillStrong).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun LayoutToggle(layout: HomeLayout, onChange: (HomeLayout) -> Unit) {
    Row(
        modifier = Modifier.clip(CircleShape).background(GlassFill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LayoutOption(Icons.AutoMirrored.Filled.ViewList, "List", selected = layout == HomeLayout.LIST) {
            onChange(HomeLayout.LIST)
        }
        LayoutOption(Icons.Filled.GridView, "Cards", selected = layout == HomeLayout.CARDS) {
            onChange(HomeLayout.CARDS)
        }
    }
}

@Composable
private fun LayoutOption(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val content = if (selected) OnAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Coral else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
        )
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
