package com.example.musicsmd.nav

/** Top-level navigation state for the main content area (sidebar selects Home/Search/Library). */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data class AlbumDetail(val albumId: String) : Screen
    data class ArtistDetail(val artistId: String) : Screen
    data class PlaylistDetail(val playlistId: String) : Screen
}

enum class SidebarTab { HOME, SEARCH, LIBRARY }
