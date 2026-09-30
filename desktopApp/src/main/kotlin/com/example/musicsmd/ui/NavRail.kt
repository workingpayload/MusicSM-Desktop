package com.example.musicsmd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.musicsmd.nav.Screen
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.GlassFillStrong

/** Spotify-style left navigation rail — a floating glass pane, not a flat Material rail. */
@Composable
fun NavRail(current: Screen, onSelect: (Screen) -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxHeight().width(96.dp).padding(vertical = 12.dp, horizontal = 8.dp),
        shape = RoundedCornerShape(28.dp),
        tint = GlassFillStrong,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(top = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailItem(Icons.Filled.Home, "Home", current == Screen.Home) { onSelect(Screen.Home) }
            RailItem(Icons.Filled.Search, "Search", current == Screen.Search) { onSelect(Screen.Search) }
            RailItem(Icons.Filled.LibraryMusic, "Library", current == Screen.Library) { onSelect(Screen.Library) }
            RailItem(Icons.Filled.Download, "Downloads", current == Screen.Downloads) { onSelect(Screen.Downloads) }
            RailItem(Icons.Filled.Folder, "Local", current == Screen.LocalMusic) { onSelect(Screen.LocalMusic) }
            RailItem(Icons.Filled.BarChart, "Stats", current == Screen.Stats) { onSelect(Screen.Stats) }
            Spacer(modifier = Modifier.weight(1f))
            RailItem(Icons.Filled.Settings, "Settings", current == Screen.Settings) { onSelect(Screen.Settings) }
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
