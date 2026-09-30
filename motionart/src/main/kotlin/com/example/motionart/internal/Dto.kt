package com.example.motionart.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The slice of the catalog search response this module reads.
 *
 * Unknown keys are ignored by the configured Json instance, so the provider is free to keep adding
 * fields without breaking us.
 */
@Serializable
internal data class SearchResponse(val results: SearchResults? = null)

@Serializable
internal data class SearchResults(val albums: AlbumResults? = null)

@Serializable
internal data class AlbumResults(val data: List<AlbumItem> = emptyList())

@Serializable
internal data class AlbumItem(
    val id: String? = null,
    val attributes: AlbumAttributes? = null,
)

@Serializable
internal data class AlbumAttributes(
    val name: String? = null,
    val artistName: String? = null,
    val editorialVideo: EditorialVideo? = null,
)

/**
 * The motion cuts a release can carry.
 *
 * All four are optional and a release commonly has none; the square variants are the ones that fit
 * a cover slot, and the tall ones are cropped stories art we only fall back to.
 */
@Serializable
internal data class EditorialVideo(
    @SerialName("motionSquareVideo1x1") val square: MotionClip? = null,
    @SerialName("motionDetailSquare") val detailSquare: MotionClip? = null,
    @SerialName("motionTallVideo3x4") val tall: MotionClip? = null,
    @SerialName("motionDetailTall") val detailTall: MotionClip? = null,
) {
    /** Square first: the cover slot it replaces is square, so anything else has to be cropped. */
    val best: MotionClip?
        get() = listOfNotNull(square, detailSquare, tall, detailTall).firstOrNull { !it.video.isNullOrBlank() }
}

@Serializable
internal data class MotionClip(
    val video: String? = null,
    val previewFrame: PreviewFrame? = null,
)

@Serializable
internal data class PreviewFrame(val url: String? = null)
