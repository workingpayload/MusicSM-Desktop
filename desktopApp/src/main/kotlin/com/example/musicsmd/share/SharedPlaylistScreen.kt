package com.example.musicsmd.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.musicsmd.ui.components.PlaceholderScreen

@Composable
fun SharedPlaylistScreen(payload: String, modifier: Modifier = Modifier) {
    PlaceholderScreen("Shared playlist", modifier)
}