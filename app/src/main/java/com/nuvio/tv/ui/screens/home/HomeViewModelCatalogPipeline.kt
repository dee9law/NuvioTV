package com.nuvio.tv.ui.screens.home

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.usesModernPresentation
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.domain.model.ContinueWatchingFilter
import com.nuvio.tv.domain.model.continueWatchingFilter
import com.nuvio.tv.domain.model.CW_DEFAULT_CARD_WIDTH_DP
import com.nuvio.tv.domain.model.CW_STYLE_METADATA_KEY
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutRowKey
import com.nuvio.tv.domain.model.LayoutRowKind
import com.nuvio.tv.domain.model.LayoutScreenScope
import com.nuvio.tv.domain.model.skipStep
import com.nuvio.tv.domain.model.supportsExtra
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import com.nuvio.tv.domain.model.isTraktCatalogRow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import com.nuvio.tv.core.util.filterReleasedItems
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class CatalogUpdateResult(
    val displayRows: List<CatalogRow>,
    val heroItems: List<com.nuvio.tv.domain.model.MetaPreview>,
    val gridItems: List<GridItem>,
    val fullRows: List<CatalogRow>
)

@OptIn(FlowPreview::class)
internal fun BaseHomeViewModel.observeCollectionsPipeline() {
    viewModelScope.launch {
        collectionsDataStore.collections
            .distinctUntilChanged()
            .debounce(300)
            .collectLatest { collections ->
                collectionsCache = collections
                rebuildCatalogOrder(addonsCache)
                scheduleUpdateCatalogRows()
            }
    }
}

internal fun BaseHomeViewModel.loadHomeCatalogOrderPreferencePipeline() {
    viewModelScope.launch {
        // distinctUntilChanged: every cross-scope Preferences write re-emits
        // this Flow with the same list. Without de-dup, MOVIES toggles would
        // trigger HOME's `rebuildCatalogOrder` + `scheduleUpdateCatalogRows`
        // on every keystroke — see Q3 contamination audit.
        layoutPreferenceDataStore.homeCatalogOrderKeys
            .distinctUntilChanged()
            .collectLatest { keys ->
                homeCatalogOrderKeys = keys
                rebuildCatalogOrder(addonsCache)
                scheduleUpdateCatalogRows()
            }
    }
}

internal fun BaseHomeViewModel.loadFollowAddonsOrderPipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.followAddonsOrder
            .distinctUntilChanged()
            .collectLatest { enabled ->
                followAddonsOrderEnabled = enabled
                rebuildCatalogOrder(addonsCache)
                scheduleUpdateCatalogRows()
            }
    }
}

internal fun BaseHomeViewModel.loadDisabledHomeCatalogPreferencePipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.disabledHomeCatalogKeys.collectLatest { keys ->
            val newKeys = keys.toSet()
            if (newKeys == disabledHomeCatalogKeys) return@collectLatest
            disabledHomeCatalogKeys = newKeys
            rebuildCatalogOrder(addonsCache)
            if (addonsCache.isNotEmpty()) {
                loadAllCatalogsPipeline(addonsCache)
            } else {
                scheduleUpdateCatalogRows()
            }
        }
    }
}

internal fun BaseHomeViewModel.loadCustomCatalogTitlesPipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.customCatalogTitles
            .distinctUntilChanged()
            .collectLatest { titles ->
                customCatalogTitles = titles
                scheduleUpdateCatalogRows()
            }
    }
}

internal fun BaseHomeViewModel.observeTmdbSettingsPipeline() {
    viewModelScope.launch {
        tmdbSettingsDataStore.settings
            .distinctUntilChanged()
            .collectLatest { settings ->
                val languageChanged = currentTmdbSettings.language != settings.language
                currentTmdbSettings = settings
                val tmdbEnabledForLayout = settings.enabled &&
                    (!_uiState.value.homeLayout.usesModernPresentation || settings.modernHomeEnabled)
                val enrichEnabled = tmdbEnabledForLayout || externalMetaPrefetchEnabled
                _uiState.update { it.copy(heroEnrichmentEnabled = enrichEnabled) }
                if (languageChanged) {
                    // Allow re-enrichment with the new language on next focus.
                    prefetchedTmdbIds.clear()
                    prefetchedExternalMetaIds.clear()
                }
                scheduleUpdateCatalogRows()
            }
    }
}

@OptIn(FlowPreview::class)
internal fun BaseHomeViewModel.observeInstalledAddonsPipeline() {
    // Rows-only mode: catalog loading is driven entirely by
    // [observeConfiguredHomeRowsForScopePipeline], which combines installed
    // addons with the user's configured rows. There is no separate
    // addons-only observer because that would duplicate every load.
    //
    // TODO: re-enable for discovery mode later. The old auto-fetch pulled
    //  EVERY installed addon catalog and rendered them all on the home
    //  screen. To restore: re-add the `collectLatest` block below that
    //  forwards to `loadAllCatalogsPipeline(addons)` without the rows-only
    //  allowlist filter.
    //
    // viewModelScope.launch {
    //     addonRepository.getInstalledAddons()
    //         .distinctUntilChanged()
    //         .collectLatest { addons ->
    //             addonsCache = addons
    //             loadAllCatalogsPipeline(addons)
    //         }
    // }
}

/**
 * Rows-only pipeline: combines installed addons with the user's configured
 * rows for [BaseHomeViewModel.homeScope] and drives all catalog loading from that.
 *
 * Behavior:
 *  - No enabled rows configured → empty state (no auto-fetch).
 *  - Some rows configured → derive the catalog allowlist + order from the row
 *    config, then call into [loadAllCatalogsPipeline] which filters by the
 *    allowlist.
 */
@OptIn(FlowPreview::class)
internal fun BaseHomeViewModel.observeConfiguredHomeRowsForScopePipeline() {
    viewModelScope.launch {
        // No blanket COLLECTION filter here.  Per-scope visibility is already
        // enforced upstream by `LayoutPreferenceDataStore.rowsForScope`, which
        // returns only rows whose `viewContext == scope`.  And the two writers
        // that auto-create rows (`autoPopulateFromAddons` and `followAddons
        // Order`'s populator in `NewLayoutSettingsViewModel`) only insert
        // ADDON-kind rows — they never bulk-insert Collections into MOVIES /
        // TV scopes.  So any Collection row reaching the TV / Movies pipeline
        // here was explicitly added by the user via "+ Add Row" while that
        // scope was active, and should be honored.
        combine(
            addonRepository.getInstalledAddons().distinctUntilChanged(),
            layoutPreferenceDataStore.rowsForScope(homeScope).distinctUntilChanged(),
        ) { addons, rows -> addons to rows }
            .collectLatest { (addons, rows) ->
                addonsCache = addons
                applyConfiguredHomeRows(addons, rows)
                configuredRowsObserved = true
                if (allowedHomeCatalogKeys.isEmpty()) {
                    // No enabled rows under this scope — render empty state.
                    cancelInFlightCatalogLoads()
                    synchronized(catalogStateLock) {
                        catalogOrder.clear()
                    }
                    clearCatalogData()
                    activeCatalogLoadSignature = null
                    catalogsLoadInProgress = false
                    hasRenderedFirstCatalog = false
                    pendingCatalogLoads = 0
                    _fullCatalogRows.value = emptyList()
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = null,
                            installedAddonsCount = addons.size,
                            catalogRows = emptyList(),
                            homeRows = emptyList(),
                            gridItems = emptyList(),
                            heroItems = emptyList(),
                        )
                    }
                } else {
                    // `forceReload = false`: the signature check inside
                    // `loadAllCatalogsPipeline` skips re-loading when addons
                    // and the rows allowlist haven't actually changed AND
                    // existing catalog state is healthy. This is what makes
                    // catalogs survive a Home → Settings → Home round-trip:
                    // the rowsForScope flow re-emits on return, but the
                    // signature is unchanged, so we don't clear `catalogsMap`
                    // and don't re-fire `loadCatalogPipeline` for rows that
                    // already have data. Truly-new rows still load because
                    // the signature includes the addon catalog set.
                    loadAllCatalogsPipeline(addons, forceReload = false)
                }
            }
    }
}

