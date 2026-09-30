package com.example.musicsmd.local

import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.LocalMusicRepository
import com.example.musicsmd.playback.OfflineSource
import com.example.musicsmd.settings.AppPaths
import com.example.musicsmd.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.security.MessageDigest
import java.util.logging.Level
import java.util.logging.Logger

class LocalMusicManager(
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : LocalMusicRepository, OfflineSource {

    private val indexFile = File(AppPaths.dataDir, "local_index.json")
    private val artworkDir = File(AppPaths.dataDir, "local_artwork").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _status = MutableStateFlow("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        silenceTaggerLogs()
        _songs.value = loadIndex().filter { File(it.path).exists() && it.isInConfiguredDirs() }.map { it.song.toDomain() }
    }

    override suspend fun localSongs(): List<Song> = songs.value

    override fun localStream(songId: String): PlayableStream? {
        if (!songId.startsWith(LOCAL_PREFIX)) return null
        val path = songId.removePrefix(LOCAL_PREFIX)
        val file = File(path)
        if (!file.exists()) return null
        return PlayableStream(url = file.absolutePath, mimeType = mimeTypeFor(file), expiresAtMs = Long.MAX_VALUE)
    }

    fun rescan() {
        if (_isScanning.value) return
        scope.launch { scanNow() }
    }

    suspend fun scanNow() = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        _isScanning.value = true
        try {
            val roots = settingsStore.current.localMusicDirs.map(::File).filter { it.exists() && it.isDirectory }
            val previous = loadIndex().associateBy { it.cacheKey }
            val discovered = mutableListOf<File>()
            for (root in roots) {
                currentCoroutineContext().ensureActive()
                _status.value = "Scanning ${root.name}"
                for (file in root.walkTopDown()) {
                    currentCoroutineContext().ensureActive()
                    if (file.isFile && file.extension.lowercase() in AUDIO_EXTENSIONS) {
                        discovered += file
                    }
                }
            }
            val entries = ArrayList<LocalTrackEntry>(discovered.size)
            discovered.forEachIndexed { index, file ->
                currentCoroutineContext().ensureActive()
                _status.value = "Reading ${index + 1}/${discovered.size}"
                val key = cacheKey(file)
                entries += previous[key] ?: readTrack(file, key)
            }
            persist(entries)
            _songs.value = entries.map { it.song.toDomain() }.sortedBy { it.title.lowercase() }
            _status.value = "${entries.size} songs indexed"
        } finally {
            _isScanning.value = false
        }
    }

    fun addFolder(folder: File) {
        val path = folder.absoluteFile.normalize().path
        settingsStore.update { settings ->
            if (path in settings.localMusicDirs) settings else settings.copy(localMusicDirs = settings.localMusicDirs + path)
        }
        rescan()
    }

    fun removeFolder(path: String) {
        settingsStore.update { settings -> settings.copy(localMusicDirs = settings.localMusicDirs.filterNot { it == path }) }
        _songs.value = loadIndex().filter { it.isInConfiguredDirs() && File(it.path).exists() }.map { it.song.toDomain() }
    }

    private fun readTrack(file: File, key: String): LocalTrackEntry {
        val fallbackTitle = file.nameWithoutExtension.replace('_', ' ').trim().ifBlank { file.name }
        return runCatching {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() } ?: "Unknown artist"
            val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }
            val durationMs = audio.audioHeader?.trackLength?.coerceAtLeast(0)?.toLong()?.times(1000) ?: 0L
            val art = tag?.firstArtwork?.let { artwork -> writeArtwork(file, artwork.binaryData, artwork.mimeType) }
            LocalTrackEntry(
                path = file.absolutePath,
                lastModified = file.lastModified(),
                length = file.length(),
                song = PersistedSong(LOCAL_PREFIX + file.absolutePath, title, artist, album, art, durationMs),
            )
        }.getOrElse {
            LocalTrackEntry(
                path = file.absolutePath,
                lastModified = file.lastModified(),
                length = file.length(),
                song = PersistedSong(LOCAL_PREFIX + file.absolutePath, fallbackTitle, "Unknown artist"),
            )
        }.copy(cacheKey = key)
    }

    private fun writeArtwork(file: File, bytes: ByteArray?, mimeType: String?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val ext = when (mimeType?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val out = File(artworkDir, sha1(file.absolutePath) + ".$ext")
        if (!out.exists() || out.length() != bytes.size.toLong()) out.writeBytes(bytes)
        return out.toURI().toString()
    }

    private fun persist(entries: List<LocalTrackEntry>) {
        indexFile.parentFile?.mkdirs()
        val tmp = File(indexFile.parentFile, indexFile.name + ".tmp")
        tmp.writeText(json.encodeToString(ListSerializer(LocalTrackEntry.serializer()), entries))
        if (!tmp.renameTo(indexFile)) {
            indexFile.delete()
            tmp.renameTo(indexFile)
        }
    }

    private fun loadIndex(): List<LocalTrackEntry> =
        runCatching { json.decodeFromString(ListSerializer(LocalTrackEntry.serializer()), indexFile.readText()) }.getOrDefault(emptyList())

    private fun LocalTrackEntry.isInConfiguredDirs(): Boolean {
        val normalized = File(path).absoluteFile.normalize().path
        return settingsStore.current.localMusicDirs.any { dir -> normalized.startsWith(File(dir).absoluteFile.normalize().path) }
    }

    private fun cacheKey(file: File): String = "${file.absolutePath}|${file.lastModified()}|${file.length()}"

    private fun mimeTypeFor(file: File): String? = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> null
    }

    private fun sha1(text: String): String = MessageDigest.getInstance("SHA-1")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun silenceTaggerLogs() {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    companion object {
        const val LOCAL_PREFIX = "local:"
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
    }
}

@Serializable
private data class LocalTrackEntry(
    val path: String,
    val lastModified: Long,
    val length: Long,
    val song: PersistedSong,
    val cacheKey: String = "$path|$lastModified|$length",
)

@Serializable
private data class PersistedSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
) {
    fun toDomain() = Song(id, title, artist, album, artworkUrl, durationMs)
}
