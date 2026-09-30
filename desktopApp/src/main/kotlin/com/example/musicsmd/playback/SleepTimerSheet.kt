package com.example.musicsmd.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.musicsmd.ui.theme.Coral

/**
 * Sleep-timer choices, shown inside a [com.example.musicsmd.ui.components.LiquidGlassSheet] so it
 * is the same piece of glass as the output picker.
 */
@Composable
fun ColumnScope.SleepTimerContent(
    state: SleepTimerState,
    onPick: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            "Sleep timer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (state.isActive) {
            Text(
                "Stops in ${formatSleepRemaining(state)}",
                style = MaterialTheme.typography.bodySmall,
                color = Coral,
                modifier = Modifier.padding(end = 10.dp),
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClickLabel = "Cancel sleep timer") { onCancel(); onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Cancel sleep timer", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    listOf(listOf(5, 10, 15), listOf(30, 45, 60)).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            row.forEach { minutes ->
                TimerChip("$minutes min", modifier = Modifier.weight(1f)) {
                    onPick(minutes)
                    onDismiss()
                }
            }
        }
    }
    TimerChip(
        label = "End of current track",
        selected = state.endOfTrack,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        onEndOfTrack()
        onDismiss()
    }
}

/** Same pill as the output picker's selected row, so both sheets read as one design. */
@Composable
private fun TimerChip(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Coral else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}