/**
 * Translates the user's configured rows for [BaseHomeViewModel.homeScope] into the
 * internal allowlist + order used by the rest of the catalog pipeline.
 *
 * The canonical row id is `addon|<addonId>|<apiType>|<catalogId>` (see
 * [com.nuvio.tv.domain.model.LayoutRowKey.forAddon]). The internal catalog key
 * uses underscores instead of pipes (see [catalogKey]).
 */
/**
 * One-shot migration: seed an enabled Continue Watching row at the top of the
 * HOME scope so existing users keep seeing CW after it became a real,
 * toggleable/reorderable row (it used to be force-injected in Modern).
 *
 * Guarded by [LayoutPreferenceDataStore.continueWatchingDefaultSeeded] so a
 * user who later deletes the row isn't re-seeded. Waits for the scope's rows
 * to be non-empty before seeding — seeding a *lone* CW row would flip the home
 * into rows-only mode showing only CW (there is no "all addon catalogs" auto
 * mode). The CW row is NOT pinned: it's an ordinary reorderable row.
 */
internal fun BaseHomeViewModel.seedDefaultContinueWatchingRowIfNeeded() {
    viewModelScope.launch {
        // Only the HOME-scope ViewModel performs the default CW seed + legacy
        // migration. Other scopes (Movies / TV / For You) share this base init
        // but must never grab the seed — otherwise a non-Home scope that
        // initializes first would seed a CW row into ITS scope and set the
        // global flag, blocking Home.
        if (homeScope != LayoutScreenScope.HOME) return@launch

        // One-shot: gated by the split-seed flag. Moved AHEAD of the legacy
        // migration so the migration is also one-shot — otherwise it would
        // re-run every launch and rewrite the new user-facing "Both" rows
        // (kind CONTINUE_WATCHING) into Series. Existing users already ran the
        // migration on a prior build, so gating here is safe.
        if (layoutPreferenceDataStore.continueWatchingSplitSeeded.first()) return@launch

        // Migrate any pre-split legacy single CONTINUE_WATCHING rows →
        // CONTINUE_WATCHING_SERIES across every scope (idempotent). Runs once.
        for (scope in LayoutScreenScope.entries) {
            val rows = layoutPreferenceDataStore.rowsForScope(scope).first()
            if (rows.any { it.kind == LayoutRowKind.CONTINUE_WATCHING }) {
                val migrated = rows.map { row ->
                    if (row.kind == LayoutRowKind.CONTINUE_WATCHING) {
                        row.copy(
                            id = LayoutRowKey.forContinueWatchingSeries(),
                            kind = LayoutRowKind.CONTINUE_WATCHING_SERIES,
                        )
                    } else {
                        row
                    }
                }
                layoutPreferenceDataStore.setRowsForScope(scope, migrated)
            }
        }

        // Ensure a default Series CW row exists in HOME so it shows in the Rows
        // Manager, so upgraders who ended up with no CW-family row get one; a
        // later manual delete stays sticky.
        val homeRows = layoutPreferenceDataStore.rowsForScope(homeScope).first { it.isNotEmpty() }
        if (homeRows.none { it.kind.continueWatchingFilter != null }) {
            val cwRow = LayoutRowConfig(
                id = LayoutRowKey.forContinueWatchingSeries(),
                kind = LayoutRowKind.CONTINUE_WATCHING_SERIES,
                name = "Continue Watching",
                cardWidthDp = CW_DEFAULT_CARD_WIDTH_DP,
                viewContext = homeScope,
                metadata = mapOf(CW_STYLE_METADATA_KEY to ContinueWatchingCardStyle.CARD.name),
            )
            layoutPreferenceDataStore.setRowsForScope(homeScope, listOf(cwRow) + homeRows)
        }
        layoutPreferenceDataStore.setContinueWatchingSplitSeeded(true)
        layoutPreferenceDataStore.setContinueWatchingDefaultSeeded(true)
    }
}

/**
 * Fetches the Trakt catalog rows (recommendations / watchlist / calendars)
 * configured for [BaseHomeViewModel.homeScope] and injects them into
 * [traktCatalogRowsByKey], then refreshes the rendered rows. Auth-gated +
 * TTL-cached inside [com.nuvio.tv.core.trakt.TraktHomeCatalogResolver].
 *
 * Deliberately a separate observer from the addon/CW pipeline — it never reads
 * or writes playback/progress state.
 */
@OptIn(FlowPreview::class)
internal fun BaseHomeViewModel.observeTraktCatalogRowsPipeline() {
    viewModelScope.launch {
        layoutPreferenceDataStore.rowsForScope(homeScope)
            .map { rows ->
                rows.filter { it.enabled && it.kind.isTraktCatalogRow }.map { it.kind }.toSet()
            }
            .distinctUntilChanged()
            .collectLatest { kinds ->
                // Drop cached rows no longer configured.
                traktCatalogRowsByKey.keys.retainAll { key ->
                    kinds.any { LayoutRowKey.forTraktCatalogKind(it) == key }
                }
                if (kinds.isEmpty()) {
                    scheduleUpdateCatalogRows()
                    return@collectLatest
                }
                kinds.forEach { kind ->
                    val key = LayoutRowKey.forTraktCatalogKind(kind) ?: return@forEach
                    val row = runCatching { traktHomeCatalogResolver.resolve(kind) }.getOrNull()
                    if (row != null) {
                        traktCatalogRowsByKey[key] = row
                    }
                }
                scheduleUpdateCatalogRows()
            }
    }
}

