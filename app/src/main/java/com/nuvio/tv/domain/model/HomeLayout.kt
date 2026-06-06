package com.nuvio.tv.domain.model

enum class HomeLayout(val displayName: String) {
    CLASSIC("Classic View"),
    GRID("Grid View"),
    MODERN("Modern View"),
    IMMERSIVE("Immersive View"),
    SPOTLIGHT("Spotlight View"),
}

/**
 * Modern and Immersive share the exact same content presentation (the Modern
 * hero + rows pipeline); they differ only in how the hero backdrop renders:
 *  - [HomeLayout.MODERN]    → State 2 — non-fullscreen hero block, rows below it.
 *  - [HomeLayout.IMMERSIVE] → State 1 — fullscreen edge-to-edge backdrop with a
 *    single active row (the lazy crossfade pager) floating over it.
 *
 * Every "is this a Modern-style content layout?" check should read this so the
 * two stay in lockstep for catalog loading, poster-label suppression, hero
 * backdrop handling, enrichment, etc.
 */
val HomeLayout.usesModernPresentation: Boolean
    get() = this == HomeLayout.MODERN || this == HomeLayout.IMMERSIVE
