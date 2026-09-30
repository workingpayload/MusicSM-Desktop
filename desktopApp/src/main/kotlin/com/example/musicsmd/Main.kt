package com.example.musicsmd

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.Song
import com.example.musicsmd.audio.EqualizerScreen
import com.example.musicsmd.desktop.AppIcon
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
import com.example.musicsmd.player.AppUiState
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
import com.example.musicsmd.ui.components.HeaderWash
import com.example.musicsmd.ui.components.LocalBottomBarPadding
import com.example.musicsmd.ui.components.LocalHazeState
import com.example.musicsmd.ui.components.LocalLiquidBackdrop
import com.example.musicsmd.ui.components.glassBackdrop
import com.example.musicsmd.ui.components.rememberHazeState
import com.example.musicsmd.ui.detail.AlbumDetailScreen
import com.example.musicsmd.ui.detail.ArtistDetailScreen
import com.example.musicsmd.ui.detail.PlaylistDetailScreen
import com.example.musicsmd.ui.theme.AmoledPalette
import com.example.musicsmd.ui.theme.AppBackground
import com.example.musicsmd.ui.theme.ArtworkColors
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.DarkPalette
import com.example.musicsmd.ui.theme.MusicSMTheme
import com.example.musicsmd.ui.theme.asThemeAccent
import com.example.musicsmd.desktop.WindowChrome
import com.example.musicsmd.ui.components.LocalDockInset
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

/** Mobile's NavHost push/pop duration. */
private const val NAV_TRANSITION_MS = 300

/** The single-image window icon Compose sets before the full size set replaces it. */
private const val WINDOW_ICON_FALLBACK_SIZE = 64

/** Mobile's player sheet spring. */
private val SheetSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** One step of navigation; [state] is what that screen was showing, frozen while it animates out. */
private data class NavEntry(val screen: Screen, val backStack: List<Screen>, val state: AppUiState)

/**
 * Mobile's NavHost transitions: a pushed screen slides in from the right, going back slides the
 * other way. Switching tabs from the dock is not a push, so it cross-fades instead.
 */