internal fun BaseHomeViewModel.applyConfiguredHomeRows(
    addons: List<Addon>,
    rows: List<LayoutRowConfig>,
) {
    val addonsById = addons.associateBy { it.id }
    val orderedKeys = mutableListOf<String>()
    val allowed = linkedSetOf<String>()
    val enabledRows = rows.filter { it.enabled }
    enabledRows.forEach { row ->
        when (row.kind) {
            LayoutRowKind.ADDON -> {
                if (!row.id.startsWith("addon|")) return@forEach
                val parts = row.id.split("|")
                if (parts.size < 4) return@forEach
                val addonId = parts.subList(1, parts.size - 2).joinToString("|")
                val apiType = parts[parts.size - 2]
                val catalogId = parts.last()
                val addon = addonsById[addonId] ?: return@forEach
                val catalog = addon.catalogs.firstOrNull {
                    it.id == catalogId && it.apiType.equals(apiType, ignoreCase = true)
                } ?: return@forEach
                val key = catalogKey(
                    addonId = addon.id,
                    type = catalog.apiType,
                    catalogId = catalog.id
                )
                if (allowed.add(key)) orderedKeys.add(key)
            }
            LayoutRowKind.COLLECTION -> {
                // Format: "collection|<collectionId>" or
                //         "collection|<collectionId>|<folderId>"  (per-folder rows).
                // For both, the collection-level pipeline key uses only the
                // collection id; folder-level filtering happens downstream.
                val parts = row.id.split("|")
                if (parts.size < 2) return@forEach
                val collectionId = parts[1]
                val key = "collection_$collectionId"
                if (allowed.add(key)) orderedKeys.add(key)
            }
            LayoutRowKind.CONTINUE_WATCHING,
            LayoutRowKind.CONTINUE_WATCHING_SERIES,
            LayoutRowKind.CONTINUE_WATCHING_MOVIES,
            LayoutRowKind.TRAKT_UP_NEXT -> {
                val filter = row.kind.continueWatchingFilter ?: return@forEach
                val key = LayoutRowKey.forContinueWatchingFilter(filter)
                if (allowed.add(key)) orderedKeys.add(key)
            }
            LayoutRowKind.TRAKT_RECOMMENDED_SHOWS,
            LayoutRowKind.TRAKT_RECOMMENDED_MOVIES,
            LayoutRowKind.TRAKT_WATCHLIST_SHOWS,
            LayoutRowKind.TRAKT_WATCHLIST_MOVIES,
            LayoutRowKind.TRAKT_NEW_EPISODES,
            LayoutRowKind.TRAKT_NEW_MOVIES -> {
                // Reserve the row's slot in the order; the CatalogRow itself is
                // fetched asynchronously by observeTraktCatalogRowsPipeline and
                // injected by key in updateCatalogRowsPipeline.
                val key = LayoutRowKey.forTraktCatalogKind(row.kind) ?: return@forEach
                if (allowed.add(key)) orderedKeys.add(key)
            }
            LayoutRowKind.TRAKT,
            LayoutRowKind.TMDB_DISCOVER,
            LayoutRowKind.TMDB_NETWORK -> {
            }
        }
    }
    configuredHomeRows = enabledRows
    allowedHomeCatalogKeys = allowed
    homeCatalogOrderKeys = orderedKeys
    // Disabled-key set is unused in rows-only mode (allowlist is authoritative).
    disabledHomeCatalogKeys = emptySet()
    // In rows-only mode the user's row order is authoritative — bypass
    // `rebuildCatalogOrder` (which filters against `shouldShowOnHome`) and
    // write `catalogOrder` directly. This respects users who add catalogs
    // their addons would otherwise hide from home.
    synchronized(catalogStateLock) {
        catalogOrder.clear()
        catalogOrder.addAll(orderedKeys)
    }
    _uiState.update { it.copy(hasConfiguredRows = enabledRows.isNotEmpty()) }
}

