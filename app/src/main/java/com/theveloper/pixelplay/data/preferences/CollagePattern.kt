package com.theveloper.pixelplay.data.preferences

enum class CollagePattern(
    val storageKey: String,
    val label: String
) {
    COSMIC_SWIRL("cosmic_swirl", "Gallery Grid"),
    HONEYCOMB_GROOVE("honeycomb_groove", "Editorial Covers"),
    VINYL_STACK("vinyl_stack", "Cover Wall"),
    PIXEL_MOSAIC("pixel_mosaic", "Balanced Mosaic"),
    STARDUST_SCATTER("stardust_scatter", "Spotlight Grid");

    companion object {
        val default: CollagePattern = COSMIC_SWIRL

        fun fromStorageKey(value: String?): CollagePattern {
            return entries.firstOrNull { it.storageKey == value } ?: default
        }
    }
}
