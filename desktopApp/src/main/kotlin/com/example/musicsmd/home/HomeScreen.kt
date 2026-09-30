package com.example.musicsmd.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.HomeSection
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.player.AppUiState

/** Home/Search screen: search bar on top, then either home shelves or search results. */
@Composable
fun HomeScreen(
    state: AppUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
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
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isSearching -> LoadingRow()
            state.query.isNotBlank() && !state.searchResults.isEmpty -> {
                val allSongs = state.searchResults.songs + state.searchResults.videos
                SongList(songs = allSongs, queue = allSongs, onSongClick = onSongClick)
            }
            state.isLoadingHome -> LoadingRow()
            else -> HomeShelves(sections = state.homeFeed.sections, onSongClick = onSongClick)
        }

        state.error?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun HomeShelves(
    sections: List<HomeSection>,
    onSongClick: (Song, List<Song>) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { section ->
            item {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            val songs = section.items.filterIsInstance<HomeItem.SongItem>().map { it.song }
            items(songs) { song ->
                SongRow(song = song, onClick = { onSongClick(song, songs) })
            }
        }
    }
}

@Composable
private fun SongList(songs: List<Song>, queue: List<Song>, onSongClick: (Song, List<Song>) -> Unit) {
    LazyColumn {
        items(songs) { song ->
            SongRow(song = song, onClick = { onSongClick(song, queue) })
        }
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun LoadingRow() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
    }
}