internal suspend fun BaseHomeViewModel.loadAllCatalogsPipeline(
    addons: List<Addon>,
    forceReload: Boolean = false
) {
    val signature = buildHomeCatalogLoadSignature(addons)
    val hasActiveLoads = synchronized(activeCatalogLoadJobs) { activeCatalogLoadJobs.any { it.isActive } }
    if (!forceReload &&
        signature == activeCatalogLoadSignature &&
        (hasActiveLoads || hasAnyCatalogRows())
    ) {
        return
    }

    activeCatalogLoadSignature = signature
    catalogsLoadInProgress = true
    catalogLoadGeneration += 1
    val generation = catalogLoadGeneration
    cancelInFlightCatalogLoads()

    // On reload (not first load), keep existing UI data visible while new
    // catalogs load in the background to avoid a flash of empty content.
    val isReload = _uiState.value.catalogRows.isNotEmpty() || _uiState.value.homeRows.isNotEmpty()
    if (!isReload) {
        _uiState.update { it.copy(isLoading = true, error = null, installedAddonsCount = addons.size) }
        synchronized(catalogStateLock) {
            catalogOrder.clear()
        }
        clearCatalogData()
    } else {
        _uiState.update { it.copy(error = null, installedAddonsCount = addons.size) }
    }
    posterStatusReconcileJob?.cancel()
    reconcilePosterStatusObserversPipeline(emptyList())
    _fullCatalogRows.value = emptyList()
    hasRenderedFirstCatalog = false
    trailerPreviewLoadingIds.clear()
    trailerPreviewNegativeCache.clear()
    trailerPreviewUrlsState.clear()
    trailerPreviewAudioUrlsState.clear()
    activeTrailerPreviewItemId = null
    trailerPreviewRequestVersion = 0L
    prefetchedExternalMetaIds.clear()
    externalMetaPrefetchInFlightIds.clear()
    externalMetaPrefetchJob?.cancel()
    pendingExternalMetaPrefetchItemId = null
    prefetchedTmdbIds.clear()
    tmdbEnrichFocusJob?.cancel()
    pendingTmdbEnrichItemId = null
    lastHeroEnrichmentSignature = null
    lastHeroEnrichedItems = emptyList()
    heroItemOrder = emptyList()

    try {
        if (addons.isEmpty()) {
            catalogsLoadInProgress = false
            _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.home_error_no_addons)) }
            return
        }

        rebuildCatalogOrder(addons)

        // Hero has its own catalog sources (heroCatalogKeys) configured
        // independently in Layout Settings.  When the user has explicitly
        // selected hero catalogs, load those even if they are disabled from
        // home rows.  When no hero catalogs are selected, the hero simply
        // piggybacks on whatever home catalogs are loaded — if none are
        // loaded, the hero has no data and won't render.
        val heroCatalogSet = currentHeroCatalogKeys.toSet()
        val hasHeroSelections = heroCatalogSet.isNotEmpty()

        if (isCatalogOrderEmpty() && !hasHeroSelections) {
            catalogsLoadInProgress = false
            _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.home_error_no_catalog_addons)) }
            return
        }

        // Rows-only mode: when the user has configured rows for this scope,
        // restrict loading to ONLY those catalogs.  `shouldShowOnHome()` and
        // `isCatalogDisabled()` are bypassed because the allowlist is the
        // authoritative source of truth (a catalog the user added explicitly
        // should render even if its addon manifest marks it as off-home).
        val allowedKeys = allowedHomeCatalogKeys
        val rowsOnlyMode = allowedKeys.isNotEmpty()
        val catalogsToLoad = addons.flatMap { addon ->
            addon.catalogs
                .filter { catalog ->
                    if (rowsOnlyMode) {
                        catalogKey(addon.id, catalog.apiType, catalog.id) in allowedKeys
                    } else {
                        catalog.shouldShowOnHome() && !isCatalogDisabled(
                            addonBaseUrl = addon.baseUrl,
                            addonId = addon.id,
                            type = catalog.apiType,
                            catalogId = catalog.id,
                            catalogName = catalog.name
                        )
                    }
                }
                .map { catalog -> addon to catalog }
        }

        // Load hero-selected catalogs even if disabled from home rows —
        // the hero has its own catalog source independent of home rows.
        val alreadyLoadingKeys = catalogsToLoad.map { (addon, catalog) ->
            catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
        }.toSet()
        val heroOnlyCatalogs = if (hasHeroSelections) {
            addons.flatMap { addon ->
                addon.catalogs
                    .filter { catalog ->
                        val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
                        key in heroCatalogSet && key !in alreadyLoadingKeys && !catalog.isSearchOnlyCatalog()
                    }
                    .map { catalog -> addon to catalog }
            }
        } else {
            emptyList()
        }

        val allCatalogsToLoad = catalogsToLoad + heroOnlyCatalogs
        if (allCatalogsToLoad.isEmpty()) {
            // No home catalogs and no hero catalogs to load —
            // but collections may still exist to render.
            catalogsLoadInProgress = false
            if (hasCatalogOrderEntries()) {
                scheduleUpdateCatalogRows()
            } else {
                _uiState.update { it.copy(isLoading = false, error = appContext.getString(R.string.home_error_no_catalog_addons)) }
            }
            return
        }

        // ── Lazy loading: split into eager and deferred ──
        val heroOnlyKeys = heroOnlyCatalogs.map { (addon, catalog) ->
            catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
        }.toSet()

        // Build display title helper (respects custom titles)
        val titlesSnapshot = customCatalogTitles
        val showTypeSuffix = _uiState.value.catalogTypeSuffixEnabled
        val strTypeMovie = appContext.getString(R.string.type_movie)
        val strTypeSeries = appContext.getString(R.string.type_series)
        fun displayTitle(addon: Addon, catalog: CatalogDescriptor): String {
            val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
            val custom = titlesSnapshot[key]
            val baseName = if (!custom.isNullOrBlank()) custom else catalog.name
            val catalogName = baseName.replaceFirstChar { it.uppercase() }
            if (!showTypeSuffix) return catalogName
            val typeLabel = when (catalog.apiType.lowercase()) {
                "movie" -> strTypeMovie.ifBlank { catalog.apiType.replaceFirstChar { it.uppercase() } }
                "series" -> strTypeSeries.ifBlank { catalog.apiType.replaceFirstChar { it.uppercase() } }
                else -> catalog.apiType.replaceFirstChar { it.uppercase() }
            }
            return "$catalogName - $typeLabel"
        }

        // Determine which home catalogs to load eagerly vs lazily.
        // Grid layout loads all catalogs eagerly since it doesn't support
        // placeholder shimmer rows — all content must be available upfront.
        // Wait for layout preferences if not yet ready, to avoid wrong eager/lazy split.
        if (!_uiState.value.layoutPreferencesReady) {
            _uiState.first { it.layoutPreferencesReady }
        }
        val isGridLayout = _uiState.value.homeLayout == HomeLayout.GRID
        val eagerHomeCatalogs = if (isGridLayout) catalogsToLoad else catalogsToLoad.take(eagerCatalogLoadCount)
        val lazyHomeCatalogs = if (isGridLayout) emptyList() else catalogsToLoad.drop(eagerCatalogLoadCount)

        // Build placeholder descriptors for lazy catalogs
        synchronized(catalogStateLock) {
            pendingLazyCatalogs.clear()
            placeholderDescriptors.clear()
        }
        lazyLoadRequestedKeys.clear()

        (eagerHomeCatalogs + lazyHomeCatalogs).forEach { (addon, catalog) ->
            val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
            synchronized(catalogStateLock) {
                placeholderDescriptors.add(
                    BaseHomeViewModel.PlaceholderDescriptor(
                        catalogKey = key,
                        addonId = addon.id,
                        addonName = addon.displayName,
                        addonBaseUrl = addon.baseUrl,
                        catalogId = catalog.id,
                        catalogName = catalog.name,
                        apiType = catalog.apiType,
                        displayTitle = displayTitle(addon, catalog)
                    )
                )
            }
        }

        lazyHomeCatalogs.forEach { (addon, catalog) ->
            val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
            synchronized(catalogStateLock) {
                pendingLazyCatalogs[key] = addon to catalog
            }
        }

        Log.d(BaseHomeViewModel.TAG,
            "Lazy loading: eager=${eagerHomeCatalogs.size} lazy=${lazyHomeCatalogs.size}"
        )

        val eagerCatalogs = eagerHomeCatalogs + heroOnlyCatalogs
        pendingCatalogLoads = eagerCatalogs.size
        eagerCatalogs.forEach { (addon, catalog) ->
            loadCatalogPipeline(addon, catalog, generation)
        }

        // Immediately schedule an update so placeholder rows appear in the UI
        // while catalogs are still loading.
        scheduleUpdateCatalogRows()
    } catch (e: Exception) {
        catalogsLoadInProgress = false
        _uiState.update { it.copy(isLoading = false, error = e.message) }
    }
}

/**
 * Additively loads hero-selected catalogs that are not already in [catalogsMap].
 * Unlike [loadAllCatalogsPipeline] this does NOT clear existing state — it only
 * fills in missing hero catalog data so the hero section can render.
 *
 * Called from the presentation pipeline when [currentHeroCatalogKeys] arrives
 * after the initial catalog load (due to the layout preference debounce).
 */
internal fun BaseHomeViewModel.loadHeroCatalogsPipeline() {
    val heroCatalogKeys = currentHeroCatalogKeys
    if (heroCatalogKeys.isEmpty() || addonsCache.isEmpty()) return

    val heroCatalogSet = heroCatalogKeys.toSet()
    val alreadyLoadedKeys = snapshotCatalogKeys()
    val missingHeroKeys = heroCatalogSet - alreadyLoadedKeys
    if (missingHeroKeys.isEmpty()) {
        // All hero catalogs already loaded — just refresh presentation
        scheduleUpdateCatalogRows()
        return
    }

    val heroToLoad = addonsCache.flatMap { addon ->
        addon.catalogs
            .filter { catalog ->
                val key = catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
                key in missingHeroKeys && !catalog.isSearchOnlyCatalog()
            }
            .map { catalog -> addon to catalog }
    }

    if (heroToLoad.isEmpty()) {
        scheduleUpdateCatalogRows()
        return
    }

    val generation = catalogLoadGeneration
    pendingCatalogLoads += heroToLoad.size
    heroToLoad.forEach { (addon, catalog) ->
        loadCatalogPipeline(addon, catalog, generation)
    }
}

