package com.nuvio.tv.ui.screens.settings

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Edge-wrap focus for D-pad navigation, mirroring the TopBar pill loop.
 *
 * Attach to a list item. [onPrev] (Up for vertical, Left for horizontal) is
 * supplied only on the FIRST item; [onNext] (Down / Right) only on the LAST
 * item. Each callback should scroll the opposite edge into view and request
 * focus there. Intermediate items pass null for both so default navigation is
 * untouched.
 *
 * This is a plain (non-composable) Modifier so callers own the
 * `rememberCoroutineScope` / `FocusRequester` wiring.
 */
fun Modifier.dpadLoopWrap(
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    horizontal: Boolean = false,
): Modifier {
    if (onPrev == null && onNext == null) return this
    val prevKey = if (horizontal) Key.DirectionLeft else Key.DirectionUp
    val nextKey = if (horizontal) Key.DirectionRight else Key.DirectionDown
    return this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            prevKey -> if (onPrev != null) { onPrev(); true } else false
            nextKey -> if (onNext != null) { onNext(); true } else false
            else -> false
        }
    }
}
