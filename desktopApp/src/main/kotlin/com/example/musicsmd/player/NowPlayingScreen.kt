package com.example.musicsmd.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.musicsmd.audio.OutputPickerContent
import com.example.musicsmd.audio.currentOutputId
import com.example.musicsmd.lyrics.LyricsPanel
import com.example.musicsmd.lyrics.LyricsUiState
import com.example.musicsmd.playback.SleepTimerContent
import com.example.musicsmd.playback.formatSleepRemaining
import com.example.musicsmd.settings.RepeatMode
import com.example.musicsmd.ui.components.AppleSeekBar
import com.example.musicsmd.ui.components.ArtworkImage
import com.example.musicsmd.ui.components.LiquidGlassSheet
import com.example.musicsmd.ui.components.LocalHazeState
import com.example.musicsmd.ui.components.PlayPauseButton
import com.example.musicsmd.ui.components.accentColorFor
import com.example.musicsmd.ui.components.glassBackdrop
import com.example.musicsmd.ui.components.rememberDominantColorState
import com.example.musicsmd.ui.components.rememberHazeState
import com.example.musicsmd.ui.theme.AppBackground
import com.example.musicsmd.ui.theme.asThemeAccent
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.OnDarkVariant
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Full-screen "Now Playing", laid out like the mobile app's expanded player: a blurred copy of the
 * artwork behind everything, the cover, title row, the Apple-style scrubber, transport controls in
 * a frosted card around the hue-tinted glass [PlayPauseButton], a glassy volume track, and a
 * lyrics / output / queue row. The whole screen is recorded twice — as the Haze source the play
 * button frosts, and as the Liquid Glass backdrop the sleep-timer and output sheets refract.
 *
 * Desktop additions sit in the top bar (sleep timer, equalizer, playback speed) where mobile has
 * its overflow sheet, and synced lyrics open beside the artwork rather than over it.
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
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onStartSleepTimerAtEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onSelectAudioOutput: (String) -> Unit,
    onOpenEqualizer: () -> Unit,
    showLyrics: Boolean,
    lyricsState: LyricsUiState,
    lyricsOffsetMs: Long,
    onToggleLyrics: () -> Unit,
    onCloseLyrics: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onSetLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
) {
    val song = state.currentSong ?: return
    var showSleepTimer by remember { mutableStateOf(false) }
    var showOutputPicker by remember { mutableStateOf(false) }

    val haze = rememberHazeState()
    val liquidBackdrop = rememberLayerBackdrop()
    // So the output pill names the real device the moment the player opens, as on mobile.
    LaunchedEffect(Unit) { onRefreshAudioOutputs() }
    val accent = rememberDominantColorState(url = song.artworkUrl, fallback = accentColorFor(song.id))
    // Foreground accents (volume fill, labels) need to read on the dark player: a near-black cover
    // swatch that works as a background wash would vanish as a bar. Hueless covers keep the theme
    // accent. Background tints below still use the raw swatch, as on mobile.
    val themeAccent = Coral
    val readableAccent = accent.value.asThemeAccent() ?: themeAccent

    Box(modifier = Modifier.fillMaxSize()) {
        // Everything on screen, recorded for the sheets' Liquid Glass to refract.
        Box(Modifier.fillMaxSize().layerBackdrop(liquidBackdrop)) {
            // Backdrop layers registered as the blur source so the glass play button can frost them.
            Box(Modifier.matchParentSize().glassBackdrop(haze)) {
                // Opaque base so the player is never see-through over the content behind it.
                Box(Modifier.matchParentSize().background(AppBackground))
                if (!song.artworkUrl.isNullOrEmpty()) {
                    Box(Modifier.matchParentSize().clipToBounds()) {
                        AsyncImage(
                            model = song.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = BACKDROP_SCALE
                                    scaleY = BACKDROP_SCALE
                                }
                                .blur(60.dp),
                        )
                    }
                }
                // Dominant-colour tint + vertical darkening for legibility (colour read in draw phase),
                // as on mobile's non-immersive layout.
                Box(
                    Modifier.matchParentSize().drawBehind {
                        drawRect(accent.value.copy(alpha = 0.35f))
                        drawRect(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.20f),
                                0.6f to Color.Black.copy(alpha = 0.45f),
                                1.0f to Color.Black.copy(alpha = 0.80f),
                            ),
                        )
                    },
                )
            }

            CompositionLocalProvider(LocalHazeState provides haze) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        PlayerColumn(
                            state = state,
                            accent = readableAccent,
                            showLyrics = showLyrics,
                            onCollapse = onCollapse,
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onSeek = onSeek,
                            onToggleLike = onToggleLike,
                            onVolumeChange = onVolumeChange,
                            onToggleQueue = onToggleQueue,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeat = onCycleRepeat,
                            onPlaybackSpeedChange = onPlaybackSpeedChange,
                            onOpenSleepTimer = { showSleepTimer = true },
                            onOpenOutputPicker = {
                                onRefreshAudioOutputs()
                                showOutputPicker = true
                            },
                            onOpenEqualizer = onOpenEqualizer,
                            onToggleLyrics = onToggleLyrics,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                    if (showLyrics) {
                        LyricsPanel(
                            lyricsState = lyricsState,
                            positionMs = state.positionMs,
                            song = song,
                            lyricsOffsetMs = lyricsOffsetMs,
                            onSeekMs = onSeek,
                            onClose = onCloseLyrics,
                            onAdjustLyricsOffset = onAdjustLyricsOffset,
                            onSetLyricsOffset = onSetLyricsOffset,
                            onResetLyricsOffset = onResetLyricsOffset,
                            modifier = Modifier.weight(0.9f).fillMaxHeight(),
                        )
                    }
                }
            }
        }

        // Over the content and outside the recorded layer, so the glass refracts the screen, not itself.
        LiquidGlassSheet(
            visible = showOutputPicker,
            backdrop = liquidBackdrop,
            onDismiss = { showOutputPicker = false },
        ) {
            OutputPickerContent(
                devices = state.audioOutputDevices,
                selectedDeviceId = state.audioOutputDevice,
                onRefresh = onRefreshAudioOutputs,
                onSelect = { id ->
                    onSelectAudioOutput(id)
                    showOutputPicker = false
                },
            )
        }
        LiquidGlassSheet(
            visible = showSleepTimer,
            backdrop = liquidBackdrop,
            onDismiss = { showSleepTimer = false },
        ) {
            SleepTimerContent(
                state = state.sleepTimer,
                onPick = onStartSleepTimer,
                onEndOfTrack = onStartSleepTimerAtEndOfTrack,
                onCancel = onCancelSleepTimer,
                onDismiss = { showSleepTimer = false },
            )
        }
    }
}

