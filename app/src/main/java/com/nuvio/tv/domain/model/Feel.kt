package com.nuvio.tv.domain.model

/**
 * Selects the overall navigation surface the app shell renders.
 *
 * - [MODERN]: unified TopBar with profile avatar at the far left, no
 *   SideRail. Content area expands edge-to-edge. Profile/Search/Discover/
 *   Settings live in an overlay panel opened from the avatar or D-pad Left
 *   at carousel index 0. Default for fresh installs.
 * - [LEGACY]: current Prime-Video-style SideRail + TopBar layout preserved
 *   exactly. Selected by users who want the old shell back.
 *
 * Persisted via [LayoutPreferenceDataStore.navigationFeel] using
 * [storageValue]. Unknown / null values resolve to [MODERN] so new
 * installs (and corrupt reads) get the new default.
 */
enum class Feel(val storageValue: String) {
    MODERN("modern"),
    LEGACY("legacy");

    companion object {
        fun fromStorageValue(value: String?): Feel = when (value) {
            LEGACY.storageValue -> LEGACY
            MODERN.storageValue -> MODERN
            else -> MODERN
        }
    }
}
