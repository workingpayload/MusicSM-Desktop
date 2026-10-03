package com.example.musicsmd.player

import com.example.musicsm.data.source.youtube.signin.YouTubeAccountLibrary
import com.example.musicsm.data.source.youtube.signin.YouTubeSignInRequiredException
import com.example.musicsmd.youtube.BrowserUnavailableException
import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.HomeItem
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.MusicRepository
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsm.domain.source.MusicSource
import com.example.musicsmd.home.DesktopHomeFeedBuilder
import com.example.musicsmd.nav.Screen
import com.example.musicsmd.playback.AudioKeepAlive
import com.example.musicsmd.playback.AudioOutputDeviceInfo
import com.example.musicsmd.playback.MixController
import com.example.musicsmd.playback.OfflineSource
import com.example.musicsmd.playback.PlaybackListener
import com.example.musicsmd.playback.PlayerController
import com.example.musicsmd.playback.QueuePersistence
import com.example.musicsmd.playback.SleepTimerManager
import com.example.musicsmd.playback.SleepTimerState
import com.example.musicsmd.playback.mix.VlcSnippetDecoder
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsmd.settings.RepeatMode
import com.example.musicsmd.settings.SettingsStore
import com.example.musicsmd.stats.SkipSignals
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Ephemeral playback UI state, mirrors the shape of the mobile app's `PlayerState`. */
data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    /** A track switch is in flight: the stream is being looked up or libVLC hasn't produced audio yet. */
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    val volume: Int = 100,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1f,
    val isLiked: Boolean = false,
    val isExpanded: Boolean = false,
    val isQueueVisible: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val audioOutputDevices: List<AudioOutputDeviceInfo> = emptyList(),
    val audioOutputDevice: String? = null,
)

data class AppUiState(
    val screen: Screen = Screen.Home,
    val backStack: List<Screen> = emptyList(),
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: SearchResults = SearchResults(),
    /** The query [searchResults] belong to; while typing, [query] runs ahead of it. */
    val searchedQuery: String = "",
    val homeFeed: HomeFeed = HomeFeed(),
    val isLoadingHome: Boolean = true,
    val isLoadingMoreHome: Boolean = false,
    /** A manual refresh is rebuilding Home; the current shelves stay up until it's done. */
    val isRefreshingHome: Boolean = false,
    /** Bumped whenever Home gets a whole new feed, so it can scroll back to the top. */
    val homeGeneration: Int = 0,
    val likedSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val albumDetail: Album? = null,
    val artistDetail: Artist? = null,
    val playlistDetail: Playlist? = null,
    val isLoadingDetail: Boolean = false,
    val error: String? = null,
    /** Playback stopped because YouTube wants a sign-in on this network. */
    val youTubeSignInNeeded: Boolean = false,
    /** YouTube sign-in or signed-in playback needs a Chromium-family browser, and none is installed. */
    val browserNeeded: Boolean = false,
    /** The signed-in YouTube account's playlists ("Liked Music" first), for Library. */
    val youTubePlaylists: List<Playlist> = emptyList(),
)

/**
 * Plain (non-androidx) view-model. Owns the [MusicSource]/[LibraryRepository] calls, navigation,
 * and the queue, and drives [PlayerController] — the desktop counterpart of the mobile app's
 * `PlayerViewModel` + `MediaControllerManager` + per-feature view-models combined.
 */
