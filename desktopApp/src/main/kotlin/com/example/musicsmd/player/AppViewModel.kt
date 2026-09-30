package com.example.musicsmd.player

import com.example.musicsm.domain.model.Album
import com.example.musicsm.domain.model.Artist
import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.source.MusicSource
import com.example.musicsmd.nav.Screen
import com.example.musicsmd.playback.OfflineSource
import com.example.musicsmd.playback.PlaybackListener
import com.example.musicsmd.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val isLiked: Boolean = false,
    val isExpanded: Boolean = false,
    val isQueueVisible: Boolean = false,
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
    private val player: PlayerController,
    private val offlineSource: OfflineSource = OfflineSource { null },
    private val playbackListeners: List<PlaybackListener> = emptyList(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    // Time actually played of the current song; seeks don't count, so stats reflect listening.
    private var listenedMs = 0L
    private var lastPositionMs = 0L

    init {
        player.onPositionChanged = { positionMs, durationMs ->
            val delta = positionMs - lastPositionMs
            if (delta in 1..MAX_TICK_MS) listenedMs += delta
            lastPositionMs = positionMs
            _playback.update { it.copy(positionMs = positionMs, durationMs = durationMs) }
        }
        player.onEndReached = { playNextInQueue() }
        loadHome()
        observeLibrary()
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
        runCatching { musicSource.homeFeed() }
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

    fun search() = scope.launch {
        val query = _uiState.value.query
        if (query.isBlank()) return@launch
        _uiState.update { it.copy(isSearching = true, error = null) }
        runCatching { musicSource.search(query) }
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

    // ---- Playback ----

    /** Plays [song], replacing the queue with [queue] (defaults to just this song). */
    fun play(song: Song, queue: List<Song> = listOf(song)) = scope.launch {
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        startSong(song, queue, index)
    }

    fun togglePlayPause() {
        player.togglePlayPause()
        _playback.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun seekTo(positionMs: Long) {
        lastPositionMs = positionMs
        player.seekTo(positionMs)
    }

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        player.setVolume(clamped)
        _playback.update { it.copy(volume = clamped) }
    }

    fun playNextInQueue() {
        val state = _playback.value
        val nextIndex = state.queueIndex + 1
        val next = state.queue.getOrNull(nextIndex) ?: return
        scope.launch { startSong(next, state.queue, nextIndex) }
    }

    fun playPreviousInQueue() {
        val state = _playback.value
        val prevIndex = state.queueIndex - 1
        val prev = state.queue.getOrNull(prevIndex) ?: return
        scope.launch { startSong(prev, state.queue, prevIndex) }
    }

    fun playFromQueue(index: Int) {
        val state = _playback.value
        val song = state.queue.getOrNull(index) ?: return
        scope.launch { startSong(song, state.queue, index) }
    }

    // ---- Queue editing ----

    /** Inserts [song] right after the current one (or plays it if nothing is playing). */
    fun playNext(song: Song) {
        val state = _playback.value
        if (state.currentSong == null) {
            play(song)
            return
        }
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
        _playback.update { s -> s.copy(queue = s.queue + song) }
    }

    fun removeFromQueue(index: Int) = _playback.update { s ->
        if (index !in s.queue.indices || index == s.queueIndex) return@update s
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
        s.copy(queue = queue, queueIndex = newIndex)
    }

    /** Plays [song] followed by YouTube Music's related tracks ("Start radio"). */
    fun startRadio(song: Song) = scope.launch {
        startSong(song, listOf(song), 0)
        val related = runCatching { musicSource.relatedTo(song.id) }.getOrDefault(emptyList())
            .filter { it.id != song.id }
            .distinctBy { it.id }
        _playback.update { s -> if (s.currentSong?.id == song.id) s.copy(queue = s.queue + related) else s }
    }

    fun setExpanded(expanded: Boolean) = _playback.update { it.copy(isExpanded = expanded) }

    fun setQueueVisible(visible: Boolean) = _playback.update { it.copy(isQueueVisible = visible) }

    /** The single path every track change goes through, so listeners and stats see all of them. */
    private suspend fun startSong(song: Song, queue: List<Song>, index: Int) {
        finishCurrentSong()
        _playback.update {
            it.copy(
                currentSong = song,
                queue = queue,
                queueIndex = index,
                isPlaying = true,
                positionMs = 0L,
                durationMs = song.durationMs,
            )
        }
        playbackListeners.forEach { runCatching { it.onSongStarted(song) } }
        library.recordPlay(song)
        resolveAndPlay(song)
    }

    private fun finishCurrentSong() {
        val state = _playback.value
        val current = state.currentSong ?: return
        val listened = listenedMs
        listenedMs = 0L
        lastPositionMs = 0L
        playbackListeners.forEach { runCatching { it.onSongFinished(current, listened, state.durationMs) } }
    }

    private suspend fun resolveAndPlay(song: Song) {
        runCatching { offlineSource.localStream(song.id) ?: musicSource.resolveStream(song.id) }
            .onSuccess { stream -> player.play(stream) }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    fun dispose() {
        finishCurrentSong()
        player.release()
    }

    private companion object {
        /** Position ticks larger than this are seeks, not playback. */
        const val MAX_TICK_MS = 3_000L
    }
}