internal fun BaseHomeViewModel.loadCatalogPipeline(
    addon: Addon,
    catalog: CatalogDescriptor,
    generation: Long
) {
    val loadJob = viewModelScope.launch {
        var hasCountedCompletion = false
        catalogLoadSemaphore.withPermit {
            if (generation != catalogLoadGeneration) return@withPermit
            val supportsSkip = catalog.supportsExtra("skip")
            val skipStep = catalog.skipStep()
            Log.d(
                BaseHomeViewModel.TAG,
                "Loading home catalog addonId=${addon.id} addonName=${addon.name} type=${catalog.apiType} catalogId=${catalog.id} catalogName=${catalog.name} supportsSkip=$supportsSkip skipStep=$skipStep"
            )
            catalogRepository.getCatalog(
                addonBaseUrl = addon.baseUrl,
                addonId = addon.id,
                addonName = addon.displayName,
                catalogId = catalog.id,
                catalogName = catalog.name,
                type = catalog.apiType,
                skip = 0,
                skipStep = skipStep,
                supportsSkip = supportsSkip
            ).collect { result ->
                if (generation != catalogLoadGeneration) return@collect
                when (result) {
                    is NetworkResult.Success -> {
                        val key = catalogKey(
                            addonId = addon.id,
                            type = catalog.apiType,
                            catalogId = catalog.id
                        )
                        replaceCatalogRow(key, result.data)
                        // Remove placeholder descriptor now that real data is available
                        synchronized(catalogStateLock) {
                            placeholderDescriptors.removeAll { it.catalogKey == key }
                        }
                        if (!hasCountedCompletion) {
                            pendingCatalogLoads = (pendingCatalogLoads - 1).coerceAtLeast(0)
                            hasCountedCompletion = true
                        }
                        Log.d(
                            BaseHomeViewModel.TAG,
                            "Home catalog loaded addonId=${addon.id} type=${catalog.apiType} catalogId=${catalog.id} items=${result.data.items.size} pending=$pendingCatalogLoads"
                        )
                        if (pendingCatalogLoads == 0) {
                            catalogsLoadInProgress = false
                        }
                        scheduleUpdateCatalogRows()
                    }
                    is NetworkResult.Error -> {
                        val errorKey = catalogKey(
                            addonId = addon.id,
                            type = catalog.apiType,
                            catalogId = catalog.id
                        )
                        // Remove placeholder on error so it doesn't show forever
                        synchronized(catalogStateLock) {
                            placeholderDescriptors.removeAll { it.catalogKey == errorKey }
                        }
                        if (!hasCountedCompletion) {
                            pendingCatalogLoads = (pendingCatalogLoads - 1).coerceAtLeast(0)
                            hasCountedCompletion = true
                        }
                        Log.w(
                            BaseHomeViewModel.TAG,
                            "Home catalog failed addonId=${addon.id} type=${catalog.apiType} catalogId=${catalog.id} code=${result.code} message=${result.message}"
                        )
                        if (pendingCatalogLoads == 0) {
                            catalogsLoadInProgress = false
                        }
                        scheduleUpdateCatalogRows()
                    }
                    NetworkResult.Loading -> {
                        /* Handled by individual row */
                    }
                }
            }
        }
    }
    registerCatalogLoadJob(loadJob)
}

internal fun BaseHomeViewModel.loadMoreCatalogItemsPipeline(catalogId: String, addonId: String, type: String) {
    val key = catalogKey(addonId = addonId, type = type, catalogId = catalogId)
    val currentRow = readCatalogRow(key) ?: return

    if (currentRow.isLoading || !currentRow.hasMore) return
    if (key in _loadingCatalogs.value) return

    updateCatalogRow(key) { it.copy(isLoading = true) }
    _loadingCatalogs.update { it + key }

    viewModelScope.launch {
        val addon = addonsCache.find { it.id == addonId } ?: return@launch

        val nextSkip = (currentRow.currentPage + 1) * currentRow.skipStep
        catalogRepository.getCatalog(
            addonBaseUrl = addon.baseUrl,
            addonId = addon.id,
            addonName = addon.displayName,
            catalogId = catalogId,
            catalogName = currentRow.catalogName,
            type = currentRow.apiType,
            skip = nextSkip,
            skipStep = currentRow.skipStep,
            supportsSkip = currentRow.supportsSkip
        ).collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    updateCatalogRow(key) { latestRow ->
                        val existingIds = latestRow.items.asSequence()
                            .map { "${it.apiType}:${it.id}" }
                            .toHashSet()
                        val newUniqueItems = result.data.items.filter { item ->
                            "${item.apiType}:${item.id}" !in existingIds
                        }
                        val mergedItems = latestRow.items + newUniqueItems
                        val hasMore = if (newUniqueItems.isEmpty()) false else result.data.hasMore
                        result.data.copy(items = mergedItems, hasMore = hasMore)
                    }
                    _loadingCatalogs.update { it - key }
                    scheduleUpdateCatalogRows()
                }
                is NetworkResult.Error -> {
                    updateCatalogRow(key) { it.copy(isLoading = false) }
                    _loadingCatalogs.update { it - key }
                    scheduleUpdateCatalogRows()
                }
                NetworkResult.Loading -> { }
            }
        }
    }
}