class AppViewModel(
    private val musicSource: MusicSource,
    /** Stream lookups go through the repository so resolved URLs are cached, as on mobile. */
    private val streams: MusicRepository,
    private val library: LibraryRepository,
    private val statsRepository: StatsRepository,
    private val player: PlayerController,
    private val settingsStore: SettingsStore,
    private val offlineSource: OfflineSource = OfflineSource { null },
    private val playbackListeners: List<PlaybackListener> = emptyList(),
    private val queuePersistence: QueuePersistence = QueuePersistence(),
    /** Silence that keeps a Bluetooth output awake across track changes; null in tests. */
    private val audioKeepAlive: AudioKeepAlive? = null,
    /** Recent skips, which Home's recommendations steer away from. */
    skipSignals: () -> SkipSignals = { SkipSignals() },
    /** The signed-in YouTube account's home, history and playlists; null in tests. */
    private val youTubeLibrary: YouTubeAccountLibrary? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val homeFeedBuilder = DesktopHomeFeedBuilder(streams, library, statsRepository, youTubeLibrary, skipSignals)

    /** Bumped by each manual refresh, so Home rotates to different seeds and picks. */
    private var homeRefreshCount = 0

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    // Kept apart from [playback], as mobile's PlayerViewModel does: it ticks several times a second,
    // and only the seek bar and lyrics need it, not every screen that reads the playback state.
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val sleepTimerManager = SleepTimerManager(
        scope = scope,
        fadeOutEnabled = { settingsStore.current.sleepTimerFadeOut },
        currentVolume = { _playback.value.volume },
        setVolume = { setVolume(it) },
        pausePlayback = { pause() },
    )

    /** Crossfade / Mix: blends into the next track on the player's second deck. */
    private val mix = MixController(
        player = player,
        decoder = VlcSnippetDecoder(player.factory),
        host = object : MixController.Host {
            override val currentSongId: String? get() = loadedSongId
            override fun upcoming(): MixController.Upcoming? = upcomingForMix()
            override suspend fun resolve(song: Song): PlayableStream? = withContext(Dispatchers.IO) {
                runCatching { offlineSource.localStream(song.id) ?: streams.resolveStream(song.id) }.getOrNull()
            }
            override fun onHandoff(next: MixController.Upcoming, startMs: Long) = onMixHandoff(next, startMs)
        },
        parentScope = scope,
    )

    // Time actually played of the current song; seeks don't count, so stats reflect listening.
    private var listenedMs = 0L
    private var lastPositionMs = 0L

    // The position of the latest seek, until libVLC reports it (see isStaleAfterSeek).
    @Volatile
    private var seekTargetMs: Long? = null

    @Volatile
    private var seekAtNanos = 0L
    private var originalQueue: List<Song> = emptyList()

    /** The song libVLC is actually playing; null while switching, so the old track's events are ignored. */
    @Volatile
    private var loadedSongId: String? = null
    private var hasActiveSongForStats = false

    // Every track switch takes a new request number; a lookup that finishes after the user has
    // already moved on sees a newer number and never starts playing over the song they picked.
    private val playLock = Any()
    private var playRequest = 0L
    private var loadJob: Job? = null
    private var retriedSongId: String? = null

    private var searchJob: Job? = null
    private var lastSearchVideos: Boolean? = null

    init {
        val settings = settingsStore.current
        player.setVolume(settings.volume)
        player.setPlaybackSpeed(settings.playbackSpeed)
        settings.audioOutputDevice?.let(player::setOutputDevice)
        player.applyEqualizer(
            enabled = settings.equalizerEnabled,
            preset = settings.equalizerPreset,
            preamp = settings.equalizerPreamp,
            bands = settings.equalizerBands,
        )
        _playback.update {
            it.copy(
                volume = settings.volume,
                shuffle = settings.shuffle,
                repeatMode = settings.repeatMode,
                playbackSpeed = settings.playbackSpeed,
                audioOutputDevices = player.outputDevices(),
                audioOutputDevice = settings.audioOutputDevice,
            )
        }
        player.onPositionChanged = { positionMs, durationMs ->
            if (loadedSongId != null && !isStaleAfterSeek(positionMs)) {
                val delta = positionMs - lastPositionMs
                if (delta in 1..MAX_TICK_MS) listenedMs += delta
                lastPositionMs = positionMs
                _position.value = positionMs
                if (durationMs > 0L && durationMs != _playback.value.durationMs) {
                    _playback.update { it.copy(durationMs = durationMs) }
                }
            }
        }
        player.onEndReached = { loadedSongId?.let(::onTrackEnded) }
        player.onAudioStarted = { if (loadedSongId != null) _playback.update { it.copy(isBuffering = false) } }
        player.onError = { onPlaybackError() }
        restoreQueueIfNeeded()
        loadHome()
        observeLibrary()
        observeSettings()
        observeSleepTimer()
        observeQueuePersistence()
        observeUpcomingForPrefetch()
        observeAudioKeepAlive()
    }

    /** Holds the output open whenever a song is playing or loading, on the device libVLC uses. */
    private fun observeAudioKeepAlive() {
        val keepAlive = audioKeepAlive ?: return
        scope.launch {
            _playback.map { state ->
                val active = state.currentSong != null && (state.isPlaying || state.isBuffering)
                val device = state.audioOutputDevice?.let { id -> state.audioOutputDevices.firstOrNull { it.id == id }?.name }
                active to device
            }.distinctUntilChanged().collect { (active, device) -> keepAlive.update(active, device) }
        }
    }

    private fun observeLibrary() {
        scope.launch {
            library.likedSongs().collect { songs -> _uiState.update { it.copy(likedSongs = songs) } }
        }
        scope.launch {
            library.playlists().collect { playlists -> _uiState.update { it.copy(playlists = playlists) } }
        }
        scope.launch {
            _playback.map { it.currentSong }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .flatMapLatest { song -> song?.let { library.isLiked(it.id) } ?: kotlinx.coroutines.flow.flowOf(false) }
                .collect { liked -> _playback.update { it.copy(isLiked = liked) } }
        }
    }

    private fun observeSettings() {
        scope.launch {
            settingsStore.settings.collect { settings ->
                player.keepEqualizerAttached = settings.crossfadeMs > 0 || settings.mixMode
                mix.crossfadeMs = settings.crossfadeMs
                mix.mixMode = settings.mixMode
                player.setVolume(settings.volume)
                player.setPlaybackSpeed(settings.playbackSpeed)
                settings.audioOutputDevice?.let(player::setOutputDevice)
                player.applyEqualizer(
                    enabled = settings.equalizerEnabled,
                    preset = settings.equalizerPreset,
                    preamp = settings.equalizerPreamp,
                    bands = settings.equalizerBands,
                )
                if (_playback.value.shuffle != settings.shuffle) {
                    applyShuffle(settings.shuffle, persist = false)
                }
                _playback.update {
                    it.copy(
                        volume = settings.volume,
                        repeatMode = settings.repeatMode,
                        playbackSpeed = settings.playbackSpeed,
                        audioOutputDevice = settings.audioOutputDevice,
                    )
                }
                if (!settings.restoreQueue) queuePersistence.clear()
                // Flipping "Videos in search" re-runs the current search, as on mobile.
                if (settings.searchVideos != lastSearchVideos) {
                    val first = lastSearchVideos == null
                    lastSearchVideos = settings.searchVideos
                    if (!first && _uiState.value.searchedQuery.isNotEmpty()) search(settings.searchVideos)
                }
            }
        }
    }

    private fun observeSleepTimer() {
        scope.launch {
            sleepTimerManager.state.collect { timer -> _playback.update { it.copy(sleepTimer = timer) } }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeQueuePersistence() {
        scope.launch {
            combine(_playback, _position) { state, positionMs -> state to positionMs }
                .debounce(1_000L)
                .collect { (state, positionMs) ->
                    if (!settingsStore.current.restoreQueue) return@collect
                    if (state.currentSong == null || state.queue.isEmpty()) return@collect
                    queuePersistence.save(state.queue, originalQueue.ifEmpty { state.queue }, state.queueIndex, positionMs)
                }
        }
    }

    private fun restoreQueueIfNeeded() {
        if (!settingsStore.current.restoreQueue) return
        val saved = queuePersistence.load() ?: return
        originalQueue = saved.originalQueue
        val current = saved.queue.getOrNull(saved.index) ?: return
        _playback.update {
            it.copy(
                currentSong = current,
                queue = saved.queue,
                queueIndex = saved.index,
                durationMs = current.durationMs,
                isPlaying = false,
            )
        }
        _position.value = saved.positionMs
        prefetchStream(current.id)
    }

    /** Mobile parity: warm the next track's stream URL so skipping or auto-advancing starts at once. */
    @OptIn(FlowPreview::class)
    private fun observeUpcomingForPrefetch() {
        scope.launch {
            _playback.map { state -> nextIndexOf(state)?.let { state.queue[it].id } }
                .distinctUntilChanged()
                .debounce(PREFETCH_DEBOUNCE_MS)
                .collect { id -> if (id != null) prefetchStream(id) }
        }
    }

    private fun prefetchStream(songId: String) {
        if (songId.startsWith(LOCAL_ID_PREFIX)) return
        scope.launch(Dispatchers.IO) {
            if (offlineSource.localStream(songId) == null) runCatching { streams.resolveStream(songId) }
        }
    }

    // ---- Navigation ----

    fun navigateTo(screen: Screen) {
        _uiState.update { it.copy(backStack = it.backStack + it.screen, screen = screen) }
        when (screen) {
            is Screen.AlbumDetail -> loadAlbum(screen.albumId)
            is Screen.ArtistDetail -> loadArtist(screen.artistId)
            is Screen.PlaylistDetail -> loadPlaylist(screen.playlistId)
            else -> Unit
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            val previous = state.backStack.lastOrNull() ?: return@update state.copy(screen = Screen.Home)
            state.copy(screen = previous, backStack = state.backStack.dropLast(1))
        }
    }

    fun selectTab(screen: Screen) {
        _uiState.update { it.copy(screen = screen, backStack = emptyList()) }
    }

    // ---- Home / Search ----

    fun loadHome() = scope.launch {
        _uiState.update { it.copy(isLoadingHome = true, error = null) }
        runCatching { homeFeedBuilder.initialFeed(homeRefreshCount) }
            .onSuccess { feed -> _uiState.update { it.copy(homeFeed = feed, isLoadingHome = false, homeGeneration = it.homeGeneration + 1) } }
            .onFailure { e -> _uiState.update { it.copy(isLoadingHome = false, error = e.message) } }
    }

    /**
     * The YouTube account became usable or stopped being so (sign-in, sign-out, the Settings
     * switch): reload its playlists and rebuild Home with or without its shelves.
     */
    fun onYouTubeAccountChanged(refreshHome: Boolean = true) {
        val account = youTubeLibrary ?: return
        scope.launch {
            val playlists = if (account.isAvailable) runCatching { account.accountPlaylists() }.getOrDefault(emptyList()) else emptyList()
            _uiState.update { it.copy(youTubePlaylists = playlists) }
        }
        if (refreshHome && _uiState.value.homeFeed.sections.isNotEmpty()) refreshHome()
    }

    /** Rebuilds Home with fresh picks, keeping the current shelves on screen until the new ones are ready. */
    fun refreshHome() {
        val state = _uiState.value
        if (state.isLoadingHome || state.isRefreshingHome) return
        if (state.homeFeed.sections.isEmpty()) {
            loadHome()
            return
        }
        _uiState.update { it.copy(isRefreshingHome = true, error = null) }
        scope.launch {
            val refresh = ++homeRefreshCount
            runCatching { homeFeedBuilder.initialFeed(refresh) }
                .onSuccess { feed ->
                    _uiState.update {
                        it.copy(
                            homeFeed = feed,
                            isRefreshingHome = false,
                            isLoadingMoreHome = false,
                            homeGeneration = it.homeGeneration + 1,
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isRefreshingHome = false, error = "Couldn't refresh Home: ${e.message}") } }
        }
    }

    /** Appends the next page of home shelves; YouTube Music's first page is often only 2 shelves. */
    fun loadMoreHome() {
        val state = _uiState.value
        val continuation = state.homeFeed.continuation ?: return
        if (state.isLoadingHome || state.isLoadingMoreHome || state.isRefreshingHome) return
        val generation = state.homeGeneration
        val shownSongIds = state.homeFeed.sections
            .flatMap { section -> section.items.mapNotNull { (it as? HomeItem.SongItem)?.song?.id } }
            .toSet()
        _uiState.update { it.copy(isLoadingMoreHome = true) }
        scope.launch {
            val more = runCatching { homeFeedBuilder.moreShelves(continuation, shownSongIds) }.getOrNull()
            _uiState.update { current ->
                // A refresh replaced the feed meanwhile: these shelves belong to the old one.
                if (current.homeGeneration != generation) return@update current
                if (more == null) return@update current.copy(isLoadingMoreHome = false)
                val known = current.homeFeed.sections.map { it.title }.toSet()
                val added = more.sections.filter { it.items.isNotEmpty() && it.title !in known }
                current.copy(
                    homeFeed = HomeFeed(
                        sections = current.homeFeed.sections + added,
                        continuation = more.continuation.takeIf { added.isNotEmpty() },
                    ),
                    isLoadingMoreHome = false,
                )
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(isSearching = false, searchResults = SearchResults(), searchedQuery = "") }
            return
        }
        // A trailing space doesn't change what's being searched for.
        if (query.trim() == _uiState.value.searchedQuery) {
            _uiState.update { it.copy(isSearching = false) }
            return
        }
        // Search as you type, as mobile does: once typing pauses, and each keystroke restarts the wait.
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(query, settingsStore.current.searchVideos)
        }
    }

    /** Searches for the current query right away (Enter, or another screen asking for a search). */
    fun search(includeVideos: Boolean = settingsStore.current.searchVideos) {
        searchJob?.cancel()
        val query = _uiState.value.query
        if (query.isBlank()) return
        searchJob = scope.launch { runSearch(query, includeVideos) }
    }

    private suspend fun runSearch(query: String, includeVideos: Boolean) {
        val clean = query.trim()
        _uiState.update { it.copy(isSearching = true, error = null) }
        val outcome = try {
            Result.success(musicSource.search(clean, includeVideos = includeVideos))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
        // A lookup can't always be interrupted; one that finishes after the user has typed on must
        // not replace the results for what they typed since.
        _uiState.update { state ->
            if (state.query.trim() != clean) return@update state
            outcome.fold(
                onSuccess = { results -> state.copy(searchResults = results, searchedQuery = clean, isSearching = false) },
                onFailure = { e -> state.copy(isSearching = false, searchedQuery = clean, error = e.message) },
            )
        }
    }

    // ---- Album / Artist / Playlist detail ----

    private fun loadAlbum(id: String) = scope.launch {
        _uiState.update { it.copy(isLoadingDetail = true, albumDetail = null, error = null) }
        runCatching { musicSource.album(id) }
            .onSuccess { album -> _uiState.update { it.copy(albumDetail = album, isLoadingDetail = false) } }
            .onFailure { e -> _uiState.update { it.copy(isLoadingDetail = false, error = e.message) } }
    }

    private fun loadArtist(id: String) = scope.launch {
        _uiState.update { it.copy(isLoadingDetail = true, artistDetail = null, error = null) }
        runCatching { musicSource.artist(id) }
            .onSuccess { artist -> _uiState.update { it.copy(artistDetail = artist, isLoadingDetail = false) } }
            .onFailure { e -> _uiState.update { it.copy(isLoadingDetail = false, error = e.message) } }
    }

    private fun loadPlaylist(id: String) = scope.launch {
        _uiState.update { it.copy(isLoadingDetail = true, playlistDetail = null, error = null) }
        // Local playlists (numeric ids from FileLibraryRepository) are served from the library
        // flow directly; remote ones go through the catalog.
        val local = _uiState.value.playlists.find { it.id == id }
        if (local != null) {
            _uiState.update { it.copy(playlistDetail = local, isLoadingDetail = false) }
            return@launch
        }
        runCatching { musicSource.playlist(id) }
            .onSuccess { playlist -> _uiState.update { it.copy(playlistDetail = playlist, isLoadingDetail = false) } }
            .onFailure { e -> _uiState.update { it.copy(isLoadingDetail = false, error = e.message) } }
    }

    // ---- Library ----

    fun toggleLike(song: Song) = scope.launch { library.toggleLike(song) }

    fun toggleLikeCurrent() {
        val song = _playback.value.currentSong ?: return
        toggleLike(song)
    }

    fun createPlaylist(name: String) = scope.launch { library.createPlaylist(name) }

    fun addToPlaylist(playlistId: String, song: Song) = scope.launch {
        library.addToPlaylist(playlistId.toLong(), song)
    }

    fun createPlaylistWith(song: Song, name: String) = scope.launch {
        val clean = name.trim()
        if (clean.isBlank()) return@launch
        val playlistId = library.createPlaylist(clean)
        library.addToPlaylist(playlistId, song)
    }

    fun goToAlbum(song: Song) = scope.launch {
        val albumName = song.album?.trim().orEmpty()
        if (albumName.isBlank()) return@launch
        _uiState.update { it.copy(error = null) }
        val query = listOf(song.artist, albumName).filter { it.isNotBlank() }.joinToString(" ")
        val albums = runCatching { musicSource.search(query).albums }.getOrDefault(emptyList())
        val match = albums.firstOrNull { it.title.equals(albumName, ignoreCase = true) } ?: albums.firstOrNull()
        if (match != null) {
            navigateTo(Screen.AlbumDetail(match.id))
        } else {
            _uiState.update { it.copy(error = "Album not found: $albumName") }
        }
    }

    // ---- Playback ----

    /** Plays [song], replacing the queue with [queue] (defaults to just this song). */
    fun play(song: Song, queue: List<Song> = listOf(song)) = scope.launch {
        val cleanQueue = queue.ifEmpty { listOf(song) }
        originalQueue = cleanQueue
        val (playQueue, index) = playbackQueueFor(song, cleanQueue, _playback.value.shuffle)
        startSong(song, playQueue, index)
    }

    fun togglePlayPause() {
        val state = _playback.value
        val song = state.currentSong ?: return
        if (state.isPlaying) {
            pause()
            return
        }
        if (loadedSongId != song.id) {
            val resumePosition = _position.value.takeUnless { state.durationMs > 0L && it >= state.durationMs - 1_000L } ?: 0L
            scope.launch { startSong(song, state.queue, state.queueIndex.coerceAtLeast(0), startPositionMs = resumePosition) }
        } else {
            player.resume()
            _playback.update { it.copy(isPlaying = true) }
        }
    }

    fun pause() {
        mix.finishBlend()
        player.pause()
        _playback.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        lastPositionMs = clamped
        if (loadedSongId == _playback.value.currentSong?.id) {
            mix.finishBlend()
            seekTargetMs = clamped
            seekAtNanos = System.nanoTime()
            player.seekTo(clamped)
        }
        _position.value = clamped
    }

    /**
     * libVLC seeks asynchronously, and the position it reports for a moment afterwards can still be
     * the old one, which snapped the seek bar back as if the click hadn't taken. Those are skipped.
     */
    private fun isStaleAfterSeek(positionMs: Long): Boolean {
        val target = seekTargetMs ?: return false
        val sinceSeekMs = (System.nanoTime() - seekAtNanos) / 1_000_000
        if (sinceSeekMs > SEEK_SETTLE_MS || abs(positionMs - target) <= SEEK_TOLERANCE_MS) {
            seekTargetMs = null
            return false
        }
        return true
    }

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        player.setVolume(clamped)
        _playback.update { it.copy(volume = clamped) }
        settingsStore.update { it.copy(volume = clamped) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2f)
        player.setPlaybackSpeed(clamped)
        _playback.update { it.copy(playbackSpeed = clamped) }
        settingsStore.update { it.copy(playbackSpeed = clamped) }
    }

    fun toggleShuffle() {
        applyShuffle(!_playback.value.shuffle, persist = true)
    }

    fun cycleRepeat() {
        val next = when (_playback.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playback.update { it.copy(repeatMode = next) }
        settingsStore.update { it.copy(repeatMode = next) }
    }

    fun playNextInQueue() {
        val state = _playback.value
        val nextIndex = nextIndexOf(state) ?: return
        val next = state.queue.getOrNull(nextIndex) ?: return
        scope.launch { startSong(next, state.queue, nextIndex) }
    }

    private fun nextIndexOf(state: PlaybackUiState): Int? = when {
        state.queueIndex + 1 in state.queue.indices -> state.queueIndex + 1
        state.repeatMode == RepeatMode.ALL && state.queue.isNotEmpty() -> 0
        else -> null
    }

    fun playPreviousInQueue() {
        val state = _playback.value
        if (_position.value > RESTART_PREVIOUS_MS) {
            seekTo(0L)
            return
        }
        val prevIndex = when {
            state.queueIndex - 1 in state.queue.indices -> state.queueIndex - 1
            state.repeatMode == RepeatMode.ALL && state.queue.isNotEmpty() -> state.queue.lastIndex
            else -> return
        }
        val prev = state.queue.getOrNull(prevIndex) ?: return
        scope.launch { startSong(prev, state.queue, prevIndex) }
    }

    fun playFromQueue(index: Int) {
        val state = _playback.value
        val song = state.queue.getOrNull(index) ?: return
        scope.launch { startSong(song, state.queue, index) }
    }

    fun refreshAudioOutputs() {
        _playback.update { it.copy(audioOutputDevices = player.outputDevices()) }
    }

    fun selectAudioOutput(deviceId: String) {
        player.setOutputDevice(deviceId)
        settingsStore.update { it.copy(audioOutputDevice = deviceId) }
        refreshAudioOutputs()
    }

    fun startSleepTimer(minutes: Int) = sleepTimerManager.start(minutes)

    fun startSleepTimerAtEndOfTrack() = sleepTimerManager.startAtEndOfTrack()

    fun cancelSleepTimer() = sleepTimerManager.cancel()

    // ---- Queue editing ----

    /** Inserts [song] right after the current one (or plays it if nothing is playing). */
    fun playNext(song: Song) {
        val state = _playback.value
        if (state.currentSong == null) {
            play(song)
            return
        }
        originalQueue = insertAfterCurrent(originalQueue.ifEmpty { state.queue }, state.currentSong, song)
        _playback.update { s ->
            val queue = s.queue.toMutableList().apply { add((s.queueIndex + 1).coerceIn(0, size), song) }
            s.copy(queue = queue)
        }
    }

    /** Appends [song] to the end of the queue (or plays it if nothing is playing). */
    fun addToQueue(song: Song) {
        if (_playback.value.currentSong == null) {
            play(song)
            return
        }
        originalQueue = originalQueue.ifEmpty { _playback.value.queue } + song
        _playback.update { s -> s.copy(queue = s.queue + song) }
    }

    fun removeFromQueue(index: Int) = _playback.update { s ->
        if (index !in s.queue.indices || index == s.queueIndex) return@update s
        val removed = s.queue[index]
        originalQueue = removeFirstById(originalQueue.ifEmpty { s.queue }, removed.id)
        val queue = s.queue.toMutableList().apply { removeAt(index) }
        s.copy(queue = queue, queueIndex = if (index < s.queueIndex) s.queueIndex - 1 else s.queueIndex)
    }

    fun moveInQueue(from: Int, to: Int) = _playback.update { s ->
        if (from !in s.queue.indices || to !in s.queue.indices || from == to) return@update s
        val queue = s.queue.toMutableList().apply { add(to, removeAt(from)) }
        val current = s.queueIndex
        val newIndex = when {
            from == current -> to
            from < current && to >= current -> current - 1
            from > current && to <= current -> current + 1
            else -> current
        }
        if (!s.shuffle) originalQueue = queue
        s.copy(queue = queue, queueIndex = newIndex)
    }

    fun clearUpcoming() = _playback.update { s ->
        if (s.queueIndex !in s.queue.indices) return@update s
        val queue = s.queue.take(s.queueIndex + 1)
        originalQueue = originalQueue.ifEmpty { s.queue }.filter { song -> queue.any { it.id == song.id } }
        s.copy(queue = queue)
    }

    /** Plays [song] followed by YouTube Music's related tracks ("Start radio"). */
    fun startRadio(song: Song) = scope.launch {
        startSong(song, listOf(song), 0)
        val related = runCatching { musicSource.relatedTo(song.id) }.getOrDefault(emptyList())
            .filter { it.id != song.id }
            .distinctBy { it.id }
        originalQueue = listOf(song) + related
        _playback.update { s -> if (s.currentSong?.id == song.id) s.copy(queue = s.queue + related) else s }
    }

    fun setExpanded(expanded: Boolean) = _playback.update { it.copy(isExpanded = expanded) }

    fun setQueueVisible(visible: Boolean) = _playback.update { it.copy(isQueueVisible = visible) }

    /** The single path every track change goes through, so listeners and stats see all of them. */
    private suspend fun startSong(song: Song, queue: List<Song>, index: Int, startPositionMs: Long = 0L) {
        loadedSongId = null
        finishCurrentSong()
        val safeIndex = index.coerceIn(queue.indices)
        _position.value = startPositionMs
        _playback.update {
            it.copy(
                currentSong = song,
                queue = queue,
                queueIndex = safeIndex,
                isPlaying = true,
                durationMs = song.durationMs,
            )
        }
        hasActiveSongForStats = true
        retriedSongId = null
        loadSong(song)
        playbackListeners.forEach { runCatching { it.onSongStarted(song) } }
        library.recordPlay(song)
    }

    /** Swaps libVLC over to [song], superseding any switch still waiting on its stream. */
    private fun loadSong(song: Song) {
        // Outside playLock: the mix controller takes its own lock first, then playLock on a hand-off.
        mix.cancel()
        synchronized(playLock) {
            loadedSongId = null
            _playback.update { it.copy(isBuffering = true) }
            val request = ++playRequest
            loadJob?.cancel()
            loadJob = scope.launch { resolveAndPlay(song, request) }
        }
    }

    /** Where playback moves on by itself, for a blend; null when it stops, repeats one or is about to fetch radio. */
    private fun upcomingForMix(): MixController.Upcoming? {
        val state = _playback.value
        if (state.repeatMode == RepeatMode.ONE || sleepTimerManager.shouldStopAtEndOfTrack()) return null
        val index = nextIndexOf(state) ?: return null
        return MixController.Upcoming(state.queue[index], index)
    }

    /** A blend started: [next] is now playing (from [startMs]) on the player's other deck. */
    private fun onMixHandoff(next: MixController.Upcoming, startMs: Long) {
        synchronized(playLock) {
            ++playRequest
            loadJob?.cancel()
        }
        finishCurrentSong()
        loadedSongId = next.song.id
        lastPositionMs = startMs
        _position.value = startMs
        _playback.update { s ->
            val index = next.index.takeIf { s.queue.getOrNull(it)?.id == next.song.id }
                ?: s.queue.indexOfFirst { it.id == next.song.id }.takeIf { it >= 0 }
                ?: s.queueIndex
            s.copy(
                currentSong = next.song,
                queueIndex = index,
                isPlaying = true,
                isBuffering = false,
                durationMs = next.song.durationMs,
            )
        }
        hasActiveSongForStats = true
        retriedSongId = null
        playbackListeners.forEach { runCatching { it.onSongStarted(next.song) } }
        scope.launch { library.recordPlay(next.song) }
    }

    private suspend fun resolveAndPlay(song: Song, request: Long) = coroutineScope {
        // The current track keeps playing while the new stream is looked up, so a switch to a song
        // that wasn't prefetched isn't a second or two of silence; play() replaces it once ready.
        val result = withContext(Dispatchers.IO) {
            runCatching { offlineSource.localStream(song.id) ?: streams.resolveStream(song.id) }
        }
        synchronized(playLock) {
            if (request != playRequest) return@coroutineScope
            result
                .onSuccess { stream ->
                    val state = _playback.value
                    loadedSongId = song.id
                    player.play(
                        stream = stream,
                        rate = state.playbackSpeed,
                        startPositionMs = _position.value,
                        outputDeviceId = state.audioOutputDevice,
                        paused = !state.isPlaying,
                    )
                    if (!state.isPlaying) _playback.update { it.copy(isBuffering = false) }
                }
                .onFailure { e ->
                    player.stop()
                    _playback.update { it.copy(isPlaying = false, isBuffering = false) }
                    _uiState.update {
                        it.copy(
                            error = e.message,
                            youTubeSignInNeeded = e is YouTubeSignInRequiredException,
                            browserNeeded = it.browserNeeded || e is BrowserUnavailableException,
                        )
                    }
                }
        }
    }

    fun showBrowserNeeded() = _uiState.update { it.copy(browserNeeded = true, youTubeSignInNeeded = false) }

    fun dismissBrowserNeeded() = _uiState.update { it.copy(browserNeeded = false) }

    fun dismissYouTubeSignInPrompt() = _uiState.update { it.copy(youTubeSignInNeeded = false) }

    /** After a sign-in, plays the song that stopped for want of one. */
    fun onYouTubeSignedIn() {
        val wasBlocked = _uiState.value.youTubeSignInNeeded
        _uiState.update { it.copy(youTubeSignInNeeded = false, error = null) }
        val song = _playback.value.currentSong ?: return
        if (wasBlocked && !_playback.value.isPlaying) {
            _playback.update { it.copy(isPlaying = true) }
            loadSong(song)
        }
    }

    /** A cached URL can go stale (or be refused); fetch a fresh one once before giving up. */
    private fun onPlaybackError() {
        scope.launch {
            val song = _playback.value.currentSong ?: return@launch
            if (loadedSongId != song.id) return@launch
            if (retriedSongId == song.id) {
                loadedSongId = null
                _playback.update { it.copy(isPlaying = false, isBuffering = false) }
                _uiState.update { it.copy(error = "Couldn't play ${song.title}") }
                return@launch
            }
            retriedSongId = song.id
            streams.invalidateStream(song.id)
            loadSong(song)
        }
    }

    private fun finishCurrentSong() {
        if (!hasActiveSongForStats) return
        val state = _playback.value
        val current = state.currentSong ?: return
        val listened = listenedMs
        listenedMs = 0L
        lastPositionMs = 0L
        hasActiveSongForStats = false
        playbackListeners.forEach { runCatching { it.onSongFinished(current, listened, state.durationMs) } }
    }

    private fun onTrackEnded(endedSongId: String) {
        scope.launch {
            val state = _playback.value
            val current = state.currentSong ?: return@launch
            // A blend may already have moved on: only the track that ended advances the queue.
            if (current.id != endedSongId || loadedSongId != endedSongId) return@launch
            loadedSongId = null
            if (sleepTimerManager.shouldStopAtEndOfTrack()) {
                sleepTimerManager.finishEndOfTrack()
                finishCurrentSong()
                stopAtEnd()
                return@launch
            }
            when {
                state.repeatMode == RepeatMode.ONE -> startSong(current, state.queue, state.queueIndex)
                state.queueIndex + 1 in state.queue.indices -> {
                    val nextIndex = state.queueIndex + 1
                    startSong(state.queue[nextIndex], state.queue, nextIndex)
                }
                state.repeatMode == RepeatMode.ALL && state.queue.isNotEmpty() -> startSong(state.queue.first(), state.queue, 0)
                settingsStore.current.autoplayRadio && !current.id.startsWith(LOCAL_ID_PREFIX) -> autoplayRadioAfter(current, state)
                else -> {
                    finishCurrentSong()
                    stopAtEnd()
                }
            }
        }
    }

    private fun stopAtEnd() {
        _playback.update { it.copy(isPlaying = false) }
        _position.value = _playback.value.durationMs
    }

    private suspend fun autoplayRadioAfter(lastSong: Song, state: PlaybackUiState) {
        val queuedIds = state.queue.map { it.id }.toSet()
        val related = runCatching { musicSource.relatedTo(lastSong.id) }.getOrDefault(emptyList())
            .filter { it.id !in queuedIds && !it.id.startsWith(LOCAL_ID_PREFIX) }
            .distinctBy { it.id }
        if (related.isEmpty()) {
            finishCurrentSong()
            stopAtEnd()
            return
        }
        val newQueue = state.queue + related
        originalQueue = originalQueue.ifEmpty { state.queue } + related
        startSong(related.first(), newQueue, state.queue.size)
    }

    private fun playbackQueueFor(song: Song, queue: List<Song>, shuffle: Boolean): Pair<List<Song>, Int> {
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        if (!shuffle) return queue to index
        val shuffled = queue.filterIndexed { i, queued -> i != index && queued.id != song.id }.shuffled()
        return (listOf(song) + shuffled) to 0
    }

    private fun applyShuffle(enabled: Boolean, persist: Boolean) {
        val state = _playback.value
        val current = state.currentSong
        if (current == null || state.queue.isEmpty()) {
            _playback.update { it.copy(shuffle = enabled) }
        } else if (enabled) {
            if (originalQueue.isEmpty()) originalQueue = state.queue
            val played = state.queue.take(state.queueIndex.coerceAtLeast(0))
            val upcoming = state.queue.drop(state.queueIndex + 1).shuffled()
            _playback.update { it.copy(shuffle = true, queue = played + current + upcoming, queueIndex = played.size) }
        } else {
            val restored = originalQueue.ifEmpty { state.queue }
            val restoredIndex = restored.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: state.queueIndex
            _playback.update { it.copy(shuffle = false, queue = restored, queueIndex = restoredIndex) }
        }
        if (persist) settingsStore.update { it.copy(shuffle = enabled) }
    }

    private fun insertAfterCurrent(queue: List<Song>, current: Song?, song: Song): List<Song> {
        if (queue.isEmpty() || current == null) return queue + song
        val index = queue.indexOfFirst { it.id == current.id }
        if (index < 0) return queue + song
        return queue.toMutableList().apply { add(index + 1, song) }
    }

    private fun removeFirstById(queue: List<Song>, id: String): List<Song> {
        var removed = false
        return queue.filterNot { song ->
            if (!removed && song.id == id) {
                removed = true
                true
            } else {
                false
            }
        }
    }

    fun dispose() {
        if (settingsStore.current.restoreQueue) {
            val state = _playback.value
            queuePersistence.save(state.queue, originalQueue.ifEmpty { state.queue }, state.queueIndex, _position.value)
        }
        sleepTimerManager.cancel()
        finishCurrentSong()
        audioKeepAlive?.close()
        mix.release()
        player.release()
    }

    private companion object {
        /** Position ticks larger than this are seeks, not playback. */
        const val MAX_TICK_MS = 3_000L
        const val RESTART_PREVIOUS_MS = 3_000L

        /** After a seek, position reports this far from the target are the pre-seek ones... */
        const val SEEK_TOLERANCE_MS = 1_500L

        /** ...for at most this long. */
        const val SEEK_SETTLE_MS = 1_500L
        const val LOCAL_ID_PREFIX = "local:"

        /** Lets rapid skips and queue edits settle before looking up the next track. */
        const val PREFETCH_DEBOUNCE_MS = 500L

        /** Mobile's search-as-you-type pause. */
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
