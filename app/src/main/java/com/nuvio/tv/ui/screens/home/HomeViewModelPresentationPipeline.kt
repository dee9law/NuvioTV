package com.nuvio.tv.ui.screens.home

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbEnrichment
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.TmdbSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stripped Layout pipeline state. Per the simplification spec, the layout
 * pipeline observes ONLY the five settings that actually influence layout
 * choice and the catalog-loading allowlist:
 *  - [layout]           — Modern / Grid / Classic
 *  - [fullscreenHero]   — Modern-only backdrop expansion
 *  - [showHeroSection]  — Grid / Classic hero toggle
 *  - [heroCatalogKeys]  — which catalogs feed the hero
 *  - [focusItemGradient]— Classic-only artwork blend on the right
 *
 * Everything else (poster labels / addon name / hide unreleased / etc.) is
 * observed by independent per-flow pipelines below and merged into
 * [HomeUiState] without re-triggering the catalog refresh path.
 */
private data class LayoutCorePrefs(
    val layout: HomeLayout,
    val fullscreenHero: Boolean,
    val showHeroSection: Boolean,
    val heroCatalogKeys: List<String>,
    val focusItemGradient: Boolean,
)

@OptIn(FlowPreview::class)
internal fun BaseHomeViewModel.observeLayoutPreferencesPipeline() {
    viewModelScope.launch {
        combine(
            layoutPreferenceDataStore.selectedLayoutForScope(homeScope),
            layoutPreferenceDataStore.fullscreenHeroBackdropForScope(homeScope),
            layoutPreferenceDataStore.heroSectionEnabledForScope(homeScope),
            layoutPreferenceDataStore.heroCatalogSelectionsForScope(homeScope),
            layoutPreferenceDataStore.classicFocusGradientEnabledForScope(homeScope),
        ) { layout, fullscreenHero, showHero, heroKeys, focusGradient ->
            LayoutCorePrefs(layout, fullscreenHero, showHero, heroKeys, focusGradient)
        }
            .distinctUntilChanged()
            .debounce(300)
            .collectLatest { prefs ->
                val previousState = _uiState.value
                val heroKeysChanged = currentHeroCatalogKeys != prefs.heroCatalogKeys
                val shouldRefreshCatalogPresentation =
                    heroKeysChanged ||
                        previousState.heroSectionEnabled != prefs.showHeroSection ||
                        previousState.homeLayout != prefs.layout
                currentHeroCatalogKeys = prefs.heroCatalogKeys
                // Reset focus state when layout changes so the outgoing
                // layout's onDispose doesn't poison the incoming layout.
                if (previousState.homeLayout != prefs.layout) {
                    suppressFocusSave = true
                    clearFocusState()
                }
                _uiState.update { state ->
                    state.copy(
                        layoutPreferencesReady = true,
                        homeLayout = prefs.layout,
                        heroCatalogKeys = prefs.heroCatalogKeys,
                        heroSectionEnabled = prefs.showHeroSection,
                        classicFocusGradientEnabled = prefs.focusItemGradient && prefs.layout == HomeLayout.CLASSIC,
                        modernHeroFullScreenBackdropEnabled = prefs.fullscreenHero,
                    )
                }
                if (shouldRefreshCatalogPresentation) {
                    if (prefs.layout == HomeLayout.GRID) {
                        loadAllPendingLazyCatalogs()
                    }
                    if (heroKeysChanged && prefs.heroCatalogKeys.isNotEmpty()) {
                        loadHeroCatalogsPipeline()
                    } else {
                        scheduleUpdateCatalogRows()
                    }
                }
            }
    }
}

/**
 * Display-preference observers — independent flows that each write their own
 * slice of [HomeUiState]. Splitting these out of the layout pipeline avoids
 * spurious catalog refreshes when an orthogonal toggle changes (e.g. flipping
 * Hide Unreleased should not clear focus state). Each is gated by its own
 * [distinctUntilChanged] so other scopes' DataStore writes don't fan out
 * into this scope's UI state.
 */