internal suspend fun BaseHomeViewModel.updateCatalogRowsPipeline() {
    val (orderedKeys, catalogSnapshot) = snapshotCatalogState()
    val collectionsSnapshot = collectionsCache.associateBy { "collection_${it.id}" }
    val heroCatalogKeys = currentHeroCatalogKeys
    val currentLayout = _uiState.value.homeLayout
    val currentGridItems = _uiState.value.gridItems
    val heroSectionEnabled = _uiState.value.heroSectionEnabled
    val hideUnreleased = _uiState.value.hideUnreleasedContent
    val titlesSnapshot = customCatalogTitles

    val (displayRows, baseHeroItems, baseGridItems, fullRowsFiltered) = withContext(Dispatchers.Default) {
        val rawRows = orderedKeys.mapNotNull { key ->
            val row = catalogSnapshot[key] ?: return@mapNotNull null
            val custom = titlesSnapshot[key]
            if (!custom.isNullOrBlank()) row.copy(catalogName = custom) else row
        }
        val orderedRows = if (hideUnreleased) {
            val today = LocalDate.now()
            rawRows.map { it.filterReleasedItems(today) }
        } else {
            rawRows
        }
        val selectedHeroCatalogSet = heroCatalogKeys.toSet()
        val orderedKeySet = orderedKeys.toSet()
        val selectedHeroRows = if (selectedHeroCatalogSet.isNotEmpty()) {
            // Include hero catalogs from ordered rows
            val fromOrdered = orderedRows.filter { row ->
                val key = "${row.addonId}_${row.apiType}_${row.catalogId}"
                key in selectedHeroCatalogSet
            }
            // Also include hero catalogs loaded but not in catalog order
            // (e.g., catalogs disabled from home rows but selected for hero)
            val heroOnlyRows = selectedHeroCatalogSet
                .filter { it !in orderedKeySet }
                .mapNotNull { catalogSnapshot[it] }
            val heroOnlyFiltered = if (hideUnreleased) {
                val today = LocalDate.now()
                heroOnlyRows.map { it.filterReleasedItems(today) }
            } else {
                heroOnlyRows
            }
            fromOrdered + heroOnlyFiltered
        } else {
            emptyList()
        }
        fun stableHeroCandidates(row: CatalogRow, candidates: kotlin.collections.Collection<MetaPreview>): List<MetaPreview> {
            return candidates.sortedWith(
                compareBy<MetaPreview> { stableHeroSortKey(row, it) }
                    .thenBy { it.id }
            )
        }
        fun slotShuffled(rows: List<CatalogRow>, filter: (MetaPreview) -> Boolean, currentOrder: List<String>): List<MetaPreview> {
            val totalCatalogs = rows.size.coerceAtLeast(1)
            val baseSlot = 7 / totalCatalogs
            val remainder = 7 % totalCatalogs
            val seen = mutableSetOf<String>()
            val result = mutableListOf<MetaPreview>()
            rows.forEachIndexed { index, row ->
                val slot = baseSlot + if (index < remainder) 1 else 0
                val existing = currentOrder.filter { id -> row.items.any { it.id == id } }
                val byId = row.items.filter(filter).associateBy { it.id }
                val ordered = existing.mapNotNull { byId[it] }
                val new = stableHeroCandidates(
                    row = row,
                    candidates = byId.values.filter { it.id !in existing }
                )
                // Filter out duplicates but keep taking until slot is filled
                val unique = (ordered + new).filter { seen.add(it.id) }
                result += unique.take(slot)
            }
            return result
        }

        val currentHeroOrder = heroItemOrder

        val heroItemsFromSelectedCatalogs = slotShuffled(
            selectedHeroRows, { it.hasHeroArtwork() }, currentHeroOrder
        )
        val fallbackHeroItemsFromSelectedCatalogs = slotShuffled(
            selectedHeroRows, { true }, currentHeroOrder
        )
        // When orderedRows is empty (all catalogs disabled), include any
        // hero-only loaded catalogs as fallback hero sources.
        val allHeroFallbackRows = if (orderedRows.isNotEmpty()) {
            orderedRows
        } else {
            val nonOrderedRows = catalogSnapshot.keys
                .filter { it !in orderedKeySet }
                .mapNotNull { catalogSnapshot[it] }
            if (hideUnreleased) {
                val today = LocalDate.now()
                nonOrderedRows.map { it.filterReleasedItems(today) }
            } else {
                nonOrderedRows
            }
        }
        val fallbackHeroItemsWithArtwork = slotShuffled(
            allHeroFallbackRows, { it.hasHeroArtwork() }, currentHeroOrder
        )

        // Rows-only mode: hero source is the first non-empty configured row.
        // (Spec: "Hero carousel source: first enabled row in the rows config
        // that has content".) Overrides the legacy `heroCatalogKeys` selection
        // unless the user has explicitly set hero catalogs.
        val rowsOnlyHeroItems = if (allowedHomeCatalogKeys.isNotEmpty() &&
            selectedHeroCatalogSet.isEmpty()) {
            orderedRows
                .firstOrNull { it.items.isNotEmpty() }
                ?.items
                ?.take(8)
                .orEmpty()
        } else {
            emptyList()
        }
        val computedHeroItems = when {
            rowsOnlyHeroItems.isNotEmpty() -> rowsOnlyHeroItems
            heroItemsFromSelectedCatalogs.isNotEmpty() -> heroItemsFromSelectedCatalogs
            fallbackHeroItemsFromSelectedCatalogs.isNotEmpty() -> fallbackHeroItemsFromSelectedCatalogs
            fallbackHeroItemsWithArtwork.isNotEmpty() -> fallbackHeroItemsWithArtwork
            else -> emptyList()
        }

        val computedDisplayRows = orderedRows.map { row ->
            // Fix 2: Spotlight gets the same full-row treatment as Modern so its
            // carousels show the complete catalog instead of being truncated to
            // 25. Other layouts (Classic/Grid) keep the truncation.
            val shouldKeepFullRow =
                (currentLayout.usesModernPresentation || currentLayout == HomeLayout.SPOTLIGHT) &&
                    row.supportsSkip
            if (row.items.size > 25 && !shouldKeepFullRow) {
                val key = "${row.addonId}_${row.apiType}_${row.catalogId}"
                val cachedEntry = getTruncatedRowCacheEntry(key)
                if (cachedEntry != null && cachedEntry.sourceRow === row) {
                    cachedEntry.truncatedRow
                } else {
                    val truncatedRow = row.copy(items = row.items.take(25))
                    putTruncatedRowCacheEntry(
                        key,
                        BaseHomeViewModel.TruncatedRowCacheEntry(
                            sourceRow = row,
                            truncatedRow = truncatedRow
                        )
                    )
                    truncatedRow
                }
            } else {
                val key = "${row.addonId}_${row.apiType}_${row.catalogId}"
                removeTruncatedRowCacheEntry(key)
                row
            }
        }

        CatalogUpdateResult(computedDisplayRows, computedHeroItems, emptyList(), orderedRows)
    }

    _fullCatalogRows.update { rows ->
        if (rows == fullRowsFiltered) rows else fullRowsFiltered
    }

    heroItemOrder = baseHeroItems.map { it.id }

    val computedHomeRows = buildList {
        val displayRowsByKey = displayRows.associateBy { "${it.addonId}_${it.apiType}_${it.catalogId}" }
        // Build a lookup of placeholder descriptors by key for lazy catalogs
        val placeholdersByKey = synchronized(catalogStateLock) {
            placeholderDescriptors.associateBy { it.catalogKey }
        }
        // Rows-only mode: skip the legacy "pin every pinToTop collection to
        // the top of Home" block entirely. Collections appear on Home ONLY when
        // the user explicitly adds them as collection-kind rows in
        // Settings → Appearance → Rows (HOME scope), and they appear at the
        // position the user chose — not at the top by virtue of `pinToTop`.
        //
        // TODO: re-enable for discovery mode later. The block below was the
        //  only path that surfaced un-configured collections on Home:
        //
        // collectionsCache.forEach { collection ->
        //     val key = "collection_${collection.id}"
        //     if (collection.pinToTop && key !in disabledHomeCatalogKeys) {
        //         add(HomeRow.CollectionRow(collection))
        //     }
        // }
        for (key in orderedKeys) {
            if (key in disabledHomeCatalogKeys) continue
            val cwFilter = when (key) {
                LayoutRowKey.forContinueWatchingSeries() -> ContinueWatchingFilter.SERIES
                LayoutRowKey.forContinueWatchingMovies() -> ContinueWatchingFilter.MOVIES
                LayoutRowKey.forTraktUpNext() -> ContinueWatchingFilter.UP_NEXT
                LayoutRowKey.forContinueWatchingBoth() -> ContinueWatchingFilter.BOTH
                else -> null
            }
            if (cwFilter != null) {
                add(HomeRow.ContinueWatching(cwFilter))
                continue
            }
            // Trakt catalog rows (recommendations / watchlist / calendars) are
            // fetched out-of-band into traktCatalogRowsByKey; inject by key.
            val traktRow = traktCatalogRowsByKey[key]
            if (traktRow != null) {
                if (traktRow.items.isNotEmpty()) add(HomeRow.Catalog(traktRow))
                continue
            }
            val collectionEntry = collectionsSnapshot[key]
            if (collectionEntry != null) {
                // Always render: in rows-only mode the user's explicit row
                // ordering wins over `pinToTop` (no separate pinToTop block
                // above means there's no longer a risk of duplication).
                add(HomeRow.CollectionRow(collectionEntry))
            } else {
                val catalogRow = displayRowsByKey[key]
                if (catalogRow != null && catalogRow.items.isNotEmpty()) {
                    add(HomeRow.Catalog(catalogRow))
                } else {
                    val placeholder = placeholdersByKey[key]
                    if (placeholder != null) {
                        if (currentLayout.usesModernPresentation) {
                            add(HomeRow.PlaceholderCatalog(
                                catalogKey = placeholder.catalogKey,
                                addonId = placeholder.addonId,
                                addonName = placeholder.addonName,
                                addonBaseUrl = placeholder.addonBaseUrl,
                                catalogId = placeholder.catalogId,
                                catalogName = placeholder.catalogName,
                                apiType = placeholder.apiType,
                                displayTitle = placeholder.displayTitle
                            ))
                        } else {
                            val fakeItems = (0 until 8).map { i ->
                                MetaPreview(
                                    id = "__placeholder_${placeholder.catalogKey}_$i",
                                    type = com.nuvio.tv.domain.model.ContentType.fromString(placeholder.apiType),
                                    rawType = placeholder.apiType,
                                    name = " ",
                                    poster = "placeholder://empty",
                                    posterShape = com.nuvio.tv.domain.model.PosterShape.POSTER,
                                    background = null,
                                    logo = null,
                                    description = null,
                                    releaseInfo = " ",
                                    imdbRating = null,
                                    genres = emptyList()
                                )
                            }
                            add(HomeRow.Catalog(CatalogRow(
                                addonId = placeholder.addonId,
                                addonName = placeholder.addonName,
                                addonBaseUrl = placeholder.addonBaseUrl,
                                catalogId = placeholder.catalogId,
                                catalogName = placeholder.catalogName,
                                type = com.nuvio.tv.domain.model.ContentType.fromString(placeholder.apiType),
                                rawType = placeholder.apiType,
                                items = fakeItems,
                                isLoading = true,
                                hasMore = false
                            )))
                        }
                    }
                }
            }
        }
    }

    val nextGridItems = if (currentLayout == HomeLayout.GRID) {
        val posterCardWidthDp = _uiState.value.posterCardWidthDp
        val itemsPerRow = when (posterCardWidthDp) {
            104 -> 7; 112 -> 6; 120 -> 6; 126 -> 6; 134 -> 5; 140 -> 5; else -> 6
        }
        val rowCount = if (posterCardWidthDp <= 104) 2 else 3
        val seeAllThreshold = itemsPerRow * rowCount + 2
        val maxWithSeeAll = itemsPerRow * rowCount - 1
        val maxWithoutSeeAll = itemsPerRow * rowCount
        buildList {
            if (heroSectionEnabled && baseHeroItems.isNotEmpty()) {
                add(GridItem.Hero(baseHeroItems))
            }
            computedHomeRows.forEach { homeRow ->
                when (homeRow) {
                    is HomeRow.Catalog -> {
                        val row = homeRow.row
                        val isPlaceholderRow = row.isLoading &&
                            row.items.firstOrNull()?.id?.startsWith("__placeholder_") == true
                        if (row.items.isNotEmpty() && !isPlaceholderRow) {
                            add(GridItem.SectionDivider(
                                catalogName = row.catalogName,
                                catalogId = row.catalogId,
                                addonBaseUrl = row.addonBaseUrl,
                                addonId = row.addonId,
                                type = row.apiType
                            ))
                            val hasEnoughForSeeAll = row.items.size >= seeAllThreshold
                            val displayItems = if (hasEnoughForSeeAll) row.items.take(maxWithSeeAll) else row.items.take(maxWithoutSeeAll)
                            displayItems.forEach { item ->
                                add(GridItem.Content(
                                    item = item,
                                    addonBaseUrl = row.addonBaseUrl,
                                    catalogId = row.catalogId,
                                    catalogName = row.catalogName
                                ))
                            }
                            if (hasEnoughForSeeAll) {
                                add(GridItem.SeeAll(
                                    catalogId = row.catalogId,
                                    addonId = row.addonId,
                                    type = row.apiType
                                ))
                            }
                        }
                    }
                    is HomeRow.CollectionRow -> {
                        val col = homeRow.collection
                        add(GridItem.CollectionHeader(
                            collectionId = col.id,
                            title = col.title
                        ))
                        col.folders.forEach { folder ->
                            add(GridItem.CollectionFolder(
                                collectionId = col.id,
                                collectionTitle = col.title,
                                focusGlowEnabled = col.focusGlowEnabled,
                                folder = folder
                            ))
                        }
                    }
                    is HomeRow.ContinueWatching -> { }
                    is HomeRow.PlaceholderCatalog -> { }
                }
            }
        }.let { replaceGridHeroItemsPipeline(it, baseHeroItems) }
    } else {
        currentGridItems
    }

    // Clear any stale error when content is now available (e.g., hero
    // catalogs loaded after the initial startup race set an error).
    val hasContent = computedHomeRows.isNotEmpty() || baseHeroItems.isNotEmpty() || displayRows.isNotEmpty()

    _uiState.update { state ->
        state.copy(
            catalogRows = if (state.catalogRows == displayRows) state.catalogRows else displayRows,
            heroItems = if (state.heroItems == baseHeroItems) state.heroItems else baseHeroItems,
            gridItems = if (state.gridItems == nextGridItems) state.gridItems else nextGridItems,
            homeRows = if (state.homeRows == computedHomeRows) state.homeRows else computedHomeRows,
            isLoading = false,
            error = if (hasContent) null else state.error
        )
    }

    val tmdbSettings = currentTmdbSettings
    val tmdbEnabledForCurrentLayout = tmdbSettings.enabled &&
        (!currentLayout.usesModernPresentation || tmdbSettings.modernHomeEnabled)
    val shouldUseEnrichedHeroItems = tmdbEnabledForCurrentLayout &&
        (tmdbSettings.useArtwork || tmdbSettings.useBasicInfo || tmdbSettings.useDetails || tmdbSettings.useReleaseDates)

    if (shouldUseEnrichedHeroItems && baseHeroItems.isNotEmpty()) {
        heroEnrichmentJob?.cancel()
        heroEnrichmentJob = viewModelScope.launch {
            val enrichmentSignature = heroEnrichmentSignaturePipeline(baseHeroItems, tmdbSettings)
            if (lastHeroEnrichmentSignature == enrichmentSignature) {
                val cached = lastHeroEnrichedItems
                _uiState.update { state ->
                    state.copy(
                        heroItems = if (state.heroItems == cached) state.heroItems else cached,
                        gridItems = if (currentLayout == HomeLayout.GRID) {
                            val enrichedGrid = replaceGridHeroItemsPipeline(state.gridItems, cached)
                            if (state.gridItems == enrichedGrid) state.gridItems else enrichedGrid
                        } else state.gridItems
                    )
                }
            } else {
                val enrichedItems = enrichHeroItemsPipeline(baseHeroItems, tmdbSettings)
                lastHeroEnrichmentSignature = enrichmentSignature
                lastHeroEnrichedItems = enrichedItems
                _uiState.update { state ->
                    state.copy(
                        heroItems = if (state.heroItems == enrichedItems) state.heroItems else enrichedItems,
                        gridItems = if (currentLayout == HomeLayout.GRID) {
                            val enrichedGrid = replaceGridHeroItemsPipeline(state.gridItems, enrichedItems)
                            if (state.gridItems == enrichedGrid) state.gridItems else enrichedGrid
                        } else state.gridItems
                    )
                }
            }
        }
    } else {
        lastHeroEnrichmentSignature = null
        lastHeroEnrichedItems = emptyList()
        heroItemOrder = emptyList()
    }

    schedulePosterStatusReconcilePipeline(displayRows)
}

