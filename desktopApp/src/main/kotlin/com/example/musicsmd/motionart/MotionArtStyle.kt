package com.example.musicsmd.motionart

enum class MotionArtStyle(val label: String) {
    CARD("Card"),
    FULL_SCREEN("Full screen");

    companion object {
        /** Unknown keys, including the retired "EDGE" (Top) style, fall back to full screen. */
        fun fromKey(key: String?): MotionArtStyle =
            entries.firstOrNull { it.name == key } ?: FULL_SCREEN
    }
}
