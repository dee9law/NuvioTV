package com.nuvio.tv.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester

/**
 * Universal Back-Level-Up hierarchy shared by every home layout
 * (Classic / Grid / Modern / Spotlight).
 *
 * Levels (top → bottom of the screen):
 *
 *  L5  Deep in row (item index > 0)
 *      Back → snap to first card in that row.
 *
 *  L4  At first card in row (item index == 0)
 *      Back → IF hero has action buttons → focus hero actions (L3)
 *           → ELSE → focus hero content (L2).
 *
 *  L3  At hero action buttons (Play / Details / ...)
 *      Back → focus hero content (L2).
 *
 *  L2  At hero content / carousel
 *      Back → focus TopBar.
 *
 *  L1  At TopBar — Back is owned by MainActivity (app-exit dialog) and
 *      is intentionally NOT handled here. This BackHandler stays disabled
 *      while content does not have focus so MainActivity's handler wins.
 *
 *  L0  Profile Overlay open — owned by the overlay's own BackHandler.
 *
 * Today no hero exposes action buttons, so the L4 path always reduces to
 * "focus hero content" and L3 is unreachable. The optional
 * `heroHasActions` / `requestFocusHeroActions` knobs reserve the contract
 * so individual layouts can opt-in later without churn at the call site.
 *
 * Wired once at the [HomeScreen] dispatch level. Per-layout files do NOT
 * install their own BackHandlers — they only emit focus-state events the
 * ViewModel forwards back as the `requestX` callbacks below.
 */
@Composable
fun UniversalHomeBackNavigation(
    enabled: Boolean,
    focusedRowIndex: Int,
    focusedItemIndex: Int,
    requestResetCurrentRowFocus: () -> Unit,
    requestFocusHero: () -> Unit,
    requestFocusTopBar: () -> Unit,
    heroHasActions: Boolean = false,
    requestFocusHeroActions: (() -> Unit)? = null,
    isOnHeroActions: Boolean = false,
) {
    BackHandler(enabled = enabled) {
        when {
            // L5 → L4: deep in row, snap to first card.
            focusedItemIndex > 0 -> requestResetCurrentRowFocus()

            // L4 → L3 or L2: first card in a non-hero row.
            focusedRowIndex > 0 -> {
                if (heroHasActions && requestFocusHeroActions != null) {
                    requestFocusHeroActions()
                } else {
                    requestFocusHero()
                }
            }

            // L3 → L2: at hero actions, drop back to hero content.
            isOnHeroActions -> requestFocusHero()

            // L2 → L1: at hero, jump to TopBar.
            else -> requestFocusTopBar()
        }
    }
}

/**
 * Convenience overload for the common case: focus the navbar via a
 * supplied [FocusRequester]. Wraps the request in a runCatching guard
 * since the requester may not be attached on some focus paths.
 */
@Composable
fun UniversalHomeBackNavigation(
    enabled: Boolean,
    focusedRowIndex: Int,
    focusedItemIndex: Int,
    requestResetCurrentRowFocus: () -> Unit,
    requestFocusHero: () -> Unit,
    topBarFocusRequester: FocusRequester,
    heroHasActions: Boolean = false,
    requestFocusHeroActions: (() -> Unit)? = null,
    isOnHeroActions: Boolean = false,
) {
    UniversalHomeBackNavigation(
        enabled = enabled,
        focusedRowIndex = focusedRowIndex,
        focusedItemIndex = focusedItemIndex,
        requestResetCurrentRowFocus = requestResetCurrentRowFocus,
        requestFocusHero = requestFocusHero,
        requestFocusTopBar = { runCatching { topBarFocusRequester.requestFocus() } },
        heroHasActions = heroHasActions,
        requestFocusHeroActions = requestFocusHeroActions,
        isOnHeroActions = isOnHeroActions,
    )
}
