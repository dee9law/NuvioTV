package com.nuvio.tv.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutRowKind
import com.nuvio.tv.domain.model.LayoutScreenScope
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Catalog tile sourced from an installed addon. */
data class AddonCatalogTile(
    val addonId: String,
    val addonName: String,
    val addonLogoUrl: String?,
    val catalogId: String,
    val apiType: String,
    val name: String,
)

/** Catalog tile sourced from a collection's folder. */
data class CollectionFolderTile(
    val collectionId: String,
    val folderId: String,
    val name: String,
    val coverImageUrl: String?,
    val coverEmoji: String?,
)

/** Filter chip in the browse header. */
enum class BrowseSourceFilter { ALL, ADDONS, COLLECTIONS }

/** A hero strip item paired with the addonBaseUrl needed for detail nav. */
data class HeroMetaItem(
    val meta: MetaPreview,
    val addonBaseUrl: String,
)

data class MediaTypeBrowseUiState(
    val layout: HomeLayout = HomeLayout.GRID,
    val cornerRadiusDp: Int = 12,
    val fullscreenHero: Boolean = false,
    val sourceFilter: BrowseSourceFilter = BrowseSourceFilter.ALL,
    val addonTiles: List<AddonCatalogTile> = emptyList(),
    val folderTiles: List<CollectionFolderTile> = emptyList(),
    /** Loaded content rows for the configured rows under this scope. */
    val contentRows: List<CatalogRow> = emptyList(),
    /** Hero items derived from the first non-empty content row. */
    val heroItems: List<HeroMetaItem> = emptyList(),
    /** Per-row overrides keyed by canonical row id. */
    val rowConfigLookup: Map<String, LayoutRowConfig> = emptyMap(),
    /**
     * Whether the user has configured any rows for this scope in
     * Settings → Appearance → Rows. Drives the empty-state copy: when false
     * we show the "go to Settings to add rows" prompt; when true and rows are
     * still empty we show a loading state.
     */
    val hasConfiguredRows: Boolean = false,
)

/**
 * Base ViewModel for [MediaTypeBrowseScreen]. The screen renders ONLY the
 * rows the user configured under Settings → Appearance → Rows for this
 * scope — there's no auto-discovery of every addon catalog matching the media
 * type. The old auto-fetch is preserved in commented form below as a
 * `// TODO: re-enable for discovery mode later` reference.
 *
 * The addon catalog *tiles* (and collection folder tiles) used by the GRID
 * variant are unaffected; they still surface every matching catalog so users
 * can drill in via the See All flow.
 */
