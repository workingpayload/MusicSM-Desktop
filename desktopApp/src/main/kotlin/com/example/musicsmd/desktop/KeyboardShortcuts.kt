package com.example.musicsmd.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.example.musicsmd.player.AppViewModel

/** Handles desktop-only playback shortcuts at the Window level. */
fun handleMusicShortcut(event: KeyEvent, viewModel: AppViewModel, onEscape: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val playback = viewModel.playback.value
    return when {
        event.key == Key.Spacebar && !event.isCtrlPressed && !event.isShiftPressed && playback.currentSong != null -> {
            viewModel.togglePlayPause(); true
        }
        event.key == Key.Escape -> {
            onEscape(); true
        }
        event.isCtrlPressed && event.key == Key.DirectionRight -> {
            viewModel.playNextInQueue(); true
        }
        event.isCtrlPressed && event.key == Key.DirectionLeft -> {
            viewModel.playPreviousInQueue(); true
        }
        event.isShiftPressed && event.key == Key.DirectionRight -> {
            viewModel.seekTo(playback.positionMs + 10_000L); true
        }
        event.isShiftPressed && event.key == Key.DirectionLeft -> {
            viewModel.seekTo(playback.positionMs - 10_000L); true
        }
        event.isCtrlPressed && event.key == Key.DirectionUp -> {
            viewModel.setVolume(playback.volume + 5); true
        }
        event.isCtrlPressed && event.key == Key.DirectionDown -> {
            viewModel.setVolume(playback.volume - 5); true
        }
        event.isCtrlPressed && event.key == Key.L -> {
            viewModel.toggleLikeCurrent(); true
        }
        event.isCtrlPressed && event.key == Key.S -> {
            viewModel.toggleShuffle(); true
        }
        event.isCtrlPressed && event.key == Key.R -> {
            viewModel.cycleRepeat(); true
        }
        else -> false
    }
}
