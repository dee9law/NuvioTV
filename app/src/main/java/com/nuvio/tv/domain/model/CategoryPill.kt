package com.nuvio.tv.domain.model

/**
 * Every navigation surface that can live on the Modern TopBar or in the
 * Profile Overlay. The list is the source of truth for both the unified
 * TopBar Settings screen and the Modern TopBar's pill renderer.
 *
 *  - Category surfaces (HOME / MOVIES / TV_SHOWS / COLLECTIONS) render
 *    as regular pills.
 *  - Drawer-default surfaces (SEARCH / DISCOVER / MY_STUFF / SETTINGS)
 *    live in the Profile Overlay by default but can be promoted to the
 *    TopBar.
 *  - CHANNELS is a virtual entry that controls the channel-pill rail's
 *    visibility and the channel-pill display mode (icon/text/both).
 *
 * Storage values are stable and survive reorder; they do NOT correspond
 * to display position. The order of [entries] is the default first-launch
 * order for the persisted layout.
 */
enum class CategoryPill(
    val storageId: String,
    val displayLabel: String,
    val defaultVisibility: PillVisibility,
) {
    HOME("home", "Home", PillVisibility.TOPBAR),
    MOVIES("movies", "Movies", PillVisibility.TOPBAR),
    TV_SHOWS("tv_shows", "TV Shows", PillVisibility.TOPBAR),
    COLLECTIONS("collections", "Collections", PillVisibility.TOPBAR),
    CHANNELS("channels", "Channels", PillVisibility.TOPBAR),
    SEARCH("search", "Search", PillVisibility.DRAWER),
    DISCOVER("discover", "Discover", PillVisibility.DRAWER),
    MY_STUFF("my_stuff", "My Stuff", PillVisibility.DRAWER),
    SETTINGS("settings", "Settings", PillVisibility.DRAWER);

    companion object {
        fun fromStorageId(id: String?): CategoryPill? =
            entries.firstOrNull { it.storageId == id }
    }
}

/** Where a category pill currently lives — on the TopBar, or demoted. */
enum class PillVisibility(val storageValue: String) {
    TOPBAR("topbar"),
    DRAWER("drawer");

    companion object {
        fun fromStorageValue(value: String?): PillVisibility = when (value) {
            DRAWER.storageValue -> DRAWER
            else -> TOPBAR
        }
    }
}

/**
 * Display mode for a single category pill on the TopBar. Cycled through
 * via the display-mode chip in TopBar settings; persisted per-pill.
 *
 *  - [ICON_AND_TEXT] — render the leading icon (or channel logo) plus
 *    the pill label.
 *  - [ICON_ONLY] — render the icon / logo only; useful for compacting
 *    crowded bars.
 *  - [TEXT_ONLY] — render the label only; useful when the icon is
 *    redundant or absent.
 */
enum class CategoryPillDisplayMode(val storageValue: String) {
    ICON_AND_TEXT("icon_text"),
    ICON_ONLY("icon"),
    TEXT_ONLY("text");

    fun next(): CategoryPillDisplayMode = when (this) {
        ICON_AND_TEXT -> ICON_ONLY
        ICON_ONLY -> TEXT_ONLY
        TEXT_ONLY -> ICON_AND_TEXT
    }

    companion object {
        fun fromStorageValue(value: String?): CategoryPillDisplayMode = when (value) {
            ICON_ONLY.storageValue -> ICON_ONLY
            TEXT_ONLY.storageValue -> TEXT_ONLY
            else -> ICON_AND_TEXT
        }
    }
}

/**
 * One row in the persisted pill order. Defines both the position in the
 * ordering and whether the pill is currently shown on the TopBar or
 * demoted to the Profile Overlay.
 *
 * [displayMode] controls how the TopBar renders this specific pill (full
 * icon + text, icon only, or text only). The legacy [iconsOnly] boolean
 * is preserved as a derived getter so existing callers that only care
 * about the icons-only case keep working.
 */
data class CategoryPillOrderEntry(
    val pill: CategoryPill,
    val visibility: PillVisibility,
    val displayMode: CategoryPillDisplayMode = CategoryPillDisplayMode.ICON_AND_TEXT,
) {
    /** Backward-compat shim — true iff [displayMode] is [CategoryPillDisplayMode.ICON_ONLY]. */
    val iconsOnly: Boolean
        get() = displayMode == CategoryPillDisplayMode.ICON_ONLY

    companion object {
        /**
         * Default first-launch order — every pill in its enum-declared
         * position with its [CategoryPill.defaultVisibility]. Category
         * surfaces and Channels start on the TopBar; Search / Discover /
         * My Stuff / Settings start in the drawer (Profile Overlay).
         */
        fun defaultOrder(): List<CategoryPillOrderEntry> =
            CategoryPill.entries.map {
                CategoryPillOrderEntry(it, it.defaultVisibility)
            }
    }
}
