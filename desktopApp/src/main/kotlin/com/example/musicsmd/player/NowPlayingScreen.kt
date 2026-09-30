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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsmd.ui.components.AppleSeekBar
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.GlassFillStrong

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
                    AppleSeekBar(
                        progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                        durationMs = state.durationMs,
                        onSeek = { fraction -> onSeek((fraction * state.durationMs).toLong()) },
                        playing = state.isPlaying,
                    )

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
                        AppleSeekBar(
                            progress = state.volume / 100f,
                            durationMs = 0L,
                            onSeek = { fraction -> onVolumeChange((fraction * 100).toInt()) },
                            showLabels = false,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
