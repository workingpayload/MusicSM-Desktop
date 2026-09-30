package com.example.motionart

/**
 * A short, silent, looping video that stands in for a track's cover art.
 *
 * [videoUrl] may be either a progressive MP4 or an HLS playlist depending on [provider], which is
 * why [isHls] is carried alongside it: the player has to be told which, and guessing from the file
 * extension fails on signed URLs that carry a query string.
 *
 * [previewImageUrl] is the still frame the provider renders before the video starts. Not every
 * provider offers one.
 */
data class MotionArt(
    val videoUrl: String,
    val provider: MotionArtProvider,
    val previewImageUrl: String? = null,
) {
    /** Whether [videoUrl] is an HLS playlist rather than a self-contained file. */
    val isHls: Boolean
        get() = videoUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
}
