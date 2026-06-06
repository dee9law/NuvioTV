package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.domain.model.ContinueWatchingFilter
import com.nuvio.tv.domain.model.CW_DEFAULT_CARD_WIDTH_DP
import com.nuvio.tv.domain.model.CW_STYLE_METADATA_KEY
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutRowKey
import com.nuvio.tv.domain.model.LayoutRowKind
import com.nuvio.tv.domain.model.LayoutScreenScope
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One picker option in the "Add Row" dialog. Grouped under [groupLabel]
 * (e.g. "Netflix Addon", "Collections", "Trakt") for the sectioned picker.
 */
data class CatalogSourceOption(
    val id: String,
    val kind: LayoutRowKind,
    val name: String,
    val groupLabel: String,
    val apiType: String? = null,
)

/**
 * One catalog the user can flip into the Hero strip via the per-pill Hero
 * Catalogs multi-picker. [key] matches the canonical hero-catalog key format
 * used by `heroCatalogSelectionsForScope`.
 */
data class HeroCatalogChoice(
    val key: String,
    val name: String,
    val addonName: String,
)

/**
 * Stripped-down Layout settings state. Per the simplification spec, the
 * Layout screen exposes ONLY the layout picker plus a handful of toggles
 * tied to the currently selected layout:
 *  - Modern / Immersive → no per-layout toggles
 *  - Grid    → showHeroSection + heroCatalogKeys
 *  - Classic → focusItemGradient + showHeroSection + heroCatalogKeys
 *
 * Everything else (poster labels, addon name, catalog type suffix, hide
 * unreleased, focused-poster expand/delay/mute, poster card width / corner
 * radius, landscape posters) lives elsewhere — see [GlobalSettingsContent]
 * and the Rows tab's Card Orientation toggle.
 */
data class NewLayoutUiState(
    val selectedScope: LayoutScreenScope = LayoutScreenScope.HOME,
    val layout: HomeLayout = HomeLayout.MODERN,
    val showHeroSection: Boolean = true,
    val heroCatalogKeys: List<String> = emptyList(),
    val availableHeroCatalogs: List<HeroCatalogChoice> = emptyList(),
    val focusItemGradient: Boolean = false,
    // Rows tab state
    val rows: List<LayoutRowConfig> = emptyList(),
    val availableSources: List<CatalogSourceOption> = emptyList(),
    val availableCollections: List<Collection> = emptyList(),
    val landscapePostersDefault: Boolean = false,
    /**
     * When true, the active scope's row arrangement is derived from
     * installed addons in manifest order. Manual edits to the row list
     * still write through, but the toggle indicates the user's intent.
     * Auto-populate runs automatically when the toggle flips ON.
     */
    val followAddonsOrder: Boolean = false,
    /**
     * Per-scope global expand-to-backdrop setting for the active scope.
     * Floor of the per-row → per-scope expand hierarchy: a row whose
     * [LayoutRowConfig.expandEnabled] is null follows this value.
     */
    val expandBackdropEnabled: Boolean = false,
)

private data class CoreLayoutState(
    val scope: LayoutScreenScope,
    val layout: HomeLayout,
    val showHeroSection: Boolean,
    val heroCatalogKeys: List<String>,
    val focusItemGradient: Boolean,
)