private fun AnimatedContentTransitionScope<NavEntry>.navTransition(from: NavEntry, to: NavEntry): ContentTransform {
    val push = to.backStack.size > from.backStack.size && to.backStack.lastOrNull() == from.screen
    val pop = to.backStack.size < from.backStack.size && from.backStack.lastOrNull() == to.screen
    val slide = tween<IntOffset>(NAV_TRANSITION_MS)
    val transform = when {
        push -> slideInHorizontally(slide) { it } togetherWith slideOutHorizontally(slide) { -it }
        pop -> slideInHorizontally(slide) { -it } togetherWith slideOutHorizontally(slide) { it }
        else -> fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120))
    }
    // Unclipped, so tinted screens still paint under the glass dock (see LocalDockInset).
    return transform using SizeTransform(clip = false)
}

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
    // Per-screen saved state (scroll positions), so going back lands where you left, as on mobile.
    val saveableStates = rememberSaveableStateHolder()
    val savedScreenKeys = remember { mutableSetOf<String>() }
    LaunchedEffect(uiState.screen, uiState.backStack) {
        savedScreenKeys += uiState.screen.toString()
        // Tabs keep theirs; a popped detail page starts from the top if it is opened again.
        val live = (TopLevelScreens + uiState.backStack + uiState.screen).mapTo(HashSet()) { it.toString() }
        savedScreenKeys.filter { it !in live }.forEach { key ->
            saveableStates.removeState(key)
            savedScreenKeys -= key
        }
    }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var sharePlaylist by remember { mutableStateOf<Playlist?>(null) }
    // Kept after leaving Search, so going back shows its header colour at once.
    var searchTint by remember { mutableStateOf<Color?>(null) }

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

    // Resolved once per track rather than animated through the root, as mobile's AppThemeViewModel
    // does: animating it would recompose the whole app every frame of the transition. The previous
    // accent is kept while the next cover loads, so the theme never flashes back to the default.
    val artworkUrl = playback.currentSong?.artworkUrl
    val themeAccent by produceState<Color?>(initialValue = null, settings.themeFromArtwork, artworkUrl) {
        value = if (settings.themeFromArtwork) ArtworkColors.accentFor(artworkUrl)?.asThemeAccent() else null
    }

    MusicSMTheme(accent = themeAccent, amoled = settings.amoled) {
        val hazeState = rememberHazeState()
        val liquidBackdrop = rememberLayerBackdrop()
        val density = LocalDensity.current
        // Measured, so the content inset tracks the dock's and mini player's real size.
        var dockSpace by remember { mutableStateOf(0.dp) }
        var barHeight by remember { mutableStateOf(0.dp) }
        val hasSong = playback.currentSong != null
        val bottomInset = if (hasSong) barHeight else 0.dp

        // 0 = collapsed into the mini player, 1 = Now Playing fully up.
        val sheet = remember { Animatable(0f) }
        val sheetOpen = playback.isExpanded && hasSong
        LaunchedEffect(sheetOpen) { sheet.animateTo(if (sheetOpen) 1f else 0f, SheetSpring) }
        // Derived, so the root recomposes when the sheet appears or goes, not on every frame.
        val sheetShown by remember { derivedStateOf { sheet.value > 0.001f } }

        // The header gradient: Search's top-result colour on Search, the accent on every other
        // tab, faded out on detail pages, which wash in their own cover's colour.
        val washColor by animateColorAsState(
            targetValue = searchTint.takeIf { uiState.screen == Screen.Search } ?: Coral,
            animationSpec = tween(WASH_COLOR_MS),
            label = "washColor",
        )
        val washOpacity by animateFloatAsState(
            targetValue = if (uiState.screen.hasHeaderWash()) 1f else 0f,
            animationSpec = tween(NAV_TRANSITION_MS),
            label = "washOpacity",
        )

        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalSongActions provides songActions,
            LocalBottomBarPadding provides bottomInset,
            LocalDockInset provides dockSpace,
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
                        .background(AppBackground),
                ) {
                    // Behind every page and outside their transitions, so it holds still while
                    // tabs cross-fade over it (see HeaderWash).
                    HeaderWash(color = { washColor }, opacity = { washOpacity })
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = NavEntry(uiState.screen, uiState.backStack, uiState),
                                contentKey = { it.screen },
                                transitionSpec = { navTransition(initialState, targetState) },
                                label = "screen",
                            ) { entry ->
                                // The outgoing screen keeps the state it had, so it doesn't flash the
                                // incoming screen's data (or its loading spinner) as it slides away.
                                val navState = entry.state
                                // The dock inset is applied inside the animated screen, not around
                                // it: a fading screen is drawn into a layer clipped to its own box,
                                // and a tint that runs under the dock (see LocalDockInset) must lie
                                // inside that box or it is cut off until the fade ends.
                                Box(Modifier.fillMaxSize().padding(start = dockSpace)) {
                                saveableStates.SaveableStateProvider(entry.screen.toString()) {
                                    when (val screen = entry.screen) {
                                        Screen.Home -> HomeScreen(
                                            state = navState,
                                            layout = settings.homeLayout,
                                            onLayoutChange = { layout ->
                                                AppGraph.settingsStore.update { it.copy(homeLayout = layout) }
                                            },
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
                                            state = navState,
                                            settings = settings,
                                            historyStore = AppGraph.searchHistoryStore,
                                            browseGenres = remember { AppGraph.musicRepository.browseTiles() },
                                            onHeaderTint = { searchTint = it },
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
                                            likedSongs = navState.likedSongs,
                                            playlists = navState.playlists,
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
                                            album = navState.albumDetail,
                                            isLoading = navState.isLoadingDetail,
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
                                            artist = navState.artistDetail,
                                            isLoading = navState.isLoadingDetail,
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
                                            playlist = navState.playlistDetail,
                                            isLoading = navState.isLoadingDetail,
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
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = playback.isQueueVisible,
                            enter = slideInHorizontally(tween(NAV_TRANSITION_MS)) { it } + fadeIn(tween(NAV_TRANSITION_MS)),
                            exit = slideOutHorizontally(tween(NAV_TRANSITION_MS)) { it } + fadeOut(tween(NAV_TRANSITION_MS)),
                        ) {
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
                                .onSizeChanged { barHeight = with(density) { it.height.toDp() } }
                                // Fades out as the player sheet rises over it, as on mobile.
                                .graphicsLayer { alpha = (1f - sheet.value * 1.5f).coerceIn(0f, 1f) },
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
                            // A pushed page keeps the tab it was opened from, as on mobile.
                            current = tabOf(uiState.backStack.firstOrNull() ?: uiState.screen),
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
                // state (scroll position, search results) while the player is open. Slides up from
                // the mini player and back down, as mobile's sheet does; the offset is read in the
                // layer, so the animation never recomposes the player.
                if (hasSong && (sheetOpen || sheetShown)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Clamped: the spring's overshoot must not lift the sheet off the bottom edge.
                                translationY = ((1f - sheet.value) * size.height).coerceAtLeast(0f)
                            }
                            // Taps on the player's empty areas must not fall through to the screen behind.
                            .pointerInput(Unit) { detectTapGestures {} },
                    ) {
                        NowPlayingScreen(
                            state = playback,
                            position = viewModel.position,
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
}
/**
 * Pages under the header gradient: Search, every other tab, and the pages pushed from them. Album,
 * artist and playlist pages are left out; they wash in their cover's colour instead.
 */
private fun Screen.hasHeaderWash(): Boolean = when (this) {
    is Screen.AlbumDetail, is Screen.ArtistDetail, is Screen.PlaylistDetail -> false
    else -> true
}

/** How long the header gradient takes to move to a new colour — as long as a cover tint's ease. */
private const val WASH_COLOR_MS = 700

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
        streams = AppGraph.musicRepository,
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
            icon = remember { AppIcon.painter(WINDOW_ICON_FALLBACK_SIZE) },
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
            // Title bar and border in the app's background colour, re-applied when AMOLED toggles.
            val frameColor = if (settings.amoled) AmoledPalette.background else DarkPalette.background
            LaunchedEffect(frameColor, windowVisible) {
                WindowChrome.apply(window, background = frameColor, text = DarkPalette.onSurface)
            }
            // Every icon size at once, so Windows picks a sharp one for the title bar, taskbar and
            // Alt+Tab; the single `icon` above is only the fallback Compose sets first.
            LaunchedEffect(Unit) {
                AppIcon.windowImages.takeIf { it.isNotEmpty() }?.let { window.iconImages = it }
            }
            App(viewModel, lyricsController)
        }
    }
}
