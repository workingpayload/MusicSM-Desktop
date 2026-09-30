package com.example.musicsmd.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsmd.playback.AudioOutputDeviceInfo
import com.example.musicsmd.ui.theme.Coral

/**
 * The audio-output list shown inside a [com.example.musicsmd.ui.components.LiquidGlassSheet] —
 * the mobile app's `OutputPickerSheet` content, listing libVLC's output devices.
 */
@Composable
fun ColumnScope.OutputPickerContent(
    devices: List<AudioOutputDeviceInfo>,
    selectedDeviceId: String?,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            "Audio output",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClickLabel = "Refresh outputs", onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = "Refresh outputs",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp),
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    if (devices.isEmpty()) {
        Text(
            "No switchable output devices reported by VLC.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
        )
    } else {
        val currentId = currentOutputId(devices, selectedDeviceId)
        devices.forEach { device ->
            OutputDeviceRow(name = device.name, isCurrent = device.id == currentId, onClick = { onSelect(device.id) })
        }
    }
}

/**
 * The device audio is going to: the user's pick, else what libVLC reports, else its first entry —
 * libVLC lists the system default first and reports no current device while it is in use.
 */
fun currentOutputId(devices: List<AudioOutputDeviceInfo>, selectedDeviceId: String?): String? =
    selectedDeviceId?.takeIf { id -> devices.any { it.id == id } }
        ?: devices.firstOrNull { it.isCurrent }?.id
        ?: devices.firstOrNull()?.id

@Composable
private fun OutputDeviceRow(name: String, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isCurrent) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = !isCurrent, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isCurrent) Coral else Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = outputIcon(name),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isCurrent) {
            Icon(Icons.Filled.Check, contentDescription = "Current output", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
    Spacer(Modifier.height(4.dp))
}

/** libVLC only reports names, so the kind is guessed from them the way the mobile icons read. */
private fun outputIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return if ("headphone" in lower || "headset" in lower || "earbud" in lower || "buds" in lower) {
        Icons.Outlined.Headphones
    } else {
        Icons.Outlined.Speaker
    }
}
