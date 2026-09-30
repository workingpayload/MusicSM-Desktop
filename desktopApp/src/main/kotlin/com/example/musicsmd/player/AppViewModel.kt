package com.example.musicsmd.player

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.StatsRepository
import com.example.musicsm.domain.source.MusicSource
import com.example.musicsmd.home.DesktopHomeFeedBuilder
import com.example.musicsmd.nav.Screen
import com.example.musicsmd.playback.AudioOutputDeviceInfo
import com.example.musicsmd.playback.OfflineSource
import com.example.musicsmd.playback.PlaybackListener
import com.example.musicsmd.playback.PlayerController
import com.example.musicsmd.playback.QueuePersistence
import com.example.musicsmd.playback.SleepTimerManager
import com.example.musicsmd.playback.SleepTimerState
import com.example.musicsmd.settings.RepeatMode
import com.example.musicsmd.settings.SettingsStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ephemeral playback UI state, mirrors the shape of the mobile app's `PlayerState`. */
data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
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
    val homeFeed: HomeFeed = HomeFeed(),
    val isLoadingHome: Boolean = true,
    val isLoadingMoreHome: Boolean = false,
    val likedSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val albumDetail: Album? = null,
    val artistDetail: Artist? = null,
    val playlistDetail: Playlist? = null,
    val isLoadingDetail: Boolean = false,
    val error: String? = null,
)

/**
 * Plain (non-androidx) view-model. Owns the [MusicSource]/[LibraryRepository] calls, navigation,
 * and the queue, and drives [PlayerController] — the desktop counterpart of the mobile app's
 * `PlayerViewModel` + `MediaControllerManager` + per-feature view-models combined.
 */
