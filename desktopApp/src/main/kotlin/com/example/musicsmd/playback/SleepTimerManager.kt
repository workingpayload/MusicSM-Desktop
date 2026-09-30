package com.example.musicsmd.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.SurfaceHigh
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

/** Frosted sleep-timer picker used by Now Playing. */
@Composable
fun SleepTimerPopup(
    state: SleepTimerState,
    onPick: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        // Popups render in their own layer where the Haze backdrop can't blur, so use a solid fill.
        GlassPanel(shape = RoundedCornerShape(28.dp), tint = SurfaceHigh.copy(alpha = 0.97f)) {
            Column(modifier = Modifier.width(360.dp).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null, tint = Coral)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sleep timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.isActive) "Stops in ${formatSleepRemaining(state)}" else "Pick when playback should pause",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.isActive) {
                        IconButton(onClick = { onCancel(); onDismiss() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel sleep timer")
                        }
                    }
                }
                Spacer(Modifier.size(16.dp))
                val rows = listOf(listOf(5, 10, 15), listOf(30, 45, 60))
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        row.forEach { minutes ->
                            TimerChip("$minutes min", modifier = Modifier.weight(1f)) {
                                onPick(minutes)
                                onDismiss()
                            }
                        }
                    }
                }
                TimerChip(
                    label = "End of current track",
                    selected = state.endOfTrack,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    onEndOfTrack()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun TimerChip(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}
