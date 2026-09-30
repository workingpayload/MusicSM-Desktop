package com.example.musicsm.domain.repository

/** Result of importing an external playlist into the local library. */
data class ImportResult(
    val playlistId: Long,
    val name: String,
    val matched: Int,
    val total: Int,
)

/**
 * Imports a playlist from a public share link (Spotify, Apple Music, YouTube / YouTube Music) into
 * the local library. YouTube tracks are saved as they are; others are matched against YouTube.
 */
interface PlaylistImportRepository {
    /**
     * Import the playlist at [link] into a new local playlist. [onProgress] reports
     * (processed-so-far, total). The playlist must be public.
     */
    suspend fun importFromLink(
        link: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result<ImportResult>
}
