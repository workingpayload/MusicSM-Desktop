package com.example.musicsmd.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.musicsmd.playback.AudioOutputDeviceInfo
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.SurfaceHigh

/** Glass popup that lists libVLC audio output devices and checks the selected one. */
@Composable
fun OutputPickerPopup(
    devices: List<AudioOutputDeviceInfo>,
    selectedDeviceId: String?,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        // Popups render in their own layer where the Haze backdrop can't blur, so use a solid fill.
        GlassPanel(shape = RoundedCornerShape(28.dp), tint = SurfaceHigh.copy(alpha = 0.97f)) {
            Column(modifier = Modifier.width(380.dp).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speaker, contentDescription = null, tint = Coral)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Audio output", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Choose a libVLC output device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh outputs") }
                }
                if (devices.isEmpty()) {
                    Text(
                        "No switchable output devices reported by VLC.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    devices.forEach { device ->
                        val selected = selectedDeviceId == device.id || (selectedDeviceId == null && device.isCurrent)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelect(device.id)
                                onDismiss()
                            }.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Speaker, contentDescription = null, tint = if (selected) Coral else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                device.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) Icon(Icons.Filled.Check, contentDescription = "Current output", tint = Coral)
                        }
                    }
                }
            }
        }
    }
}
