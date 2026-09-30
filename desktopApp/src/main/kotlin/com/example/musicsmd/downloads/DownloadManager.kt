package com.example.musicsmd.downloads

import com.example.musicsm.data.source.youtube.NewPipeDownloaderImpl
import com.example.musicsm.domain.model.PlayableStream
import com.example.musicsm.domain.model.Song
import com.example.musicsm.domain.repository.DownloadRepository
import com.example.musicsm.domain.repository.DownloadingSong
import com.example.musicsm.domain.repository.FailedDownload
import com.example.musicsm.domain.source.MusicSource
import com.example.musicsmd.playback.OfflineSource
import com.example.musicsmd.settings.AppPaths
import com.example.musicsmd.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(
    private val musicSource: MusicSource,
    private val client: OkHttpClient,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : DownloadRepository, OfflineSource {

    private val indexFile = File(AppPaths.dataDir, "downloads.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val semaphore = Semaphore(2)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _records = MutableStateFlow(loadIndex())
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    override val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _activeDownloads = MutableStateFlow<List<DownloadingSong>>(emptyList())
    override val activeDownloads: StateFlow<List<DownloadingSong>> = _activeDownloads.asStateFlow()

    private val _failedDownloads = MutableStateFlow<List<FailedDownload>>(emptyList())
    override val failedDownloads: StateFlow<List<FailedDownload>> = _failedDownloads.asStateFlow()

    override fun isDownloaded(songId: String): Flow<Boolean> =
        _records.map { it.any { record -> record.song.id == songId && File(record.path).exists() } }

    override fun downloads(): Flow<List<Song>> =
        _records.map { records -> records.filter { File(it.path).exists() }.map { it.song.toDomain() } }

    override suspend fun download(song: Song) {
        if (song.id.startsWith("local:")) return
        if (jobs.containsKey(song.id) || localPath(song.id) != null) return
        _failedDownloads.value = _failedDownloads.value.filterNot { it.song.id == song.id }
        setActive(song, 0f)
        val job = scope.launch {
            semaphore.withPermit { doDownload(song) }
        }
        jobs[song.id] = job
        job.invokeOnCompletion { jobs.remove(song.id) }
    }

    override suspend fun downloadAll(songs: List<Song>) {
        songs.forEach { download(it) }
    }

    override fun cancel(songId: String) {
        jobs.remove(songId)?.cancel()
        removeActive(songId)
    }

    override suspend fun retry(songId: String) {
        val song = _failedDownloads.value.firstOrNull { it.song.id == songId }?.song ?: return
        _failedDownloads.value = _failedDownloads.value.filterNot { it.song.id == songId }
        download(song)
    }

    override suspend fun delete(songId: String) = withContext(Dispatchers.IO) {
        cancel(songId)
        val records = _records.value
        records.firstOrNull { it.song.id == songId }?.let { File(it.path).delete() }
        downloadsDir().resolve("${safeName(songId)}.part").delete()
        _records.value = records.filterNot { it.song.id == songId }
        _failedDownloads.value = _failedDownloads.value.filterNot { it.song.id == songId }
        persist()
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        jobs.keys.toList().forEach(::cancel)
        _records.value.forEach { File(it.path).delete() }
        downloadsDir().listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
        _records.value = emptyList()
        _failedDownloads.value = emptyList()
        persist()
    }

    override suspend fun storageUsedBytes(): Long = withContext(Dispatchers.IO) {
        _records.value.sumOf { File(it.path).takeIf(File::exists)?.length() ?: 0L }
    }

    override suspend fun localPath(songId: String): String? = withContext(Dispatchers.IO) {
        _records.value.firstOrNull { it.song.id == songId }?.path?.takeIf { File(it).exists() }
    }

    override fun localStream(songId: String): PlayableStream? {
        val record = _records.value.firstOrNull { it.song.id == songId } ?: return null
        val file = File(record.path)
        if (!file.exists()) return null
        return PlayableStream(url = file.absolutePath, mimeType = record.mimeType, expiresAtMs = Long.MAX_VALUE)
    }

    private suspend fun doDownload(song: Song) {
        val dir = downloadsDir().apply { mkdirs() }
        val part = File(dir, "${safeName(song.id)}.part")
        try {
            val stream = musicSource.resolveStream(song.id)
            val extension = extensionFor(stream.mimeType)
            val target = File(dir, "${safeName(song.id)}.$extension")
            fetchChunked(song, stream.url, part)
            target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            upsert(DownloadRecord(PersistedSong.from(song), target.absolutePath, stream.mimeType, target.length(), System.currentTimeMillis()))
            _failedDownloads.value = _failedDownloads.value.filterNot { it.song.id == song.id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            println("[Download] failed for ${song.id}: ${failure.message}")
            _failedDownloads.value = _failedDownloads.value.filterNot { it.song.id == song.id } +
                FailedDownload(song, failure.message ?: "Download failed")
        } finally {
            removeActive(song.id)
        }
    }

    private suspend fun fetchChunked(song: Song, url: String, part: File) {
        var offset = part.length().coerceAtLeast(0L)
        var total: Long? = null
        var append = offset > 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val end = offset + CHUNK_BYTES - 1
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NewPipeDownloaderImpl.USER_AGENT)
                .header("Range", "bytes=$offset-$end")
                .cacheControl(CacheControl.Builder().noStore().build())
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == HTTP_RANGE_NOT_SATISFIABLE) {
                    part.delete()
                    offset = 0L
                    append = false
                    return@use
                }
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty body")
                if (response.code == HTTP_OK) {
                    offset = 0L
                    append = false
                    total = body.contentLength().takeIf { it > 0 }
                } else {
                    total = parseTotal(response.header("Content-Range")) ?: total
                }
                val written = writeBody(body.byteStream(), part, append, song.id, offset, total)
                offset = written
                append = true
                if (total != null && offset >= total!!) return
                if (response.code == HTTP_OK || written == 0L) return
            }
        }
    }

    private suspend fun writeBody(
        input: java.io.InputStream,
        part: File,
        append: Boolean,
        songId: String,
        startAt: Long,
        total: Long?,
    ): Long = input.use { source ->
        FileOutputStream(part, append).use { output ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            var written = startAt
            var lastPublished = startAt
            while (source.read(buffer).also { read = it } != -1) {
                currentCoroutineContext().ensureActive()
                output.write(buffer, 0, read)
                written += read
                if (total != null && written - lastPublished >= PROGRESS_STEP_BYTES) {
                    lastPublished = written
                    setProgress(songId, (written.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            output.flush()
            if (total != null) setProgress(songId, (written.toFloat() / total).coerceIn(0f, 1f))
            written
        }
    }

    private fun setActive(song: Song, progress: Float) {
        _activeDownloads.value = _activeDownloads.value.filterNot { it.song.id == song.id } + DownloadingSong(song, progress)
        _progress.value = _progress.value + (song.id to progress)
    }

    private fun setProgress(songId: String, value: Float) {
        _progress.value = _progress.value + (songId to value)
        _activeDownloads.value = _activeDownloads.value.map { if (it.song.id == songId) it.copy(progress = value) else it }
    }

    private fun removeActive(songId: String) {
        _activeDownloads.value = _activeDownloads.value.filterNot { it.song.id == songId }
        _progress.value = _progress.value - songId
    }

    @Synchronized
    private fun upsert(record: DownloadRecord) {
        _records.value = _records.value.filterNot { it.song.id == record.song.id } + record
        persist()
    }

    @Synchronized
    private fun persist() {
        indexFile.parentFile?.mkdirs()
        val tmp = File(indexFile.parentFile, indexFile.name + ".tmp")
        tmp.writeText(json.encodeToString(ListSerializer, _records.value))
        if (!tmp.renameTo(indexFile)) {
            indexFile.delete()
            tmp.renameTo(indexFile)
        }
    }

    private fun loadIndex(): List<DownloadRecord> =
        runCatching { json.decodeFromString(ListSerializer, indexFile.readText()) }.getOrDefault(emptyList())

    private fun downloadsDir(): File = File(settingsStore.current.downloadsDir).apply { mkdirs() }

    private fun parseTotal(contentRange: String?): Long? =
        contentRange?.substringAfter('/')?.toLongOrNull()

    private fun extensionFor(mimeType: String?): String = when (mimeType?.substringBefore(';')?.lowercase()) {
        "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
        "audio/webm", "video/webm" -> "webm"
        "audio/mpeg" -> "mp3"
        "audio/ogg", "application/ogg" -> "ogg"
        else -> "audio"
    }

    private fun safeName(id: String): String = id.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private companion object {
        const val CHUNK_BYTES = 4L * 1024L * 1024L
        const val PROGRESS_STEP_BYTES = 128L * 1024L
        const val HTTP_OK = 200
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(DownloadRecord.serializer())
    }
}

@Serializable
private data class DownloadRecord(
    val song: PersistedSong,
    val path: String,
    val mimeType: String? = null,
    val bytes: Long = 0L,
    val downloadedAtMs: Long = 0L,
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

    companion object {
        fun from(song: Song) = PersistedSong(song.id, song.title, song.artist, song.album, song.artworkUrl, song.durationMs)
    }
}
