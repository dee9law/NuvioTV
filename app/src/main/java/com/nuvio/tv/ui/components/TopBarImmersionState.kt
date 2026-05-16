package com.nuvio.tv.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global state holder for the TopBar's immersion-mode visibility.
 *
 * When the user scrolls deep into a home screen's content carousels (past
 * the hero / row 0), HomeScreen sets [setVisible(false)] so the TopBar
 * fades out and the rows feel immersive.  When focus returns to the hero
 * or the top of the screen, [setVisible(true)] brings it back.
 *
 * Lives outside the ViewModel because the TopBar itself is owned by
 * MainActivity's scaffold (above the NavHost), while the focus signal
 * originates inside each home-style screen's composition.  Using a
 * top-level holder avoids plumbing a flow up through nav-host boundaries.
 *
 * Same pattern as [com.nuvio.tv.ui.screens.home.HeroBackdropState].
 *
 * Default is `true` — non-home screens (Search, Settings, etc.) never
 * touch this and should keep the TopBar visible.  Home/Movies/TV reset to
 * `true` in their `onDispose` so navigating away doesn't leave the bar
 * hidden for the next screen.
 */
object TopBarImmersionState {
    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun setVisible(value: Boolean) {
        if (_visible.value != value) _visible.value = value
    }

    /** Force-reset to visible. Call on screen dispose. */
    fun reset() {
        _visible.value = true
    }
}