private data class RowsAndSources(
    val rows: List<LayoutRowConfig>,
    val sources: List<CatalogSourceOption>,
    val collections: List<Collection>,
    val heroCatalogs: List<HeroCatalogChoice>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewLayoutSettingsViewModel @Inject constructor(
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val addonRepository: AddonRepository,
    private val collectionsDataStore: CollectionsDataStore,
    traktAuthDataStore: com.nuvio.tv.data.local.TraktAuthDataStore,
) : ViewModel() {

    private val _selectedScope = MutableStateFlow(LayoutScreenScope.HOME)

    /** Whether the user is signed into Trakt — gates the "+ Trakt" submenu. */
    val traktSignedIn: StateFlow<Boolean> = traktAuthDataStore.state
        .map { it.isAuthenticated }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val layoutFlow: Flow<HomeLayout> = _selectedScope
        .flatMapLatest { layoutPreferenceDataStore.selectedLayoutForScope(it) }
    private val showHeroSectionFlow: Flow<Boolean> = _selectedScope
        .flatMapLatest { layoutPreferenceDataStore.heroSectionEnabledForScope(it) }
    private val heroCatalogKeysFlow: Flow<List<String>> = _selectedScope
        .flatMapLatest { layoutPreferenceDataStore.heroCatalogSelectionsForScope(it) }
    private val focusItemGradientFlow: Flow<Boolean> = _selectedScope
        .flatMapLatest { layoutPreferenceDataStore.classicFocusGradientEnabledForScope(it) }
    private val rowsFlow: Flow<List<LayoutRowConfig>> = _selectedScope
        .flatMapLatest { layoutPreferenceDataStore.rowsForScope(it) }
    private val followAddonsOrderFlow: Flow<Boolean> = _selectedScope
        .flatMapLatest { layoutPreferenceDataStore.followAddonsOrderForScope(it) }
    private val expandBackdropEnabledFlow: Flow<Boolean> = _selectedScope
        .flatMapLatest { layoutPreferenceDataStore.focusedPosterBackdropExpandEnabledForScope(it) }

    private val installedAddonsFlow = addonRepository.getInstalledAddons()

    private val sourcesFlow: Flow<List<CatalogSourceOption>> = combine(
        installedAddonsFlow,
        collectionsDataStore.collections,
    ) { addons, collections ->
        val addonOptions = addons.flatMap { addon ->
            addon.catalogs
                .filter { catalog ->
                    !catalog.extra.any { it.name.equals("search", ignoreCase = true) && it.isRequired }
                }
                .map { catalog ->
                    CatalogSourceOption(
                        id = "addon|${addon.id}|${catalog.apiType}|${catalog.id}",
                        kind = LayoutRowKind.ADDON,
                        name = catalog.name,
                        groupLabel = addon.displayName,
                        apiType = catalog.apiType,
                    )
                }
        }
        val collectionOptions = collections.map { c ->
            CatalogSourceOption(
                id = "collection|${c.id}",
                kind = LayoutRowKind.COLLECTION,
                name = c.title,
                groupLabel = "Collections",
            )
        }
        val traktOptions = listOf(
            CatalogSourceOption("trakt|watchlist",       LayoutRowKind.TRAKT, "Watchlist",       "Trakt"),
            CatalogSourceOption("trakt|recommended",     LayoutRowKind.TRAKT, "Recommended",     "Trakt"),
            CatalogSourceOption("trakt|trending_movies", LayoutRowKind.TRAKT, "Trending Movies", "Trakt"),
            CatalogSourceOption("trakt|trending_shows",  LayoutRowKind.TRAKT, "Trending Shows",  "Trakt"),
        )
        addonOptions + collectionOptions + traktOptions
    }

    /**
     * All installed catalogs flattened into hero-picker choices. The key
     * format matches `heroCatalogSelectionsForScope` reads ("<addonId>_<type>_<catalogId>")
     * so the multi-picker can compare against [NewLayoutUiState.heroCatalogKeys].
     */
    private val heroCatalogChoicesFlow: Flow<List<HeroCatalogChoice>> =
        installedAddonsFlow.map { list ->
            list.flatMap { addon ->
                addon.catalogs
                    .filter { catalog ->
                        !catalog.extra.any { it.name.equals("search", ignoreCase = true) && it.isRequired }
                    }
                    .map { catalog ->
                        HeroCatalogChoice(
                            key = "${addon.id}_${catalog.apiType}_${catalog.id}",
                            name = catalog.name,
                            addonName = addon.displayName,
                        )
                    }
            }
        }

    private val coreState: Flow<CoreLayoutState> = combine(
        combine(_selectedScope, layoutFlow) { s, l -> s to l },
        combine(showHeroSectionFlow, heroCatalogKeysFlow, focusItemGradientFlow) { sh, hk, fg ->
            Triple(sh, hk, fg)
        },
    ) { scopeAndLayout, secondThree ->
        CoreLayoutState(
            scope = scopeAndLayout.first,
            layout = scopeAndLayout.second,
            showHeroSection = secondThree.first,
            heroCatalogKeys = secondThree.second,
            focusItemGradient = secondThree.third,
        )
    }

    private val rowsAndSources: Flow<RowsAndSources> = combine(
        rowsFlow, sourcesFlow, collectionsDataStore.collections, heroCatalogChoicesFlow,
    ) { r, s, c, h -> RowsAndSources(r, s, c, h) }

    val uiState: StateFlow<NewLayoutUiState> = combine(
        coreState,
        rowsAndSources,
        layoutPreferenceDataStore.modernLandscapePostersEnabled,
        followAddonsOrderFlow,
        expandBackdropEnabledFlow,
    ) { core, rs, landscape, followAddons, expandBackdrop ->
        NewLayoutUiState(
            selectedScope = core.scope,
            layout = core.layout,
            showHeroSection = core.showHeroSection,
            heroCatalogKeys = core.heroCatalogKeys,
            availableHeroCatalogs = rs.heroCatalogs,
            focusItemGradient = core.focusItemGradient,
            rows = rs.rows,
            availableSources = rs.sources,
            availableCollections = rs.collections,
            landscapePostersDefault = landscape,
            followAddonsOrder = followAddons,
            expandBackdropEnabled = expandBackdrop,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewLayoutUiState())

    fun selectScope(scope: LayoutScreenScope) {
        _selectedScope.value = scope
    }

    fun setLayout(layout: HomeLayout) = viewModelScope.launch {
        layoutPreferenceDataStore.setSelectedLayoutForScope(_selectedScope.value, layout)
    }

    fun setShowHeroSection(enabled: Boolean) = viewModelScope.launch {
        layoutPreferenceDataStore.setHeroSectionEnabledForScope(_selectedScope.value, enabled)
    }

    fun toggleHeroCatalog(catalogKey: String) = viewModelScope.launch {
        val scope = _selectedScope.value
        val current = layoutPreferenceDataStore.heroCatalogSelectionsForScope(scope).first().toMutableList()
        if (catalogKey in current) current.remove(catalogKey) else current.add(catalogKey)
        layoutPreferenceDataStore.setHeroCatalogKeysForScope(scope, current)
    }

    fun setFocusItemGradient(enabled: Boolean) = viewModelScope.launch {
        layoutPreferenceDataStore.setClassicFocusGradientEnabledForScope(_selectedScope.value, enabled)
    }

    /**
     * Global default card orientation for rows. Per-row [LayoutCardStyle]
     * overrides this floor.
     */
    fun setLandscapePostersDefault(enabled: Boolean) = viewModelScope.launch {
        layoutPreferenceDataStore.setModernLandscapePostersEnabled(enabled)
    }

    /**
     * Per-scope toggle. Flipping ON auto-populates the row list from
     * installed addon catalogs in manifest order (overrides whatever the
     * user had manually arranged). Flipping OFF leaves the current rows
     * in place so the user can keep editing.
     */
    fun setFollowAddonsOrder(enabled: Boolean) = viewModelScope.launch {
        val scope = _selectedScope.value
        layoutPreferenceDataStore.setFollowAddonsOrderForScope(scope, enabled)
        if (enabled) {
            populateAddonRowsForScope(scope)
        }
    }

    /**
     * Replace the active scope's rows with every installed addon's
     * catalogs (manifest order). Exposed as the "Auto-populate from
     * addon" button — only used when the toggle is OFF, since flipping
     * ON already triggers this.
     */
    fun autoPopulateFromAddons() = viewModelScope.launch {
        populateAddonRowsForScope(_selectedScope.value)
    }

    private suspend fun populateAddonRowsForScope(scope: LayoutScreenScope) {
        val addons = installedAddonsFlow.first()
        val addonRows = addons.flatMap { addon ->
            addon.catalogs
                .filter { catalog ->
                    !catalog.extra.any { it.name.equals("search", ignoreCase = true) && it.isRequired }
                }
                .filter { catalog ->
                    when (scope) {
                        LayoutScreenScope.MOVIES -> catalog.apiType.equals("movie", ignoreCase = true)
                        LayoutScreenScope.TV -> catalog.apiType.equals("series", ignoreCase = true)
                        else -> true
                    }
                }
                .map { catalog ->
                    LayoutRowConfig(
                        id = "addon|${addon.id}|${catalog.apiType}|${catalog.id}",
                        kind = LayoutRowKind.ADDON,
                        name = catalog.name,
                        viewContext = scope,
                    )
                }
        }
        layoutPreferenceDataStore.setRowsForScope(scope, addonRows)
    }

    /** Append [row] to the active scope; stamps [viewContext]. */
    fun addRow(row: LayoutRowConfig) {
        viewModelScope.launch {
            val scope = _selectedScope.value
            val current = layoutPreferenceDataStore.rowsForScope(scope).first()
            if (current.any { it.id == row.id }) return@launch
            val stamped = row.copy(viewContext = scope)
            layoutPreferenceDataStore.setRowsForScope(scope, current + stamped)
        }
    }

    /** Legacy single-dialog "Add Row" entry point — delegates to [addRow]. */
    fun addRow(source: CatalogSourceOption, defaultWidthDp: Int = 126) {
        addRow(
            LayoutRowConfig(
                id = source.id,
                kind = source.kind,
                name = source.name,
                cardWidthDp = defaultWidthDp,
            )
        )
    }

    fun addTmdbDiscoverRow(
        mediaType: String,
        sortBy: String,
        genre: String? = null,
        year: String? = null,
        displayName: String,
    ) {
        val id = LayoutRowKey.forTmdbDiscover(mediaType, sortBy, genre, year)
        val metadata = buildMap {
            put("media_type", mediaType)
            put("sort_by", sortBy)
            if (!genre.isNullOrBlank()) put("with_genres", genre)
            if (!year.isNullOrBlank()) put("year", year)
        }
        addRow(
            LayoutRowConfig(
                id = id,
                kind = LayoutRowKind.TMDB_DISCOVER,
                name = displayName,
                metadata = metadata,
            )
        )
    }

    fun addTmdbNetworkRow(networkId: Int, mediaType: String, displayName: String) {
        val id = LayoutRowKey.forTmdbNetwork(networkId, mediaType)
        addRow(
            LayoutRowConfig(
                id = id,
                kind = LayoutRowKind.TMDB_NETWORK,
                name = displayName,
                metadata = mapOf(
                    "network_id" to networkId.toString(),
                    "media_type" to mediaType,
                ),
            )
        )
    }

    fun addCollectionFolderRow(collectionId: String, folderId: String, displayName: String) {
        val id = LayoutRowKey.forCollectionFolder(collectionId, folderId)
        addRow(
            LayoutRowConfig(
                id = id,
                kind = LayoutRowKind.COLLECTION,
                name = displayName,
                metadata = mapOf(
                    "collection_id" to collectionId,
                    "folder_id" to folderId,
                ),
            )
        )
    }

    /**
     * Add a Continue-Watching-derived row (Series / Movies / Up Next). Default
     * Orient = Card, Size = Medium. Deduped by id, so max one of each variant.
     */
    fun addContinueWatchingRow(filter: ContinueWatchingFilter) {
        val (id, kind, name) = when (filter) {
            ContinueWatchingFilter.SERIES ->
                Triple(LayoutRowKey.forContinueWatchingSeries(), LayoutRowKind.CONTINUE_WATCHING_SERIES, "Continue Watching")
            ContinueWatchingFilter.MOVIES ->
                Triple(LayoutRowKey.forContinueWatchingMovies(), LayoutRowKind.CONTINUE_WATCHING_MOVIES, "Continue Watching Movies")
            ContinueWatchingFilter.UP_NEXT ->
                Triple(LayoutRowKey.forTraktUpNext(), LayoutRowKind.TRAKT_UP_NEXT, "Up Next")
            ContinueWatchingFilter.BOTH ->
                Triple(LayoutRowKey.forContinueWatchingBoth(), LayoutRowKind.CONTINUE_WATCHING, "Continue Watching")
        }
        addRow(
            LayoutRowConfig(
                id = id,
                kind = kind,
                name = name,
                cardWidthDp = CW_DEFAULT_CARD_WIDTH_DP,
                metadata = mapOf(CW_STYLE_METADATA_KEY to ContinueWatchingCardStyle.CARD.name),
            )
        )
    }

    /** Continue Watching orientation (Poster / Card / Wide) — CW-specific. */
    fun setContinueWatchingStyle(rowId: String, style: ContinueWatchingCardStyle) = mutateRows { rows ->
        rows.map { row ->
            if (row.id == rowId) {
                row.copy(metadata = row.metadata + (CW_STYLE_METADATA_KEY to style.name))
            } else {
                row
            }
        }
    }

    fun removeRow(rowId: String) = mutateRows { it.filterNot { row -> row.id == rowId } }

    fun clearAllRows() = mutateRows { emptyList() }

    /**
     * Append many rows to the active scope in a single write (dedup by id),
     * stamping each with the scope. Used by the source pickers' "Populate All".
     * One write avoids the read-modify-write race that looping [addRow] hits.
     */
    fun addRows(rows: List<LayoutRowConfig>) {
        viewModelScope.launch {
            val scope = _selectedScope.value
            val current = layoutPreferenceDataStore.rowsForScope(scope).first()
            val existing = current.map { it.id }.toSet()
            val stamped = rows
                .filter { it.id !in existing }
                .map { it.copy(viewContext = scope) }
            if (stamped.isNotEmpty()) {
                layoutPreferenceDataStore.setRowsForScope(scope, current + stamped)
            }
        }
    }

    /**
     * Delete only the rows of the given [kinds] from the active scope — the
     * pickers' per-source "Delete All". Rows of every other kind are untouched.
     */
    fun deleteRowsOfKinds(kinds: Set<LayoutRowKind>) = mutateRows { rows ->
        rows.filterNot { it.kind in kinds }
    }

    /**
     * Re-sort the active scope's rows to match the installed addons' catalog
     * manifest order (the "Order" global action). Addon rows come first in
     * manifest order; non-addon rows (Collection / Trakt / TMDB / CW) keep
     * their relative order after them.
     */
    fun resortToAddonOrder() {
        viewModelScope.launch {
            val scope = _selectedScope.value
            val current = layoutPreferenceDataStore.rowsForScope(scope).first()
            if (current.isEmpty()) return@launch
            val manifestIndex = HashMap<String, Int>()
            var i = 0
            installedAddonsFlow.first().forEach { addon ->
                addon.catalogs.forEach { catalog ->
                    manifestIndex["addon|${addon.id}|${catalog.apiType}|${catalog.id}"] = i++
                }
            }
            val sorted = current.sortedWith(
                compareBy(
                    { if (manifestIndex.containsKey(it.id)) 0 else 1 },
                    { manifestIndex[it.id] ?: Int.MAX_VALUE },
                ),
            )
            if (sorted != current) {
                layoutPreferenceDataStore.setRowsForScope(scope, sorted)
            }
        }
    }

    fun moveRow(rowId: String, direction: Int) = mutateRows { rows ->
        val idx = rows.indexOfFirst { it.id == rowId }
        if (idx < 0) return@mutateRows rows
        val target = (idx + direction).coerceIn(0, rows.size - 1)
        if (target == idx) return@mutateRows rows
        rows.toMutableList().apply {
            val item = removeAt(idx)
            add(target, item)
        }
    }

    fun toggleRowEnabled(rowId: String) = mutateRows { rows ->
        rows.map { if (it.id == rowId) it.copy(enabled = !it.enabled) else it }
    }

    fun setRowCardStyle(rowId: String, style: LayoutCardStyle) = mutateRows { rows ->
        rows.map { if (it.id == rowId) it.copy(cardStyle = style) else it }
    }

    fun setRowCardWidth(rowId: String, widthDp: Int) = mutateRows { rows ->
        rows.map { if (it.id == rowId) it.copy(cardWidthDp = widthDp) else it }
    }

    /**
     * Per-row expand override. [enabled] null follows the per-scope global,
     * true forces always-expand, false forces never-expand.
     */
    fun setRowExpandEnabled(rowId: String, enabled: Boolean?) = mutateRows { rows ->
        rows.map { if (it.id == rowId) it.copy(expandEnabled = enabled) else it }
    }

    /** Per-scope global expand-to-backdrop toggle (the floor of the hierarchy). */
    fun setExpandBackdropEnabled(enabled: Boolean) = viewModelScope.launch {
        layoutPreferenceDataStore.setFocusedPosterBackdropExpandEnabledForScope(
            _selectedScope.value, enabled,
        )
    }

    // ── Global "apply to every row in scope" toolbar actions ────────────────

    /** Apply [style] to every row in the active scope (global Orientation button). */
    fun setAllRowsCardStyle(style: LayoutCardStyle) = mutateRows { rows ->
        rows.map { it.copy(cardStyle = style) }
    }

    /** Apply [widthDp] to every row in the active scope (global Size button). */
    fun setAllRowsCardWidth(widthDp: Int) = mutateRows { rows ->
        rows.map { it.copy(cardWidthDp = widthDp) }
    }

    /** Enable or disable every row in the active scope (Enable All / Disable All). */
    fun setAllRowsEnabled(enabled: Boolean) = mutateRows { rows ->
        rows.map { it.copy(enabled = enabled) }
    }

    private fun mutateRows(transform: (List<LayoutRowConfig>) -> List<LayoutRowConfig>) {
        viewModelScope.launch {
            val scope = _selectedScope.value
            val current = layoutPreferenceDataStore.rowsForScope(scope).first()
            val next = transform(current)
            if (next != current) {
                layoutPreferenceDataStore.setRowsForScope(scope, next)
            }
        }
    }
}
