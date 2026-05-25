package com.nuvio.tv.ui.screens.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure-function sizing helpers shared by every home layout's parent
 * compositor. Centralising these stops the same padding-stack from
 * being copy-pasted across Modern, Classic, Grid and Spotlight where
 * a tweak in one would silently desync the others.
 *
 *  - [singleRowContainerHeight] sizes a one-row-at-a-time strip
 *    (Spotlight's bottom strip).
 *  - [multiRowContainerHeight] sizes a multi-row visible strip
 *    (Modern's bottom carousel area).
 *  - [heroHeightForRowsContainer] derives the hero's allocated height
 *    from the rows strip and the available screen height.
 *
 * No layout block calculates these internally any more. The parent
 * compositor passes the result into the hero / rows blocks.
 */

/**
 * Per-row vertical padding stack used by [CatalogRowSection]. Kept in
 * sync with the Prime-Video-tight rhythm landed on 2026-05-23.
 *
 *  ─ titleHeight (24dp lineHeight)
 *  ─ titlePadding (2dp — title→cards gap)
 *  ─ cardTopPadding (LazyRow contentPadding.top = 0)
 *  ─ cardBottomPadding (LazyRow contentPadding.bottom = 16 for glow clearance)
 */
private val ROW_PADDING_STACK_DP = 24.dp + 2.dp + 0.dp + 16.dp

/** Top + bottom breathing room around a single-row strip (Spotlight pattern). */
private val SINGLE_ROW_BREATHING_DP = 16.dp + 16.dp

/** Vertical gap between stacked rows in a multi-row strip (Modern pattern). */
private val MULTI_ROW_VERTICAL_GAP_DP = 24.dp

/**
 * Height of a one-row-tall container for the given card height.
 * Matches Spotlight's formula exactly: cardHeight + 72.dp.
 */
fun singleRowContainerHeight(cardHeight: Dp): Dp =
    cardHeight + ROW_PADDING_STACK_DP + SINGLE_ROW_BREATHING_DP

/**
 * Height of an N-row container for the given card height. Used by
 * Modern's bottom strip so the visible-rows count scales with the
 * user's card-size setting instead of a fixed % of the screen.
 *
 * Formula: visibleRows * (cardHeight + paddingStack) + (visibleRows-1) * gap
 * + 16dp top breathing.
 */
fun multiRowContainerHeight(cardHeight: Dp, visibleRows: Int = 2): Dp {
    val perRow = cardHeight + ROW_PADDING_STACK_DP
    val gaps = MULTI_ROW_VERTICAL_GAP_DP * (visibleRows - 1).coerceAtLeast(0)
    return perRow * visibleRows + gaps + 16.dp
}

/**
 * Allocate the hero block's height as `screenHeight - rowsHeight`,
 * clamped so the hero never becomes unreadably short (default floor
 * 200dp matches Spotlight).
 */
fun heroHeightForRowsContainer(
    screenHeight: Dp,
    rowsHeight: Dp,
    minHero: Dp = 200.dp,
): Dp = (screenHeight - rowsHeight).coerceAtLeast(minHero)
