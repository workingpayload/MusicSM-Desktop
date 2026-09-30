package com.example.musicsmd.settings

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the desktop app keeps its files (`~/.musicsm-desktop`). */
object AppPaths {
    val dataDir: File = File(System.getProperty("user.home"), ".musicsm-desktop").apply { mkdirs() }
    val defaultDownloadsDir: File = File(dataDir, "downloads")
    val defaultMusicDir: File = File(System.getProperty("user.home"), "Music")
}

enum class RepeatMode { OFF, ALL, ONE }

/** How Home shows its song shelves: a vertical list of rows, or mobile's horizontal cards. */
enum class HomeLayout { LIST, CARDS }

/**
 * Every user setting, persisted as one JSON file — the desktop counterpart of the mobile app's
 * SharedPreferences-backed `AppPreferences`. New fields must have defaults so older files load.
 */
@Serializable
data class DesktopSettings(
    // Playback
    val autoplayRadio: Boolean = true,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Int = 100,
    val playbackSpeed: Float = 1f,
    val restoreQueue: Boolean = true,
    val sleepTimerFadeOut: Boolean = true,

    // Search
    val searchVideos: Boolean = false,

    // Appearance — mobile's "Theme from artwork"; on by default on desktop.
    val themeFromArtwork: Boolean = true,
    // Mobile's "AMOLED black": pure black base surfaces.
    val amoled: Boolean = false,
    val homeLayout: HomeLayout = HomeLayout.LIST,

    // Lyrics — names of `com.example.musicsm.domain.model.LyricsSource` entries.
    val lyricsSourceOrder: List<String> = emptyList(),
    val disabledLyricsSources: Set<String> = emptySet(),
    val preferWordSyncedLyrics: Boolean = true,
    val lyricsOffsetsMs: Map<String, Long> = emptyMap(),
    // Open the lyrics beside Now Playing on their own whenever the song has them.
    val autoOpenLyrics: Boolean = true,

    // Audio effects / output (libVLC)
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String? = null,
    val equalizerPreamp: Float = 0f,
    val equalizerBands: List<Float> = emptyList(),
    val audioOutputDevice: String? = null,

    // Files
    val downloadsDir: String = AppPaths.defaultDownloadsDir.absolutePath,
    val localMusicDirs: List<String> = listOf(AppPaths.defaultMusicDir.absolutePath),

    // Desktop
    val minimizeToTray: Boolean = false,
    val mediaKeys: Boolean = true,

    // Updates — a version the user said "Later" to, which isn't announced again.
    val dismissedUpdateVersion: String? = null,
)

/** Observable, file-backed settings. Writes are synchronous and tiny, so no debouncing. */
class SettingsStore(private val file: File = File(AppPaths.dataDir, "settings.json")) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

    val current: DesktopSettings get() = _settings.value

    fun update(transform: (DesktopSettings) -> DesktopSettings) {
        _settings.update(transform)
        save(_settings.value)
    }

    private fun load(): DesktopSettings =
        runCatching { json.decodeFromString<DesktopSettings>(file.readText().removePrefix("\uFEFF")) }
            .getOrDefault(DesktopSettings())

    @Synchronized
    private fun save(settings: DesktopSettings) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(DesktopSettings.serializer(), settings))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
