@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioColors

/**
 * Continue Watching settings extracted from `LayoutSettingsContent` for the
 * Settings hub. Reuses [LayoutSettingsViewModel] — the keys already live on
 * `LayoutPreferenceDataStore`.
 */
@Composable
fun ContinueWatchingSettingsContent(
    viewModel: LayoutSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "cw_header") {
            SettingsDetailHeader(
                title = "Continue Watching",
                subtitle = "Tune how Continue Watching shelves look and behave.",
            )
        }
        item(key = "cw_use_episode_thumbnails") {
            CwToggleRow(
                title = "Episode thumbnails",
                subtitle = "Show the episode still on Continue Watching cards.",
                checked = uiState.useEpisodeThumbnailsInCw,
                onCheckedChange = { enabled ->
                    viewModel.onEvent(LayoutSettingsEvent.SetUseEpisodeThumbnailsInCw(enabled))
                },
            )
        }
        if (uiState.useEpisodeThumbnailsInCw) {
            item(key = "cw_blur_next_up") {
                CwToggleRow(
                    title = "Blur Next Up",
                    subtitle = "Blur thumbnails for unseen Next Up episodes.",
                    checked = uiState.blurContinueWatchingNextUp,
                    onCheckedChange = { enabled ->
                        viewModel.onEvent(LayoutSettingsEvent.SetBlurContinueWatchingNextUp(enabled))
                    },
                )
            }
        }
        item(key = "cw_next_up_furthest") {
            CwToggleRow(
                title = "Next Up from furthest episode",
                subtitle = "Pick Next Up after the latest watched episode rather than the next sequential one.",
                checked = uiState.nextUpFromFurthestEpisode,
                onCheckedChange = { enabled ->
                    viewModel.onEvent(LayoutSettingsEvent.SetNextUpFromFurthestEpisode(enabled))
                },
            )
        }
        item(key = "cw_show_unaired_next_up") {
            CwToggleRow(
                title = "Show unaired Next Up",
                subtitle = "Surface upcoming episodes that haven't aired yet.",
                checked = uiState.showUnairedNextUp,
                onCheckedChange = { enabled ->
                    viewModel.onEvent(LayoutSettingsEvent.SetShowUnairedNextUp(enabled))
                },
            )
        }
    }
}

@Composable
private fun CwToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioColors.TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NuvioColors.TextSecondary,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