internal fun BaseHomeViewModel.observeDisplayPreferencesPipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.posterLabelsEnabled
            .distinctUntilChanged()
            .collect { enabled ->
                _uiState.update { state ->
                    val effective = if (state.homeLayout == HomeLayout.MODERN) false else enabled
                    if (state.posterLabelsEnabled == effective) state
                    else state.copy(posterLabelsEnabled = effective)
                }
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.catalogAddonNameEnabled
            .distinctUntilChanged()
            .collect { enabled ->
                _uiState.update { state ->
                    if (state.catalogAddonNameEnabled == enabled) state
                    else state.copy(catalogAddonNameEnabled = enabled)
                }
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.catalogTypeSuffixEnabled
            .distinctUntilChanged()
            .collect { enabled ->
                _uiState.update { state ->
                    if (state.catalogTypeSuffixEnabled == enabled) state
                    else state.copy(catalogTypeSuffixEnabled = enabled)
                }
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.hideUnreleasedContent
            .distinctUntilChanged()
            .collect { enabled ->
                val previousValue = _uiState.value.hideUnreleasedContent
                if (previousValue == enabled) return@collect
                _uiState.update { it.copy(hideUnreleasedContent = enabled) }
                // Re-render catalog rows so the unreleased filter takes effect.
                scheduleUpdateCatalogRows()
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.showFullReleaseDate
            .distinctUntilChanged()
            .collect { enabled ->
                _uiState.update { state ->
                    if (state.showFullReleaseDate == enabled) state
                    else state.copy(showFullReleaseDate = enabled)
                }
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.modernLandscapePostersEnabled
            .distinctUntilChanged()
            .collect { enabled ->
                _uiState.update { state ->
                    if (state.modernLandscapePostersEnabled == enabled) state
                    else state.copy(modernLandscapePostersEnabled = enabled)
                }
            }
    }
}

/**
 * Focused-poster observers — expand / delay / mute / trailer-enabled /
 * trailer-target. Independent from layout so flipping any of these does not
 * clear focus or refresh catalogs.
 */
internal fun BaseHomeViewModel.observeFocusedPosterPipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.focusedPosterBackdropExpandEnabled
            .distinctUntilChanged()
            .collect { enabled ->
                _uiState.update { state ->
                    if (state.focusedPosterBackdropExpandEnabled == enabled) state
                    else state.copy(focusedPosterBackdropExpandEnabled = enabled)
                }
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.focusedPosterBackdropExpandDelaySeconds
            .distinctUntilChanged()
            .collect { seconds ->
                _uiState.update { state ->
                    if (state.focusedPosterBackdropExpandDelaySeconds == seconds) state
                    else state.copy(focusedPosterBackdropExpandDelaySeconds = seconds)
                }
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.focusedPosterBackdropTrailerEnabled
            .distinctUntilChanged()
            .collect { enabled ->
                val effective = enabled && AppFeaturePolicy.inAppTrailerPlaybackEnabled
                _uiState.update { state ->
                    if (state.focusedPosterBackdropTrailerEnabled == effective) state
                    else state.copy(focusedPosterBackdropTrailerEnabled = effective)
                }
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.focusedPosterBackdropTrailerMuted
            .distinctUntilChanged()
            .collect { muted ->
                _uiState.update { state ->
                    if (state.focusedPosterBackdropTrailerMuted == muted) state
                    else state.copy(focusedPosterBackdropTrailerMuted = muted)
                }
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.focusedPosterBackdropTrailerPlaybackTarget
            .distinctUntilChanged()
            .collect { target ->
                _uiState.update { state ->
                    if (state.focusedPosterBackdropTrailerPlaybackTarget == target) state
                    else state.copy(focusedPosterBackdropTrailerPlaybackTarget = target)
                }
            }
    }
}

/**
 * Poster-card-size observers — width / height / corner radius. Split out so a
 * single dimension change doesn't ripple through the layout pipeline.
 */
internal fun BaseHomeViewModel.observePosterCardSizePipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.posterCardWidthDp
            .distinctUntilChanged()
            .collect { width ->
                val previous = _uiState.value.posterCardWidthDp
                if (previous == width) return@collect
                _uiState.update { it.copy(posterCardWidthDp = width) }
                // Card width participates in row layout — schedule a refresh
                // so changes take effect without a restart.
                scheduleUpdateCatalogRows()
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.posterCardHeightDp
            .distinctUntilChanged()
            .collect { height ->
                _uiState.update { state ->
                    if (state.posterCardHeightDp == height) state
                    else state.copy(posterCardHeightDp = height)
                }
            }
    }
    viewModelScope.launch {
        layoutPreferenceDataStore.posterCardCornerRadiusForScope(homeScope)
            .distinctUntilChanged()
            .collect { radius ->
                _uiState.update { state ->
                    if (state.posterCardCornerRadiusDp == radius) state
                    else state.copy(posterCardCornerRadiusDp = radius)
                }
            }
    }
}

/**
 * Wires the GLOBAL tier of the 3-tier `resolveLayoutSetting` hierarchy into
 * UiState — `globalCardStyle` and `globalLayout`. These are the floor used by
 * row renderers when both the per-row override and the per-screen value are
 * absent. See [com.nuvio.tv.domain.model.resolveLayoutSetting].
 */
internal fun BaseHomeViewModel.observeGlobalLayoutTierPipeline() {
    viewModelScope.launch {
        combine(
            layoutPreferenceDataStore.globalCardStyle,
            layoutPreferenceDataStore.globalLayout,
        ) { cardStyle, layout -> cardStyle to layout }
            .distinctUntilChanged()
            .collect { (cardStyle, layout) ->
                _uiState.update { state ->
                    if (state.globalCardStyle == cardStyle && state.globalLayout == layout) {
                        state
                    } else {
                        state.copy(globalCardStyle = cardStyle, globalLayout = layout)
                    }
                }
            }
    }
}

@OptIn(FlowPreview::class)
internal fun BaseHomeViewModel.observeModernHomePresentationPipeline() {
    viewModelScope.launch {
        combine(uiState, _currentLocaleTag) { state, localeTag ->
                ModernHomePresentationInput(
                    homeRows = state.homeRows,
                    catalogRows = state.catalogRows,
                    continueWatchingItems = state.continueWatchingItems,
                    useLandscapePosters = state.modernLandscapePostersEnabled,
                    showCatalogTypeSuffix = state.catalogTypeSuffixEnabled,
                    showFullReleaseDate = state.showFullReleaseDate,
                    localeTag = localeTag,
                    rowConfigLookup = state.rowConfigLookup
                )
            }
            // Compare by row structure only (keys + item counts), not by
            // item content.  TMDB/meta enrichment changes item fields but
            // not the row structure — the hero section reads enriched data
            // via lastEnrichedPreview instead.
            // rowConfigLookup is identity-compared (===) because it only
            // changes when the user edits per-row settings; in that case
            // we need to rebuild so the new override flows into image-URL
            // selection and click-target metrics.
            .distinctUntilChanged { old, new ->
                old.homeRows === new.homeRows
                    && old.continueWatchingItems === new.continueWatchingItems
                    && old.useLandscapePosters == new.useLandscapePosters
                    && old.showCatalogTypeSuffix == new.showCatalogTypeSuffix
                    && old.showFullReleaseDate == new.showFullReleaseDate
                    && old.localeTag == new.localeTag
                    && old.catalogRows.size == new.catalogRows.size
                    && old.rowConfigLookup === new.rowConfigLookup
            }
            .debounce(80)
            .collectLatest { input ->
                val shouldWarmStart = uiState.value.modernHomePresentation.rows.list.isEmpty()
                val visibleCatalogRowCount = input.catalogRows.count { it.items.isNotEmpty() }
                val warmStartCatalogRowCount = if (input.continueWatchingItems.isNotEmpty()) 2 else 3

                if (shouldWarmStart && visibleCatalogRowCount > warmStartCatalogRowCount) {
                    val warmStartPresentation = withContext(Dispatchers.Default) {
                        buildModernHomePresentation(
                            input = input,
                            cache = modernCarouselRowBuildCache,
                            context = appContext,
                            maxCatalogRows = warmStartCatalogRowCount
                        )
                    }
                    _uiState.update { state ->
                        if (state.modernHomePresentation == warmStartPresentation) {
                            state
                        } else {
                            state.copy(modernHomePresentation = warmStartPresentation)
                        }
                    }
                }

                val presentation = withContext(Dispatchers.Default) {
                    buildModernHomePresentation(
                        input = input,
                        cache = modernCarouselRowBuildCache,
                        context = appContext
                    )
                }
                _uiState.update { state ->
                    if (state.modernHomePresentation == presentation) {
                        state
                    } else {
                        state.copy(modernHomePresentation = presentation)
                    }
                }
            }
    }
}

internal fun BaseHomeViewModel.observeExternalMetaPrefetchPreferencePipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.preferExternalMetaAddonDetail
            .distinctUntilChanged()
            .collectLatest { enabled ->
                externalMetaPrefetchEnabled = enabled
                if (!enabled) {
                    externalMetaPrefetchJob?.cancel()
                    pendingExternalMetaPrefetchItemId = null
                    externalMetaPrefetchInFlightIds.clear()
                }
            }
    }
}

internal fun BaseHomeViewModel.requestTrailerPreviewPipeline(item: MetaPreview) {
    requestTrailerPreviewPipeline(
        itemId = item.id,
        title = item.name,
        releaseInfo = item.releaseInfo,
        apiType = item.apiType,
        fallbackYtId = item.trailerYtIds.firstOrNull()
    )
}

internal fun BaseHomeViewModel.requestTrailerPreviewPipeline(
    itemId: String,
    title: String,
    releaseInfo: String?,
    apiType: String,
    fallbackYtId: String? = null
) {
    if (!AppFeaturePolicy.inAppTrailerPlaybackEnabled) return
    if (startupGracePeriodActive) return

    // Resolve fallbackYtId from catalog item if not provided
    val resolvedFallbackYtId = fallbackYtId ?: findCatalogItemById(itemId)?.trailerYtIds?.firstOrNull()

    // Always bump version — only the latest request (highest version) will proceed after debounce
    activeTrailerPreviewItemId = itemId
    trailerPreviewRequestVersion++
    val requestVersion = trailerPreviewRequestVersion

    if (trailerPreviewNegativeCache.contains(itemId)) return
    if (trailerPreviewUrlsState.containsKey(itemId)) return
    if (!trailerPreviewLoadingIds.add(itemId)) return

    viewModelScope.launch(Dispatchers.IO) {
        try {
            // Debounce: wait for focus to settle before hitting network
            delay(180)

            // Only the LATEST request proceeds — all earlier ones are stale
            if (trailerPreviewRequestVersion != requestVersion) {
                return@launch
            }

            val tmdbId = try {
                tmdbService.ensureTmdbId(itemId, apiType)
            } catch (_: Exception) {
                null
            }

            val trailerSource = trailerService.getTrailerPlaybackSource(
                title = title,
                year = extractYear(releaseInfo),
                tmdbId = tmdbId,
                type = apiType
            )

            withContext(Dispatchers.Main) {
                if (trailerSource?.videoUrl.isNullOrBlank()) {
                    val fallbackSource = resolvedFallbackYtId?.let { ytId ->
                        trailerService.getTrailerPlaybackSourceFromYouTubeUrl(
                            youtubeUrl = "https://www.youtube.com/watch?v=$ytId",
                            title = title,
                            year = extractYear(releaseInfo)
                        )
                    }
                    if (fallbackSource?.videoUrl != null) {
                        if (trailerPreviewUrlsState[itemId] != fallbackSource.videoUrl) {
                            trailerPreviewUrlsState[itemId] = fallbackSource.videoUrl
                        }
                        val fallbackAudio = fallbackSource.audioUrl
                        if (fallbackAudio.isNullOrBlank()) {
                            trailerPreviewAudioUrlsState.remove(itemId)
                        } else if (trailerPreviewAudioUrlsState[itemId] != fallbackAudio) {
                            trailerPreviewAudioUrlsState[itemId] = fallbackAudio
                        }
                    } else {
                        trailerPreviewNegativeCache.add(itemId)
                        trailerPreviewUrlsState.remove(itemId)
                        trailerPreviewAudioUrlsState.remove(itemId)
                    }
                } else {
                    val videoUrl = trailerSource.videoUrl
                    if (trailerPreviewUrlsState[itemId] != videoUrl) {
                        trailerPreviewUrlsState[itemId] = videoUrl
                    }
                    val audioUrl = trailerSource.audioUrl
                    if (audioUrl.isNullOrBlank()) {
                        trailerPreviewAudioUrlsState.remove(itemId)
                    } else if (trailerPreviewAudioUrlsState[itemId] != audioUrl) {
                        trailerPreviewAudioUrlsState[itemId] = audioUrl
                    }
                }
            }
        } finally {
            trailerPreviewLoadingIds.remove(itemId)
        }
    }
}

internal fun BaseHomeViewModel.onItemFocusPipeline(item: MetaPreview) {
    if (startupGracePeriodActive) return
    if (item.id in prefetchedTmdbIds || item.id in prefetchedExternalMetaIds) return
    if (pendingTmdbEnrichItemId == item.id) return

    // Clear enriching for previous item immediately when focus moves away
    if (_enrichingItemId.value != null && _enrichingItemId.value != item.id) {
        setEnrichingItemId(null)
    }

    val tmdbEnabledForCurrentLayout = currentTmdbSettings.enabled &&
        (_uiState.value.homeLayout != HomeLayout.MODERN || currentTmdbSettings.modernHomeEnabled)
    val willEnrich = tmdbEnabledForCurrentLayout || externalMetaPrefetchEnabled

    if (willEnrich) setEnrichingItemId(item.id)

    pendingTmdbEnrichItemId = item.id
    tmdbEnrichFocusJob?.cancel()
    tmdbEnrichFocusJob = viewModelScope.launch(Dispatchers.IO) {
        delay(BaseHomeViewModel.EXTERNAL_META_PREFETCH_FOCUS_DEBOUNCE_MS)
        if (pendingTmdbEnrichItemId != item.id) {
            if (_enrichingItemId.value == item.id) setEnrichingItemId(null)
            return@launch
        }
        if (item.id in prefetchedTmdbIds || item.id in prefetchedExternalMetaIds) {
            if (_enrichingItemId.value == item.id) setEnrichingItemId(null)
            return@launch
        }

        try {
            var tmdbEnriched = false

            if (tmdbEnabledForCurrentLayout) {
                val tmdbId = runCatching { tmdbService.ensureTmdbId(item.id, item.apiType) }.getOrNull()

                val enrichmentDeferred = if (tmdbId != null) async {
                    runCatching {
                        tmdbMetadataService.fetchEnrichment(
                            tmdbId = tmdbId,
                            contentType = item.type,
                            language = currentTmdbSettings.language
                        )
                    }.getOrNull()
                } else null

                val enrichment = enrichmentDeferred?.await()

                if (enrichment != null) {
                    prefetchedTmdbIds.add(item.id)
                    prefetchedExternalMetaIds.add(item.id)
                    updateCatalogItemWithTmdb(item.id, enrichment)
                    tmdbEnriched = true
                }
            }
            if (!tmdbEnriched && externalMetaPrefetchEnabled &&
                item.id !in prefetchedExternalMetaIds &&
                externalMetaPrefetchInFlightIds.add(item.id)) {
                try {
                    val result = metaRepository.getMetaFromAllAddons(item.apiType, item.id)
                        .first { it is NetworkResult.Success || it is NetworkResult.Error }
                    if (result is NetworkResult.Success) {
                        prefetchedExternalMetaIds.add(item.id)
                        updateCatalogItemWithMeta(item.id, result.data)
                    }
                } finally {
                    externalMetaPrefetchInFlightIds.remove(item.id)
                    if (pendingTmdbEnrichItemId == item.id) pendingTmdbEnrichItemId = null
                }
            }
        } finally {
            if (_enrichingItemId.value == item.id) {
                setEnrichingItemId(null)
                // If enrichment completed but no enriched data exists for this item,
                // mark it as failed so the UI can show addon data immediately.
                if (item.id !in _enrichedPreviews.value) {
                    _failedEnrichmentIds.value = _failedEnrichmentIds.value + item.id
                }
            }
        }
    }
}

internal fun BaseHomeViewModel.preloadAdjacentItemPipeline(item: MetaPreview) {
    if (startupGracePeriodActive) return
    if (item.id in prefetchedTmdbIds || item.id in prefetchedExternalMetaIds) return
    if (pendingTmdbEnrichItemId == item.id || pendingAdjacentPrefetchItemId == item.id) return

    pendingAdjacentPrefetchItemId = item.id
    adjacentItemPrefetchJob?.cancel()
    adjacentItemPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
        val tmdbEnabledForCurrentLayout = currentTmdbSettings.enabled &&
            (_uiState.value.homeLayout != HomeLayout.MODERN || currentTmdbSettings.modernHomeEnabled)
        delay(BaseHomeViewModel.EXTERNAL_META_PREFETCH_ADJACENT_DEBOUNCE_MS)
        if (pendingAdjacentPrefetchItemId != item.id) return@launch

        if (item.id in prefetchedTmdbIds || item.id in prefetchedExternalMetaIds) return@launch

        try {
            var tmdbEnriched = false
            if (tmdbEnabledForCurrentLayout) {
                val tmdbId = runCatching { tmdbService.ensureTmdbId(item.id, item.apiType) }.getOrNull()
                val enrichment = if (tmdbId != null) runCatching {
                    tmdbMetadataService.fetchEnrichment(
                        tmdbId = tmdbId,
                        contentType = item.type,
                        language = currentTmdbSettings.language
                    )
                }.getOrNull() else null
                if (enrichment != null) {
                    prefetchedTmdbIds.add(item.id)
                    prefetchedExternalMetaIds.add(item.id)
                    updateCatalogItemWithTmdb(item.id, enrichment)
                    tmdbEnriched = true
                }
            }
            if (!tmdbEnriched &&
                externalMetaPrefetchEnabled &&
                item.id !in prefetchedExternalMetaIds &&
                externalMetaPrefetchInFlightIds.add(item.id)
            ) {
                try {
                    val result = metaRepository.getMetaFromAllAddons(item.apiType, item.id)
                        .first { it is NetworkResult.Success || it is NetworkResult.Error }
                    if (result is NetworkResult.Success) {
                        prefetchedExternalMetaIds.add(item.id)
                        updateCatalogItemWithMeta(item.id, result.data)
                    }
                } finally {
                    externalMetaPrefetchInFlightIds.remove(item.id)
                }
            }
        } finally {
            if (pendingAdjacentPrefetchItemId == item.id) {
                pendingAdjacentPrefetchItemId = null
            }
        }
    }
}

private fun BaseHomeViewModel.updateCatalogItemWithTmdb(itemId: String, enrichment: TmdbEnrichment) {
    val isModernLayout = _uiState.value.homeLayout == HomeLayout.MODERN
    fun mergeItem(currentItem: MetaPreview): MetaPreview {
        var merged = currentItem
        if (currentTmdbSettings.useBasicInfo) {
            merged = merged.copy(
                name = if (isModernLayout) enrichment.localizedTitle ?: merged.name else merged.name,
                description = enrichment.description ?: merged.description,
                genres = if (enrichment.genres.isNotEmpty()) enrichment.genres else merged.genres
            )
        }
        if (currentTmdbSettings.useArtwork) {
            merged = merged.copy(
                background = enrichment.backdrop ?: merged.background,
                logo = enrichment.logo ?: merged.logo
            )
        }
        if (currentTmdbSettings.useDetails) {
            merged = merged.copy(
                runtime = enrichment.runtimeMinutes?.toString() ?: merged.runtime,
                ageRating = enrichment.ageRating ?: merged.ageRating,
                status = enrichment.status ?: merged.status
            )
        }
        if (currentTmdbSettings.useReleaseDates) {
            merged = merged.copy(
                releaseInfo = enrichment.releaseInfo ?: merged.releaseInfo
            )
        }
        return merged
    }

    updateIndexedCatalogItem(itemId, ::mergeItem)

    // Modern layout reads enrichment via enrichedPreviews / lastEnrichedPreview.
    // Rebuilding catalogRows here triggers a useless full-home recomposition.
    if (!isModernLayout) {
        _uiState.update { state ->
            var changed = false
            val updatedRows = state.catalogRows.map { row ->
                val idx = row.items.indexOfFirst { it.id == itemId }
                if (idx < 0) row
                else {
                    val mergedItem = mergeItem(row.items[idx])
                    if (mergedItem == row.items[idx]) row
                    else {
                        changed = true
                        val mutableItems = row.items.toMutableList()
                        mutableItems[idx] = mergedItem
                        row.copy(items = mutableItems)
                    }
                }
            }
            if (changed) state.copy(catalogRows = updatedRows) else state
        }
    }

    findCatalogItemById(itemId)?.let { enriched ->
        _lastEnrichedPreview.value = enriched
        _enrichedPreviews.update { it + (itemId to enriched) }
    }
}

internal fun BaseHomeViewModel.updateCatalogItemImdbRating(itemId: String, rating: Float) {
    updateIndexedCatalogItem(itemId) { currentItem ->
        currentItem.copy(imdbRating = rating)
    }
    _uiState.update { state ->
        var changed = false
        val updatedRows = state.catalogRows.map { row ->
            val idx = row.items.indexOfFirst { it.id == itemId }
            if (idx < 0) row
            else {
                val updated = row.items[idx].copy(imdbRating = rating)
                if (updated == row.items[idx]) row
                else {
                    changed = true
                    val mutableItems = row.items.toMutableList()
                    mutableItems[idx] = updated
                    row.copy(items = mutableItems)
                }
            }
        }
        if (changed) state.copy(catalogRows = updatedRows) else state
    }
}

private fun BaseHomeViewModel.updateCatalogItemWithMeta(itemId: String, meta: Meta) {
    val incomingTrailerYtIds = meta.trailerYtIds
    val seasonCount = meta.videos
        .asSequence()
        .mapNotNull { it.season }
        .filter { it > 0 }
        .distinct()
        .count()
        .takeIf { it > 0 }

    fun mergeItem(currentItem: MetaPreview): MetaPreview = currentItem.copy(
        background = meta.backdropUrl ?: currentItem.backdropUrl,
        logo = meta.logo ?: currentItem.logo,
        description = meta.description ?: currentItem.description,
        imdbRating = meta.imdbRating ?: currentItem.imdbRating,
        genres = if (meta.genres.isNotEmpty()) meta.genres else currentItem.genres,
        runtime = meta.runtime ?: currentItem.runtime,
        status = meta.status ?: currentItem.status,
        ageRating = meta.ageRating ?: currentItem.ageRating,
        language = meta.language ?: currentItem.language,
        country = meta.country ?: currentItem.country,
        seasonCount = seasonCount ?: currentItem.seasonCount,
        trailerYtIds = if (incomingTrailerYtIds.isNotEmpty()) incomingTrailerYtIds else currentItem.trailerYtIds
    )

    updateIndexedCatalogItem(itemId, ::mergeItem)

    _uiState.update { state ->
        var changed = false
        val updatedRows = state.catalogRows.map { row ->
            val itemIndex = row.items.indexOfFirst { it.id == itemId }
            if (itemIndex < 0) {
                row
            } else {
                val mergedItem = mergeItem(row.items[itemIndex])
                if (mergedItem == row.items[itemIndex]) {
                    row
                } else {
                    changed = true
                    val mutableItems = row.items.toMutableList()
                    mutableItems[itemIndex] = mergedItem
                    row.copy(items = mutableItems)
                }
            }
        }
        if (changed) state.copy(catalogRows = updatedRows) else state
    }
    findCatalogItemById(itemId)?.let { enriched ->
        _lastEnrichedPreview.value = enriched
        _enrichedPreviews.update { it + (itemId to enriched) }
    }

    // If external meta brought new trailerYtIds and the item has no trailer resolved yet, retry.
    // Only retry if this item is currently focused — avoid prefetching trailers for adjacent items.
    if (incomingTrailerYtIds.isNotEmpty() && !trailerPreviewUrlsState.containsKey(itemId) && activeTrailerPreviewItemId == itemId) {
        trailerPreviewNegativeCache.remove(itemId)
        trailerPreviewLoadingIds.remove(itemId)
        // Bump version so any in-flight pipeline for this item treats itself as stale
        // and won't overwrite the retry result with a negative cache entry.
        trailerPreviewRequestVersion++
        val currentItem = findCatalogItemById(itemId) ?: return
        requestTrailerPreviewPipeline(currentItem)
    }
}

internal suspend fun BaseHomeViewModel.enrichHeroItemsPipeline(
    items: List<MetaPreview>,
    settings: TmdbSettings
): List<MetaPreview> {
    if (items.isEmpty()) return items
    val mdbSettings = currentMdbListSettings
    val mdbEnabled = mdbSettings.enabled && mdbSettings.apiKey.isNotBlank()

    return coroutineScope {
        items.map { item ->
            async(Dispatchers.IO) {
                try {
                    val tmdbDeferred = async {
                        val tmdbId = tmdbService.ensureTmdbId(item.id, item.apiType) ?: return@async null
                        tmdbId.toIntOrNull()?.let { numericId ->
                            runCatching { tmdbService.tmdbToImdb(numericId, item.apiType) }
                        }
                        tmdbMetadataService.fetchEnrichment(
                            tmdbId = tmdbId,
                            contentType = item.type,
                            language = settings.language
                        )
                    }
                    val mdbDeferred = if (mdbEnabled) async {
                        runCatching { mdbListRepository.getImdbRatingForItem(item.id, item.apiType) }.getOrNull()
                    } else null

                    val enrichment = tmdbDeferred.await() ?: return@async item
                    val mdbImdbRating = mdbDeferred?.await()

                    var enriched = item

                    if (settings.useArtwork) {
                        enriched = enriched.copy(
                            background = enrichment.backdrop ?: enriched.background,
                            logo = enrichment.logo ?: enriched.logo,
                            poster = enrichment.poster ?: enriched.poster
                        )
                    }

                    if (settings.useBasicInfo) {
                        enriched = enriched.copy(
                            name = enrichment.localizedTitle ?: enriched.name,
                            description = enrichment.description ?: enriched.description,
                            genres = if (enrichment.genres.isNotEmpty()) enrichment.genres else enriched.genres,
                            imdbRating = mdbImdbRating?.toFloat() ?: enriched.imdbRating
                        )
                    }

                    if (settings.useDetails) {
                        enriched = enriched.copy(
                            runtime = enrichment.runtimeMinutes?.toString() ?: enriched.runtime,
                            status = enrichment.status ?: enriched.status,
                            ageRating = enrichment.ageRating ?: enriched.ageRating,
                            country = enrichment.countries?.joinToString(", ") ?: enriched.country,
                            language = enrichment.language ?: enriched.language
                        )
                    }

                    if (settings.useReleaseDates) {
                        enriched = enriched.copy(
                            releaseInfo = enrichment.releaseInfo ?: enriched.releaseInfo
                        )
                    }

                    enriched
                } catch (e: Exception) {
                    Log.w(BaseHomeViewModel.TAG, "Hero enrichment failed for ${item.id}: ${e.message}")
                    item
                }
            }
        }.awaitAll()
    }
}

internal fun BaseHomeViewModel.replaceGridHeroItemsPipeline(
    gridItems: List<GridItem>,
    heroItems: List<MetaPreview>
): List<GridItem> {
    if (gridItems.isEmpty()) return gridItems
    return gridItems.map { item ->
        if (item is GridItem.Hero) {
            item.copy(items = heroItems)
        } else {
            item
        }
    }
}

internal fun BaseHomeViewModel.heroEnrichmentSignaturePipeline(
    items: List<MetaPreview>,
    settings: TmdbSettings
): String {
    val itemSignature = items.joinToString(separator = "|") { item ->
        "${item.id}:${item.apiType}:${item.name}:${item.backdropUrl}:${item.logo}:${item.poster}"
    }
    return buildString {
        append(settings.enabled)
        append(':')
        append(settings.language)
        append(':')
        append(settings.useArtwork)
        append(':')
        append(settings.useBasicInfo)
        append(':')
        append(settings.useDetails)
        append("::")
        append(itemSignature)
    }
}