class AppViewModel(
    private val musicSource: MusicSource,
    private val library: LibraryRepository,
    private val statsRepository: StatsRepository,
    private val player: PlayerController,
    private val settingsStore: SettingsStore,
    private val offlineSource: OfflineSource = OfflineSource { null },
    private val playbackListeners: List<PlaybackListener> = emptyList(),
    private val queuePersistence: QueuePersistence = QueuePersistence(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val homeFeedBuilder = DesktopHomeFeedBuilder(musicSource, library, statsRepository)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    private val sleepTimerManager = SleepTimerManager(
        scope = scope,
        fadeOutEnabled = { settingsStore.current.sleepTimerFadeOut },
        currentVolume = { _playback.value.volume },
        setVolume = { setVolume(it) },
        pausePlayback = { pause() },
    )

    // Time actually played of the current song; seeks don't count, so stats reflect listening.
    private var listenedMs = 0L
    private var lastPositionMs = 0L
    private var originalQueue: List<Song> = emptyList()
    private var loadedSongId: String? = null
    private var hasActiveSongForStats = false

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
            val delta = positionMs - lastPositionMs
            if (delta in 1..MAX_TICK_MS) listenedMs += delta
            lastPositionMs = positionMs
            _playback.update { it.copy(positionMs = positionMs, durationMs = durationMs) }
        }
        player.onEndReached = { onTrackEnded() }
        restoreQueueIfNeeded()
        loadHome()
        observeLibrary()
        observeSettings()
        observeSleepTimer()
        observeQueuePersistence()
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
            _playback.debounce(1_000L).collect { state ->
                if (!settingsStore.current.restoreQueue) return@collect
                if (state.currentSong == null || state.queue.isEmpty()) return@collect
                queuePersistence.save(state.queue, originalQueue.ifEmpty { state.queue }, state.queueIndex, state.positionMs)
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
                positionMs = saved.positionMs,
                durationMs = current.durationMs,
                isPlaying = false,
            )
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
        runCatching { homeFeedBuilder.initialFeed() }
            .onSuccess { feed -> _uiState.update { it.copy(homeFeed = feed, isLoadingHome = false) } }
            .onFailure { e -> _uiState.update { it.copy(isLoadingHome = false, error = e.message) } }
    }

    /** Appends the next page of home shelves; YouTube Music's first page is often only 2 shelves. */
    fun loadMoreHome() {
        val state = _uiState.value
        val continuation = state.homeFeed.continuation ?: return
        if (state.isLoadingHome || state.isLoadingMoreHome) return
        _uiState.update { it.copy(isLoadingMoreHome = true) }
        scope.launch {
            val more = runCatching { musicSource.moreHomeShelves(continuation) }.getOrNull()
            _uiState.update { current ->
                if (more == null) return@update current.copy(isLoadingMoreHome = false)
                val known = current.homeFeed.sections.map { it.title }.toSet()
                val added = more.sections.filter { it.title !in known }
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
    }

    fun search(includeVideos: Boolean = false) = scope.launch {
        val query = _uiState.value.query
        if (query.isBlank()) return@launch
        _uiState.update { it.copy(isSearching = true, error = null) }
        runCatching { musicSource.search(query, includeVideos = includeVideos) }
            .onSuccess { results -> _uiState.update { it.copy(searchResults = results, isSearching = false) } }
            .onFailure { e -> _uiState.update { it.copy(isSearching = false, error = e.message) } }
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
            val resumePosition = state.positionMs.takeUnless { state.durationMs > 0L && it >= state.durationMs - 1_000L } ?: 0L
            scope.launch { startSong(song, state.queue, state.queueIndex.coerceAtLeast(0), startPositionMs = resumePosition) }
        } else {
            player.resume()
            _playback.update { it.copy(isPlaying = true) }
        }
    }

    fun pause() {
        player.pause()
        _playback.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        lastPositionMs = clamped
        if (loadedSongId == _playback.value.currentSong?.id) {
            player.seekTo(clamped)
        }
        _playback.update { it.copy(positionMs = clamped) }
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
        val nextIndex = when {
            state.queueIndex + 1 in state.queue.indices -> state.queueIndex + 1
            state.repeatMode == RepeatMode.ALL && state.queue.isNotEmpty() -> 0
            else -> return
        }
        val next = state.queue.getOrNull(nextIndex) ?: return
        scope.launch { startSong(next, state.queue, nextIndex) }
    }

    fun playPreviousInQueue() {
        val state = _playback.value
        if (state.positionMs > RESTART_PREVIOUS_MS) {
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
        finishCurrentSong()
        val safeIndex = index.coerceIn(queue.indices)
        _playback.update {
            it.copy(
                currentSong = song,
                queue = queue,
                queueIndex = safeIndex,
                isPlaying = true,
                positionMs = startPositionMs,
                durationMs = song.durationMs,
            )
        }
        hasActiveSongForStats = true
        playbackListeners.forEach { runCatching { it.onSongStarted(song) } }
        library.recordPlay(song)
        resolveAndPlay(song, startPositionMs)
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

    private suspend fun resolveAndPlay(song: Song, startPositionMs: Long = 0L) {
        runCatching { offlineSource.localStream(song.id) ?: musicSource.resolveStream(song.id) }
            .onSuccess { stream ->
                player.play(
                    stream = stream,
                    rate = _playback.value.playbackSpeed,
                    startPositionMs = startPositionMs,
                    outputDeviceId = _playback.value.audioOutputDevice,
                )
                loadedSongId = song.id
            }
            .onFailure { e ->
                _playback.update { it.copy(isPlaying = false) }
                _uiState.update { it.copy(error = e.message) }
            }
    }

    private fun onTrackEnded() {
        scope.launch {
            val state = _playback.value
            val current = state.currentSong ?: return@launch
            loadedSongId = null
            if (sleepTimerManager.shouldStopAtEndOfTrack()) {
                sleepTimerManager.finishEndOfTrack()
                finishCurrentSong()
                _playback.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
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
                    _playback.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
                }
            }
        }
    }

    private suspend fun autoplayRadioAfter(lastSong: Song, state: PlaybackUiState) {
        val queuedIds = state.queue.map { it.id }.toSet()
        val related = runCatching { musicSource.relatedTo(lastSong.id) }.getOrDefault(emptyList())
            .filter { it.id !in queuedIds && !it.id.startsWith(LOCAL_ID_PREFIX) }
            .distinctBy { it.id }
        if (related.isEmpty()) {
            finishCurrentSong()
            _playback.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
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
            queuePersistence.save(state.queue, originalQueue.ifEmpty { state.queue }, state.queueIndex, state.positionMs)
        }
        sleepTimerManager.cancel()
        finishCurrentSong()
        player.release()
    }

    private companion object {
        /** Position ticks larger than this are seeks, not playback. */
        const val MAX_TICK_MS = 3_000L
        const val RESTART_PREVIOUS_MS = 3_000L
        const val LOCAL_ID_PREFIX = "local:"
    }
}