@Composable
private fun PlayerColumn(
    state: PlaybackUiState,
    accent: Color,
    showLyrics: Boolean,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenOutputPicker: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong ?: return
    Column(modifier = modifier.widthIn(max = PLAYER_MAX_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        // Top bar: close on the left; desktop's sleep timer, EQ and speed where mobile has its overflow.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (state.sleepTimer.isActive) {
                Text(
                    formatSleepRemaining(state.sleepTimer),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenSleepTimer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Text(
                "${formatSpeed(state.playbackSpeed)}×",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (state.playbackSpeed != 1f) accent else Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onPlaybackSpeedChange(nextSpeed(state.playbackSpeed)) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            IconButton(onClick = onOpenSleepTimer) {
                Icon(Icons.Filled.Bedtime, contentDescription = "Sleep timer", tint = if (state.sleepTimer.isActive) accent else Color.White)
            }
            IconButton(onClick = onOpenEqualizer) {
                Icon(Icons.Filled.GraphicEq, contentDescription = "Equalizer", tint = Color.White)
            }
        }

        // Apple Music-style motion: art springs large while playing, shrinks when paused, with a
        // subtle continuous "breathing" so it never feels static.
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val artSize = minOf(maxWidth, maxHeight, 420.dp)
            val playing = state.isPlaying
            val artScale by animateFloatAsState(
                targetValue = if (playing) 1f else 0.82f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "artScale",
            )
            val breath by rememberInfiniteTransition(label = "artBreathe").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
                label = "breath",
            )
            val finalScale = artScale * (1f + if (playing) 0.012f * breath else 0f)
            val artShape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .size(artSize)
                    .graphicsLayer {
                        scaleX = finalScale
                        scaleY = finalScale
                    }
                    .shadow(elevation = 24.dp, shape = artShape)
                    .clip(artShape),
            ) {
                ArtworkImage(url = song.artworkUrl, size = artSize, shape = artShape)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDarkVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (state.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        AppleSeekBar(
            progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
            durationMs = state.durationMs,
            onSeek = { fraction -> onSeek((fraction * state.durationMs).toLong()) },
            playing = state.isPlaying,
        )

        Spacer(Modifier.height(16.dp))

        // Controls, in a frosted card.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else Color.White)
            }
            IconButton(onClick = onPrevious, enabled = state.queueIndex > 0 || state.positionMs > 3_000) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            // Play/pause, ringed by a progress indicator while the stream buffers.
            Box(contentAlignment = Alignment.Center) {
                PlayPauseButton(isPlaying = state.isPlaying, onClick = onTogglePlayPause, size = 72.dp)
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.85f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(84.dp),
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassyVolume(volume = state.volume, accent = accent, onVolumeChange = onVolumeChange)

        Spacer(Modifier.height(10.dp))

        val secondaryTint = Color.White.copy(alpha = 0.72f)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onToggleLyrics) {
                Icon(Icons.Filled.Lyrics, contentDescription = "Lyrics", tint = if (showLyrics) MaterialTheme.colorScheme.primary else secondaryTint)
            }
            Box(Modifier.weight(1f)) {
                AudioOutputIndicator(state = state, onClick = onOpenOutputPicker)
            }
            IconButton(onClick = onToggleQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = secondaryTint)
            }
        }
    }
}

