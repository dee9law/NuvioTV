package com.nuvio.tv.ui.screens.foryou

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.HomeScreen

/**
 * For You screen — a thin wrapper around [HomeScreen] that supplies a
 * [ForYouViewModel] (which scopes content to `LayoutScreenScope.FOR_YOU`).
 *
 * The FOR_YOU scope is forced to Classic layout with the hero disabled (see
 * [com.nuvio.tv.data.local.LayoutPreferenceDataStore.selectedLayoutForScope] /
 * `heroSectionEnabledForScope`), so the shared pipeline + HomeScreen render a
 * pure-rows canvas — no hero, no backdrop. Rows are fully user-configurable
 * via Settings → Appearance → Rows under the For You scope.
 */
@Composable
fun ForYouScreen(
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
    viewModel: ForYouViewModel = hiltViewModel(),
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
