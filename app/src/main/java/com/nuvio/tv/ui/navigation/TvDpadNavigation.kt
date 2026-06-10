package com.nuvio.tv.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.nuvio.tv.LocalNavBarFocusRequester
import com.nuvio.tv.LocalSideRailController

/**
 * D-pad Up routing: first attempt natural focus movement upward; if no focusable exists
 * above the currently focused element, route to the top navigation bar via
 * [LocalNavBarFocusRequester].
 *
 * Apply to the outer container of a screen. The modifier captures Up at the screen level
 * and lets Compose's focus traversal handle in-screen navigation, falling back to the nav
 * bar only at the topmost edge. [enabled] can be used to disable the modifier entirely.
 */
fun Modifier.dpadUpToTopNav(enabled: () -> Boolean = { true }): Modifier = composed {
    val navBarFr = LocalNavBarFocusRequester.current
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (enabled() &&
            event.type == KeyEventType.KeyDown &&
            event.key == Key.DirectionUp
        ) {
            if (focusManager.moveFocus(FocusDirection.Up)) {
                true
            } else {
                runCatching { navBarFr.requestFocus() }.isSuccess
            }
        } else {
            false
        }
    }
}

/**
 * D-pad Left routing: first attempt natural focus movement leftward; if no focusable
 * exists to the left, invoke the SideRail via [LocalSideRailController].
 *
 * Apply to the outer container of a screen. The modifier captures Left at the screen
 * level and lets Compose's focus traversal handle in-screen navigation, falling back to
 * the SideRail only at the leftmost edge. [enabled] can be used to disable the modifier
 * entirely.
 */
fun Modifier.dpadLeftToSideRail(enabled: () -> Boolean = { true }): Modifier = composed {
    val rail = LocalSideRailController.current
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (enabled() &&
            event.type == KeyEventType.KeyDown &&
            event.key == Key.DirectionLeft
        ) {
            if (focusManager.moveFocus(FocusDirection.Left)) {
                true
            } else if (rail != null) {
                rail()
                true
            } else {
                false
            }
        } else {
            false
        }
    }
}

/**
 * Per-item D-pad Left → SideRail routing. Apply ONLY to the FIRST item
 * (index 0) of a horizontal carousel.  When that item is focused and the
 * user presses Left, the SideRail opens directly — no fallback through
 * `focusManager.moveFocus`, so an empty area or non-carousel gap between
 * rows won't accidentally invoke the rail (which the screen-level
 * [dpadLeftToSideRail] used to do).
 *
 * This is the implementation of nav-spec rule "Entry trigger: D-pad Left
 * from the FIRST item (index 0) of any horizontal carousel only".
 */
fun Modifier.tvLeftFromFirstItemToSideRail(enabled: () -> Boolean = { true }): Modifier = composed {
    val rail = LocalSideRailController.current
    onPreviewKeyEvent { event ->
        if (enabled() &&
            event.type == KeyEventType.KeyDown &&
            event.key == Key.DirectionLeft
        ) {
            // Legacy feel: open the SideRail. Modern feel provides a null
            // controller → the press is still consumed so the row behaves as a
            // closed horizontal container (no spill into the TopBar or
            // neighbouring focusables).
            rail?.invoke()
            true
        } else {
            false
        }
    }
}

/**
 * Right-edge hard stop for horizontal carousels. Apply ONLY to the LAST
 * focusable item of a row (last content card, or the trailing "See All"
 * card when one is shown). Consumes D-pad Right so focus never escapes the
 * row — no jump to the TopBar, no wrap, no spill into other rows.
 *
 * Together with [tvLeftFromFirstItemToSideRail] on the first item this makes
 * every content row a closed horizontal container. The TopBar's own
 * loop-wrap is unaffected: it lives on the bar's pills and only fires while
 * focus is inside the bar.
 */
fun Modifier.tvStopRightAtLastItem(enabled: () -> Boolean = { true }): Modifier =
    onPreviewKeyEvent { event ->
        enabled() &&
            event.type == KeyEventType.KeyDown &&
            event.key == Key.DirectionRight
    }

/**
 * Unified Back-button routing for any screen with carousel/list content:
 *  - When [contentHasFocus] is false, the handler is disabled — back falls through to the
 *    parent BackHandler (typically the root in MainActivity which exits the app).
 *  - When content has focus and [isAtFirstItem] is false, [requestFirstItemFocus] is invoked
 *    to scroll/focus the first item of the current carousel or list.
 *  - When content has focus and [isAtFirstItem] is true, focus moves to the top nav bar.
 */
@Composable
fun TvBackToFirstThenTopNav(
    contentHasFocus: () -> Boolean,
    isAtFirstItem: () -> Boolean,
    requestFirstItemFocus: () -> Unit,
) {
    val navBarFr = LocalNavBarFocusRequester.current
    BackHandler(enabled = contentHasFocus()) {
        if (isAtFirstItem()) {
            runCatching { navBarFr.requestFocus() }
        } else {
            requestFirstItemFocus()
        }
    }
}
