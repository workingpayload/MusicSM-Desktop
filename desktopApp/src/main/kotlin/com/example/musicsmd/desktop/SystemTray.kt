package com.example.musicsmd.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import com.example.musicsmd.player.PlaybackUiState

/** MusicSM system tray menu; close-to-tray is controlled by desktop settings in Main. */
@Composable
fun ApplicationScope.MusicSmTray(
    playback: PlaybackUiState,
    onShow: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onQuit: () -> Unit,
) {
    val song = playback.currentSong
    Tray(
        state = rememberTrayState(),
        icon = rememberVectorPainter(Icons.Filled.MusicNote),
        tooltip = song?.let { "${it.title} — ${it.artist}" } ?: "MusicSM Desktop",
        onAction = onShow,
        menu = {
            Item(if (playback.isPlaying) "Pause" else "Play", onClick = onPlayPause)
            Item("Next", onClick = onNext, enabled = song != null)
            Item("Previous", onClick = onPrevious, enabled = song != null)
            Separator()
            Item("Show MusicSM", onClick = onShow)
            Item("Quit", onClick = onQuit)
        },
    )
}
