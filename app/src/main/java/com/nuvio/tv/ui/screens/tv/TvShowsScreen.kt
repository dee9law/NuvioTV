package com.nuvio.tv.ui.screens.tv

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.HomeScreen

/**
 * TV Shows screen — a thin wrapper around [HomeScreen] that supplies a
 * [TvShowsViewModel] (which scopes content to `LayoutScreenScope.TV`).
 * See [com.nuvio.tv.ui.screens.movies.MoviesScreen] for design rationale.
 */
@Composable
fun TvShowsScreen(
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit = { _, _, _ -> },
    onNavigateToFolderDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToAddonManager: () -> Unit = {},
    onNavigateToAppearanceRows: (com.nuvio.tv.domain.model.LayoutScreenScope) -> Unit = {},
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit = { item ->
        onNavigateToDetail(
            when (item) {
                is ContinueWatchingItem.InProgress -> item.progress.contentId
                is ContinueWatchingItem.NextUp -> item.info.contentId
            },
            when (item) {
                is ContinueWatchingItem.InProgress -> item.progress.contentType
                is ContinueWatchingItem.NextUp -> item.info.contentType
            },
            ""
        )
    },
    onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit = onContinueWatchingClick,
    onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit = onContinueWatchingClick,
    viewModel: TvShowsViewModel = hiltViewModel(),
) {
    HomeScreen(
        viewModel = viewModel,
        onNavigateToDetail = onNavigateToDetail,
        onContinueWatchingClick = onContinueWatchingClick,
        onContinueWatchingStartFromBeginning = onContinueWatchingStartFromBeginning,
        onContinueWatchingPlayManually = onContinueWatchingPlayManually,
        onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAll,
        onNavigateToFolderDetail = onNavigateToFolderDetail,
        onNavigateToAddonManager = onNavigateToAddonManager,
        onNavigateToAppearanceRows = onNavigateToAppearanceRows,
    )
}
