package com.example.musicsmd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.audio.EqualizerScreen
import com.example.musicsmd.desktop.MediaKeyController
import com.example.musicsmd.desktop.MusicSmTray
import com.example.musicsmd.desktop.handleMusicShortcut
import com.example.musicsmd.downloads.DownloadsScreen
import com.example.musicsmd.home.HomeScreen
import com.example.musicsmd.importer.ImportScreen
import com.example.musicsmd.library.LibraryScreen
import com.example.musicsmd.local.LocalMusicScreen
import com.example.musicsmd.lyrics.LyricsController
import com.example.musicsmd.nav.Screen
import com.example.musicsmd.nav.TopLevelScreens
import com.example.musicsmd.player.AppViewModel
import com.example.musicsmd.player.LocalSongActions
import com.example.musicsmd.player.NowPlayingScreen
import com.example.musicsmd.player.PlayerBar
import com.example.musicsmd.player.QueuePanel
import com.example.musicsmd.player.SongActions
import com.example.musicsmd.search.SearchScreen
import com.example.musicsmd.settings.SettingsScreen
import com.example.musicsmd.share.PlaylistShareDialog
import com.example.musicsmd.share.SharedPlaylistScreen
import com.example.musicsmd.share.copyTextToClipboard
import com.example.musicsmd.stats.StatsScreen
import com.example.musicsmd.ui.SideDock
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.LocalBottomBarPadding
import com.example.musicsmd.ui.components.LocalHazeState
import com.example.musicsmd.ui.components.LocalLiquidBackdrop
import com.example.musicsmd.ui.components.glassBackdrop
import com.example.musicsmd.ui.components.rememberHazeState
import com.example.musicsmd.ui.detail.AlbumDetailScreen
import com.example.musicsmd.ui.detail.ArtistDetailScreen
import com.example.musicsmd.ui.detail.PlaylistDetailScreen
import com.example.musicsmd.ui.theme.AppBackground
import com.example.musicsmd.ui.theme.MusicSMTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How far the fade reaches above the mini player. Long enough that the transition happens over
 * several rows of a list rather than across one, so it reads as depth, not as a band.
 */
private val SCRIM_OVERHANG = 56.dp

/** Mobile's landscape cap is 520 dp; desktop's pill carries more buttons, so it gets more room. */
private val MINI_PLAYER_MAX_WIDTH = 820.dp

