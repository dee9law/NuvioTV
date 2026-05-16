package com.nuvio.tv.ui.screens.movies

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.HomeScreen

/**
 * Movies screen — a thin wrapper around [HomeScreen] that supplies a
 * [MoviesViewModel] (which scopes content to `LayoutScreenScope.MOVIES`).
 *
 * The rendering pipeline (hero carousel, Modern/Grid/Classic layouts,
 * Continue Watching, focused-poster trailer, per-row card style/width) is
 * identical to Home — only the data source differs. Content is sourced
 * exclusively from rows configured in Settings → Appearance → Rows under the
 * MOVIES scope; if the user hasn't configured any rows the empty state
 * prompts them to do so.
 */
@Composable
fun MoviesScreen(
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
    viewModel: MoviesViewModel = hiltViewModel(),
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
