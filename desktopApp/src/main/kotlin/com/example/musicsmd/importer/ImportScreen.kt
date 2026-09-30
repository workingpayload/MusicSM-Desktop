package com.example.musicsmd.importer

import com.example.musicsmd.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.repository.ImportResult
import com.example.musicsm.domain.repository.LibraryRepository
import com.example.musicsm.domain.repository.PlaylistImportRepository
import com.example.musicsmd.ui.components.GlassPanel
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    repository: PlaylistImportRepository,
    libraryRepository: LibraryRepository,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var nameOverride by remember { mutableStateOf("") }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var displayName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Import playlist", style = MaterialTheme.typography.headlineSmall)
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Paste a public Spotify, Apple Music, YouTube or YouTube Music playlist link.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(onClick = {}, label = { Text(detectService(link)) }, icon = { Icon(Icons.Filled.Link, contentDescription = null) })
                }
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; result = null; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !importing,
                    label = { Text("Playlist link") },
                )
                OutlinedTextField(
                    value = nameOverride,
                    onValueChange = { nameOverride = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !importing,
                    label = { Text("Name override (optional)") },
                )
                Button(
                    enabled = link.isNotBlank() && !importing,
                    onClick = {
                        importing = true
                        result = null
                        error = null
                        done = 0
                        total = 0
                        scope.launch {
                            val imported = repository.importFromLink(link.trim()) { d, t ->
                                done = d
                                total = t
                            }
                            imported.onSuccess { res ->
                                val override = nameOverride.trim().takeIf { it.isNotBlank() }
                                if (override != null) libraryRepository.renamePlaylist(res.playlistId, override)
                                result = if (override != null) res.copy(name = override) else res
                                displayName = override ?: res.name
                            }.onFailure { failure ->
                                error = failure.message ?: "Import failed"
                            }
                            importing = false
                        }
                    },
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Import")
                }
            }
        }

        if (importing) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text(if (total > 0) "Matching $done of $total tracks" else "Reading playlist…")
                    }
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        result?.let { res ->
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Imported \"${displayName ?: res.name}\"", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Matched ${res.matched} of ${res.total} tracks")
                    Text("Unmatched tracks: ${(res.total - res.matched).coerceAtLeast(0)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { onOpenPlaylist(res.playlistId) }) { Text("Open playlist") }
                }
            }
        }
    }
}

private fun detectService(text: String): String {
    val lower = text.lowercase()
    return when {
        lower.contains("spotify") || lower.startsWith("spotify:") -> "Spotify"
        lower.contains("apple.com") || lower.contains("music.apple") || lower.startsWith("pl.") -> "Apple Music"
        lower.contains("youtube") || lower.contains("youtu.be") || Regex("^(VL)?(PL|OLAK5uy_|RD|UU|FL)").containsMatchIn(text) -> "YouTube"
        text.isBlank() -> "Waiting for link"
        else -> "Unknown service"
    }
}
