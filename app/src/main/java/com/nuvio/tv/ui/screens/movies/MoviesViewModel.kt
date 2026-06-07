package com.nuvio.tv.ui.screens.movies

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.AuthSessionNoticeDataStore
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.ContinueWatchingEnrichmentCache
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.MDBListSettingsDataStore
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.data.trailer.TrailerService
import com.nuvio.tv.domain.model.LayoutScreenScope
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import com.nuvio.tv.ui.screens.home.BaseHomeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Movies screen ViewModel — a [HomeViewModel] specialization scoped to
 * [LayoutScreenScope.MOVIES]. All rendering and pipeline behavior comes from
 * the shared base; this class only changes the per-screen scope (which keys
 * the rows config, layout, corner radius, fullscreen hero, and rowConfigLookup
 * lookups) and the empty-state copy.
 */
@HiltViewModel
class MoviesViewModel @Inject constructor(
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
    profileManager: com.nuvio.tv.core.profile.ProfileManager,
    traktHomeCatalogResolver: com.nuvio.tv.core.trakt.TraktHomeCatalogResolver,
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
    traktHomeCatalogResolver = traktHomeCatalogResolver,
    homeScope = LayoutScreenScope.MOVIES,
    emptyStateStringRes = R.string.movies_no_rows_configured,
)
