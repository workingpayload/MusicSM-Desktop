package com.example.musicsmd.player

import androidx.compose.runtime.compositionLocalOf
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song

/**
 * Everything a song's context menu can do. Built once in `App` and provided through
 * [LocalSongActions] so any [com.example.musicsmd.ui.components.SongRow] can show the menu
 * without threading a dozen callbacks through every screen.
 */
data class SongActions(
    val playNext: (Song) -> Unit = {},
    val addToQueue: (Song) -> Unit = {},
    val startRadio: (Song) -> Unit = {},
    /** Local playlists the song can be added to. */
    val playlists: List<Playlist> = emptyList(),
    val addToPlaylist: (song: Song, playlistId: String) -> Unit = { _, _ -> },
    val createPlaylistWith: (song: Song, name: String) -> Unit = { _, _ -> },
    val goToArtist: (Song) -> Unit = {},
    val goToAlbum: (Song) -> Unit = {},
    val isDownloaded: (songId: String) -> Boolean = { false },
    val download: (Song) -> Unit = {},
    val removeDownload: (Song) -> Unit = {},
    val share: (Song) -> Unit = {},
)

val LocalSongActions = compositionLocalOf { SongActions() }