@Composable
fun App(viewModel: AppViewModel, lyricsController: LyricsController) {
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val lyricsState by lyricsController.state.collectAsState()
    val lyricsVisible by lyricsController.visible.collectAsState()
    val lyricsOffsetMs by lyricsController.offsetMs.collectAsState()
    val settings by AppGraph.settingsStore.settings.collectAsState()
    val downloadedSongs by AppGraph.downloadManager.downloads().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var sharePlaylist by remember { mutableStateOf<Playlist?>(null) }

    val likedIds = uiState.likedSongs.map { it.id }.toSet()
    val downloadedIds = downloadedSongs.map { it.id }.toSet()
    fun isLiked(songId: String) = songId in likedIds || (playback.currentSong?.id == songId && playback.isLiked)
    fun playSong(song: Song, queue: List<Song>) = viewModel.play(song, queue)

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(1_800)
            toastMessage = null
        }
    }

    val songActions = SongActions(
        playNext = viewModel::playNext,
        addToQueue = viewModel::addToQueue,
        startRadio = { viewModel.startRadio(it) },
        isDownloaded = { songId -> songId in downloadedIds },
        download = { song ->
            coroutineScope.launch {
                AppGraph.downloadManager.download(song)
                toastMessage = "Downloading ${song.title}"
            }
        },
        removeDownload = { song ->
            coroutineScope.launch {
                AppGraph.downloadManager.delete(song.id)
                toastMessage = "Removed download"
            }
        },
        share = { song ->
            copyTextToClipboard("https://music.youtube.com/watch?v=${song.id}")
            toastMessage = "Copied song link"
        },
        playlists = uiState.playlists,
        createPlaylistWith = viewModel::createPlaylistWith,
        goToAlbum = viewModel::goToAlbum,
        addToPlaylist = { song, playlistId -> viewModel.addToPlaylist(playlistId, song) },
        goToArtist = { song ->
            val name = song.artist.split(",", "&").first().trim()
            if (name.isNotEmpty()) viewModel.navigateTo(Screen.ArtistDetail(name))
        },
    )

    MusicSMTheme {
        val hazeState = rememberHazeState()
        val liquidBackdrop = rememberLayerBackdrop()
        val density = LocalDensity.current
        // Measured, so the content inset tracks the dock's and mini player's real size.
        var dockSpace by remember { mutableStateOf(0.dp) }
        var barHeight by remember { mutableStateOf(0.dp) }
        val hasSong = playback.currentSong != null
        val bottomInset = if (hasSong) barHeight else 0.dp

        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalSongActions provides songActions,
            LocalBottomBarPadding provides bottomInset,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
            Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
                // Content fills the whole window and registers as both glass sources. The background
                // is painted *inside* the source node — after glassBackdrop, so hazeSource records
                // it — because a source with no opaque fill of its own lets the glass sample
                // transparency and drop its blur for a frame. Ordering matters: hazeSource only
                // captures what is drawn inner to it.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(liquidBackdrop)
                        .glassBackdrop(hazeState)
                        .background(AppBackground)
                        // Inside the recorded layer, so the strip under the dock is still painted.
                        .padding(start = dockSpace),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            when (val screen = uiState.screen) {
                                Screen.Home -> HomeScreen(
                                    state = uiState,
                                    isLiked = ::isLiked,
                                    onSongClick = ::playSong,
                                    onToggleLike = viewModel::toggleLike,
                                    onAlbumClick = { viewModel.navigateTo(Screen.AlbumDetail(it.id)) },
                                    onArtistClick = { viewModel.navigateTo(Screen.ArtistDetail(it.id)) },
                                    onPlaylistClick = { viewModel.navigateTo(Screen.PlaylistDetail(it.id)) },
                                    onLoadMoreHome = viewModel::loadMoreHome,
                                    onRetryHome = { viewModel.loadHome() },
                                )
                                Screen.Search -> SearchScreen(
                                    state = uiState,
                                    settings = settings,
                                    historyStore = AppGraph.searchHistoryStore,
                                    isLiked = ::isLiked,
                                    onQueryChange = viewModel::onQueryChange,
                                    onSearch = viewModel::search,
                                    onSongClick = ::playSong,
                                    onToggleLike = viewModel::toggleLike,
                                    onAlbumClick = { viewModel.navigateTo(Screen.AlbumDetail(it.id)) },
                                    onArtistClick = { viewModel.navigateTo(Screen.ArtistDetail(it.id)) },
                                    onPlaylistClick = { viewModel.navigateTo(Screen.PlaylistDetail(it.id)) },
                                )
                                Screen.Library -> LibraryScreen(
                                    likedSongs = uiState.likedSongs,
                                    playlists = uiState.playlists,
                                    isLiked = ::isLiked,
                                    onSongClick = ::playSong,
                                    onToggleLike = viewModel::toggleLike,
                                    onPlaylistClick = { viewModel.navigateTo(Screen.PlaylistDetail(it.id)) },
                                    onCreatePlaylist = viewModel::createPlaylist,
                                    onImportClick = { viewModel.navigateTo(Screen.Import) },
                                    onOpenSharedPlaylist = { viewModel.navigateTo(Screen.SharedPlaylist(it)) },
                                    onSharePlaylist = { sharePlaylist = it },
                                )
                                is Screen.AlbumDetail -> AlbumDetailScreen(
                                    album = uiState.albumDetail,
                                    isLoading = uiState.isLoadingDetail,
                                    isLiked = ::isLiked,
                                    onBack = viewModel::navigateBack,
                                    onSongClick = ::playSong,
                                    onToggleLike = viewModel::toggleLike,
                                    onDownloadAll = { songs ->
                                        coroutineScope.launch {
                                            AppGraph.downloadManager.downloadAll(songs)
                                            toastMessage = "Downloading ${songs.size} songs"
                                        }
                                    },
                                )
                                is Screen.ArtistDetail -> ArtistDetailScreen(
                                    artist = uiState.artistDetail,
                                    isLoading = uiState.isLoadingDetail,
                                    isLiked = ::isLiked,
                                    onBack = viewModel::navigateBack,
                                    onSongClick = ::playSong,
                                    onToggleLike = viewModel::toggleLike,
                                    onDownloadAll = { songs ->
                                        coroutineScope.launch {
                                            AppGraph.downloadManager.downloadAll(songs)
                                            toastMessage = "Downloading ${songs.size} songs"
                                        }
                                    },
                                )
                                is Screen.PlaylistDetail -> PlaylistDetailScreen(
                                    playlist = uiState.playlistDetail,
                                    isLoading = uiState.isLoadingDetail,
                                    isLiked = ::isLiked,
                                    onBack = viewModel::navigateBack,
                                    onSongClick = ::playSong,
                                    onToggleLike = viewModel::toggleLike,
                                    onDownloadAll = { songs ->
                                        coroutineScope.launch {
                                            AppGraph.downloadManager.downloadAll(songs)
                                            toastMessage = "Downloading ${songs.size} songs"
                                        }
                                    },
                                    onSharePlaylist = { sharePlaylist = it },
                                )
                                Screen.Downloads -> DownloadsScreen(
                                    manager = AppGraph.downloadManager,
                                    settingsStore = AppGraph.settingsStore,
                                    onSongClick = ::playSong,
                                )
                                Screen.LocalMusic -> LocalMusicScreen(
                                    manager = AppGraph.localMusicManager,
                                    settingsStore = AppGraph.settingsStore,
                                    onSongClick = ::playSong,
                                )
                                Screen.Stats -> StatsScreen(
                                    repository = AppGraph.statsRepository,
                                    isLiked = ::isLiked,
                                    onToggleLike = viewModel::toggleLike,
                                    onPlaySongs = { songs, index -> songs.getOrNull(index)?.let { viewModel.play(it, songs) } },
                                    onArtistSearch = { artist ->
                                        viewModel.selectTab(Screen.Search)
                                        viewModel.onQueryChange(artist)
                                        viewModel.search(settings.searchVideos)
                                    },
                                )
                                Screen.Settings -> SettingsScreen(
                                    settings = settings,
                                    onUpdate = AppGraph.settingsStore::update,
                                    onOpenEqualizer = { viewModel.navigateTo(Screen.Equalizer) },
                                    onClearListeningHistory = { AppGraph.statsRepository.clear() },
                                    onClearSearchHistory = AppGraph.searchHistoryStore::clear,
                                )
                                Screen.Equalizer -> EqualizerScreen(
                                    settingsStore = AppGraph.settingsStore,
                                    player = AppGraph.playerController,
                                    onBack = viewModel::navigateBack,
                                )
                                Screen.Import -> ImportScreen(
                                    repository = AppGraph.playlistImportRepository,
                                    libraryRepository = AppGraph.libraryRepository,
                                    onBack = viewModel::navigateBack,
                                    onOpenPlaylist = { viewModel.navigateTo(Screen.PlaylistDetail(it.toString())) },
                                )
                                is Screen.SharedPlaylist -> SharedPlaylistScreen(
                                    payload = screen.payload,
                                    libraryRepository = AppGraph.libraryRepository,
                                    onBack = viewModel::navigateBack,
                                    onPlay = ::playSong,
                                    onOpenPlaylist = { viewModel.navigateTo(Screen.PlaylistDetail(it.toString())) },
                                )
                            }
                        }

                        if (playback.isQueueVisible) {
                            QueuePanel(
                                state = playback,
                                onClose = { viewModel.setQueueVisible(false) },
                                onSelect = viewModel::playFromQueue,
                                onRemove = viewModel::removeFromQueue,
                                onMove = viewModel::moveInQueue,
                                onClearUpcoming = viewModel::clearUpcoming,
                                modifier = Modifier.padding(bottom = bottomInset),
                            )
                        }
                    }
                }

                // The content does not simply end at the glass: it fades into the background over
                // the strip above the mini player. A hard cut at the blur's edge announces where
                // the panel stops, which is the one thing a pane of glass should not do.
                if (hasSong) {
                    val scrimColor = AppBackground
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = dockSpace)
                            .height(barHeight + SCRIM_OVERHANG)
                            .drawWithCache {
                                val fade = Brush.verticalGradient(
                                    0.00f to Color.Transparent,
                                    0.35f to scrimColor.copy(alpha = 0.15f),
                                    0.60f to scrimColor.copy(alpha = 0.40f),
                                    0.85f to scrimColor.copy(alpha = 0.70f),
                                    1.00f to scrimColor,
                                )
                                onDrawBehind { drawRect(fade) }
                            },
                    )
                }

                // The chrome is outside the recorded content, so its Liquid Glass can refract it.
                CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
                    if (hasSong) {
                        // Tabs live in the dock, so only the mini player floats at the bottom,
                        // centred over the content rather than the whole window.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(start = dockSpace)
                                .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            PlayerBar(
                                state = playback,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::playNextInQueue,
                                onPrevious = viewModel::playPreviousInQueue,
                                onExpand = { viewModel.setExpanded(true) },
                                onToggleLike = viewModel::toggleLikeCurrent,
                                onToggleQueue = { viewModel.setQueueVisible(!playback.isQueueVisible) },
                                onToggleShuffle = viewModel::toggleShuffle,
                                onCycleRepeat = viewModel::cycleRepeat,
                                onShowLyrics = {
                                    lyricsController.setVisible(true)
                                    viewModel.setExpanded(true)
                                },
                                modifier = Modifier
                                    .widthIn(max = MINI_PLAYER_MAX_WIDTH)
                                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .onSizeChanged { dockSpace = with(density) { it.width.toDp() } }
                            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SideDock(
                            current = tabOf(uiState.screen),
                            onSelect = { viewModel.selectTab(it) },
                        )
                    }
                }

                toastMessage?.let { message ->
                    GlassPanel(modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)) {
                        Text(message, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                }

                sharePlaylist?.let { playlist ->
                    PlaylistShareDialog(playlist = playlist, onDismiss = { sharePlaylist = null })
                }

                // Full-screen player — composed over everything, so the screen behind keeps its
                // state (scroll position, search results) while the player is open.
                if (playback.isExpanded && hasSong) {
                    NowPlayingScreen(
                        state = playback,
                        onCollapse = { viewModel.setExpanded(false) },
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::playNextInQueue,
                        onPrevious = viewModel::playPreviousInQueue,
                        onSeek = viewModel::seekTo,
                        onToggleLike = viewModel::toggleLikeCurrent,
                        onVolumeChange = viewModel::setVolume,
                        onToggleQueue = {
                            viewModel.setExpanded(false)
                            viewModel.setQueueVisible(true)
                        },
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeat,
                        onPlaybackSpeedChange = viewModel::setPlaybackSpeed,
                        onStartSleepTimer = viewModel::startSleepTimer,
                        onStartSleepTimerAtEndOfTrack = viewModel::startSleepTimerAtEndOfTrack,
                        onCancelSleepTimer = viewModel::cancelSleepTimer,
                        onRefreshAudioOutputs = viewModel::refreshAudioOutputs,
                        onSelectAudioOutput = viewModel::selectAudioOutput,
                        onOpenEqualizer = {
                            viewModel.setExpanded(false)
                            viewModel.navigateTo(Screen.Equalizer)
                        },
                        showLyrics = lyricsVisible,
                        lyricsState = lyricsState,
                        lyricsOffsetMs = lyricsOffsetMs,
                        onToggleLyrics = lyricsController::toggleVisible,
                        onCloseLyrics = { lyricsController.setVisible(false) },
                        onAdjustLyricsOffset = lyricsController::adjustOffset,
                        onSetLyricsOffset = lyricsController::setOffset,
                        onResetLyricsOffset = lyricsController::resetOffset,
                    )
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
        statsRepository = AppGraph.statsRepository,
        player = AppGraph.playerController,
        settingsStore = AppGraph.settingsStore,
        offlineSource = AppGraph.offlineSource,
        playbackListeners = AppGraph.playbackListeners,
    )
    val lyricsController = LyricsController(
        playback = viewModel.playback,
        repository = AppGraph.lyricsRepository,
        settingsStore = AppGraph.settingsStore,
    )
    val mediaKeys = MediaKeyController(AppGraph.settingsStore, viewModel).also { it.start() }

    application {
        val playback by viewModel.playback.collectAsState()
        val settings by AppGraph.settingsStore.settings.collectAsState()
        var windowVisible by remember { mutableStateOf(true) }
        var disposed by remember { mutableStateOf(false) }
        fun quit() {
            if (!disposed) {
                disposed = true
                mediaKeys.dispose()
                lyricsController.dispose()
                viewModel.dispose()
            }
            exitApplication()
        }
        MusicSmTray(
            playback = playback,
            onShow = { windowVisible = true },
            onPlayPause = viewModel::togglePlayPause,
            onNext = viewModel::playNextInQueue,
            onPrevious = viewModel::playPreviousInQueue,
            onQuit = ::quit,
        )
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
                if (settings.minimizeToTray) {
                    windowVisible = false
                } else {
                    quit()
                }
            },
            visible = windowVisible,
            state = windowState,
            title = "MusicSM Desktop",
            onKeyEvent = { event ->
                handleMusicShortcut(event, viewModel) {
                    if (viewModel.playback.value.isExpanded) {
                        viewModel.setExpanded(false)
                    } else {
                        viewModel.navigateBack()
                    }
                }
            },
        ) {
            App(viewModel, lyricsController)
        }
    }
}
