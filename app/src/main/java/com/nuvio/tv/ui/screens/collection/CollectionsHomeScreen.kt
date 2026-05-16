package com.nuvio.tv.ui.screens.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutRowKey
import com.nuvio.tv.ui.components.CollectionRowSection
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.theme.NuvioColors

/**
 * Home-style layout that renders every collection from [CollectionsDataStore] as
 * a horizontally-scrollable row of folder cards. Each row = one collection;
 * each card = one folder (using its [coverImageUrl] and title).
 *
 * Reuses [CollectionRowSection] / its `FolderCard` so the visual treatment and
 * focus behaviour match the existing home rows.
 */
@Composable
fun CollectionsHomeScreen(
    onFolderClick: (collectionId: String, folderId: String) -> Unit,
    viewModel: CollectionsHomeViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val rowConfigLookup by viewModel.rowConfigLookup.collectAsStateWithLifecycle()
    val contentFocusRequester = LocalContentFocusRequester.current
    val listState = rememberLazyListState()

    // Only show collections that actually have at least one folder — empty
    // rows would land focus on nothing and look broken.
    val visible = collections.filter { it.folders.isNotEmpty() }

    if (visible.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No collections yet",
                color = NuvioColors.TextSecondary,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(contentFocusRequester)
            .focusRestorer(),
        contentPadding = PaddingValues(top = 120.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        items(
            items = visible,
            key = { it.id },
            contentType = { "collection_row" },
        ) { collection ->
            val rowConfig = rowConfigLookup[LayoutRowKey.forCollection(collection.id)]
            CollectionRowSection(
                collection = collection,
                onFolderClick = onFolderClick,
                posterCardStyle = resolvePosterCardStyle(rowConfig),
            )
        }
    }
}

private fun resolvePosterCardStyle(config: LayoutRowConfig?): PosterCardStyle {
    val base = PosterCardDefaults.Style
    if (config == null) return base
    val width = config.cardWidthDp.dp
    val height = if (config.cardStyle == LayoutCardStyle.LANDSCAPE) {
        width * (9f / 16f)
    } else {
        width * 1.5f
    }
    return base.copy(width = width, height = height)
}
