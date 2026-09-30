package com.example.motionart

/**
 * Where a motion cover may be fetched from.
 *
 * Coverage differs wildly between catalogues — one has the big Western releases, another has a
 * hand-curated list that includes things the majors never animated — so the useful default is to
 * try them in turn rather than to pick one. Pinning a single provider exists for the case where a
 * listener's library is served well by one of them and the extra requests are wasted effort.
 */
enum class MotionArtProvider {
    /** Try every provider in turn and take the first that answers. */
    AUTO,

    /** Editorial motion artwork from the Apple Music catalogue. Broadest coverage. */
    APPLE,

    /** Square video covers from Tidal's catalogue. */
    TIDAL,

    /** A community-curated track-to-video manifest. */
    VIVI,
    ;

    companion object {
        /**
         * The order [AUTO] walks.
         *
         * The two catalogue providers come first because they answer for anything in a major
         * release; the hand-maintained manifest is small, so it is worth consulting only for
         * what the catalogues missed.
         */
        val AUTO_ORDER = listOf(APPLE, TIDAL, VIVI)

        fun fromName(name: String?): MotionArtProvider =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTO
    }
}
