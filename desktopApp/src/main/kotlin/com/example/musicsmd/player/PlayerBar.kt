package com.example.musicsmd.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsmd.settings.RepeatMode
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.GlassFillStrong
import com.example.musicsmd.ui.theme.OnDarkVariant

/**
 * Floating Liquid Glass mini-player — the mobile app's `MiniPlayer` card: a 60 dp pill with a round
 * artwork tile, title/artist, and transport buttons. Click the card to open the full player.
 *
 * Desktop keeps a few more controls in the same pill (like, shuffle, previous, repeat, lyrics,
 * queue) since there is width to spare and no swipe gestures; seeking lives in Now Playing, as on
 * mobile.
 */
@Composable
fun PlayerBar(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        tint = GlassFillStrong,
        liquid = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clickable(onClick = onExpand)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(url = song.artworkUrl, size = 44.dp, shape = CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val onSurface = MaterialTheme.colorScheme.onSurface
            val accent = MaterialTheme.colorScheme.primary
            IconButton(onClick = onToggleLike) {
                Icon(
                    if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (state.isLiked) accent else OnDarkVariant,
                )
            }
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffle) accent else OnDarkVariant)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = onSurface)
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = onSurface,
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = onSurface)
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != RepeatMode.OFF) accent else OnDarkVariant,
                )
            }
            IconButton(onClick = onShowLyrics) {
                Icon(Icons.Filled.Lyrics, contentDescription = "Lyrics", tint = OnDarkVariant)
            }
            IconButton(onClick = onToggleQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = OnDarkVariant)
            }
        }
    }
}
