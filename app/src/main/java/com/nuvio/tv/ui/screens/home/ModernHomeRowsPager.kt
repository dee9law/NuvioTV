package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.LocalNavBarFocusRequester
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.domain.model.continueWatchingStyle
import com.nuvio.tv.domain.model.CW_DEFAULT_CARD_WIDTH_DP
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.continueWatchingCardFootprint
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.util.StableMap
import com.nuvio.tv.ui.util.StableRef

/**
 * Modern **State 1** (fullscreen backdrop) row presentation: ONE row visible at
 * a time, fixed at the bottom of the screen, with a crossfade between rows on
 * D-pad up/down and dimmed prev/next row-name hints above and below.
 *
 * This is a deliberate divergence from [ModernHomeRowsList] (the State-2 multi-
 * row LazyColumn). State 1's fullscreen backdrop reads best with a single
 * focused row floating over it, swapping with the same crossfade language as the
 * backdrop itself. The hero/backdrop are untouched — only the row list changes.
 *
 * Vertical navigation: a single row has no spatial sibling for Compose to move
 * focus to, so up/down are intercepted at the container, the visible row index
 * is swapped, and focus is re-driven onto the new row via its
 * [ModernRowSection] `rowFocusRequester` (whose `focusRestorer` lands on the
 * row's saved item). At index 0, D-pad Up is NOT consumed so it escapes to the
 * TopBar exactly like the LazyColumn's row 0 does.
 */
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
internal fun ModernHomeRowsPager(
    carouselRows: com.nuvio.tv.ui.util.StableList<HeroCarouselRow>,
    focusState: HomeScreenFocusState,
    activeRowKey: State<String?>,
    activeItemIndex: State<Int>,
    focusedItemByRow: StableRef<MutableMap<String, Int>>,
    rowListStates: StableRef<MutableMap<String, LazyListState>>,
    loadMoreRequestedTotals: StableRef<MutableMap<String, Int>>,
    resetRowFocusTrigger: Int,
    focusHeroTrigger: Int,
    contentFocusRequester: FocusRequester,
    onRowItemFocusedInternal: (String, Int, Boolean) -> Unit,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit,
    onLoadMoreCatalog: (String, String, String) -> Unit,
    onNavigateToCatalogSeeAll: (catalogId: String, addonId: String, apiType: String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onContinueWatchingOptions: (ContinueWatchingItem) -> Unit,
    onRequestLazyCatalogLoad: (String) -> Unit,
    onBackdropInteraction: () -> Unit,
    onExpandedCatalogFocusKeyChange: (String?) -> Unit,
    isCatalogItemWatched: (MetaPreview) -> Boolean,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit,
    onItemFocus: (MetaPreview) -> Unit,
    onPreloadAdjacentItem: (MetaPreview) -> Unit,
    enrichedPreviews: StableMap<String, MetaPreview>,
    useLandscapePosters: Boolean,
    showLabels: Boolean,
    posterCardCornerRadius: Dp,
    focusedPosterBackdropTrailerMuted: Boolean,
    effectiveExpandEnabled: Boolean,
    effectiveAutoplayEnabled: Boolean,
    trailerPlaybackTarget: FocusedPosterTrailerPlaybackTarget,
    expandedCatalogFocusKey: State<String?>,
    expandedTrailerPreviewUrl: () -> String?,
    expandedTrailerPreviewAudioUrl: () -> String?,
    portraitCatalogCardWidth: Dp,
    portraitCatalogCardHeight: Dp,
    landscapeCatalogCardWidth: Dp,
    landscapeCatalogCardHeight: Dp,
    continueWatchingCardWidth: Dp,
    continueWatchingCardHeight: Dp,
    blurUnwatchedEpisodes: Boolean,
    useEpisodeThumbnails: Boolean,
    rowConfigLookup: Map<String, LayoutRowConfig>,
    pendingRowFocusKey: State<String?>,
    pendingRowFocusIndex: State<Int?>,
    pendingRowFocusNonce: State<Int>,
    onPendingRowFocusCleared: () -> Unit,
    onActiveRowKeyChange: (String?) -> Unit,
    onActiveItemIndexChange: (Int) -> Unit,
    lastHeroNavigationAtMs: State<Long>,
    onLastHeroNavigationAtMsChange: (Long) -> Unit,
    onHeroFocusSettleDelayChange: (Long) -> Unit,
    lastFocusedContinueWatchingIndex: State<Int>,
    onLastFocusedContinueWatchingIndexChange: (Int) -> Unit,
    focusedCatalogSelection: State<FocusedCatalogSelection?>,
    onFocusedCatalogSelectionChange: (FocusedCatalogSelection?) -> Unit,
    focusedHeroMediaNonce: State<Int>,
    onFocusedHeroMediaNonceChange: (Int) -> Unit,
    isVerticalRowsScrollingState: State<Boolean>,
    defaultBringIntoViewSpec: androidx.compose.foundation.gestures.BringIntoViewSpec,
    onContentFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = carouselRows.list
    if (rows.isEmpty()) return

    val focusedItemByRowMap = focusedItemByRow.value

    // ── Which single row is on screen ───────────────────────────────────────
    var currentRowIndex by remember {
        // Restore to the active row, falling back to the saved focus row (on
        // return-from-detail activeRowKey may still be null), else the first row.
        val key = activeRowKey.value ?: focusState.focusedRowKey
        val saved = rows.indexOfFirst { it.key == key }
        mutableIntStateOf(saved.coerceAtLeast(0))
    }
    // Keep the index valid as the row set changes (lazy load, settings edits).
    LaunchedEffect(rows.size) {
        if (currentRowIndex > rows.lastIndex) currentRowIndex = rows.lastIndex.coerceAtLeast(0)
    }
    // Bumped on every row switch so the focus LaunchedEffect re-fires even when
    // landing back on a previously-visited index.
    var switchNonce by remember { mutableIntStateOf(0) }
    // Throttle held D-pad up/down so an autorepeat flood steps one row per window
    // instead of skipping several rows in a frame.
    var lastSwitchMs by remember { mutableLongStateOf(0L) }

    val navBarFr = LocalNavBarFocusRequester.current
    // The current row's LazyRow requester. focusRestorer on the row lands on its
    // saved item, so requesting this after a switch restores per-row focus.
    val currentRowFr = remember { FocusRequester() }
    // Throwaway requester for the OUTGOING crossfade copy (never focused).
    val rowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val stableItemFocusRequestersByRow = remember { mutableMapOf<String, StableRef<MutableMap<Int, FocusRequester>>>() }
    val falseScrolling = remember { derivedStateOf { false } }

    val latestOnActiveRowKeyChange = rememberUpdatedState(onActiveRowKeyChange)
    val latestOnActiveItemIndexChange = rememberUpdatedState(onActiveItemIndexChange)
    val latestRows = rememberUpdatedState(rows)

    // Switch to a target row index: update the hero immediately (active row/item),
    // bump the nonce so focus is re-driven onto the new row.
    val switchTo: (Int) -> Unit = remember {
        { target: Int ->
            val list = latestRows.value
            val clamped = target.coerceIn(0, list.lastIndex)
            if (clamped != currentRowIndex) {
                val targetRow = list[clamped]
                val savedIdx = (focusedItemByRowMap[targetRow.key] ?: 0)
                    .coerceIn(0, (targetRow.items.list.size - 1).coerceAtLeast(0))
                latestOnActiveRowKeyChange.value(targetRow.key)
                latestOnActiveItemIndexChange.value(savedIdx)
                currentRowIndex = clamped
                switchNonce++
            }
        }
    }

    // Drive focus onto the visible row after a switch (and on first entry the
    // container's focusRestorer handles it). Retry a few frames because the new
    // crossfade content needs to attach its requester first.
    LaunchedEffect(switchNonce) {
        if (switchNonce == 0) return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            val landed = runCatching { currentRowFr.requestFocus(); true }.getOrDefault(false)
            if (landed) return@LaunchedEffect
        }
    }

    // Lazy-load the visible row + the next one when they're still placeholders.
    LaunchedEffect(currentRowIndex, rows) {
        for (idx in intArrayOf(currentRowIndex, currentRowIndex + 1)) {
            val row = rows.getOrNull(idx) ?: continue
            if (row.isLoading && row.items.list.firstOrNull()?.imageUrl == "placeholder://empty") {
                onRequestLazyCatalogLoad(row.key)
            }
        }
    }

    // L3 Back: focus the first item of the visible row.
    LaunchedEffect(resetRowFocusTrigger) {
        if (resetRowFocusTrigger <= 0) return@LaunchedEffect
        val row = rows.getOrNull(currentRowIndex) ?: return@LaunchedEffect
        val target = stableItemFocusRequestersByRow[row.key]?.value?.get(0)
            ?: rowFocusRequesters[row.key]
            ?: currentRowFr
        runCatching { target.requestFocus() }
    }

    // L1 Back: jump to the first row (the billboard equivalent in Modern).
    LaunchedEffect(focusHeroTrigger) {
        if (focusHeroTrigger <= 0) return@LaunchedEffect
        switchTo(0)
        repeat(10) {
            withFrameNanos { }
            if (runCatching { currentRowFr.requestFocus(); true }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    // ── Container height tracks the visible row's card height ────────────────
    val currentRow = rows[currentRowIndex]
    val currentCardHeight = modernPagerRowCardHeight(
        row = currentRow,
        rowConfigLookup = rowConfigLookup,
        useLandscapePosters = useLandscapePosters,
        portraitCardHeight = portraitCatalogCardHeight,
        landscapeCardHeight = landscapeCatalogCardHeight,
        cwCardHeight = continueWatchingCardHeight,
    )
    val targetPagerHeight = singleRowContainerHeight(currentCardHeight) + MODERN_PAGER_HINTS_RESERVE
    val pagerHeight by animateDpAsState(
        targetValue = targetPagerHeight,
        animationSpec = tween(durationMillis = 220),
        label = "modernPagerHeight",
    )

    val hintStyle = MaterialTheme.typography.bodySmall
    val rowStartInset = 16.dp

    CompositionLocalProvider(
        LocalFastScrollActive provides false,
        LocalVerticalRowsScrolling provides falseScrolling,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(pagerHeight)
                .clipToBounds()
                .focusRequester(contentFocusRequester)
                .onFocusChanged { onContentFocusChanged(it.hasFocus) }
                .focusRestorer { currentRowFr }
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            // Consume at the last row so focus never escapes
                            // downward off the rows; otherwise throttle autorepeat
                            // and step to the next row.
                            if (currentRowIndex >= rows.lastIndex) return@onPreviewKeyEvent true
                            if (event.nativeKeyEvent.repeatCount > 0 &&
                                System.currentTimeMillis() - lastSwitchMs < MODERN_PAGER_REPEAT_MS
                            ) {
                                return@onPreviewKeyEvent true
                            }
                            lastSwitchMs = System.currentTimeMillis()
                            switchTo(currentRowIndex + 1)
                            true
                        }
                        Key.DirectionUp -> {
                            // At row 0, DON'T consume → up escapes to the TopBar.
                            if (currentRowIndex <= 0) return@onPreviewKeyEvent false
                            if (event.nativeKeyEvent.repeatCount > 0 &&
                                System.currentTimeMillis() - lastSwitchMs < MODERN_PAGER_REPEAT_MS
                            ) {
                                return@onPreviewKeyEvent true
                            }
                            lastSwitchMs = System.currentTimeMillis()
                            switchTo(currentRowIndex - 1)
                            true
                        }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Prev row-name hint (above) ──────────────────────────────────
            val prevTitle = rows.getOrNull(currentRowIndex - 1)?.title
            if (prevTitle != null) {
                Text(
                    text = prevTitle,
                    style = hintStyle,
                    color = NuvioColors.TextSecondary.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = rowStartInset, bottom = 4.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(MODERN_PAGER_HINT_HEIGHT))
            }

            // ── Current row (crossfaded on switch) ──────────────────────────
            Crossfade(
                targetState = currentRowIndex,
                animationSpec = tween(durationMillis = 220),
                label = "modernPagerRow",
            ) { idx ->
                val row = latestRows.value.getOrNull(idx) ?: return@Crossfade
                val isVisibleRow = idx == currentRowIndex
                val rowFr = if (isVisibleRow) {
                    currentRowFr
                } else {
                    rowFocusRequesters.getOrPut(row.key) { FocusRequester() }
                }
                ModernPagerRow(
                    row = row,
                    isVisibleRow = isVisibleRow,
                    rowFocusRequester = rowFr,
                    upFocusRequester = if (idx == 0) navBarFr else null,
                    defaultBringIntoViewSpec = defaultBringIntoViewSpec,
                    focusState = focusState,
                    focusedItemByRow = focusedItemByRow,
                    rowListStates = rowListStates,
                    loadMoreRequestedTotals = loadMoreRequestedTotals,
                    activeRowKey = activeRowKey,
                    activeItemIndex = activeItemIndex,
                    pendingRowFocusKey = pendingRowFocusKey,
                    pendingRowFocusIndex = pendingRowFocusIndex,
                    pendingRowFocusNonce = pendingRowFocusNonce,
                    onPendingRowFocusCleared = onPendingRowFocusCleared,
                    onRowItemFocusedInternal = onRowItemFocusedInternal,
                    onActiveRowKeyChange = onActiveRowKeyChange,
                    onActiveItemIndexChange = onActiveItemIndexChange,
                    lastHeroNavigationAtMs = lastHeroNavigationAtMs,
                    onLastHeroNavigationAtMsChange = onLastHeroNavigationAtMsChange,
                    onHeroFocusSettleDelayChange = onHeroFocusSettleDelayChange,
                    lastFocusedContinueWatchingIndex = lastFocusedContinueWatchingIndex,
                    onLastFocusedContinueWatchingIndexChange = onLastFocusedContinueWatchingIndexChange,
                    focusedCatalogSelection = focusedCatalogSelection,
                    onFocusedCatalogSelectionChange = onFocusedCatalogSelectionChange,
                    focusedHeroMediaNonce = focusedHeroMediaNonce,
                    onFocusedHeroMediaNonceChange = onFocusedHeroMediaNonceChange,
                    itemFocusRequesters = stableItemFocusRequestersByRow.getOrPut(row.key) {
                        StableRef(mutableMapOf())
                    },
                    useLandscapePosters = useLandscapePosters,
                    showLabels = showLabels,
                    posterCardCornerRadius = posterCardCornerRadius,
                    focusedPosterBackdropTrailerMuted = focusedPosterBackdropTrailerMuted,
                    effectiveExpandEnabled = (row.layoutConfigKey?.let { rowConfigLookup[it] }?.let {
                        resolveRowDisplayConfig(it, it.viewContext, effectiveExpandEnabled).effectiveExpands
                    } ?: effectiveExpandEnabled),
                    effectiveAutoplayEnabled = effectiveAutoplayEnabled,
                    trailerPlaybackTarget = trailerPlaybackTarget,
                    expandedCatalogFocusKey = expandedCatalogFocusKey,
                    expandedTrailerPreviewUrl = expandedTrailerPreviewUrl,
                    expandedTrailerPreviewAudioUrl = expandedTrailerPreviewAudioUrl,
                    portraitCatalogCardWidth = portraitCatalogCardWidth,
                    portraitCatalogCardHeight = portraitCatalogCardHeight,
                    landscapeCatalogCardWidth = landscapeCatalogCardWidth,
                    landscapeCatalogCardHeight = landscapeCatalogCardHeight,
                    continueWatchingCardWidth = continueWatchingCardWidth,
                    continueWatchingCardHeight = continueWatchingCardHeight,
                    blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                    useEpisodeThumbnails = useEpisodeThumbnails,
                    onContinueWatchingClick = onContinueWatchingClick,
                    onContinueWatchingOptions = onContinueWatchingOptions,
                    isCatalogItemWatched = isCatalogItemWatched,
                    onCatalogItemLongPress = onCatalogItemLongPress,
                    onItemFocus = onItemFocus,
                    onPreloadAdjacentItem = onPreloadAdjacentItem,
                    enrichedPreviews = enrichedPreviews,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToFolderDetail = onNavigateToFolderDetail,
                    onLoadMoreCatalog = onLoadMoreCatalog,
                    onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAll,
                    onBackdropInteraction = onBackdropInteraction,
                    onExpandedCatalogFocusKeyChange = onExpandedCatalogFocusKeyChange,
                    isVerticalRowsScrollingState = isVerticalRowsScrollingState,
                    rowConfig = row.layoutConfigKey?.let { rowConfigLookup[it] },
                )
            }

            // ── Next row-name hint (below) ──────────────────────────────────
            val nextTitle = rows.getOrNull(currentRowIndex + 1)?.title
            if (nextTitle != null) {
                Text(
                    text = nextTitle,
                    style = hintStyle,
                    color = NuvioColors.TextSecondary.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = rowStartInset, top = 4.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(MODERN_PAGER_HINT_HEIGHT))
            }
        }
    }
}

/**
 * Thin wrapper around [ModernRowSection] for the pager. Wires the per-row
 * focus/hero plumbing that [ModernHomeRowsList] builds inline, so the pager call
 * site stays readable.
 */
@Composable
private fun ModernPagerRow(
    row: HeroCarouselRow,
    isVisibleRow: Boolean,
    rowFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    defaultBringIntoViewSpec: androidx.compose.foundation.gestures.BringIntoViewSpec,
    focusState: HomeScreenFocusState,
    focusedItemByRow: StableRef<MutableMap<String, Int>>,
    rowListStates: StableRef<MutableMap<String, LazyListState>>,
    loadMoreRequestedTotals: StableRef<MutableMap<String, Int>>,
    activeRowKey: State<String?>,
    activeItemIndex: State<Int>,
    pendingRowFocusKey: State<String?>,
    pendingRowFocusIndex: State<Int?>,
    pendingRowFocusNonce: State<Int>,
    onPendingRowFocusCleared: () -> Unit,
    onRowItemFocusedInternal: (String, Int, Boolean) -> Unit,
    onActiveRowKeyChange: (String?) -> Unit,
    onActiveItemIndexChange: (Int) -> Unit,
    lastHeroNavigationAtMs: State<Long>,
    onLastHeroNavigationAtMsChange: (Long) -> Unit,
    onHeroFocusSettleDelayChange: (Long) -> Unit,
    lastFocusedContinueWatchingIndex: State<Int>,
    onLastFocusedContinueWatchingIndexChange: (Int) -> Unit,
    focusedCatalogSelection: State<FocusedCatalogSelection?>,
    onFocusedCatalogSelectionChange: (FocusedCatalogSelection?) -> Unit,
    focusedHeroMediaNonce: State<Int>,
    onFocusedHeroMediaNonceChange: (Int) -> Unit,
    itemFocusRequesters: StableRef<MutableMap<Int, FocusRequester>>,
    useLandscapePosters: Boolean,
    showLabels: Boolean,
    posterCardCornerRadius: Dp,
    focusedPosterBackdropTrailerMuted: Boolean,
    effectiveExpandEnabled: Boolean,
    effectiveAutoplayEnabled: Boolean,
    trailerPlaybackTarget: FocusedPosterTrailerPlaybackTarget,
    expandedCatalogFocusKey: State<String?>,
    expandedTrailerPreviewUrl: () -> String?,
    expandedTrailerPreviewAudioUrl: () -> String?,
    portraitCatalogCardWidth: Dp,
    portraitCatalogCardHeight: Dp,
    landscapeCatalogCardWidth: Dp,
    landscapeCatalogCardHeight: Dp,
    continueWatchingCardWidth: Dp,
    continueWatchingCardHeight: Dp,
    blurUnwatchedEpisodes: Boolean,
    useEpisodeThumbnails: Boolean,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onContinueWatchingOptions: (ContinueWatchingItem) -> Unit,
    isCatalogItemWatched: (MetaPreview) -> Boolean,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit,
    onItemFocus: (MetaPreview) -> Unit,
    onPreloadAdjacentItem: (MetaPreview) -> Unit,
    enrichedPreviews: StableMap<String, MetaPreview>,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit,
    onLoadMoreCatalog: (String, String, String) -> Unit,
    onNavigateToCatalogSeeAll: (catalogId: String, addonId: String, apiType: String) -> Unit,
    onBackdropInteraction: () -> Unit,
    onExpandedCatalogFocusKeyChange: (String?) -> Unit,
    isVerticalRowsScrollingState: State<Boolean>,
    rowConfig: LayoutRowConfig?,
) {
    val stableOnContinueWatchingOptions = remember(onContinueWatchingOptions) {
        { item: ContinueWatchingItem -> onContinueWatchingOptions(item) }
    }
    val stableOnRowItemFocused = remember {
        { rowKey: String, index: Int, isContinueWatchingRow: Boolean ->
            val rowBecameActive = activeRowKey.value != rowKey
            val itemChanged = activeItemIndex.value != index
            if (rowBecameActive || itemChanged) {
                val now = System.currentTimeMillis()
                val timeSinceLastHeroNav = now - lastHeroNavigationAtMs.value
                onHeroFocusSettleDelayChange(
                    if (lastHeroNavigationAtMs.value != 0L && timeSinceLastHeroNav in 1 until 130L) 400L
                    else 450L
                )
                onLastHeroNavigationAtMsChange(now)
                onActiveRowKeyChange(rowKey)
                onActiveItemIndexChange(index)
            }
            if (focusedItemByRow.value[rowKey] != index) {
                focusedItemByRow.value[rowKey] = index
            }
            if (isContinueWatchingRow) {
                if (lastFocusedContinueWatchingIndex.value != index) {
                    onLastFocusedContinueWatchingIndexChange(index)
                }
                if (focusedCatalogSelection.value != null) {
                    onFocusedCatalogSelectionChange(null)
                }
            }
            onRowItemFocusedInternal(rowKey, index, isContinueWatchingRow)
        }
    }
    val isActiveRowLambda = remember(row.key, isVisibleRow) { { isVisibleRow } }
    val stableOnCatalogSelectionFocused = remember {
        { selection: FocusedCatalogSelection ->
            val isCollectionFolder = selection.payload is ModernPayload.CollectionFolder
            if (focusedCatalogSelection.value != selection || isCollectionFolder) {
                onFocusedCatalogSelectionChange(selection)
                if (isCollectionFolder) {
                    onFocusedHeroMediaNonceChange(focusedHeroMediaNonce.value + 1)
                }
            }
        }
    }
    ModernRowSection(
        row = row,
        isActiveRow = isActiveRowLambda,
        rowFocusRequester = rowFocusRequester,
        rowTitleBottom = 2.dp,
        defaultBringIntoViewSpec = defaultBringIntoViewSpec,
        focusStateCatalogRowScrollIndex = focusState.catalogRowScrollStates[row.key] ?: 0,
        focusedItemByRow = focusedItemByRow,
        rowListStates = rowListStates,
        loadMoreRequestedTotals = loadMoreRequestedTotals,
        pendingRowFocusKey = pendingRowFocusKey,
        pendingRowFocusIndex = pendingRowFocusIndex,
        pendingRowFocusNonce = pendingRowFocusNonce,
        onPendingRowFocusCleared = onPendingRowFocusCleared,
        onRowItemFocused = stableOnRowItemFocused,
        useLandscapePosters = useLandscapePosters,
        showLabels = showLabels,
        posterCardCornerRadius = posterCardCornerRadius,
        focusedPosterBackdropTrailerMuted = focusedPosterBackdropTrailerMuted,
        effectiveExpandEnabled = effectiveExpandEnabled,
        effectiveAutoplayEnabled = effectiveAutoplayEnabled,
        trailerPlaybackTarget = trailerPlaybackTarget,
        expandedCatalogFocusKey = expandedCatalogFocusKey,
        expandedTrailerPreviewUrl = expandedTrailerPreviewUrl,
        expandedTrailerPreviewAudioUrl = expandedTrailerPreviewAudioUrl,
        portraitCatalogCardWidth = portraitCatalogCardWidth,
        portraitCatalogCardHeight = portraitCatalogCardHeight,
        landscapeCatalogCardWidth = landscapeCatalogCardWidth,
        landscapeCatalogCardHeight = landscapeCatalogCardHeight,
        continueWatchingCardWidth = continueWatchingCardWidth,
        continueWatchingCardHeight = continueWatchingCardHeight,
        blurUnwatchedEpisodes = blurUnwatchedEpisodes,
        useEpisodeThumbnails = useEpisodeThumbnails,
        onContinueWatchingClick = onContinueWatchingClick,
        onContinueWatchingOptions = stableOnContinueWatchingOptions,
        isCatalogItemWatched = isCatalogItemWatched,
        onCatalogItemLongPress = onCatalogItemLongPress,
        onItemFocus = onItemFocus,
        onPreloadAdjacentItem = onPreloadAdjacentItem,
        enrichedPreviews = enrichedPreviews,
        onCatalogSelectionFocused = stableOnCatalogSelectionFocused,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToFolderDetail = onNavigateToFolderDetail,
        onLoadMoreCatalog = onLoadMoreCatalog,
        onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAll,
        onBackdropInteraction = onBackdropInteraction,
        onExpandedCatalogFocusKeyChange = onExpandedCatalogFocusKeyChange,
        isVerticalRowsScrollingState = isVerticalRowsScrollingState,
        itemFocusRequesters = itemFocusRequesters,
        upFocusRequester = upFocusRequester,
        rowConfig = rowConfig,
    )
}

/** Reserve a bit of vertical space for the two row-name hints + their gaps. */
private val MODERN_PAGER_HINT_HEIGHT = 18.dp
private val MODERN_PAGER_HINTS_RESERVE = 52.dp
private const val MODERN_PAGER_REPEAT_MS = 180L

/**
 * Card height for the pager's container sizing — mirrors [ModernRowSection]'s
 * own per-row scaling so the strip is exactly tall enough for the visible row.
 */
private fun modernPagerRowCardHeight(
    row: HeroCarouselRow,
    rowConfigLookup: Map<String, LayoutRowConfig>,
    useLandscapePosters: Boolean,
    portraitCardHeight: Dp,
    landscapeCardHeight: Dp,
    cwCardHeight: Dp,
): Dp {
    if (row.key == MODERN_CONTINUE_WATCHING_ROW_KEY) {
        // Match ModernRowSection's CW footprint so Poster/Wide aren't clipped.
        val cwConfig = row.layoutConfigKey?.let { rowConfigLookup[it] }
        val cwStyle = cwConfig?.continueWatchingStyle ?: ContinueWatchingCardStyle.CARD
        val sizeScale = (cwConfig?.cardWidthDp ?: CW_DEFAULT_CARD_WIDTH_DP)
            .toFloat() / CW_DEFAULT_CARD_WIDTH_DP
        return continueWatchingCardFootprint(cwStyle, cwCardHeight * sizeScale).imageHeight
    }
    val resolved = row.layoutConfigKey?.let { rowConfigLookup[it] }?.let {
        resolveRowDisplayConfig(it, it.viewContext, globalExpandForScope = false)
    }
    if (resolved?.effectiveCardStyle == LayoutCardStyle.CINEMA) return CINEMA_CARD_HEIGHT_DP.dp
    val landscape = resolved?.let { it.effectiveCardStyle == LayoutCardStyle.LANDSCAPE } ?: useLandscapePosters
    val baseWidth = resolved?.effectiveCardWidthDp?.dp
    val modernPortraitScale = 0.84f * 1.08f
    val modernLandscapeScale = 1.24f * 1.34f
    return if (landscape) {
        baseWidth?.times(modernLandscapeScale)?.div(1.77f) ?: landscapeCardHeight
    } else {
        baseWidth?.times(modernPortraitScale)?.times(1.5f) ?: portraitCardHeight
    }
}
