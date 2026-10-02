package com.example.musicsmd.motionart

import com.example.motionart.MotionArt
import com.example.motionart.MotionArtProvider
import com.example.musicsmd.player.PlaybackUiState
import com.example.musicsmd.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class MotionArtUiState(val songId: String? = null, val art: MotionArt? = null)

class MotionArtController(
    playback: StateFlow<PlaybackUiState>,
    settingsStore: SettingsStore,
    repository: DesktopMotionArtRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val visible = MutableStateFlow(false)
    private val _state = MutableStateFlow(MotionArtUiState())
    val state: StateFlow<MotionArtUiState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(
                playback.map { it.currentSong }.distinctUntilChanged(),
                settingsStore.settings.map {
                    it.animatedArtwork to MotionArtProvider.fromName(it.animatedArtworkSource)
                }.distinctUntilChanged(),
                visible,
            ) { song, settings, shown ->
                song.takeIf { settings.first && shown } to settings.second
            }.distinctUntilChanged().collectLatest { (song, source) ->
                _state.value = MotionArtUiState(song?.id)
                if (song != null) {
                    try {
                        val art = repository.forSong(song, source)
                        currentCoroutineContext().ensureActive()
                        _state.value = MotionArtUiState(song.id, art)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        System.err.println("Animated artwork lookup failed for ${song.id}: ${error.message}")
                    }
                }
            }
        }
    }

    fun setVisible(shown: Boolean) {
        visible.value = shown
    }

    fun dispose() {
        scope.cancel()
    }
}
