package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutScreenScope

/**
 * Fully-resolved per-row display values, consumed by every row-based home
 * layout (Modern / Classic / Spotlight). Grid renders a uniform grid rather
 * than per-catalog rows, so it has no per-row config and no expand mechanic —
 * it uses a single global card style instead of this resolver.
 */
data class ResolvedRowDisplayConfig(
    val effectiveCardStyle: LayoutCardStyle,
    val effectiveCardWidthDp: Int,
    val effectiveExpands: Boolean,
)

/**
 * Single source of truth for resolving a row's effective display config.
 * Pure — no Compose, no side effects, data in / data out.
 *
 * Orientation + size come straight from the row's own override (the per-row
 * tier always wins once a row is configured). Expand follows the three-state
 * hierarchy:
 *  - [LayoutRowConfig.expandEnabled] == true  → always expand
 *  - [LayoutRowConfig.expandEnabled] == false → never expand
 *  - [LayoutRowConfig.expandEnabled] == null  → follow [globalExpandForScope]
 *
 * [scope] is accepted for call-site clarity / future per-scope tiers; it does
 * not currently affect the result (callers pass the row's own [LayoutRowConfig.viewContext]).
 */
fun resolveRowDisplayConfig(
    row: LayoutRowConfig,
    scope: LayoutScreenScope,
    globalExpandForScope: Boolean,
): ResolvedRowDisplayConfig = ResolvedRowDisplayConfig(
    effectiveCardStyle = row.cardStyle,
    effectiveCardWidthDp = row.cardWidthDp,
    effectiveExpands = when (row.expandEnabled) {
        true -> true
        false -> false
        null -> globalExpandForScope
    },
)
