package com.example.musicsmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicsmd.nav.Screen

/** Spotify-style left navigation rail: Home / Search / Library. */
@Composable
fun NavRail(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationRail(modifier = Modifier.fillMaxHeight().width(88.dp)) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            NavigationRailItem(
                selected = current == Screen.Home,
                onClick = { onSelect(Screen.Home) },
                icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                label = { Text("Home") },
            )
            NavigationRailItem(
                selected = current == Screen.Search,
                onClick = { onSelect(Screen.Search) },
                icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                label = { Text("Search") },
            )
            NavigationRailItem(
                selected = current == Screen.Library,
                onClick = { onSelect(Screen.Library) },
                icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Library") },
                label = { Text("Library") },
            )
        }
    }
}
