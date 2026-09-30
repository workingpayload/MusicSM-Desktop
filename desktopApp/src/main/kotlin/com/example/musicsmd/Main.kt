package com.example.musicsmd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.home.HomeScreen
import com.example.musicsmd.library.LibraryScreen
import com.example.musicsmd.nav.Screen
import com.example.musicsmd.player.AppViewModel
import com.example.musicsmd.player.NowPlayingScreen
import com.example.musicsmd.player.PlayerBar
import com.example.musicsmd.player.QueuePanel
import com.example.musicsmd.ui.NavRail
import com.example.musicsmd.ui.components.LocalHazeState
import com.example.musicsmd.ui.components.glassBackdrop
import com.example.musicsmd.ui.components.rememberHazeState
import com.example.musicsmd.ui.detail.AlbumDetailScreen
import com.example.musicsmd.ui.detail.ArtistDetailScreen
import com.example.musicsmd.ui.detail.PlaylistDetailScreen
import com.example.musicsmd.ui.theme.MusicSMTheme
import com.example.musicsmd.ui.theme.SurfaceLowest

@Composable
fun App(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.playback.collectAsState()

    val likedIds = uiState.likedSongs.map { it.id }.toSet()
    fun isLiked(songId: String) = songId in likedIds || (playback.currentSong?.id == songId && playback.isLiked)

    fun playSong(song: Song, queue: List<Song>) = viewModel.play(song, queue)

    MusicSMTheme {
        val hazeState = rememberHazeState()
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Surface(modifier = Modifier.fillMaxSize(), color = SurfaceLowest) {
                if (playback.isExpanded && playback.currentSong != null) {
                    NowPlayingScreen(
                        state = playback,
                        onCollapse = { viewModel.setExpanded(false) },
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::playNextInQueue,
                        onPrevious = viewModel::playPreviousInQueue,
                        onSeek = viewModel::seekTo,
                        onToggleLike = viewModel::toggleLikeCurrent,
                        onVolumeChange = viewModel::setVolume,
                        onToggleQueue = { viewModel.setQueueVisible(!playback.isQueueVisible) },
                    )
                    return@Surface
                }

                Row(modifier = Modifier.fillMaxSize().glassBackdrop(hazeState)) {
                    NavRail(
                        current = tabOf(uiState.screen),
                        onSelect = { viewModel.selectTab(it) },
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    when (val screen = uiState.screen) {
                                        Screen.Home, Screen.Search -> HomeScreen(
                                            state = uiState,
                                            isLiked = ::isLiked,
                                            onQueryChange = viewModel::onQueryChange,
                                            onSearch = viewModel::search,
                                            onSongClick = ::playSong,
                                            onToggleLike = viewModel::toggleLike,
                                            onAlbumClick = { viewModel.navigateTo(Screen.AlbumDetail(it.id)) },
                                            onArtistClick = { viewModel.navigateTo(Screen.ArtistDetail(it.id)) },
                                        )
                                        Screen.Library -> LibraryScreen(
                                            likedSongs = uiState.likedSongs,
                                            playlists = uiState.playlists,
                                            isLiked = ::isLiked,
                                            onSongClick = ::playSong,
                                            onToggleLike = viewModel::toggleLike,
                                            onPlaylistClick = { viewModel.navigateTo(Screen.PlaylistDetail(it.id)) },
                                            onCreatePlaylist = viewModel::createPlaylist,
                                        )
                                        is Screen.AlbumDetail -> AlbumDetailScreen(
                                            album = uiState.albumDetail,
                                            isLoading = uiState.isLoadingDetail,
                                            isLiked = ::isLiked,
                                            onBack = viewModel::navigateBack,
                                            onSongClick = ::playSong,
                                            onToggleLike = viewModel::toggleLike,
                                        )
                                        is Screen.ArtistDetail -> ArtistDetailScreen(
                                            artist = uiState.artistDetail,
                                            isLoading = uiState.isLoadingDetail,
                                            isLiked = ::isLiked,
                                            onBack = viewModel::navigateBack,
                                            onSongClick = ::playSong,
                                            onToggleLike = viewModel::toggleLike,
                                        )
                                        is Screen.PlaylistDetail -> PlaylistDetailScreen(
                                            playlist = uiState.playlistDetail,
                                            isLoading = uiState.isLoadingDetail,
                                            isLiked = ::isLiked,
                                            onBack = viewModel::navigateBack,
                                            onSongClick = ::playSong,
                                            onToggleLike = viewModel::toggleLike,
                                        )
                                    }
                                }

                                if (playback.isQueueVisible) {
                                    QueuePanel(
                                        state = playback,
                                        onClose = { viewModel.setQueueVisible(false) },
                                        onSelect = viewModel::playFromQueue,
                                    )
                                }
                            }
                        }

                        if (playback.currentSong != null) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                PlayerBar(
                                    state = playback,
                                    onTogglePlayPause = viewModel::togglePlayPause,
                                    onNext = viewModel::playNextInQueue,
                                    onPrevious = viewModel::playPreviousInQueue,
                                    onSeek = viewModel::seekTo,
                                    onExpand = { viewModel.setExpanded(true) },
                                    onToggleLike = viewModel::toggleLikeCurrent,
                                    onToggleQueue = { viewModel.setQueueVisible(!playback.isQueueVisible) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Collapses detail screens back to whichever sidebar tab they were pushed from. */
private fun tabOf(screen: Screen): Screen = when (screen) {
    Screen.Home, Screen.Search, Screen.Library -> screen
    else -> Screen.Home
}

fun main() {
    AppGraph.init()
    val viewModel = AppViewModel(
        musicSource = AppGraph.musicSource,
        library = AppGraph.libraryRepository,
        player = AppGraph.playerController,
    )

    application {
        val windowState = rememberWindowState()
        Window(
            onCloseRequest = {
                viewModel.dispose()
                exitApplication()
            },
            state = windowState,
            title = "MusicSM Desktop",
        ) {
            App(viewModel)
        }
    }
}

