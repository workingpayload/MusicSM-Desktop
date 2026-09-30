package com.example.musicsmd.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.GlassFillStrong
import java.util.concurrent.TimeUnit

/**
 * Full-screen "Now Playing" — desktop counterpart of the mobile app's expanded player sheet.
 * A heavily blurred, darkened copy of the artwork fills the background (the mobile app's
 * `AmbientScreen`/`MotionArtwork` treatment); the controls sit in a glass panel on top.
 */
@Composable
fun NowPlayingScreen(
    state: PlaybackUiState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleQueue: () -> Unit,
) {
    val song = state.currentSong ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        ArtworkImage(
            url = song.artworkUrl,
            size = 900.dp,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxSize().blur(80.dp),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.75f))),
            ),
        )

        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onCollapse) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White) }
            }

            Spacer(modifier = Modifier.height(24.dp))
            ArtworkImage(url = song.artworkUrl, size = 320.dp, shape = RoundedCornerShape(20.dp))
            Spacer(modifier = Modifier.height(32.dp))

            Text(song.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))

            Spacer(modifier = Modifier.height(24.dp))

            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tint = GlassFillStrong,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Slider(
                        value = state.positionMs.toFloat(),
                        valueRange = 0f..(state.durationMs.toFloat().coerceAtLeast(1f)),
                        onValueChange = { onSeek(it.toLong()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(formatMs(state.positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onToggleLike) {
                            Icon(
                                if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (state.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = onTogglePlayPause) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.padding(8.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = onToggleQueue) { Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = state.volume.toFloat(),
                            valueRange = 0f..100f,
                            onValueChange = { onVolumeChange(it.toInt()) },
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun formatMs(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