/** Mobile's volume slider: a glassy 8 dp track with a hairline rim, tinted by the album-art accent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassyVolume(volume: Int, accent: Color, onVolumeChange: (Int) -> Unit) {
    val volAccent = accent
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Slider(
            value = volume / 100f,
            onValueChange = { onVolumeChange((it * 100).toInt()) },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(volAccent)
                        .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                )
            },
            track = { sliderState ->
                val fraction = sliderState.value.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(volAccent.copy(alpha = 0.55f), volAccent))),
                    )
                }
            },
        )
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
    }
}

/** The current output's name as a pill, as on mobile; opens the Liquid Glass output sheet. */
@Composable
private fun AudioOutputIndicator(state: PlaybackUiState, onClick: () -> Unit) {
    val currentId = currentOutputId(state.audioOutputDevices, state.audioOutputDevice)
    val name = state.audioOutputDevices.firstOrNull { it.id == currentId }?.name ?: "Choose output"
    val headphones = name.lowercase().let { "headphone" in it || "headset" in it || "buds" in it }
    val tint = Color.White.copy(alpha = 0.72f)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = "Audio output", onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (headphones) Icons.Outlined.Headphones else Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun nextSpeed(current: Float): Float {
    val index = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return SPEEDS[(index + 1).mod(SPEEDS.size)]
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "%.1f".format(speed) else speed.toString().trimEnd('0')

/** Oversized so the blur never shows a hard edge at the window border. */
private const val BACKDROP_SCALE = 1.2f

/** A player column wider than this reads as stretched; lyrics take the rest of the width. */
private val PLAYER_MAX_WIDTH = 640.dp
