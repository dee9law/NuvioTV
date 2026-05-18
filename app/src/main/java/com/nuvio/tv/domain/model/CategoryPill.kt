package com.nuvio.tv.domain.model

/**
 * The four built-in category pills that live on the Modern TopBar.
 * Edit Mode (F10) lets the user reorder them and demote some into the
 * Profile Overlay's "Hidden Items" section — but Home is always present
 * and ≥2 pills must remain on the TopBar at all times.
 *
 * Storage values are stable and survive reorder; they do NOT correspond
 * to display position. The order of [entries] is the default first-launch
 * order.
 */
enum class CategoryPill(val storageId: String, val displayLabel: String) {
    HOME("home", "Home"),
    MOVIES("movies", "Movies"),
    TV_SHOWS("tv_shows", "TV Shows"),
    COLLECTIONS("collections", "Collections");

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
 * One row in the persisted pill order. Defines both the position in the
 * ordering and whether the pill is currently shown on the TopBar or
 * demoted to the Profile Overlay.
 *
 * [iconsOnly] toggles the pill's display mode. When true the TopBar
 * renders only the leading icon (no text label) — useful on cramped
 * bars with many channel pills. Falls back to icon+text when false.
 */
data class CategoryPillOrderEntry(
    val pill: CategoryPill,
    val visibility: PillVisibility,
    val iconsOnly: Boolean = false,
) {
    companion object {
        /** Default first-launch order — every pill on the TopBar, icon+text. */
        fun defaultOrder(): List<CategoryPillOrderEntry> =
            CategoryPill.entries.map { CategoryPillOrderEntry(it, PillVisibility.TOPBAR) }
    }
}