private fun stableHeroSortKey(
    row: CatalogRow,
    item: MetaPreview
): Int {
    return "${row.addonId}|${row.apiType}|${row.catalogId}|${item.id}".hashCode()
}

internal fun BaseHomeViewModel.schedulePosterStatusReconcilePipeline(rows: List<CatalogRow>) {
    posterStatusReconcileJob?.cancel()
    if (rows.isEmpty()) {
        reconcilePosterStatusObserversPipeline(rows)
        return
    }
    posterStatusReconcileJob = viewModelScope.launch {
        delay(500)
        reconcilePosterStatusObserversPipeline(rows)
    }
}

internal fun BaseHomeViewModel.reconcilePosterStatusObserversPipeline(rows: List<CatalogRow>) {
    val desiredLibraryItemsByKey = linkedMapOf<String, Pair<String, String>>()
    rows.asSequence()
        .flatMap { row -> row.items.asSequence() }
        .take(BaseHomeViewModel.MAX_POSTER_STATUS_OBSERVERS)
        .forEach { item ->
            val key = homeItemStatusKey(item.id, item.apiType)
            if (key !in desiredLibraryItemsByKey) {
                desiredLibraryItemsByKey[key] = item.id to item.apiType
            }
        }
    val desiredLibraryKeys = desiredLibraryItemsByKey.keys

    val allMovieItemsByKey = linkedMapOf<String, String>()
    rows.asSequence()
        .flatMap { row -> row.items.asSequence() }
        .filter { it.apiType.equals("movie", ignoreCase = true) }
        .forEach { item ->
            val key = homeItemStatusKey(item.id, item.apiType)
            if (key !in allMovieItemsByKey) {
                allMovieItemsByKey[key] = item.id
            }
        }
    val desiredMovieKeys = allMovieItemsByKey.keys

    val allSeriesItemsByKey = linkedMapOf<String, String>()
    rows.asSequence()
        .flatMap { row -> row.items.asSequence() }
        .filter { it.apiType.equals("series", ignoreCase = true) || it.apiType.equals("tv", ignoreCase = true) }
        .forEach { item ->
            val key = homeItemStatusKey(item.id, item.apiType)
            if (key !in allSeriesItemsByKey) {
                allSeriesItemsByKey[key] = item.id
            }
        }

    posterLibraryObserverJobs.keys
        .filterNot { it in desiredLibraryKeys }
        .forEach { staleKey ->
            posterLibraryObserverJobs.remove(staleKey)?.cancel()
        }

    desiredLibraryItemsByKey.forEach { (statusKey, itemRef) ->
        val itemId = itemRef.first
        val itemType = itemRef.second

        if (statusKey !in posterLibraryObserverJobs) {
            posterLibraryObserverJobs[statusKey] = viewModelScope.launch {
                libraryRepository.isInLibrary(itemId = itemId, itemType = itemType)
                    .distinctUntilChanged()
                    .collectLatest { isInLibrary ->
                        _uiState.update { state ->
                            if (state.posterLibraryMembership[statusKey] == isInLibrary) {
                                state
                            } else {
                                state.copy(
                                    posterLibraryMembership = state.posterLibraryMembership + (statusKey to isInLibrary)
                                )
                            }
                        }
                    }
            }
        }
    }

    if (desiredMovieKeys != lastMovieWatchedItemKeys) {
        lastMovieWatchedItemKeys = desiredMovieKeys
        movieWatchedObserverJobs.values.forEach { it.cancel() }
        movieWatchedObserverJobs.clear()
        movieWatchedBatchJob?.cancel()

        if (desiredMovieKeys.isNotEmpty()) {
            movieWatchedBatchJob = viewModelScope.launch {
                watchProgressRepository.observeWatchedMovieIds()
                    .collectLatest { watchedIds ->
                        _uiState.update { state ->
                            val movieStatus = buildMap {
                                allMovieItemsByKey.forEach { (statusKey, contentId) ->
                                    put(statusKey, contentId in watchedIds)
                                }
                            }
                            // Merge with existing status to preserve series entries.
                            val merged = state.movieWatchedStatus
                                .filterKeys { it !in desiredMovieKeys } + movieStatus
                            if (state.movieWatchedStatus == merged) {
                                state
                            } else {
                                state.copy(movieWatchedStatus = merged)
                            }
                        }
                    }
            }
        }
    }

    // Update series watched status from CW pipeline's fully-watched resolution.
    // This piggybacks on the meta lookups CW already performs — no extra network calls.
    if (allSeriesItemsByKey.isNotEmpty()) {
        seriesWatchedObserverJob?.cancel()
        seriesWatchedObserverJob = viewModelScope.launch {
            fullyWatchedSeriesIds.fullyWatchedSeriesIds.collectLatest { fullyWatched ->
                val seriesStatus = buildMap {
                    allSeriesItemsByKey.forEach { (statusKey, contentId) ->
                        put(statusKey, contentId in fullyWatched)
                    }
                }
                _uiState.update { state ->
                    // Merge with existing status to preserve movie entries.
                    val merged = state.movieWatchedStatus
                        .filterKeys { it !in allSeriesItemsByKey.keys } + seriesStatus
                    if (state.movieWatchedStatus == merged) state
                    else state.copy(movieWatchedStatus = merged)
                }
            }
        }
    } else {
        seriesWatchedObserverJob?.cancel()
        seriesWatchedObserverJob = null
    }

    _uiState.update { state ->
        val trimmedLibraryMembership =
            state.posterLibraryMembership.filterKeys { it in desiredLibraryKeys }
        val trimmedLibraryPending =
            state.posterLibraryPending.filterTo(linkedSetOf()) { it in desiredLibraryKeys }
        val trimmedMovieWatchedPending =
            state.movieWatchedPending.filterTo(linkedSetOf()) { it in desiredMovieKeys }

        if (
            trimmedLibraryMembership == state.posterLibraryMembership &&
            trimmedLibraryPending == state.posterLibraryPending &&
            trimmedMovieWatchedPending == state.movieWatchedPending
        ) {
            state
        } else {
            state.copy(
                posterLibraryMembership = trimmedLibraryMembership,
                posterLibraryPending = trimmedLibraryPending,
                movieWatchedPending = trimmedMovieWatchedPending
            )
        }
    }
}
