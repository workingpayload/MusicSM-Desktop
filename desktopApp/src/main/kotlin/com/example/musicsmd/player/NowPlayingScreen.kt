package com.example.musicsmd.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsmd.audio.OutputPickerPopup
import com.example.musicsmd.lyrics.LyricsPanel
import com.example.musicsmd.lyrics.LyricsUiState
import com.example.musicsmd.playback.SleepTimerPopup
import com.example.musicsmd.playback.formatSleepRemaining
import com.example.musicsmd.settings.RepeatMode
import com.example.musicsmd.ui.components.AppleSeekBar
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFillStrong

/**
 * Full-screen "Now Playing" — desktop counterpart of the mobile app's expanded player sheet.
 * A heavily blurred, darkened copy of the artwork fills the background (the mobile app's
 * `AmbientScreen`/`MotionArtwork` treatment); the controls sit in a glass panel on top.
 */
@Composable
fun NowPlayingScreen(
    state: PlaybackUiState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onStartSleepTimerAtEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onSelectAudioOutput: (String) -> Unit,
    onOpenEqualizer: () -> Unit,
    showLyrics: Boolean,
    lyricsState: LyricsUiState,
    lyricsOffsetMs: Long,
    onToggleLyrics: () -> Unit,
    onCloseLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onSetLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
) {
    val song = state.currentSong ?: return
    var showSleepTimer by remember { mutableStateOf(false) }
    var showOutputPicker by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        ArtworkImage(
            url = song.artworkUrl,
            size = 900.dp,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxSize().blur(80.dp),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.75f))),
            ),
        )

        Row(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onCollapse) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White) }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleLyrics) {
                    Icon(
                        Icons.Filled.Lyrics,
                        contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics",
                        tint = if (showLyrics) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }

            // Artwork takes whatever height the controls leave, so nothing is clipped in short windows.
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val artSize = minOf(maxWidth, maxHeight, 360.dp)
                ArtworkImage(url = song.artworkUrl, size = artSize, shape = RoundedCornerShape(20.dp))
            }

            Text(song.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(16.dp))

            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tint = GlassFillStrong,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AppleSeekBar(
                        progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                        durationMs = state.durationMs,
                        onSeek = { fraction -> onSeek((fraction * state.durationMs).toLong()) },
                        playing = state.isPlaying,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onToggleLike) {
                            Icon(
                                if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (state.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onToggleShuffle) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffle) Coral else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = onTogglePlayPause) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.padding(8.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = onCycleRepeat) {
                            Icon(
                                if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                contentDescription = "Repeat",
                                tint = if (state.repeatMode != RepeatMode.OFF) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onToggleQueue) { Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { showSleepTimer = true }) {
                            Icon(
                                Icons.Filled.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (state.sleepTimer.isActive) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRefreshAudioOutputs(); showOutputPicker = true }) {
                            Icon(Icons.Filled.Speaker, contentDescription = "Audio output", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onOpenEqualizer) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.sleepTimer.isActive) {
                            Text(
                                formatSleepRemaining(state.sleepTimer),
                                style = MaterialTheme.typography.labelMedium,
                                color = Coral,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Speed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            Text(
                                "${speed}x",
                                color = if (kotlin.math.abs(state.playbackSpeed - speed) < 0.01f) Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clickable { onPlaybackSpeedChange(speed) }.padding(horizontal = 4.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        AppleSeekBar(
                            progress = state.volume / 100f,
                            durationMs = 0L,
                            onSeek = { fraction -> onVolumeChange((fraction * 100).toInt()) },
                            showLabels = false,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (showSleepTimer) {
                        SleepTimerPopup(
                            state = state.sleepTimer,
                            onPick = onStartSleepTimer,
                            onEndOfTrack = onStartSleepTimerAtEndOfTrack,
                            onCancel = onCancelSleepTimer,
                            onDismiss = { showSleepTimer = false },
                        )
                    }
                    if (showOutputPicker) {
                        OutputPickerPopup(
                            devices = state.audioOutputDevices,
                            selectedDeviceId = state.audioOutputDevice,
                            onRefresh = onRefreshAudioOutputs,
                            onSelect = onSelectAudioOutput,
                            onDismiss = { showOutputPicker = false },
                        )
                    }
                }
            }
        }
            if (showLyrics) {
                LyricsPanel(
                    lyricsState = lyricsState,
                    positionMs = state.positionMs,
                    song = song,
                    lyricsOffsetMs = lyricsOffsetMs,
                    onSeekMs = onSeek,
                    onClose = onCloseLyrics,
                    onAdjustLyricsOffset = onAdjustLyricsOffset,
                    onSetLyricsOffset = onSetLyricsOffset,
                    onResetLyricsOffset = onResetLyricsOffset,
                    modifier = Modifier.weight(0.9f).fillMaxHeight(),
                )
            }
        }
    }
}