open class BaseMediaTypeBrowseViewModel(
    private val mediaTypeApi: String,
    private val scope: LayoutScreenScope,
    private val addonRepo: AddonRepository,
    private val collectionsDataStore: CollectionsDataStore,
    private val layoutPrefs: LayoutPreferenceDataStore,
    private val catalogRepo: CatalogRepository,
) : ViewModel() {

    private val sourceFilter = MutableStateFlow(BrowseSourceFilter.ALL)
    private val contentRowsFlow = MutableStateFlow<List<CatalogRow>>(emptyList())

    private var contentLoadJob: Job? = null

    private val addonTilesFlow = combine(
        addonRepo.getInstalledAddons(),
        sourceFilter,
    ) { addons, filter ->
        if (filter == BrowseSourceFilter.COLLECTIONS) emptyList()
        else matchingCatalogs(addons).map { (addon, catalog) ->
            AddonCatalogTile(
                addonId = addon.id,
                addonName = addon.displayName,
                addonLogoUrl = addon.logo,
                catalogId = catalog.id,
                apiType = catalog.apiType,
                name = catalog.name,
            )
        }
    }

    private val folderTilesFlow = combine(
        collectionsDataStore.collections,
        sourceFilter,
    ) { collections, filter ->
        if (filter == BrowseSourceFilter.ADDONS) emptyList()
        else collections.flatMap { c ->
            c.folders.map { folder ->
                CollectionFolderTile(
                    collectionId = c.id,
                    folderId = folder.id,
                    name = folder.title,
                    coverImageUrl = folder.coverImageUrl,
                    coverEmoji = folder.coverEmoji,
                )
            }
        }
    }

    init {
        // Drive content reloads from BOTH the installed-addon set and the
        // user's configured rows for this scope. flatMapLatest semantics on
        // `combine` aren't needed because the inner reload itself cancels its
        // prior coroutine.
        viewModelScope.launch {
            combine(
                addonRepo.getInstalledAddons(),
                layoutPrefs.rowsForScope(scope),
            ) { addons, rows -> addons to rows }
                .collect { (addons, rows) ->
                    reloadContentRows(addons, rows)
                }
        }
    }

    private fun matchingCatalogs(addons: List<Addon>): List<Pair<Addon, CatalogDescriptor>> =
        addons.flatMap { addon ->
            addon.catalogs
                .filter { it.apiType.equals(mediaTypeApi, ignoreCase = true) }
                .filter { catalog ->
                    !catalog.extra.any { it.name.equals("search", ignoreCase = true) && it.isRequired }
                }
                .map { addon to it }
        }

    /**
     * Loads the first page for each addon-kind row the user has configured for
     * this scope. Collection-kind and Trakt-kind rows are ignored for now —
     * separate fetch paths can be added without changing this signature.
     */
    private fun reloadContentRows(addons: List<Addon>, configuredRows: List<LayoutRowConfig>) {
        contentLoadJob?.cancel()
        if (configuredRows.isEmpty()) {
            contentRowsFlow.value = emptyList()
            return
        }
        val addonsById = addons.associateBy { it.id }
        contentLoadJob = viewModelScope.launch {
            val loaded = mutableListOf<CatalogRow>()
            contentRowsFlow.value = emptyList()
            for (rowConfig in configuredRows.filter { it.enabled && it.kind == LayoutRowKind.ADDON }) {
                val parsed = parseAddonRowId(rowConfig.id) ?: continue
                val addon = addonsById[parsed.addonId] ?: continue
                val catalog = addon.catalogs.firstOrNull {
                    it.id == parsed.catalogId && it.apiType.equals(parsed.apiType, ignoreCase = true)
                } ?: continue
                val result = runCatching {
                    catalogRepo.getCatalog(
                        addonBaseUrl = addon.baseUrl,
                        addonId = addon.id,
                        addonName = addon.displayName,
                        catalogId = catalog.id,
                        catalogName = catalog.name,
                        type = catalog.apiType,
                    ).first()
                }.getOrNull()
                if (result is NetworkResult.Success && result.data.items.isNotEmpty()) {
                    loaded += result.data
                    contentRowsFlow.value = loaded.toList()
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // TODO: re-enable for discovery mode later. The old auto-fetch loaded
    // EVERY addon catalog matching `mediaTypeApi` for the scope. Kept here
    // for reference until we add a "Discovery" pill to the browse header.
    //
    // @Suppress("UNUSED")
    // private fun reloadContentRowsAutoDiscovery(addons: List<Addon>) {
    //     contentLoadJob?.cancel()
    //     val matches = matchingCatalogs(addons)
    //     if (matches.isEmpty()) {
    //         contentRowsFlow.value = emptyList()
    //         return
    //     }
    //     contentLoadJob = viewModelScope.launch {
    //         val rows = mutableListOf<CatalogRow>()
    //         for ((addon, catalog) in matches) {
    //             val result = runCatching {
    //                 catalogRepo.getCatalog(
    //                     addonBaseUrl = addon.baseUrl,
    //                     addonId = addon.id,
    //                     addonName = addon.displayName,
    //                     catalogId = catalog.id,
    //                     catalogName = catalog.name,
    //                     type = catalog.apiType,
    //                 ).first()
    //             }.getOrNull()
    //             if (result is NetworkResult.Success && result.data.items.isNotEmpty()) {
    //                 rows += result.data
    //                 contentRowsFlow.value = rows.toList()
    //             }
    //         }
    //     }
    // }
    // ---------------------------------------------------------------------

    private data class ParsedAddonRowId(
        val addonId: String,
        val apiType: String,
        val catalogId: String,
    )

    private fun parseAddonRowId(rowId: String): ParsedAddonRowId? {
        // Canonical addon row key: "addon|<addonId>|<apiType>|<catalogId>".
        // (See `LayoutRowKey.forAddon`.) Addon IDs can themselves contain `|`,
        // so split with a limit and use the last two segments as type+id.
        if (!rowId.startsWith("addon|")) return null
        val parts = rowId.split("|")
        if (parts.size < 4) return null
        return ParsedAddonRowId(
            addonId = parts.subList(1, parts.size - 2).joinToString("|"),
            apiType = parts[parts.size - 2],
            catalogId = parts.last(),
        )
    }

    val uiState: StateFlow<MediaTypeBrowseUiState> = combine(
        combine(
            layoutPrefs.selectedLayoutForScope(scope),
            layoutPrefs.posterCardCornerRadiusForScope(scope),
            layoutPrefs.fullscreenHeroBackdropForScope(scope),
            layoutPrefs.rowConfigsForScope(scope),
        ) { layout, radius, hero, lookup ->
            CoreState(layout, radius, hero, lookup)
        },
        sourceFilter,
        addonTilesFlow,
        folderTilesFlow,
        combine(contentRowsFlow, layoutPrefs.rowsForScope(scope)) { rows, configured ->
            rows to configured.any { it.enabled }
        },
    ) { core, filter, addons, folders, rowsAndFlag ->
        val (rows, hasConfigured) = rowsAndFlag
        MediaTypeBrowseUiState(
            layout = core.layout,
            cornerRadiusDp = core.cornerRadiusDp,
            fullscreenHero = core.fullscreenHero,
            sourceFilter = filter,
            addonTiles = addons,
            folderTiles = folders,
            contentRows = rows,
            heroItems = rows.firstOrNull { it.items.isNotEmpty() }
                ?.let { firstRow ->
                    firstRow.items
                        .take(HERO_ITEMS_FROM_FIRST_ROW)
                        .map { HeroMetaItem(meta = it, addonBaseUrl = firstRow.addonBaseUrl) }
                }
                .orEmpty(),
            rowConfigLookup = core.rowConfigLookup,
            hasConfiguredRows = hasConfigured,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaTypeBrowseUiState())

    fun setSourceFilter(filter: BrowseSourceFilter) {
        sourceFilter.value = filter
    }

    private data class CoreState(
        val layout: HomeLayout,
        val cornerRadiusDp: Int,
        val fullscreenHero: Boolean,
        val rowConfigLookup: Map<String, LayoutRowConfig>,
    )

    private companion object {
        const val HERO_ITEMS_FROM_FIRST_ROW = 8
    }
}

// MoviesBrowseViewModel and TvShowsBrowseViewModel removed: Movies and TV
// Shows now render via [com.nuvio.tv.ui.screens.movies.MoviesScreen] /
// [com.nuvio.tv.ui.screens.tv.TvShowsScreen] which subclass the Home rendering
// pipeline. [BaseMediaTypeBrowseViewModel] is preserved here for any future
// reuse and because it is still the conceptual base for the (still-present)
// MediaTypeBrowseBody renderer.
