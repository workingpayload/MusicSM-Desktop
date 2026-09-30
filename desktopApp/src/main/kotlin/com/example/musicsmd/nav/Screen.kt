package com.example.musicsmd.nav

/** Top-level navigation state for the main content area (sidebar selects the top-level tabs). */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data object Downloads : Screen
    data object LocalMusic : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object Equalizer : Screen
    data object Import : Screen
    data class AlbumDetail(val albumId: String) : Screen
    data class ArtistDetail(val artistId: String) : Screen
    data class PlaylistDetail(val playlistId: String) : Screen

    /** A playlist received as a share link / pasted code (payload = the full share text). */
    data class SharedPlaylist(val payload: String) : Screen
}

/** Screens that appear in the sidebar; everything else is pushed on top of one of these. */
val TopLevelScreens: List<Screen> = listOf(
    Screen.Home, Screen.Search, Screen.Library, Screen.Downloads, Screen.LocalMusic, Screen.Stats, Screen.Settings,
)
