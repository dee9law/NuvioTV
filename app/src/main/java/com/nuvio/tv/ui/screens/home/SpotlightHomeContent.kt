package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.LocalNavBarFocusRequester
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.CatalogRowSection
import com.nuvio.tv.ui.components.HeroCarousel
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.util.asStable
import kotlinx.coroutines.delay

/**
 * Spotlight Home — fixed full-bleed hero + fixed-height row strip at
 * the bottom. The rows container is exactly **one row tall** (sized
 * dynamically from the active [PosterCardStyle]) and never resizes;
 * D-pad Down/Up swap the visible row in place, keeping the hero
 * always visible at the top.
 *
 *  - Hero height = `screenHeight - rowsContainerHeight` (no animation).
 *  - Rows container = one row's height computed from card size.
 *  - D-pad Down from card → next row (or no-op on last row).
 *  - D-pad Up from card on first row → hero. Up on row 1+ → previous row.
 *  - D-pad Down from hero → first card of the currently-displayed row.
 *
 * When the row strip has focus, the hero recomposes (via `key()`) with
 * a single-item list = the focused card, mirroring Modern's
 * focused-poster preview. A 140ms debounce keeps rapid horizontal
 * scrolling from thrashing the backdrop.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SpotlightHomeContent(
    uiState: HomeUiState,
    focusState: HomeScreenFocusState,
    posterCardStyle: PosterCardStyle,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit = { _, _ -> },
    isCatalogItemWatched: (MetaPreview) -> Boolean = { false },
    onCatalogItemLongPress: (MetaPreview, String) -> Unit = { _, _ -> },
    onItemFocus: (MetaPreview) -> Unit = {},
) {
    // ── Data sources ────────────────────────────────────────────────
    val catalogRows = remember(uiState.catalogRows, uiState.homeRows) {
        when {
            uiState.catalogRows.isNotEmpty() -> uiState.catalogRows
            uiState.homeRows.isNotEmpty() -> uiState.homeRows
                .mapNotNull { (it as? HomeRow.Catalog)?.row }
            else -> emptyList()
        }
    }
    val heroCarouselItems = uiState.heroItems

    // ── Sizing ──────────────────────────────────────────────────────
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val rowsContainerHeight = remember(posterCardStyle.height) {
        singleRowContainerHeight(posterCardStyle.height)
    }
    val heroHeight = remember(screenHeight, rowsContainerHeight) {
        heroHeightForRowsContainer(screenHeight, rowsContainerHeight)
    }

    // ── State: hero focus mirror + one-row-at-a-time index ──────────
    var rowsAreaHasFocus by remember { mutableStateOf(false) }
    var focusedRowItem by remember { mutableStateOf<MetaPreview?>(null) }
    var pendingFocusedRowItem by remember { mutableStateOf<MetaPreview?>(null) }
    LaunchedEffect(pendingFocusedRowItem) {
        delay(140L) // mirrors MODERN_HORIZONTAL_FOCUS_DEBOUNCE_MS
        if (pendingFocusedRowItem != focusedRowItem) {
            focusedRowItem = pendingFocusedRowItem
        }
    }

    var currentRowIndex by remember { mutableIntStateOf(0) }
    // Clamp index if catalogRows shrinks underneath us.
    LaunchedEffect(catalogRows.size) {
        if (catalogRows.isNotEmpty() && currentRowIndex > catalogRows.lastIndex) {
            currentRowIndex = catalogRows.lastIndex
        }
    }

    val heroDisplayItems: List<MetaPreview> = when {
        rowsAreaHasFocus && focusedRowItem != null -> listOf(focusedRowItem!!)
        heroCarouselItems.isNotEmpty() -> heroCarouselItems
        else -> listOfNotNull(catalogRows.firstOrNull()?.items?.firstOrNull())
    }

    // ── TopBar immersion: hide when rows have focus, show on hero ──
    LaunchedEffect(rowsAreaHasFocus) {
        com.nuvio.tv.ui.components.TopBarImmersionState.setVisible(!rowsAreaHasFocus)
    }
    DisposableEffect(Unit) {
        onDispose { com.nuvio.tv.ui.components.TopBarImmersionState.setVisible(true) }
    }

    val navBarFr = LocalNavBarFocusRequester.current
    // Bound to the outer Box via `Modifier.focusRequester` below — this
    // is the requester the TopBar's D-pad-Down handler invokes when the
    // user taps Down from a pill. Without this binding, returning to the
    // Spotlight screen from another tab leaves no entry point: D-pad Down
    // from the TopBar would no-op because contentFr.requestFocus() had
    // nothing to land on.
    val contentFocusRequester = LocalContentFocusRequester.current
    val heroFocusRequester = remember { FocusRequester() }
    // Attached to the first card of whichever row is currently visible.
    // Same instance across row swaps so Hero's `down` and parent-level
    // Up-from-row-N can both target it.
    val firstRowEntryFocusRequester = remember { FocusRequester() }

    val shouldRequestInitialFocus = remember(focusState) {
        !focusState.hasSavedFocus &&
            focusState.verticalScrollIndex == 0 &&
            focusState.verticalScrollOffset == 0
    }
    LaunchedEffect(shouldRequestInitialFocus, heroDisplayItems.isNotEmpty()) {
        if (!shouldRequestInitialFocus) return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            val focused = runCatching {
                if (heroDisplayItems.isNotEmpty()) {
                    heroFocusRequester.requestFocus()
                } else {
                    firstRowEntryFocusRequester.requestFocus()
                }
                true
            }.getOrDefault(false)
            if (focused) return@LaunchedEffect
        }
    }

    // When the user swaps rows via D-pad, re-target focus onto the new
    // row's first card. Gated by `rowsAreaHasFocus` so the initial-focus
    // request (hero on first mount) isn't fought.
    LaunchedEffect(currentRowIndex) {
        if (!rowsAreaHasFocus) return@LaunchedEffect
        repeat(15) {
            withFrameNanos { }
            val ok = runCatching {
                firstRowEntryFocusRequester.requestFocus(); true
            }.getOrDefault(false)
            if (ok) return@LaunchedEffect
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Bind LocalContentFocusRequester to this outer Box + provide a
            // focusRestorer that lands on the hero by default.  This is what
            // makes "tap a tab pill, press D-pad Down" reach the Spotlight
            // content — the TopBar's Down handler calls `contentFr
            // .requestFocus()`, which routes here.  Without it, returning
            // from another tab leaves the screen un-enterable.
            .focusRequester(contentFocusRequester)
            .focusRestorer(heroFocusRequester)
            .focusGroup()
    ) {
        // ── 1. Hero — fixed at top, never resizes or moves. ─────────
        key(rowsAreaHasFocus, focusedRowItem?.id) {
            HeroCarousel(
                items = heroDisplayItems.asStable(),
                onItemClick = { item ->
                    onNavigateToDetail(item.id, item.apiType, "")
                },
                focusRequester = heroFocusRequester,
                heroHeight = heroHeight,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // Explicit Down handler.  The hero lives outside any
                    // LazyColumn here, so Compose's spatial focus search
                    // doesn't reliably find the rows below; pointing
                    // `focusProperties.down` at the row's first-card
                    // requester was also racy when the row hadn't measured
                    // yet.  Intercepting Down here is the reliable path.
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionDown
                        ) {
                            runCatching { firstRowEntryFocusRequester.requestFocus() }
                                .getOrDefault(false)
                            true
                        } else false
                    }
                    .focusProperties {
                        up = navBarFr
                        // Kept as a defensive fallback in case the preview
                        // handler above doesn't fire (e.g. on some focus
                        // search paths Compose bypasses preview).
                        down = firstRowEntryFocusRequester
                    }
            )
        }

        // ── 2. Rows — FIXED one-row-tall container at the bottom. ───
        // D-pad Up/Down intercepted at parent level via onPreviewKeyEvent
        // so the row-strip swaps in place instead of moving focus
        // outside the container or scrolling vertically.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(rowsContainerHeight)
                .clipToBounds()
                .onFocusChanged { state -> rowsAreaHasFocus = state.hasFocus }
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown) {
                                android.util.Log.d("SpotlightNav", "Down pressed, currentRowIndex=$currentRowIndex, totalRows=${catalogRows.size}")
                                if (currentRowIndex < catalogRows.lastIndex) {
                                    currentRowIndex++
                                }
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (event.type == KeyEventType.KeyDown) {
                                android.util.Log.d("SpotlightNav", "Up pressed, currentRowIndex=$currentRowIndex")
                                if (currentRowIndex == 0) {
                                    runCatching { heroFocusRequester.requestFocus() }
                                } else {
                                    currentRowIndex--
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            val currentRow = catalogRows.getOrNull(currentRowIndex)
            if (currentRow != null) {
                key(currentRowIndex) {
                    CatalogRowSection(
                        catalogRow = currentRow,
                        posterCardStyle = posterCardStyle,
                        showPosterLabels = false,
                        showAddonName = false,
                        showCatalogTypeSuffix = false,
                        focusedPosterBackdropExpandEnabled = false,
                        focusedPosterBackdropTrailerEnabled = false,
                        compactTitle = true,
                        onItemClick = { id, type, addonBaseUrl ->
                            onNavigateToDetail(id, type, addonBaseUrl)
                        },
                        onSeeAll = {
                            onNavigateToCatalogSeeAll(
                                currentRow.catalogId,
                                currentRow.addonId,
                                currentRow.apiType,
                            )
                        },
                        showSeeAll = currentRow.items.size >= 15,
                        onItemFocus = { item ->
                            pendingFocusedRowItem = item
                            onItemFocus(item)
                        },
                        isItemWatched = isCatalogItemWatched,
                        onItemLongPress = onCatalogItemLongPress,
                        entryFocusRequester = firstRowEntryFocusRequester,
                        // upFocusRequester intentionally null — the parent
                        // Box's onPreviewKeyEvent owns all Up/Down handling.
                        upFocusRequester = null,
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.tv.material3.Text(
                        text = "No rows configured",
                        style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
                        color = NuvioColors.TextSecondary,
                    )
                }
            }
        }
    }
}
