package com.example.musicsmd.settings

import com.example.musicsmd.ui.components.LocalBottomBarPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsm.domain.model.LyricsSource
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.components.musicSmSliderColors
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFill
import com.example.musicsmd.ui.theme.GlassFillStrong
import com.example.musicsmd.ui.theme.OnAccent
import com.example.musicsmd.update.AppVersion
import java.awt.Desktop
import javax.swing.JFileChooser
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: DesktopSettings,
    onUpdate: ((DesktopSettings) -> DesktopSettings) -> Unit,
    onOpenEqualizer: () -> Unit,
    onClearListeningHistory: suspend () -> Unit,
    onClearSearchHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var confirmClearStats by remember { mutableStateOf(false) }
    var confirmClearSearch by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Tune MusicSM Desktop", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item { SectionTitle("Appearance") }
        item {
            SettingsCard {
                SettingsSwitch(
                    "Theme from artwork",
                    "Tint buttons, links and highlights with the current song's cover colour.",
                    settings.themeFromArtwork,
                ) {
                    onUpdate { it.copy(themeFromArtwork = it.themeFromArtwork.not()) }
                }
                SettingsSwitch(
                    "AMOLED black",
                    "Pure black backgrounds to save power on OLED screens.",
                    settings.amoled,
                ) {
                    onUpdate { it.copy(amoled = it.amoled.not()) }
                }
                SettingsSwitch(
                    "Cards on Home",
                    "Show Home's songs as rows of cards, like the phone app, instead of a list.",
                    settings.homeLayout == HomeLayout.CARDS,
                ) {
                    onUpdate {
                        it.copy(homeLayout = if (it.homeLayout == HomeLayout.CARDS) HomeLayout.LIST else HomeLayout.CARDS)
                    }
                }
            }
        }

        item { SectionTitle("Playback") }
        item {
            SettingsCard {
                SettingsSwitch("Autoplay radio", "Keep the queue going with related tracks.", settings.autoplayRadio) {
                    onUpdate { it.copy(autoplayRadio = it.autoplayRadio.not()) }
                }
                SettingsSwitch("Restore queue", "Remember your queue between launches.", settings.restoreQueue) {
                    onUpdate { it.copy(restoreQueue = it.restoreQueue.not()) }
                }
                SettingsSwitch("Sleep timer fade-out", "Gently lower volume near the timer end.", settings.sleepTimerFadeOut) {
                    onUpdate { it.copy(sleepTimerFadeOut = it.sleepTimerFadeOut.not()) }
                }
                SliderRow(
                    title = "Playback speed",
                    subtitle = "${String.format("%.2f", settings.playbackSpeed).trimEnd('0').trimEnd('.')}×",
                    value = settings.playbackSpeed,
                    valueRange = 0.5f..2.0f,
                    steps = 29,
                    onValueChange = { value -> onUpdate { it.copy(playbackSpeed = value) } },
                )
            }
        }

        item { SectionTitle("Search") }
        item {
            SettingsCard {
                SettingsSwitch("Videos in search", "Include music videos, covers, uploads and live cuts.", settings.searchVideos) {
                    onUpdate { it.copy(searchVideos = it.searchVideos.not()) }
                }
            }
        }

        item { SectionTitle("Lyrics") }
        item {
            SettingsCard {
                SettingsSwitch(
                    "Open lyrics automatically",
                    "Show the lyrics beside Now Playing whenever the song has them.",
                    settings.autoOpenLyrics,
                ) {
                    onUpdate { it.copy(autoOpenLyrics = it.autoOpenLyrics.not()) }
                }
                SettingsSwitch("Prefer word-synced lyrics", "Use karaoke-style lyrics when a source can provide them.", settings.preferWordSyncedLyrics) {
                    onUpdate { it.copy(preferWordSyncedLyrics = it.preferWordSyncedLyrics.not()) }
                }
                LyricsSourceList(
                    settings = settings,
                    onMove = { source, delta ->
                        val order = orderedLyrics(settings).toMutableList()
                        val from = order.indexOf(source)
                        val to = from + delta
                        if (from >= 0 && to in order.indices) {
                            order.add(to, order.removeAt(from))
                            onUpdate { it.copy(lyricsSourceOrder = order.map { src -> src.name }) }
                        }
                    },
                    onToggle = { source, enabled ->
                        onUpdate {
                            it.copy(
                                disabledLyricsSources = if (enabled) {
                                    it.disabledLyricsSources - source.name
                                } else {
                                    it.disabledLyricsSources + source.name
                                },
                            )
                        }
                    },
                )
            }
        }

        item { SectionTitle("Audio") }
        item {
            SettingsCard {
                SettingsRow("Equalizer", "Presets, preamp and 10-band tuning.", onClick = onOpenEqualizer)
                SettingsSwitch("Equalizer enabled", "Apply the saved equalizer preset.", settings.equalizerEnabled) {
                    onUpdate { it.copy(equalizerEnabled = it.equalizerEnabled.not()) }
                }
                SettingsRow("Output device", settings.audioOutputDevice ?: "System default", onClick = null)
            }
        }

        item { SectionTitle("Library & files") }
        item {
            SettingsCard {
                SettingsRow("Downloads folder", settings.downloadsDir, onClick = {
                    chooseDirectory(settings.downloadsDir)?.let { path -> onUpdate { it.copy(downloadsDir = path) } }
                })
                Text("Local music folders", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                settings.localMusicDirs.forEach { dir ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(dir, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Remove",
                            color = Coral,
                            modifier = Modifier.clip(CircleShape).clickable {
                                onUpdate { it.copy(localMusicDirs = it.localMusicDirs - dir) }
                            }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Text(
                    "Add folder",
                    color = OnAccent,
                    modifier = Modifier.clip(CircleShape).background(Coral).clickable {
                        chooseDirectory(AppPaths.defaultMusicDir.absolutePath)?.let { path ->
                            onUpdate { it.copy(localMusicDirs = (it.localMusicDirs + path).distinct()) }
                        }
                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        item { SectionTitle("Desktop") }
        item {
            SettingsCard {
                SettingsSwitch("Minimize to tray", "Keep MusicSM running when the window closes.", settings.minimizeToTray) {
                    onUpdate { it.copy(minimizeToTray = it.minimizeToTray.not()) }
                }
                SettingsSwitch("Global media keys", "Listen for play/pause/next/previous keys.", settings.mediaKeys) {
                    onUpdate { it.copy(mediaKeys = it.mediaKeys.not()) }
                }
            }
        }

        item { SectionTitle("Data") }
        item {
            SettingsCard {
                SettingsRow("Clear listening history", "Wipe stats and Home personalization history.", destructive = true, onClick = { confirmClearStats = true })
                SettingsRow("Clear search history", "Remove saved recent searches.", destructive = true, onClick = { confirmClearSearch = true })
                SettingsRow("Open data folder", AppPaths.dataDir.absolutePath, onClick = { runCatching { Desktop.getDesktop().open(AppPaths.dataDir) } })
            }
        }

        item { SectionTitle("About") }
        item {
            SettingsCard {
                SettingsRow("MusicSM Desktop", "Version ${AppVersion.current}", onClick = null)
                SettingsRow("Desktop port of MusicSM", "Kotlin + Compose Multiplatform, glass design, YouTube Music catalog.", onClick = null)
            }
        }
    }

    if (confirmClearStats) {
        AlertDialog(
            onDismissRequest = { confirmClearStats = false },
            title = { Text("Clear listening history?") },
            text = { Text("Stats and personalized history shelves will be rebuilt from future plays.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { onClearListeningHistory() }; confirmClearStats = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearStats = false }) { Text("Cancel") } },
        )
    }
    if (confirmClearSearch) {
        AlertDialog(
            onDismissRequest = { confirmClearSearch = false },
            title = { Text("Clear search history?") },
            text = { Text("Recent searches on the Search screen will be removed.") },
            confirmButton = {
                TextButton(onClick = { onClearSearchHistory(); confirmClearSearch = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearSearch = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Coral,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(shape = RoundedCornerShape(24.dp), tint = GlassFill) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = { onClick() })
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: (() -> Unit)?, destructive: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) Coral else MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SliderRow(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { onValueChange(((it * 20).roundToInt() / 20f).coerceIn(valueRange.start, valueRange.endInclusive)) },
            valueRange = valueRange,
            steps = steps,
            colors = musicSmSliderColors(),
        )
    }
}

@Composable
private fun LyricsSourceList(settings: DesktopSettings, onMove: (LyricsSource, Int) -> Unit, onToggle: (LyricsSource, Boolean) -> Unit) {
    val order = orderedLyrics(settings)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Source order", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        order.forEachIndexed { index, source ->
            val enabled = source.name !in settings.disabledLyricsSources
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GlassFillStrong).padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text("${index + 1}", color = if (enabled) Coral else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.45f)) {
                    Text(source.label, maxLines = 1)
                    Text(source.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onMove(source, -1) }, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                IconButton(onClick = { onMove(source, 1) }, enabled = index < order.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                Switch(checked = enabled, onCheckedChange = { onToggle(source, it) })
            }
        }
    }
}

private fun orderedLyrics(settings: DesktopSettings): List<LyricsSource> {
    val saved = settings.lyricsSourceOrder.mapNotNull { LyricsSource.fromName(it) }
    return saved + LyricsSource.entries.filter { it !in saved }
}

private val LyricsSource.description: String
    get() = when (this) {
        LyricsSource.APPLE_MUSIC -> "Line and word timings for popular releases"
        LyricsSource.BINI_LYRICS -> "Word timings with ISRC matching"
        LyricsSource.LYRICS_PLUS -> "YouLy+ word and syllable timings"
        LyricsSource.SIMPMUSIC -> "Matched by YouTube video"
        LyricsSource.LRCLIB -> "Open synced lyrics database"
        LyricsSource.KUGOU -> "Strong Asian catalogue coverage"
        LyricsSource.UNISON -> "Community-uploaded lyrics"
        LyricsSource.YOUTUBE_MUSIC -> "YouTube Music plain lyrics"
    }

private fun chooseDirectory(initial: String): String? {
    val chooser = JFileChooser(initial)
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.isAcceptAllFileFilterUsed = false
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
}
