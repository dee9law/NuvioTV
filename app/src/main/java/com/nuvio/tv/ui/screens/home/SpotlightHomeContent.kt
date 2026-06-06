package com.nuvio.tv.ui.screens.home

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.LocalNavBarFocusRequester
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.domain.model.ContinueWatchingFilter
import com.nuvio.tv.domain.model.continueWatchingStyle
import com.nuvio.tv.domain.model.CW_DEFAULT_CARD_WIDTH_DP
import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.domain.model.LayoutRowKey
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.CatalogRowSection
import com.nuvio.tv.ui.components.ContinueWatchingSection
import com.nuvio.tv.ui.components.continueWatchingCardFootprint
import com.nuvio.tv.ui.components.HeroCarousel
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.util.asStable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SpotlightHeroState { CAROUSEL, CONSTRAINED, HIDDEN }

private const val SPOTLIGHT_HERO_MIN_DP = 250
private const val SPOTLIGHT_TRANSITION_MS = 300
private const val SPOTLIGHT_FOCUS_DEBOUNCE_MS = 140L

private fun resolveRowCardHeight(
    row: com.nuvio.tv.domain.model.CatalogRow?,
    rowConfigLookup: Map<String, com.nuvio.tv.domain.model.LayoutRowConfig>,
    posterCardStyle: PosterCardStyle,
): Dp {
    if (row == null) return posterCardStyle.height
    val config = rowConfigLookup[LayoutRowKey.forAddon(row.addonId, row.apiType, row.catalogId)]
        ?: return posterCardStyle.height
    val resolved = resolveRowDisplayConfig(config, config.viewContext, globalExpandForScope = false)
    if (resolved.effectiveCardStyle == LayoutCardStyle.CINEMA) return CINEMA_CARD_HEIGHT_DP.dp
    val w = resolved.effectiveCardWidthDp.dp
    return if (resolved.effectiveCardStyle == LayoutCardStyle.LANDSCAPE) w / 1.77f else w * 1.5f
}

private fun resolveRowPosterCardStyle(
    row: com.nuvio.tv.domain.model.CatalogRow,
    rowConfigLookup: Map<String, com.nuvio.tv.domain.model.LayoutRowConfig>,
    basePosterCardStyle: PosterCardStyle,
): PosterCardStyle {
    val config = rowConfigLookup[LayoutRowKey.forAddon(row.addonId, row.apiType, row.catalogId)]
        ?: return basePosterCardStyle
    val resolved = resolveRowDisplayConfig(config, config.viewContext, globalExpandForScope = false)
    if (resolved.effectiveCardStyle == LayoutCardStyle.CINEMA) {
        return basePosterCardStyle.copy(
            width = CINEMA_CARD_WIDTH_DP.dp,
            height = CINEMA_CARD_HEIGHT_DP.dp,
        )
    }
    val w = resolved.effectiveCardWidthDp.dp
    val h = if (resolved.effectiveCardStyle == LayoutCardStyle.LANDSCAPE) w / 1.77f else w * 1.5f
    return basePosterCardStyle.copy(width = w, height = h)
}

private fun MetaPreview.toSpotlightHeroPreview(): HeroPreview = HeroPreview(
    title = name,
    logo = logo,
    description = description,
    contentTypeText = null,
    isSeries = isSeriesType(apiType),
    yearText = null,
    runtimeText = null,
    imdbText = imdbRating?.let { String.format(java.util.Locale.US, "%.1f", it) },
    ageRatingText = null,
    statusText = null,
    countryText = null,
    languageText = null,
    genres = genres.take(3).asStable(),
    poster = poster,
    backdrop = backdropUrl ?: poster,
    imageUrl = poster ?: backdropUrl,
    frozenBackdropUrl = null,
    frozenLogoUrl = null
)

