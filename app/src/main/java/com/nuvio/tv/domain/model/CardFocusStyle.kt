package com.nuvio.tv.domain.model

/**
 * Border treatment for focused content cards (and channel pills with
 * logos). Independent of the Poster Glow toggle which controls the
 * soft coloured shadow behind the card.
 *
 *  - [ACCENT] — static theme-accent border on focus, no colour
 *    extraction. Safe default for everyone.
 *  - [BLOOM] — tight coloured border (2-3dp) sampled from the artwork
 *    for a defined-edge "luminous" look (STRMR-style).
 *
 * The earlier `GLOW` option was retired in favour of an orthogonal
 * Poster Glow toggle that combines with either border style.
 */
enum class CardFocusStyle(val storageValue: String) {
    ACCENT("accent"),
    BLOOM("bloom");

    /** Toggle between the two border styles. */
    fun next(): CardFocusStyle = when (this) {
        ACCENT -> BLOOM
        BLOOM -> ACCENT
    }

    val displayLabel: String
        get() = when (this) {
            ACCENT -> "Accent"
            BLOOM -> "Border Bloom"
        }

    companion object {
        fun fromStorageValue(value: String?): CardFocusStyle = when (value) {
            BLOOM.storageValue -> BLOOM
            else -> ACCENT
        }
    }
}
