package com.nuvio.tv.domain.model

/**
 * How focused content cards (and channel pills with logos) are
 * highlighted. Replaces the earlier `poster_glow_enabled` boolean —
 * cycled by the "Card Focus Style" setting in Global settings.
 *
 *  - [ACCENT] — the original behaviour: a static theme-accent border
 *    on focus, no colour extraction. Safe default for everyone.
 *  - [GLOW] — `Modifier.shadow` with the artwork's dominant colour for
 *    a soft coloured halo behind the card.
 *  - [BLOOM] — a tight coloured border around the card (2–3dp) plus a
 *    softer outer shadow in the same colour for a defined-edge
 *    "luminous bloom" look (STRMR-style).
 */
enum class CardFocusStyle(val storageValue: String) {
    ACCENT("accent"),
    GLOW("glow"),
    BLOOM("bloom");

    /** Cycle to the next style in the rotation. */
    fun next(): CardFocusStyle = when (this) {
        ACCENT -> GLOW
        GLOW -> BLOOM
        BLOOM -> ACCENT
    }

    val displayLabel: String
        get() = when (this) {
            ACCENT -> "Accent"
            GLOW -> "Poster Glow"
            BLOOM -> "Border Bloom"
        }

    companion object {
        fun fromStorageValue(value: String?): CardFocusStyle = when (value) {
            GLOW.storageValue -> GLOW
            BLOOM.storageValue -> BLOOM
            else -> ACCENT
        }
    }
}