/**
 * Spotlight Home — Column layout with three-state hero + LazyColumn rows.
 *
 * Layout is a simple vertical stack: Hero section on top (variable height),
 * rows section below (fills remaining space via `weight(1f)`).
 *
 * **State A (CAROUSEL):** HeroCarousel at 400dp — same as Classic.
 * **State B (CONSTRAINED):** Modern non-fullscreen hero style. Hero takes
 *   its constrained height. Rows start directly below — no overlap, no gap.
 * **State C (HIDDEN):** Hero 0dp. Rows fill screen.
 *
 * D-pad between rows handled by Compose's spatial focus system (LazyColumn).
 * ALL catalogRows passed — no row limit.
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
    onRequestLazyCatalogLoad: (String) -> Unit = {},
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit = {},
    onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit = {},
    onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit = {},
    showContinueWatchingManualPlayOption: Boolean = false,
    onRemoveContinueWatching: (String, Int?, Int?, Boolean) -> Unit = { _, _, _, _ -> },
) {
    // ── Data sources ────────────────────────────────────────────────
    val catalogRows = remember(uiState.catalogRows, uiState.homeRows) {
        // Prefer homeRows (configured layout from settings) over raw catalogRows,
        // matching Classic's priority. homeRows includes user-added rows and
        // respects the Rows settings — catalogRows is only the auto-populated set.
        val rows = when {
            uiState.homeRows.isNotEmpty() -> uiState.homeRows
                .mapNotNull { (it as? HomeRow.Catalog)?.row }
            uiState.catalogRows.isNotEmpty() -> uiState.catalogRows
            else -> emptyList()
        }
        Log.d("SpotlightNav", "rows=${rows.size} (homeRows=${uiState.homeRows.size} catalogRows=${uiState.catalogRows.size})")
        rows
    }
    val heroCarouselItems = uiState.heroItems
    val heroDisplayItems = when {
        heroCarouselItems.isNotEmpty() -> heroCarouselItems
        else -> listOfNotNull(catalogRows.firstOrNull()?.items?.firstOrNull())
    }

    // ── Screen sizing ───────────────────────────────────────────────
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val density = LocalDensity.current

    // ── Focus tracking ──────────────────────────────────────────────
    var rowsAreaHasFocus by remember { mutableStateOf(false) }
    // Tracks whether focus is actually inside the hero section. Distinct from
    // `heroState == CAROUSEL`, which is merely `!rowsAreaHasFocus` and is also
    // true when focus is up on the TopBar channel pills. The Back handler must
    // gate on real hero focus so it doesn't steal Back from the TopBar's own
    // carousel back-chain (snap-to-first-pill → category pills).
    var heroHasFocus by remember { mutableStateOf(false) }
    var focusedRowIndex by remember { mutableIntStateOf(0) }
    var focusedItemInRow by remember { mutableIntStateOf(0) }
    var pendingFocusedItem by remember { mutableStateOf<MetaPreview?>(null) }
    var debouncedFocusedItem by remember { mutableStateOf<MetaPreview?>(null) }
    val firstItemRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    // ── Continue Watching (rendered as a leading row above the catalog
    // rows when configured+enabled, or by default when no CW row exists).
    // Spotlight's row list is index-coupled to catalogRows for its hero
    // state, so CW is pinned at the top here rather than threaded into an
    // arbitrary position. ──────────────────────────────────────────────
    val anyCwConfigured = ContinueWatchingFilter.entries.any {
        uiState.rowConfigLookup.containsKey(LayoutRowKey.forContinueWatchingFilter(it))
    }
    // The CW-family rows (Series / Movies / Up Next) to show, in a stable order,
    // each filtered to its slice. Configured rows honour enabled + non-empty;
    // when nothing is configured at all, Series shows by default.
    val spotlightCwFilters = remember(uiState.rowConfigLookup, uiState.continueWatchingItems) {
        val configured = ContinueWatchingFilter.entries.filter { f ->
            val cfg = uiState.rowConfigLookup[LayoutRowKey.forContinueWatchingFilter(f)]
            cfg != null && cfg.enabled &&
                uiState.continueWatchingItems.forContinueWatchingFilter(f).isNotEmpty()
        }
        if (configured.isEmpty() && !anyCwConfigured &&
            uiState.continueWatchingItems
                .forContinueWatchingFilter(ContinueWatchingFilter.SERIES).isNotEmpty()
        ) {
            listOf(ContinueWatchingFilter.SERIES)
        } else {
            configured
        }
    }
    val showSpotlightContinueWatching = spotlightCwFilters.isNotEmpty()
    // One focus requester per CW filter for hero <-> CW… <-> row0 chaining.
    val cwRowItemFrs = remember { ContinueWatchingFilter.entries.associateWith { FocusRequester() } }
    // Per-row inner LazyRow states, keyed by row index. Registered by each
    // CatalogRowSection while it's composed (see itemsIndexed below) so the
    // Back handler can scroll a row's first card back into composition before
    // requesting focus on it.
    val rowListStatesMap = remember { mutableMapOf<Int, LazyListState>() }
    val backScope = rememberCoroutineScope()
    // Fix 1a: throttle D-pad Up autorepeat. Holding Up fires a flood of repeat
    // events that skip multiple rows in one frame and shoot straight to the
    // TopBar. We let the initial press through, then allow only ONE Up step per
    // 200ms while the key is held, consuming the rest.
    var lastUpNavMs by remember { mutableLongStateOf(0L) }

    // Clean up stale focus requesters when rows data changes (e.g. after settings)
    LaunchedEffect(catalogRows) {
        firstItemRequesters.keys.retainAll((0 until catalogRows.size).toSet())
        rowListStatesMap.keys.retainAll((0 until catalogRows.size).toSet())
    }

    LaunchedEffect(pendingFocusedItem) {
        delay(SPOTLIGHT_FOCUS_DEBOUNCE_MS)
        if (pendingFocusedItem != debouncedFocusedItem) {
            debouncedFocusedItem = pendingFocusedItem
        }
    }

    // ── Per-row hero sizing (Fix 4: recalculated per focused row) ──
    // Keyed on the row data + config so the derivedStateOf is rebuilt when the
    // catalog rows or their layout configs change. Without these keys the lambda
    // captures the FIRST composition's catalogRows/rowConfigLookup forever, so a
    // later data update (lazy-load, settings change) is read against a stale list.
    val focusedRowCardHeight by remember(catalogRows, uiState.rowConfigLookup, posterCardStyle) {
        derivedStateOf {
            resolveRowCardHeight(
                catalogRows.getOrNull(focusedRowIndex),
                uiState.rowConfigLookup,
                posterCardStyle,
            )
        }
    }
    val focusedRowContainerHeight = remember(focusedRowCardHeight) {
        singleRowContainerHeight(focusedRowCardHeight)
    }
    val constrainedHeroHeight = remember(screenHeight, focusedRowContainerHeight) {
        heroHeightForRowsContainer(screenHeight, focusedRowContainerHeight)
    }
    val showHeroForFocusedRow = constrainedHeroHeight >= SPOTLIGHT_HERO_MIN_DP.dp

    val carouselHeroHeight = 400.dp

    // Purely focus-driven: is the row that currently has focus a Cinema row?
    // When it is, the hero collapses to State C (HIDDEN). Moving focus to any
    // non-Cinema row restores the hero. Tracks the focused row only — not
    // whether Cinema rows merely exist in the layout.
    // Keyed on catalogRows + rowConfigLookup (same stale-capture reason as
    // focusedRowCardHeight above). focusedRowIndex is read inside and is snapshot
    // state, so moving focus between rows re-evaluates this and the hero restores.
    val focusedRowIsCinema by remember(catalogRows, uiState.rowConfigLookup) {
        derivedStateOf {
            val row = catalogRows.getOrNull(focusedRowIndex) ?: return@derivedStateOf false
            val cfg = uiState.rowConfigLookup[
                LayoutRowKey.forAddon(row.addonId, row.apiType, row.catalogId)
            ] ?: return@derivedStateOf false
            resolveRowDisplayConfig(cfg, cfg.viewContext, globalExpandForScope = false)
                .effectiveCardStyle == LayoutCardStyle.CINEMA
        }
    }

    // ── Hero state machine ──────────────────────────────────────────
    // Plain per-recomposition val (NOT a remembered derivedStateOf): it reads
    // showHeroForFocusedRow, a plain non-state val recomputed every composition.
    // A remembered derivedStateOf would close over the FIRST composition's
    // showHeroForFocusedRow and never see it change — which left the hero stuck
    // HIDDEN after a Cinema row. All three inputs (rowsAreaHasFocus,
    // focusedRowIsCinema, showHeroForFocusedRow→focusedRowCardHeight) are snapshot
    // state read in the body, so this recomputes the moment focus leaves a Cinema
    // row and the hero restores.
    val heroState = when {
        !rowsAreaHasFocus -> SpotlightHeroState.CAROUSEL
        focusedRowIsCinema -> SpotlightHeroState.HIDDEN
        showHeroForFocusedRow -> SpotlightHeroState.CONSTRAINED
        else -> SpotlightHeroState.HIDDEN
    }

    // ── Animated hero height ────────────────────────────────────────
    val targetHeroHeight = when (heroState) {
        SpotlightHeroState.CAROUSEL -> carouselHeroHeight
        SpotlightHeroState.CONSTRAINED -> constrainedHeroHeight
        SpotlightHeroState.HIDDEN -> 0.dp
    }
    val animatedHeroHeight by animateDpAsState(
        targetValue = targetHeroHeight,
        animationSpec = tween(SPOTLIGHT_TRANSITION_MS),
        label = "spotlightHeroHeight"
    )

    val carouselAlpha by animateFloatAsState(
        targetValue = if (heroState == SpotlightHeroState.CAROUSEL) 1f else 0f,
        animationSpec = tween(SPOTLIGHT_TRANSITION_MS),
        label = "carouselAlpha"
    )
    val constrainedAlpha by animateFloatAsState(
        targetValue = if (heroState == SpotlightHeroState.CONSTRAINED) 1f else 0f,
        animationSpec = tween(SPOTLIGHT_TRANSITION_MS),
        label = "constrainedAlpha"
    )

    // Fix 1b: when focus genuinely returns to the hero (State A / CAROUSEL),
    // force the rows-focus flag false. `heroState == CAROUSEL` is by definition
    // `!rowsAreaHasFocus`, so keying on the real hero-focus signal is what
    // clears any stale `true` left behind by a fast D-pad-Up flood — without it,
    // the next D-pad Down can't re-enter the rows to transition back to State B.
    LaunchedEffect(heroHasFocus) {
        if (heroHasFocus) rowsAreaHasFocus = false
    }

    // ── Constrained hero data (State B) ─────────────────────────────
    val spotlightHeroPreview = remember(debouncedFocusedItem) {
        debouncedFocusedItem?.toSpotlightHeroPreview()
    }
    val latestHeroPreview by rememberUpdatedState(spotlightHeroPreview)
    val spotlightHeroBackdrop = remember(debouncedFocusedItem) {
        debouncedFocusedItem?.let { firstNonBlank(it.backdropUrl, it.poster) }
    }
    val spotlightHeroSceneState = remember(spotlightHeroBackdrop, spotlightHeroPreview) {
        ModernHeroSceneState(
            heroBackdrop = spotlightHeroBackdrop,
            preview = spotlightHeroPreview,
            enrichmentActive = false,
            shouldPlayTrailer = false,
            trailerFirstFrameRendered = false,
            trailerUrl = null,
            trailerAudioUrl = null,
            trailerPlaybackKey = null,
            trailerMuted = true,
            fullScreenBackdrop = false
        )
    }
    val latestHeroSceneState by rememberUpdatedState(spotlightHeroSceneState)
    val heroSceneStateLambda = remember { { latestHeroSceneState } }
    val heroMediaWidthPx = remember(screenWidth, density) {
        with(density) { (screenWidth * MODERN_HERO_MEDIA_WIDTH_FRACTION).roundToPx() }
    }
    val heroMediaHeightPx = remember(constrainedHeroHeight, density) {
        with(density) { constrainedHeroHeight.roundToPx().coerceAtLeast(1) }
    }

    // ── TopBar immersion ────────────────────────────────────────────
    LaunchedEffect(heroState) {
        com.nuvio.tv.ui.components.TopBarImmersionState.setVisible(
            heroState == SpotlightHeroState.CAROUSEL
        )
    }
    DisposableEffect(Unit) {
        onDispose { com.nuvio.tv.ui.components.TopBarImmersionState.setVisible(true) }
    }

    // ── Focus requesters ────────────────────────────────────────────
    val navBarFr = LocalNavBarFocusRequester.current
    val contentFocusRequester = LocalContentFocusRequester.current
    val heroFocusRequester = remember { FocusRequester() }
    val rowsContainerFr = remember { FocusRequester() }
    val rowsListState = rememberLazyListState()

    // ── Lazy catalog loading ────────────────────────────────────────
    // Parity with Classic/Modern: rows past the eager-load count arrive as
    // shimmer placeholders (isLoading + "__placeholder_" item ids). Without
    // a trigger they stay placeholders forever. After scroll settles, request
    // a real load for every visible-or-next placeholder row.
    val latestOnRequestLazyCatalogLoad = rememberUpdatedState(onRequestLazyCatalogLoad)
    val latestCatalogRows = rememberUpdatedState(catalogRows)
    LaunchedEffect(rowsListState) {
        val prefetchAhead = 1
        snapshotFlow {
            val scrolling = rowsListState.isScrollInProgress
            val info = rowsListState.layoutInfo
            val firstVisible = info.visibleItemsInfo.firstOrNull()?.index ?: -1
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            Triple(scrolling, firstVisible, lastVisible)
        }.collect { (scrolling, firstVisible, lastVisible) ->
            if (scrolling || lastVisible < 0) return@collect
            delay(150)
            if (rowsListState.isScrollInProgress) return@collect
            val rows = latestCatalogRows.value
            for (idx in firstVisible.coerceAtLeast(0)..(lastVisible + prefetchAhead)) {
                val row = rows.getOrNull(idx) ?: continue
                if (row.isLoading &&
                    row.items.firstOrNull()?.id?.startsWith("__placeholder_") == true
                ) {
                    latestOnRequestLazyCatalogLoad.value(
                        "${row.addonId}_${row.apiType}_${row.catalogId}"
                    )
                }
            }
        }
    }

    // ── Back hierarchy: L5 → L4 → L2 ───────────────────────────────
    // Enable ONLY when Spotlight's own content (rows or hero) has focus —
    // never when focus is up on the TopBar, otherwise this handler preempts
    // the TopBar's channel-carousel back-chain (LIFO: content composes after
    // the TopBar, so it would win). Mirrors how Classic/Grid/Modern gate on
    // their own content focus.
    BackHandler(enabled = rowsAreaHasFocus || heroHasFocus) {
        when {
            rowsAreaHasFocus && focusedItemInRow > 0 -> {
                // Snap to the first card of the CURRENTLY focused row. From the
                // 3rd+ card the inner LazyRow has recycled card 0 (its
                // FocusRequester is detached), so scroll the row back to index 0
                // first — that re-composes card 0 and re-attaches its requester —
                // then request focus on the next frame.
                val fr = firstItemRequesters[focusedRowIndex]
                val rowState = rowListStatesMap[focusedRowIndex]
                backScope.launch {
                    runCatching { rowState?.scrollToItem(0) }
                    withFrameNanos { }
                    if (fr != null) runCatching { fr.requestFocus() }
                }
            }
            rowsAreaHasFocus -> runCatching { heroFocusRequester.requestFocus() }
            else -> runCatching { navBarFr.requestFocus() }
        }
    }

    // ── Initial focus ───────────────────────────────────────────────
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
                    rowsContainerFr.requestFocus()
                }
                true
            }.getOrDefault(false)
            if (focused) return@LaunchedEffect
        }
    }

    // ── Layout: Column (hero on top, rows below) ────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(contentFocusRequester)
            .focusRestorer(heroFocusRequester)
            .focusGroup()
    ) {
        // ── Hero section: height varies by state ────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeroHeight)
                .clipToBounds()
                .onFocusChanged { heroHasFocus = it.hasFocus }
        ) {
            // State A: HeroCarousel — always in tree for focusability
            HeroCarousel(
                items = heroDisplayItems.asStable(),
                onItemClick = { item -> onNavigateToDetail(item.id, item.apiType, "") },
                focusRequester = heroFocusRequester,
                heroHeight = carouselHeroHeight,
                modifier = Modifier
                    .graphicsLayer { alpha = carouselAlpha }
                    .focusProperties {
                        up = navBarFr
                        down = rowsContainerFr
                    }
            )

            // State B: Constrained hero (Modern non-fullscreen style).
            // Metadata is capped to fit within the hero height so it
            // never bleeds past the bottom edge.
            if (constrainedAlpha > 0f) {
                val rowHorizontalPadding = if (com.nuvio.tv.LocalIsModernFeel.current) 16.dp else 48.dp
                ModernHeroScene(
                    state = heroSceneStateLambda,
                    isFullScreen = { false },
                    bgColor = NuvioColors.Background,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 56.dp)
                        .fillMaxWidth(MODERN_HERO_MEDIA_WIDTH_FRACTION)
                        .height(animatedHeroHeight)
                        .graphicsLayer { alpha = constrainedAlpha },
                    requestWidthPx = heroMediaWidthPx,
                    requestHeightPx = heroMediaHeightPx,
                    onTrailerEnded = {},
                    onFirstFrameRendered = {}
                )
                val metaBottomPad = (animatedHeroHeight.value * 0.12f).coerceIn(16f, 48f).dp
                val metaMaxHeight = (animatedHeroHeight - metaBottomPad - 16.dp).coerceAtLeast(0.dp)
                HeroTitleBlock(
                    previewProvider = { latestHeroPreview },
                    portraitMode = true,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = rowHorizontalPadding, end = 48.dp, bottom = metaBottomPad)
                        .fillMaxWidth(MODERN_HERO_TEXT_WIDTH_FRACTION)
                        .heightIn(max = metaMaxHeight)
                        .graphicsLayer { alpha = constrainedAlpha }
                )
            }
        }

        // ── Rows section: fills remaining space ─────────────────────
        if (catalogRows.isNotEmpty() || showSpotlightContinueWatching) {
            LazyColumn(
                state = rowsListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .focusRequester(rowsContainerFr)
                    // Fix 1a: throttle held D-pad Up so it steps one row at a
                    // time instead of flooding focus up to the TopBar.
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            if (event.nativeKeyEvent.repeatCount > 0) {
                                val now = System.currentTimeMillis()
                                if (now - lastUpNavMs < 200L) {
                                    true // swallow autorepeat inside the window
                                } else {
                                    lastUpNavMs = now
                                    false // allow one step
                                }
                            } else {
                                lastUpNavMs = System.currentTimeMillis()
                                false // initial press always passes through
                            }
                        } else false
                    }
                    .onFocusChanged { state ->
                        val hadFocus = rowsAreaHasFocus
                        rowsAreaHasFocus = state.hasFocus
                        if (hadFocus != state.hasFocus) {
                            Log.d("SpotlightNav", "rowsAreaHasFocus=${state.hasFocus}")
                        }
                    }
                    .focusRestorer()
            ) {
                itemsIndexed(
                    items = spotlightCwFilters,
                    key = { _, filter -> "spotlight_cw_${filter.name}" },
                ) { cwIndex, cwFilter ->
                    val cwStyle = uiState.rowConfigLookup[
                        LayoutRowKey.forContinueWatchingFilter(cwFilter)
                    ]?.continueWatchingStyle ?: ContinueWatchingCardStyle.CARD
                    val cwFootprint = remember(cwStyle, cwFilter, uiState.rowConfigLookup) {
                        continueWatchingCardFootprint(
                            style = cwStyle,
                            baseWidth = (uiState.rowConfigLookup[
                                LayoutRowKey.forContinueWatchingFilter(cwFilter)
                            ]?.cardWidthDp ?: CW_DEFAULT_CARD_WIDTH_DP).dp,
                        )
                    }
                    // Chain focus: hero <-> cw[0] <-> cw[1] <-> … <-> row0.
                    val upTarget = if (cwIndex == 0) heroFocusRequester
                        else cwRowItemFrs.getValue(spotlightCwFilters[cwIndex - 1])
                    val downTarget = if (cwIndex == spotlightCwFilters.lastIndex)
                        firstItemRequesters.getOrPut(0) { FocusRequester() }
                        else cwRowItemFrs.getValue(spotlightCwFilters[cwIndex + 1])
                    ContinueWatchingSection(
                        items = uiState.continueWatchingItems.forContinueWatchingFilter(cwFilter),
                        onItemClick = onContinueWatchingClick,
                        onRemoveItem = { item ->
                            val contentId = when (item) {
                                is ContinueWatchingItem.InProgress -> item.progress.contentId
                                is ContinueWatchingItem.NextUp -> item.info.contentId
                            }
                            val season = when (item) {
                                is ContinueWatchingItem.InProgress -> item.progress.season
                                is ContinueWatchingItem.NextUp -> item.info.seedSeason
                            }
                            val episode = when (item) {
                                is ContinueWatchingItem.InProgress -> item.progress.episode
                                is ContinueWatchingItem.NextUp -> item.info.seedEpisode
                            }
                            onRemoveContinueWatching(
                                contentId, season, episode, item is ContinueWatchingItem.NextUp,
                            )
                        },
                        onStartFromBeginning = onContinueWatchingStartFromBeginning,
                        showManualPlayOption = showContinueWatchingManualPlayOption,
                        onPlayManually = onContinueWatchingPlayManually,
                        useEpisodeThumbnails = uiState.useEpisodeThumbnailsInCw,
                        cwStyle = cwStyle,
                        cardWidth = cwFootprint.cardWidth,
                        imageHeight = cwFootprint.imageHeight,
                        upFocusRequester = upTarget,
                        downFocusRequester = downTarget,
                        firstItemFocusRequester = cwRowItemFrs.getValue(cwFilter),
                    )
                }
                itemsIndexed(
                    items = catalogRows,
                    key = { _, row -> "${row.addonId}_${row.apiType}_${row.catalogId}" }
                ) { index, row ->
                    val rowPosterStyle = remember(row, uiState.rowConfigLookup) {
                        resolveRowPosterCardStyle(row, uiState.rowConfigLookup, posterCardStyle)
                    }
                    val rowConfig = uiState.rowConfigLookup[
                        LayoutRowKey.forAddon(row.addonId, row.apiType, row.catalogId)
                    ]
                    val rowCardStyle = rowConfig?.let {
                        resolveRowDisplayConfig(it, it.viewContext, globalExpandForScope = false).effectiveCardStyle
                    } ?: LayoutCardStyle.POSTER
                    val rowFirstItemFr = firstItemRequesters.getOrPut(index) { FocusRequester() }
                    // Register this row's inner LazyRow state so the Back
                    // handler can scroll it to card 0 before requesting focus.
                    val rowInnerListState = rememberLazyListState()
                    DisposableEffect(index, rowInnerListState) {
                        rowListStatesMap[index] = rowInnerListState
                        onDispose {
                            if (rowListStatesMap[index] === rowInnerListState) {
                                rowListStatesMap.remove(index)
                            }
                        }
                    }
                    CatalogRowSection(
                        listState = rowInnerListState,
                        catalogRow = row,
                        posterCardStyle = rowPosterStyle,
                        cardStyle = rowCardStyle,
                        showPosterLabels = uiState.posterLabelsEnabled,
                        showAddonName = uiState.catalogAddonNameEnabled,
                        showCatalogTypeSuffix = uiState.catalogTypeSuffixEnabled,
                        // Per-row expand override via the shared resolver
                        // (true/false win; null follows uiState's per-scope value).
                        focusedPosterBackdropExpandEnabled = rowConfig?.let {
                            resolveRowDisplayConfig(
                                it, it.viewContext, uiState.focusedPosterBackdropExpandEnabled,
                            ).effectiveExpands
                        } ?: uiState.focusedPosterBackdropExpandEnabled,
                        focusedPosterBackdropExpandDelaySeconds = uiState.focusedPosterBackdropExpandDelaySeconds,
                        focusedPosterBackdropTrailerEnabled = uiState.focusedPosterBackdropTrailerEnabled,
                        focusedPosterBackdropTrailerMuted = uiState.focusedPosterBackdropTrailerMuted,
                        compactTitle = true,
                        leadingSeeAllEnabled = true,
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
                            if (focusedRowIndex != index) {
                                focusedRowIndex = index
                            }
                            pendingFocusedItem = item
                            onItemFocus(item)
                        },
                        onItemFocused = { itemIndex -> focusedItemInRow = itemIndex },
                        isItemWatched = isCatalogItemWatched,
                        onItemLongPress = onCatalogItemLongPress,
                        upFocusRequester = if (index == 0) {
                            if (showSpotlightContinueWatching) {
                                cwRowItemFrs.getValue(spotlightCwFilters.last())
                            } else heroFocusRequester
                        } else null,
                        firstItemFocusRequester = rowFirstItemFr,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                androidx.tv.material3.Text(
                    text = "No rows configured",
                    style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
                    color = NuvioColors.TextSecondary,
                )
            }
        }
    }
}
