package com.example.musicsmd.lyrics

import com.example.musicsm.domain.model.Lyrics
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LyricsRepository
import com.example.musicsmd.player.PlaybackUiState
import com.example.musicsmd.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data object NotFound : LyricsUiState
    data class Loaded(val songId: String, val lyrics: Lyrics) : LyricsUiState
    data class Error(val message: String) : LyricsUiState
}

class LyricsController(
    private val playback: StateFlow<PlaybackUiState>,
    private val repository: LyricsRepository,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = linkedMapOf<String, Lyrics?>()

    private val _state = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    val offsetMs: StateFlow<Long> = combine(
        playback.map { it.currentSong?.id }.distinctUntilChanged(),
        settingsStore.settings,
    ) { songId, settings ->
        songId?.let { settings.lyricsOffsetsMs[it]?.coerceLyricsOffset() } ?: 0L
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    init {
        scope.launch {
            playback.map { it.currentSong }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { song -> load(song) }
        }
    }

    fun setVisible(visible: Boolean) {
        _visible.value = visible
    }

    fun toggleVisible() {
        _visible.update { !it }
    }

    fun adjustOffset(deltaMs: Long) {
        setOffset(offsetMs.value + deltaMs)
    }

    fun setOffset(offsetMs: Long) {
        val songId = playback.value.currentSong?.id?.trim().orEmpty()
        if (songId.isEmpty()) return
        val clamped = offsetMs.coerceLyricsOffset()
        settingsStore.update { settings ->
            val updated = LinkedHashMap<String, Long>()
            if (clamped != 0L) updated[songId] = clamped
            settings.lyricsOffsetsMs.forEach { (id, storedOffset) ->
                if (id != songId) {
                    val stored = storedOffset.coerceLyricsOffset()
                    if (stored != 0L && updated.size < MAX_LYRICS_OFFSETS) updated[id] = stored
                }
            }
            settings.copy(lyricsOffsetsMs = updated)
        }
    }

    fun resetOffset() {
        setOffset(0L)
    }

    fun dispose() {
        scope.cancel()
    }

    private suspend fun load(song: Song?) {
        if (song == null) {
            _state.value = LyricsUiState.Idle
            return
        }
        val cached = synchronized(cache) { if (cache.containsKey(song.id)) cache[song.id] else null }
        if (cached != null || synchronized(cache) { cache.containsKey(song.id) }) {
            _state.value = cached?.let { LyricsUiState.Loaded(song.id, it) } ?: LyricsUiState.NotFound
            return
        }

        _state.value = LyricsUiState.Loading
        val result = runCatching { withContext(Dispatchers.IO) { repository.forSong(song) } }
        result
            .onSuccess { lyrics ->
                synchronized(cache) {
                    cache[song.id] = lyrics
                    while (cache.size > MAX_CACHE_SIZE) cache.remove(cache.keys.first())
                }
                _state.value = lyrics?.let { LyricsUiState.Loaded(song.id, it) } ?: LyricsUiState.NotFound
            }
            .onFailure { error ->
                _state.value = LyricsUiState.Error(error.message ?: "Lyrics failed to load")
            }
    }

    private fun Long.coerceLyricsOffset(): Long =
        coerceIn(-MAX_LYRICS_OFFSET_MS, MAX_LYRICS_OFFSET_MS)

    private companion object {
        const val MAX_CACHE_SIZE = 50
        const val MAX_LYRICS_OFFSETS = 200
        const val MAX_LYRICS_OFFSET_MS = 60_000L
    }
}

