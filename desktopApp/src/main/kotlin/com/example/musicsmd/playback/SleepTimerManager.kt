package com.example.musicsmd.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Snapshot of the desktop sleep timer for controls and countdown labels. */
data class SleepTimerState(
    val isActive: Boolean = false,
    val endOfTrack: Boolean = false,
    val remainingMs: Long = 0L,
)

/** Pauses playback after a countdown or at the end of the current track, with optional fade-out. */
class SleepTimerManager(
    private val scope: CoroutineScope,
    private val fadeOutEnabled: () -> Boolean,
    private val currentVolume: () -> Int,
    private val setVolume: (Int) -> Unit,
    private val pausePlayback: () -> Unit,
) {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null
    private var volumeBeforeFade: Int? = null

    fun start(minutes: Int) {
        if (minutes <= 0) {
            cancel()
            return
        }
        startCountdown(System.currentTimeMillis() + minutes * 60_000L)
    }

    fun startAtEndOfTrack() {
        job?.cancel()
        restoreVolume()
        _state.value = SleepTimerState(isActive = true, endOfTrack = true)
    }

    fun cancel() {
        job?.cancel()
        job = null
        restoreVolume()
        _state.value = SleepTimerState()
    }

    fun shouldStopAtEndOfTrack(): Boolean = _state.value.isActive && _state.value.endOfTrack

    fun finishEndOfTrack() {
        if (!shouldStopAtEndOfTrack()) return
        job?.cancel()
        job = null
        restoreVolume()
        _state.value = SleepTimerState()
    }

    private fun startCountdown(endsAtMs: Long) {
        job?.cancel()
        restoreVolume()
        job = scope.launch {
            while (isActive) {
                val remaining = endsAtMs - System.currentTimeMillis()
                if (remaining <= 0L) break
                _state.value = SleepTimerState(isActive = true, remainingMs = remaining)
                if (fadeOutEnabled() && remaining <= FADE_MS) {
                    val base = volumeBeforeFade ?: currentVolume().also { volumeBeforeFade = it }
                    val target = (base * (remaining.toFloat() / FADE_MS)).toInt().coerceIn(0, 100)
                    setVolume(target)
                    delay(200L)
                } else {
                    delay(minOf(500L, remaining))
                }
            }
            fire()
        }
    }

    private fun fire() {
        pausePlayback()
        restoreVolume()
        job = null
        _state.value = SleepTimerState()
    }

    private fun restoreVolume() {
        volumeBeforeFade?.let(setVolume)
        volumeBeforeFade = null
    }

    private companion object {
        const val FADE_MS = 8_000L
    }
}

fun formatSleepRemaining(state: SleepTimerState): String = when {
    !state.isActive -> ""
    state.endOfTrack -> "End of track"
    else -> {
        val totalSeconds = (state.remainingMs + 999) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        "%d:%02d".format(minutes, seconds)
    }
}
