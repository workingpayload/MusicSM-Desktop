package com.example.motionart.internal

import com.example.motionart.MotionArt

/** What the orchestrator knows about a track when it goes looking for a cover video. */
internal data class TrackQuery(
    val artist: String,
    val title: String,
    val album: String?,
)

/**
 * One catalogue or manifest that may hold a motion cover.
 *
 * Implementations never throw for a miss or for a transport failure — both mean "nothing here",
 * and the orchestrator simply moves on to the next source.
 */
internal interface MotionArtProviderClient {
    suspend fun lookup(query: TrackQuery): MotionArt?
}
