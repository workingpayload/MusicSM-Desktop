package com.example.musicsmd.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.Playlist
import com.example.musicsm.domain.share.PlaylistShareCodec
import com.example.musicsm.domain.share.SharedPlaylist
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun PlaylistShareDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
) {
    val link = remember(playlist.id, playlist.songs) {
        PlaylistShareCodec.shareUrl(SharedPlaylist(playlist.name, playlist.songs))
    }
    val bitmap = remember(link) { qrImageBitmap(link) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share ${playlist.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${playlist.songs.size} songs encoded in this link and QR code.")
                Spacer(Modifier.height(12.dp))
                if (bitmap != null) {
                    Box(
                        modifier = Modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(bitmap = bitmap, contentDescription = "Playlist QR", modifier = Modifier.size(260.dp))
                    }
                } else {
                    Text("This playlist is too large for a QR code. Copy the link instead.")
                }
                Spacer(Modifier.height(12.dp))
                Text(link, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    copyTextToClipboard(link)
                    message = "Copied link"
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Copy")
                }
                OutlinedButton(onClick = {
                    val dialog = FileDialog(null as Frame?, "Save QR as PNG", FileDialog.SAVE).apply {
                        file = "${playlist.name.ifBlank { "playlist" }.sanitizeFileName()}-qr.png"
                        isVisible = true
                    }
                    val selected = dialog.file
                    val dir = dialog.directory
                    if (selected != null && dir != null) {
                        saveQrPng(link, File(dir, selected))
                        message = "Saved QR"
                    }
                }, enabled = bitmap != null) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Save QR")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

private fun String.sanitizeFileName(): String = replace(Regex("[\\\\/:*?\"<>|]"), "_")
