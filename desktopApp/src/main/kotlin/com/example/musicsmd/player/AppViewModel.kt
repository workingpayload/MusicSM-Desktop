package com.example.musicsmd.player

import com.example.musicsm.domain.model.HomeFeed
import com.example.musicsm.domain.model.SearchResults
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.source.MusicSource
import com.example.musicsmd.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

data class AppUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: SearchResults = SearchResults(),
    val homeFeed: HomeFeed = HomeFeed(),
    val isLoadingHome: Boolean = true,
    val error: String? = null,
)

/**
 * Plain (non-androidx) view-model. Owns the [MusicSource] calls and the queue, and drives
 * [PlayerController] — the desktop counterpart of the mobile app's `PlayerViewModel` +
 * `MediaControllerManager`.
 */
class AppViewModel(
    private val musicSource: MusicSource,
    private val player: PlayerController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    init {
        player.onPositionChanged = { positionMs, durationMs ->
            _playback.update { it.copy(positionMs = positionMs, durationMs = durationMs) }
        }
        player.onEndReached = { playNextInQueue() }
        loadHome()
    }

    fun loadHome() = scope.launch {
        _uiState.update { it.copy(isLoadingHome = true, error = null) }
        runCatching { musicSource.homeFeed() }
            .onSuccess { feed -> _uiState.update { it.copy(homeFeed = feed, isLoadingHome = false) } }
            .onFailure { e -> _uiState.update { it.copy(isLoadingHome = false, error = e.message) } }
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

    /** Plays [song], replacing the queue with [queue] (defaults to just this song). */
    fun play(song: Song, queue: List<Song> = listOf(song)) = scope.launch {
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _playback.update { it.copy(currentSong = song, queue = queue, queueIndex = index, isPlaying = true) }
        resolveAndPlay(song)
    }

    fun togglePlayPause() {
        player.togglePlayPause()
        _playback.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun playNextInQueue() {
        val state = _playback.value
        val nextIndex = state.queueIndex + 1
        val next = state.queue.getOrNull(nextIndex) ?: return
        scope.launch {
            _playback.update { it.copy(currentSong = next, queueIndex = nextIndex, isPlaying = true) }
            resolveAndPlay(next)
        }
    }

    fun playPreviousInQueue() {
        val state = _playback.value
        val prevIndex = state.queueIndex - 1
        val prev = state.queue.getOrNull(prevIndex) ?: return
        scope.launch {
            _playback.update { it.copy(currentSong = prev, queueIndex = prevIndex, isPlaying = true) }
            resolveAndPlay(prev)
        }
    }

    private suspend fun resolveAndPlay(song: Song) {
        runCatching { musicSource.resolveStream(song.id) }
            .onSuccess { stream -> player.play(stream) }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    fun dispose() {
        player.release()
    }
}
