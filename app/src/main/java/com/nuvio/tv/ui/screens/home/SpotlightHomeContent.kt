package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.nuvio.tv.LocalNavBarFocusRequester
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.CatalogRowSection
import com.nuvio.tv.ui.components.HeroCarousel
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.util.asStable
import kotlinx.coroutines.delay

/**
 * Spotlight Home — fixed full-bleed hero with a Modern-style row strip
 * that slides up over the hero when focused. Built entirely by
 * composing existing parts:
 *
 *  - [HeroCarousel] (same one Classic / Grid use, parameterized to
 *    ~65% of screen height instead of the default 400dp).
 *  - A vertical LazyColumn of [CatalogRowSection]s (the same row
 *    composable Classic uses).
 *
 * The rows section is anchored BottomStart with an animated height:
 *  - When the user is on the hero: rows take ~35% of screen, hero
 *    visible above.
 *  - When the user moves focus into the rows: the rows container grows
 *    to ~80% of screen, sliding over the hero and revealing more rows.
 *
 * Hero behavior matches Classic / Grid exactly while idle. When a row
 * card gains focus, the hero is recomposed (via `key()`) with a
 * single-item list = [focused card] — same visual + scrim treatment,
 * just driven by the row selection. A 140ms debounce mirrors Modern's
 * `MODERN_HORIZONTAL_FOCUS_DEBOUNCE_MS` so rapid horizontal scrolling
 * doesn't thrash the hero.
 */
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
    val heroHeight = remember(screenHeight) {
        (screenHeight * 0.65f).coerceIn(360.dp, 620.dp)
    }
    val rowsCollapsedHeight = remember(screenHeight) {
        (screenHeight * 0.35f).coerceIn(220.dp, 340.dp)
    }
    val rowsExpandedHeight = remember(screenHeight) {
        // Cover everything except a thin sliver at the top so the
        // user still has visual anchor that "the hero is up there".
        (screenHeight * 0.85f).coerceAtMost(screenHeight - 60.dp)
    }

    // ── State: hero data + rows focus state + debounce ─────────────
    var rowsAreaHasFocus by remember { mutableStateOf(false) }
    var focusedRowItem by remember { mutableStateOf<MetaPreview?>(null) }
    var pendingFocusedRowItem by remember { mutableStateOf<MetaPreview?>(null) }
    LaunchedEffect(pendingFocusedRowItem) {
        delay(140L) // mirrors MODERN_HORIZONTAL_FOCUS_DEBOUNCE_MS
        if (pendingFocusedRowItem != focusedRowItem) {
            focusedRowItem = pendingFocusedRowItem
        }
    }

    // When rows take focus: hero shows the focused row card (single-item
    // hero). When rows lose focus: hero reverts to the configured hero
    // carousel. Same composable in both modes — only the items list
    // changes, and we `key()` on the source so HeroCarousel's internal
    // activeIndex resets cleanly between modes.
    val heroDisplayItems: List<MetaPreview> = when {
        rowsAreaHasFocus && focusedRowItem != null -> listOf(focusedRowItem!!)
        heroCarouselItems.isNotEmpty() -> heroCarouselItems
        // No hero items configured AND rows haven't been focused yet —
        // fall back to the very first row's first item so we don't
        // render a blank backdrop.
        else -> listOfNotNull(catalogRows.firstOrNull()?.items?.firstOrNull())
    }

    // ── Animated row container height (slide-up effect) ────────────
    val rowsContainerHeight by animateDpAsState(
        targetValue = if (rowsAreaHasFocus) rowsExpandedHeight else rowsCollapsedHeight,
        animationSpec = tween(durationMillis = 280),
        label = "spotlightRowsHeight"
    )

    // ── Reset TopBar visibility on entry ────────────────────────────
    DisposableEffect(Unit) {
        com.nuvio.tv.ui.components.TopBarImmersionState.setVisible(true)
        onDispose { com.nuvio.tv.ui.components.TopBarImmersionState.setVisible(true) }
    }

    val navBarFr = LocalNavBarFocusRequester.current
    val heroFocusRequester = remember { FocusRequester() }
    val firstRowEntryFocusRequester = remember { FocusRequester() }

    // Initial focus — mirror Classic / Grid: request focus on the hero
    // once it's composed. Retry across a handful of frames so the
    // request lands after HeroCarousel has been measured.
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

    val rowListState = rememberLazyListState()
    val isVerticalScrollingState = remember(rowListState) {
        derivedStateOf { rowListState.isScrollInProgress }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. Hero — fixed at top, reuses the existing HeroCarousel ──
        //
        // `key()` switches the carousel between "hero-mapped items" and
        // "single focused row card" modes. HeroCarousel manages its
        // own scrolling / auto-advance / page dots; we just hand it a
        // dynamic items list.
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
                    .focusProperties {
                        up = navBarFr
                        down = firstRowEntryFocusRequester
                    }
            )
        }

        // ── 2. Rows — Modern-style LazyColumn, BottomStart, animated
        //         height grows on focus so rows slide UP over the hero.
        //         No background block; the hero scrim handles contrast.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(rowsContainerHeight)
                .onFocusChanged { state -> rowsAreaHasFocus = state.hasFocus }
        ) {
            if (catalogRows.isNotEmpty()) {
                LazyColumn(
                    state = rowListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(
                        items = catalogRows,
                        key = { _, row -> "${row.addonId}_${row.apiType}_${row.catalogId}" }
                    ) { index, row ->
                        CatalogRowSection(
                            catalogRow = row,
                            posterCardStyle = posterCardStyle,
                            showPosterLabels = false,
                            showAddonName = false,
                            showCatalogTypeSuffix = false,
                            focusedPosterBackdropExpandEnabled = false,
                            focusedPosterBackdropTrailerEnabled = false,
                            // Match Modern home's row-title rhythm
                            // (titleMedium + SemiBold + 8dp bottom).
                            compactTitle = true,
                            onItemClick = { id, type, addonBaseUrl ->
                                onNavigateToDetail(id, type, addonBaseUrl)
                            },
                            onSeeAll = {
                                onNavigateToCatalogSeeAll(
                                    row.catalogId,
                                    row.addonId,
                                    row.apiType,
                                )
                            },
                            showSeeAll = row.items.size >= 15,
                            onItemFocus = { item ->
                                pendingFocusedRowItem = item
                                onItemFocus(item)
                            },
                            isItemWatched = isCatalogItemWatched,
                            onItemLongPress = onCatalogItemLongPress,
                            // First row routes D-pad Up back to the hero;
                            // subsequent rows let spatial focus search
                            // bubble up to the previous row's cards.
                            entryFocusRequester = if (index == 0) firstRowEntryFocusRequester else null,
                            upFocusRequester = if (index == 0) heroFocusRequester else null,
                        )
                    }
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
