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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.audio.EqualizerScreen
import com.example.musicsmd.downloads.DownloadsScreen
import com.example.musicsmd.home.HomeScreen
import com.example.musicsmd.importer.ImportScreen
import com.example.musicsmd.library.LibraryScreen
import com.example.musicsmd.local.LocalMusicScreen
import com.example.musicsmd.nav.Screen
import com.example.musicsmd.nav.TopLevelScreens
import com.example.musicsmd.player.AppViewModel
import com.example.musicsmd.player.LocalSongActions
import com.example.musicsmd.player.NowPlayingScreen
import com.example.musicsmd.player.PlayerBar
import com.example.musicsmd.player.QueuePanel
import com.example.musicsmd.player.SongActions
import com.example.musicsmd.settings.SettingsScreen
import com.example.musicsmd.share.SharedPlaylistScreen
import com.example.musicsmd.stats.StatsScreen
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

    val songActions = SongActions(
        playNext = viewModel::playNext,
        addToQueue = viewModel::addToQueue,
        startRadio = { viewModel.startRadio(it) },
        playlists = uiState.playlists,
        addToPlaylist = { song, playlistId -> viewModel.addToPlaylist(playlistId, song) },
        goToArtist = { song ->
            val name = song.artist.split(",", "&").first().trim()
            if (name.isNotEmpty()) viewModel.navigateTo(Screen.ArtistDetail(name))
        },
    )

    MusicSMTheme {
        val hazeState = rememberHazeState()
        CompositionLocalProvider(LocalHazeState provides hazeState, LocalSongActions provides songActions) {
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
                                            onPlaylistClick = { viewModel.navigateTo(Screen.PlaylistDetail(it.id)) },
                                            onLoadMoreHome = viewModel::loadMoreHome,
                                            onRetryHome = { viewModel.loadHome() },
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
                                        Screen.Downloads -> DownloadsScreen()
                                        Screen.LocalMusic -> LocalMusicScreen()
                                        Screen.Stats -> StatsScreen()
                                        Screen.Settings -> SettingsScreen()
                                        Screen.Equalizer -> EqualizerScreen()
                                        Screen.Import -> ImportScreen()
                                        is Screen.SharedPlaylist -> SharedPlaylistScreen(payload = screen.payload)
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

/** Collapses pushed screens back to whichever sidebar tab they belong to. */
private fun tabOf(screen: Screen): Screen = when (screen) {
    in TopLevelScreens -> screen
    Screen.Equalizer -> Screen.Settings
    Screen.Import, is Screen.SharedPlaylist -> Screen.Library
    else -> Screen.Home
}

fun main() {
    AppGraph.init()
    val viewModel = AppViewModel(
        musicSource = AppGraph.musicSource,
        library = AppGraph.libraryRepository,
        player = AppGraph.playerController,
        offlineSource = AppGraph.offlineSource,
        playbackListeners = AppGraph.playbackListeners,
    )

    application {
        // Fit within the usable desktop area (excludes the taskbar) so the player bar is never hidden.
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(
                minOf(1280, (screen.width * 0.9).toInt()).dp,
                minOf(820, (screen.height * 0.9).toInt()).dp,
            ),
        )
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

