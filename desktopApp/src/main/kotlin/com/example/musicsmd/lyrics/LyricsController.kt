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

    // Whether the panel is showing because lyrics turned up (so a song without any may close it
    // again) rather than because the user opened it.
    @Volatile
    private var autoShown = false

    // The song whose lyrics the user closed: they stay closed for it, and open again for the next.
    @Volatile
    private var dismissedSongId: String? = null

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

    /** The user showing or hiding the panel; either way it's theirs until the song changes. */
    fun setVisible(visible: Boolean) {
        if (!visible && _visible.value) dismissedSongId = playback.value.currentSong?.id
        autoShown = false
        _visible.value = visible
    }

    fun toggleVisible() {
        setVisible(!_visible.value)
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
            onLyricsResolved(null, found = false)
            return
        }
        val cached = synchronized(cache) { if (cache.containsKey(song.id)) cache[song.id] else null }
        if (cached != null || synchronized(cache) { cache.containsKey(song.id) }) {
            _state.value = cached?.let { LyricsUiState.Loaded(song.id, it) } ?: LyricsUiState.NotFound
            onLyricsResolved(song.id, found = cached != null)
            return
        }

        // The panel keeps its current state while the next song's lyrics load, so it doesn't close
        // and reopen between two songs that both have lyrics.
        _state.value = LyricsUiState.Loading
        val result = runCatching { withContext(Dispatchers.IO) { repository.forSong(song) } }
        result
            .onSuccess { lyrics ->
                synchronized(cache) {
                    cache[song.id] = lyrics
                    while (cache.size > MAX_CACHE_SIZE) cache.remove(cache.keys.first())
                }
                _state.value = lyrics?.let { LyricsUiState.Loaded(song.id, it) } ?: LyricsUiState.NotFound
                onLyricsResolved(song.id, found = lyrics != null)
            }
            .onFailure { error ->
                _state.value = LyricsUiState.Error(error.message ?: "Lyrics failed to load")
                onLyricsResolved(song.id, found = false)
            }
    }

    /**
     * Opens the panel when [songId]'s lyrics arrive (unless turned off, or the user closed it for
     * this song), and closes a panel it opened itself when a song has none, so an empty "no lyrics"
     * panel never appears unasked.
     */
    private fun onLyricsResolved(songId: String?, found: Boolean) {
        // The user may have skipped on while this song's lookup ran.
        if (songId != playback.value.currentSong?.id) return
        if (found) {
            if (!_visible.value && settingsStore.current.autoOpenLyrics && songId != dismissedSongId) {
                autoShown = true
                _visible.value = true
            }
        } else if (autoShown) {
            autoShown = false
            _visible.value = false
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

