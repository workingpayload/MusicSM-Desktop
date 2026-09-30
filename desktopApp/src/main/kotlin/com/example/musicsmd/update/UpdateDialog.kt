package com.example.musicsmd.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicsmd.ui.theme.Coral

/** "A new version is out", as on mobile; it points to the download page rather than installing. */
@Composable
fun UpdateDialog(
    release: NewVersion,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = Coral) },
        title = { Text("Update available") },
        text = {
            Column(Modifier.widthIn(min = 360.dp, max = 460.dp)) {
                Text(
                    "MusicSM Desktop ${release.version} is out. You have ${AppVersion.current}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Download it from ${DOWNLOAD_PAGE_URL.removePrefix("https://")} and install it over this " +
                        "one. Your library and settings stay.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    // Release notes can be long, so cap the height and let them scroll.
                    Text(
                        text = plainNotes(release.notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDownload) { Text("Go to download page") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
}

/** GitHub's release notes are Markdown; this shows them as plain text, bullets kept. */
internal fun plainNotes(markdown: String): String =
    markdown.lines().joinToString("\n") { line ->
        line.replace("**", "")
            .replace(Regex("^#{1,6}\\s+"), "")
            .replace(Regex("^\\s*[*-]\\s+"), "• ")
    }.trim()
