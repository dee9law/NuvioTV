package com.nuvio.tv.ui.screens.foryou

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.AuthSessionNoticeDataStore
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.ContinueWatchingEnrichmentCache
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.MDBListSettingsDataStore
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.data.trailer.TrailerService
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.domain.model.CW_DEFAULT_CARD_WIDTH_DP
import com.nuvio.tv.domain.model.CW_STYLE_METADATA_KEY
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutRowKey
import com.nuvio.tv.domain.model.LayoutRowKind
import com.nuvio.tv.domain.model.LayoutScreenScope
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import com.nuvio.tv.ui.screens.home.BaseHomeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * For You screen ViewModel — a [BaseHomeViewModel] specialization scoped to
 * [LayoutScreenScope.FOR_YOU]. Rendering + the catalog/CW pipeline come from
 * the shared base; this class only changes the scope (which keys the rows
 * config) and seeds the screen's default rows on first launch.
 *
 * For You is a fixed pure-rows canvas — the FOR_YOU scope defaults to
 * Classic layout with the hero disabled (see [LayoutPreferenceDataStore]).
 */
@HiltViewModel
class ForYouViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    addonRepository: AddonRepository,
    catalogRepository: CatalogRepository,
    watchProgressRepository: WatchProgressRepository,
    libraryRepository: LibraryRepository,
    metaRepository: MetaRepository,
    collectionsDataStore: CollectionsDataStore,
    layoutPreferenceDataStore: LayoutPreferenceDataStore,
    playerSettingsDataStore: PlayerSettingsDataStore,
    tmdbSettingsDataStore: TmdbSettingsDataStore,
    mdbListSettingsDataStore: MDBListSettingsDataStore,
    traktSettingsDataStore: TraktSettingsDataStore,
    authSessionNoticeDataStore: AuthSessionNoticeDataStore,
    tmdbService: TmdbService,
    tmdbMetadataService: TmdbMetadataService,
    mdbListRepository: MDBListRepository,
    trailerService: TrailerService,
    watchedItemsPreferences: WatchedItemsPreferences,
    watchedSeriesStateHolder: WatchedSeriesStateHolder,
    cwEnrichmentCache: ContinueWatchingEnrichmentCache,
    profileManager: ProfileManager,
    private val traktAuthDataStore: TraktAuthDataStore,
) : BaseHomeViewModel(
    appContext = appContext,
    addonRepository = addonRepository,
    catalogRepository = catalogRepository,
    watchProgressRepository = watchProgressRepository,
    libraryRepository = libraryRepository,
    metaRepository = metaRepository,
    collectionsDataStore = collectionsDataStore,
    layoutPreferenceDataStore = layoutPreferenceDataStore,
    playerSettingsDataStore = playerSettingsDataStore,
    tmdbSettingsDataStore = tmdbSettingsDataStore,
    mdbListSettingsDataStore = mdbListSettingsDataStore,
    traktSettingsDataStore = traktSettingsDataStore,
    authSessionNoticeDataStore = authSessionNoticeDataStore,
    tmdbService = tmdbService,
    tmdbMetadataService = tmdbMetadataService,
    mdbListRepository = mdbListRepository,
    trailerService = trailerService,
    watchedItemsPreferences = watchedItemsPreferences,
    watchedSeriesStateHolder = watchedSeriesStateHolder,
    cwEnrichmentCache = cwEnrichmentCache,
    profileManager = profileManager,
    homeScope = LayoutScreenScope.FOR_YOU,
    emptyStateStringRes = R.string.for_you_no_rows_configured,
) {
    init {
        seedForYouRowsIfNeeded(profileManager)
    }

    /**
     * One-shot default seed for the For You scope:
     *   1. Continue Watching — Both (series + movies)
     *   2. Up Next (Trakt next-unwatched episode)
     *
     * Only seeds when Trakt is authenticated. If not authenticated we leave the
     * scope empty (and DON'T set the flag) so the user can build it manually —
     * or get the defaults later if they sign in to Trakt.
     */
    private fun seedForYouRowsIfNeeded(profileManager: ProfileManager) {
        viewModelScope.launch {
            profileManager.activeProfileReady.first { it }
            if (layoutPreferenceDataStore.forYouSeeded.first()) return@launch
            if (!traktAuthDataStore.isAuthenticated.first()) return@launch
            val existing = layoutPreferenceDataStore
                .rowsForScope(LayoutScreenScope.FOR_YOU).first()
            if (existing.isEmpty()) {
                val cwStyle = mapOf(CW_STYLE_METADATA_KEY to ContinueWatchingCardStyle.CARD.name)
                val rows = listOf(
                    LayoutRowConfig(
                        id = LayoutRowKey.forContinueWatchingBoth(),
                        kind = LayoutRowKind.CONTINUE_WATCHING,
                        name = "Continue Watching",
                        cardWidthDp = CW_DEFAULT_CARD_WIDTH_DP,
                        viewContext = LayoutScreenScope.FOR_YOU,
                        metadata = cwStyle,
                    ),
                    LayoutRowConfig(
                        id = LayoutRowKey.forTraktUpNext(),
                        kind = LayoutRowKind.TRAKT_UP_NEXT,
                        name = "Up Next",
                        cardWidthDp = CW_DEFAULT_CARD_WIDTH_DP,
                        viewContext = LayoutScreenScope.FOR_YOU,
                        metadata = cwStyle,
                    ),
                )
                layoutPreferenceDataStore.setRowsForScope(LayoutScreenScope.FOR_YOU, rows)
            }
            layoutPreferenceDataStore.setForYouSeeded(true)
        }
    }
}
