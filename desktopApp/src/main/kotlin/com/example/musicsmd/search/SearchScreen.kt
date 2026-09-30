package com.example.musicsmd.search

import com.example.musicsmd.ui.theme.ArtworkColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import com.example.musicsmd.ui.components.ArtistCircle
import com.example.musicsmd.ui.components.AlbumCard
import com.example.musicsmd.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.BrowseTile
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.player.AppUiState
import com.example.musicsmd.settings.DesktopSettings
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.BrowseTileCard
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.SongCard
import com.example.musicsmd.ui.components.SongRow
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFill
import com.example.musicsmd.ui.theme.GlassFillStrong
import com.example.musicsmd.ui.theme.OnAccent

@Composable
fun SearchScreen(
    state: AppUiState,
    settings: DesktopSettings,
    historyStore: SearchHistoryStore,
    /** Mobile's genre tiles; Search adds its own "New releases" and "Charts" ahead of them. */
    browseGenres: List<BrowseTile>,
    isLiked: (String) -> Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: (includeVideos: Boolean) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    /** The top result's cover colour for the header gradient, or null for the accent. */
    onHeaderTint: (Color?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history by historyStore.history.collectAsState()
    val recentItems by historyStore.items.collectAsState()
    val browseTiles = remember(browseGenres) { (LeadingBrowseTiles + browseGenres).distinctBy { it.id } }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    val focusRequester = remember { FocusRequester() }

    // Whatever is opened from the results (or from the recents shelf) is remembered for the shelf,
    // along with the query that found it. Queries are only kept once they have been used like this,
    // or submitted with Enter, so searching as you type doesn't fill history with half-typed words.
    fun rememberQuery() {
        state.query.trim().takeIf { it.isNotEmpty() }?.let(historyStore::add)
    }
    val openSong: (Song, List<Song>) -> Unit = { song, queue ->
        rememberQuery()
        historyStore.addItem(RecentSearchItem.of(song))
        onSongClick(song, queue)
    }
    val openAlbum: (Album) -> Unit = { album ->
        rememberQuery()
        historyStore.addItem(RecentSearchItem.of(album))
        onAlbumClick(album)
    }
    val openArtist: (Artist) -> Unit = { artist ->
        rememberQuery()
        historyStore.addItem(RecentSearchItem.of(artist))
        onArtistClick(artist)
    }

    /** Enter (or a tile / history pick): search now rather than after the typing pause. */
    fun submit(query: String = state.query) {
        val clean = query.trim()
        if (clean.isBlank()) return
        historyStore.add(clean)
        onSearch(settings.searchVideos)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(settings.searchVideos) {
        if (!settings.searchVideos && filter == SearchFilter.VIDEOS) filter = SearchFilter.ALL
    }
    // Refining a query keeps the chosen filter; a fresh search (after clearing the box) starts on All.
    LaunchedEffect(state.query.isBlank()) {
        if (state.query.isBlank()) filter = SearchFilter.ALL
    }
    // Typed ahead of the results shown: the next search is pending or running.
    val searchPending = state.query.isNotBlank() && (state.isSearching || state.query.trim() != state.searchedQuery)

    // Tint the header by the top result's artwork (falls back to the accent), as on mobile. The
    // gradient itself is drawn behind the page by the app (see HeaderWash), so Search only says
    // which colour it should be; null means the accent.
    val firstArtwork = state.searchResults.takeIf { state.query.isNotBlank() }?.let { r ->
        r.songs.firstOrNull()?.artworkUrl
            ?: r.albums.firstOrNull()?.artworkUrl
            ?: r.artists.firstOrNull()?.artworkUrl
            ?: r.videos.firstOrNull()?.artworkUrl
    }
    LaunchedEffect(firstArtwork) { onHeaderTint(firstArtwork?.let { ArtworkColors.accentFor(it) }) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        ) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        // Mobile's search field: a frosted glass pane with a borderless field inside.
        GlassPanel(shape = RoundedCornerShape(16.dp)) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("Songs, albums, artists…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Results stay on screen while the next ones load; this says they're coming.
                        if (searchPending && !state.searchResults.isEmpty) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        }
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
            )
        }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        when {
            state.query.isBlank() -> SearchLanding(
                recentItems = recentItems,
                history = history,
                browseTiles = browseTiles,
                isLiked = isLiked,
                onToggleLike = onToggleLike,
                onOpenSong = { song ->
                    openSong(song, recentItems.filter { it.kind == RecentKind.SONG }.map { it.toSong() })
                },
                onOpenAlbum = openAlbum,
                onOpenArtist = openArtist,
                onRemoveItem = historyStore::removeItem,
                onPickHistory = { query -> onQueryChange(query); submit(query) },
                onRemoveHistory = historyStore::remove,
                onClearHistory = historyStore::clear,
                onTileClick = { tile -> onQueryChange(tile.query); submit(tile.query) },
            )
            // Nothing to show yet: the first results for this query are on their way.
            state.searchResults.isEmpty && searchPending ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.searchResults.isEmpty -> EmptyResults(state.query)
            else -> SearchResultsContent(
                results = state.searchResults,
                selected = filter,
                videosEnabled = settings.searchVideos,
                onSelectFilter = { filter = it },
                isLiked = isLiked,
                onSongClick = openSong,
                onToggleLike = onToggleLike,
                onAlbumClick = openAlbum,
                onArtistClick = openArtist,
                onPlaylistClick = onPlaylistClick,
            )
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLanding(
    recentItems: List<RecentSearchItem>,
    history: List<String>,
    browseTiles: List<BrowseTile>,
    isLiked: (String) -> Boolean,
    onToggleLike: (Song) -> Unit,
    onOpenSong: (Song) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onRemoveItem: (RecentSearchItem) -> Unit,
    onPickHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTileClick: (BrowseTile) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 28.dp + LocalBottomBarPadding.current),
    ) {
        if (recentItems.isNotEmpty() || history.isNotEmpty()) {
            item(key = "recent-title") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Clear", color = Coral, modifier = Modifier.clip(CircleShape).clickable(onClick = onClearHistory).padding(8.dp))
                }
            }
        }
        if (recentItems.isNotEmpty()) {
            item(key = "recent-items") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(recentItems, key = { it.key }) { item ->
                        RemovableCard(label = item.title, onRemove = { onRemoveItem(item) }) {
                            when (item.kind) {
                                RecentKind.SONG -> {
                                    val song = item.toSong()
                                    SongCard(
                                        song = song,
                                        onClick = { onOpenSong(song) },
                                        isLiked = isLiked(song.id),
                                        onToggleLike = { onToggleLike(song) },
                                    )
                                }
                                RecentKind.ALBUM -> AlbumCard(
                                    title = item.title,
                                    subtitle = listOf("Album", item.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                                    artworkUrl = item.artworkUrl,
                                    onClick = { onOpenAlbum(item.toAlbum()) },
                                )
                                RecentKind.ARTIST -> ArtistCircle(
                                    name = item.title,
                                    artworkUrl = item.artworkUrl,
                                    onClick = { onOpenArtist(item.toArtist()) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item(key = "recent-queries") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEach { value ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(CircleShape).background(GlassFillStrong).clickable { onPickHistory(value) }
                                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(value, maxLines = 1)
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove $value",
                                modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onRemoveHistory(value) }.padding(2.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item(key = "browse-title") { Text("Browse all", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item(key = "browse-grid") { BrowseGrid(browseTiles, onTileClick) }
    }
}

/** A shelf card with a remove button that appears on hover, like the query chips' ×. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RemovableCard(label: String, onRemove: () -> Unit, content: @Composable () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
    ) {
        content()
        if (hovered) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove $label",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onRemove)
                    .padding(4.dp),
            )
        }
    }
}

/** Genre tiles in even columns that fill the width — mobile's two-column grid, widened for desktop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowseGrid(tiles: List<BrowseTile>, onTileClick: (BrowseTile) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val columns = ((maxWidth + gap) / (BROWSE_TILE_MIN_WIDTH + gap)).toInt().coerceAtLeast(2)
        // Sized in whole pixels: dp widths that add up to exactly the row can round a pixel over
        // it, and FlowRow would then wrap the last tile onto its own line.
        val density = LocalDensity.current
        val gapPx = with(density) { gap.roundToPx() }
        val tileWidth = with(density) { ((constraints.maxWidth - gapPx * (columns - 1)) / columns).toDp() }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = columns,
        ) {
            tiles.forEach { tile ->
                BrowseTileCard(
                    title = tile.title,
                    color = Color(tile.accentColor),
                    onClick = { onTileClick(tile) },
                    modifier = Modifier.width(tileWidth),
                )
            }
        }
    }
}

private val BROWSE_TILE_MIN_WIDTH = 200.dp

@Composable
private fun SearchResultsContent(
    results: SearchResults,
    selected: SearchFilter,
    videosEnabled: Boolean,
    onSelectFilter: (SearchFilter) -> Unit,
    isLiked: (String) -> Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    val available = buildList {
        add(SearchFilter.ALL)
        add(SearchFilter.SONGS)
        if (videosEnabled) add(SearchFilter.VIDEOS)
        add(SearchFilter.ALBUMS)
        add(SearchFilter.ARTISTS)
    }
    val effective = if (selected in available) selected else SearchFilter.ALL
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 28.dp + LocalBottomBarPadding.current),
    ) {
        item { FilterChips(available, effective, onSelectFilter) }
        when (effective) {
            SearchFilter.ALL -> {
                item { TopResultCard(results, onSongClick, onAlbumClick, onArtistClick) }
                songSection("Songs", results.songs.take(PREVIEW_COUNT), results.songs, isLiked, onSongClick, onToggleLike)
                if (videosEnabled) songSection("Videos", results.videos.take(PREVIEW_COUNT), results.videos, isLiked, onSongClick, onToggleLike)
                if (results.albums.isNotEmpty()) item { AlbumShelf("Albums", results.albums, onAlbumClick) }
                if (results.artists.isNotEmpty()) item { ArtistShelf("Artists", results.artists, onArtistClick) }
                if (results.playlists.isNotEmpty()) item { PlaylistShelf("Playlists", results.playlists, onPlaylistClick) }
            }
            SearchFilter.SONGS -> songSection("Songs", results.songs, results.songs, isLiked, onSongClick, onToggleLike)
            SearchFilter.VIDEOS -> songSection("Videos", results.videos, results.videos, isLiked, onSongClick, onToggleLike)
            SearchFilter.ALBUMS -> item { AlbumShelf("Albums", results.albums, onAlbumClick) }
            SearchFilter.ARTISTS -> item { ArtistShelf("Artists", results.artists, onArtistClick) }
        }
    }
}

@Composable
private fun FilterChips(filters: List<SearchFilter>, selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { filter ->
            val active = filter == selected
            Text(
                text = filter.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).background(if (active) Coral else GlassFill)
                    .clickable { onSelect(filter) }.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TopResultCard(
    results: SearchResults,
    onSongClick: (Song, List<Song>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
) {
    val song = results.songs.firstOrNull() ?: results.videos.firstOrNull()
    if (song != null) {
        TopCard(title = song.title, subtitle = song.artist, artworkUrl = song.artworkUrl, label = "Top result", onClick = { onSongClick(song, results.songs.ifEmpty { results.videos }) })
        return
    }
    val album = results.albums.firstOrNull()
    if (album != null) {
        TopCard(title = album.title, subtitle = album.artist, artworkUrl = album.artworkUrl, label = "Top album", onClick = { onAlbumClick(album) })
        return
    }
    val artist = results.artists.firstOrNull() ?: return
    TopCard(title = artist.name, subtitle = artist.subscribers.orEmpty(), artworkUrl = artist.artworkUrl, label = "Top artist", circular = true, onClick = { onArtistClick(artist) })
}

@Composable
private fun TopCard(title: String, subtitle: String, artworkUrl: String?, label: String, circular: Boolean = false, onClick: () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(26.dp), tint = GlassFillStrong) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(url = artworkUrl, size = 84.dp, shape = if (circular) RoundedCornerShape(44.dp) else RoundedCornerShape(16.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Coral, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.songSection(
    title: String,
    visibleSongs: List<Song>,
    queue: List<Song>,
    isLiked: (String) -> Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onToggleLike: (Song) -> Unit,
) {
    if (visibleSongs.isEmpty()) return
    item { SectionTitle(title) }
    items(visibleSongs, key = { it.id }) { song ->
        SongRow(
            song = song,
            onClick = { onSongClick(song, queue) },
            isLiked = isLiked(song.id),
            onToggleLike = { onToggleLike(song) },
        )
    }
}

@Composable
private fun AlbumShelf(title: String, albums: List<Album>, onClick: (Album) -> Unit) {
    CardShelf(title, albums, onClick) { CardInfo(it.title, it.artist, it.artworkUrl) }
}

@Composable
private fun ArtistShelf(title: String, artists: List<Artist>, onClick: (Artist) -> Unit) {
    CardShelf(title, artists, onClick) { CardInfo(it.name, it.subscribers.orEmpty(), it.artworkUrl, circular = true) }
}

@Composable
private fun PlaylistShelf(title: String, playlists: List<Playlist>, onClick: (Playlist) -> Unit) {
    CardShelf(title, playlists, onClick) { CardInfo(it.name, "Playlist", it.artworkUrl) }
}

@Composable
private fun <T> CardShelf(title: String, items: List<T>, onClick: (T) -> Unit, card: (T) -> CardInfo) {
    if (items.isEmpty()) {
        EmptyResults("No ${title.lowercase()}")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
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
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EmptyResults(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No results for $query", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class CardInfo(val title: String, val subtitle: String, val artworkUrl: String?, val circular: Boolean = false)

private enum class SearchFilter(val label: String) { ALL("All"), SONGS("Songs"), VIDEOS("Videos"), ALBUMS("Albums"), ARTISTS("Artists") }

private const val PREVIEW_COUNT = 5

/** Desktop's own tiles, ahead of mobile's genres; overlapping moods come from mobile's list. */
private val LeadingBrowseTiles = listOf(
    BrowseTile("new", "New releases", 0xFFE85D75, "new releases"),
    BrowseTile("charts", "Charts", 0xFF35C2A1, "top songs"),
)
