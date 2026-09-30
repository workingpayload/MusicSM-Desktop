package com.example.musicsmd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.musicsmd.home.HomeScreen
import com.example.musicsmd.player.AppViewModel
import com.example.musicsmd.player.PlayerBar

/** Dark theme to match the mobile app, which forces dark mode (see `ui/theme/Theme.kt`). */
private val MusicSmDarkColors = darkColorScheme()

@Composable
fun App(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val playback by viewModel.playback.collectAsState()

    MaterialTheme(colorScheme = MusicSmDarkColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    HomeScreen(
                        state = uiState,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = viewModel::search,
                        onSongClick = { song, queue -> viewModel.play(song, queue) },
                    )
                }
                PlayerBar(
                    state = playback,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNextInQueue,
                    onPrevious = viewModel::playPreviousInQueue,
                    onSeek = viewModel::seekTo,
                )
            }
        }
    }
}

fun main() {
    AppGraph.init()
    val viewModel = AppViewModel(musicSource = AppGraph.musicSource, player = AppGraph.playerController)

    application {
        val windowState = rememberWindowState()
        Window(
            onCloseRequest = {
                viewModel.dispose()
                exitApplication()
            },
            state = windowState,
            title = "MusicSM Desktop",
        ) {
            App(viewModel)
        }
    }
}
