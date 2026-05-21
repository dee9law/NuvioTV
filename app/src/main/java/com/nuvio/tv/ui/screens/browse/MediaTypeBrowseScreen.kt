@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutRowKey
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.ContentCard
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.theme.NuvioColors

/**
 * Polymorphic browse body for a single media type (movie or series).
 * Dispatches on per-screen [HomeLayout] from settings:
 *  - GRID    → grid of catalog tiles (one card per catalog source)
 *  - MODERN  → hero strip on top + vertical list of catalog content rows
 *  - CLASSIC → vertical list of catalog content rows (no hero)
 *
 * Public entry points: [com.nuvio.tv.ui.screens.browse.MoviesScreen],
 * [com.nuvio.tv.ui.screens.browse.TvShowsScreen].
 */
@Composable
internal fun MediaTypeBrowseBody(
    title: String,
    state: MediaTypeBrowseUiState,
    onSourceFilterChange: (BrowseSourceFilter) -> Unit,
    onAddonCatalogClick: (AddonCatalogTile) -> Unit,
    onFolderClick: (CollectionFolderTile) -> Unit,
    onMetaClick: (id: String, apiType: String, addonBaseUrl: String) -> Unit,
) {
    val contentFocusRequester = LocalContentFocusRequester.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 96.dp, start = 96.dp, end = 56.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = NuvioColors.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        SourceFilterRow(
            selected = state.sourceFilter,
            onSelect = onSourceFilterChange,
        )
        when (state.layout) {
            HomeLayout.GRID -> GridBody(
                state = state,
                onAddonCatalogClick = onAddonCatalogClick,
                onFolderClick = onFolderClick,
                contentFocusRequester = contentFocusRequester,
            )
            HomeLayout.MODERN -> ModernBody(
                state = state,
                onMetaClick = onMetaClick,
                contentFocusRequester = contentFocusRequester,
            )
            HomeLayout.CLASSIC -> ClassicBody(
                state = state,
                onMetaClick = onMetaClick,
                contentFocusRequester = contentFocusRequester,
            )
            // MediaTypeBrowse doesn't have a dedicated Spotlight body —
            // fall through to the Classic horizontal-row treatment for
            // browse / search-result surfaces.
            HomeLayout.SPOTLIGHT -> ClassicBody(
                state = state,
                onMetaClick = onMetaClick,
                contentFocusRequester = contentFocusRequester,
            )
        }
    }
}

// ── Source filter chips ──────────────────────────────────────────────────────

@Composable
private fun SourceFilterRow(
    selected: BrowseSourceFilter,
    onSelect: (BrowseSourceFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("All Sources",  selected == BrowseSourceFilter.ALL)        { onSelect(BrowseSourceFilter.ALL) }
        FilterPill("Addons",       selected == BrowseSourceFilter.ADDONS)     { onSelect(BrowseSourceFilter.ADDONS) }
        FilterPill("Collections",  selected == BrowseSourceFilter.COLLECTIONS){ onSelect(BrowseSourceFilter.COLLECTIONS) }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = when {
            isSelected -> NuvioColors.TextPrimary
            isFocused  -> Color.White.copy(alpha = 0.16f)
            else       -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "filterPillBg",
    )
    Card(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
        colors = CardDefaults.colors(containerColor = bg, focusedContainerColor = bg),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(20.dp),
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) NuvioColors.Background else NuvioColors.TextPrimary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ── Grid body ────────────────────────────────────────────────────────────────

@Composable
private fun GridBody(
    state: MediaTypeBrowseUiState,
    onAddonCatalogClick: (AddonCatalogTile) -> Unit,
    onFolderClick: (CollectionFolderTile) -> Unit,
    contentFocusRequester: FocusRequester,
) {
    val gridState = rememberLazyGridState()
    val cardShape = RoundedCornerShape(state.cornerRadiusDp.dp)

    val combined: List<BrowseTile> = remember(state.addonTiles, state.folderTiles) {
        state.addonTiles.map { BrowseTile.Addon(it) } +
            state.folderTiles.map { BrowseTile.Folder(it) }
    }

    if (combined.isEmpty()) {
        EmptyState(text = "No catalogs match this filter yet.")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(contentFocusRequester),
        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = combined, key = { it.key }) { tile ->
            when (tile) {
                is BrowseTile.Addon -> AddonCatalogCard(
                    tile = tile.value,
                    cardShape = cardShape,
                    onClick = { onAddonCatalogClick(tile.value) },
                )
                is BrowseTile.Folder -> CollectionFolderCard(
                    tile = tile.value,
                    cardShape = cardShape,
                    onClick = { onFolderClick(tile.value) },
                )
            }
        }
    }
}

private sealed interface BrowseTile {
    val key: String
    data class Addon(val value: AddonCatalogTile) : BrowseTile {
        override val key get() = "addon|${value.addonId}|${value.apiType}|${value.catalogId}"
    }
    data class Folder(val value: CollectionFolderTile) : BrowseTile {
        override val key get() = "folder|${value.collectionId}|${value.folderId}"
    }
}

// ── Modern body (hero strip + content rows) ─────────────────────────────────

@Composable
private fun ModernBody(
    state: MediaTypeBrowseUiState,
    onMetaClick: (id: String, apiType: String, addonBaseUrl: String) -> Unit,
    contentFocusRequester: FocusRequester,
) {
    if (state.contentRows.isEmpty()) {
        if (state.hasConfiguredRows) {
            EmptyState(text = "Loading catalogs…")
        } else {
            NoRowsConfiguredEmptyState()
        }
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(contentFocusRequester),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.heroItems.isNotEmpty()) {
            item(key = "media_type_hero") {
                MediaTypeHeroStrip(
                    items = state.heroItems,
                    onMetaClick = onMetaClick,
                    fullscreen = state.fullscreenHero,
                )
            }
        }
        items(
            items = state.contentRows,
            key = { row -> "row|${row.addonId}|${row.apiType}|${row.catalogId}" },
        ) { row ->
            MediaTypeContentRow(
                row = row,
                rowConfig = state.rowConfigLookup[
                    LayoutRowKey.forAddon(row.addonId, row.apiType, row.catalogId)
                ],
                cornerRadiusDp = state.cornerRadiusDp,
                onMetaClick = { meta ->
                    onMetaClick(meta.id, meta.apiType, row.addonBaseUrl)
                },
            )
        }
    }
}

// ── Classic body (content rows only, no hero) ───────────────────────────────

@Composable
private fun ClassicBody(
    state: MediaTypeBrowseUiState,
    onMetaClick: (id: String, apiType: String, addonBaseUrl: String) -> Unit,
    contentFocusRequester: FocusRequester,
) {
    if (state.contentRows.isEmpty()) {
        if (state.hasConfiguredRows) {
            EmptyState(text = "Loading catalogs…")
        } else {
            NoRowsConfiguredEmptyState()
        }
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(contentFocusRequester),
        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(
            items = state.contentRows,
            key = { row -> "row|${row.addonId}|${row.apiType}|${row.catalogId}" },
        ) { row ->
            MediaTypeContentRow(
                row = row,
                rowConfig = state.rowConfigLookup[
                    LayoutRowKey.forAddon(row.addonId, row.apiType, row.catalogId)
                ],
                cornerRadiusDp = state.cornerRadiusDp,
                onMetaClick = { meta ->
                    onMetaClick(meta.id, meta.apiType, row.addonBaseUrl)
                },
            )
        }
    }
}

// ── Hero strip ──────────────────────────────────────────────────────────────

@Composable
private fun MediaTypeHeroStrip(
    items: List<HeroMetaItem>,
    onMetaClick: (id: String, apiType: String, addonBaseUrl: String) -> Unit,
    fullscreen: Boolean,
) {
    val cardHeight: Dp = if (fullscreen) 340.dp else 260.dp
    val cardShape = RoundedCornerShape(16.dp)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items, key = { it.meta.id }) { hero ->
            HeroStripCard(
                item = hero.meta,
                height = cardHeight,
                cardShape = cardShape,
                onClick = { onMetaClick(hero.meta.id, hero.meta.apiType, hero.addonBaseUrl) },
            )
        }
    }
}

@Composable
private fun HeroStripCard(
    item: MetaPreview,
    height: Dp,
    cardShape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .height(height)
            .aspectRatio(16f / 9f)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(cardShape),
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.BackgroundCard,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = cardShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1.03f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val backdrop = item.background ?: item.landscapePoster ?: item.poster
            if (!backdrop.isNullOrBlank()) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cardShape),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )
            Text(
                text = item.name,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Content row (one catalog, horizontal items) ─────────────────────────────

@Composable
private fun MediaTypeContentRow(
    row: com.nuvio.tv.domain.model.CatalogRow,
    rowConfig: LayoutRowConfig?,
    cornerRadiusDp: Int,
    onMetaClick: (MetaPreview) -> Unit,
) {
    val posterStyle = remember(rowConfig, cornerRadiusDp) {
        resolveRowPosterStyle(rowConfig, cornerRadiusDp)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = row.catalogName,
            style = MaterialTheme.typography.titleMedium,
            color = NuvioColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(row.items, key = { it.id }) { meta ->
                ContentCard(
                    item = meta,
                    posterCardStyle = posterStyle,
                    onClick = { onMetaClick(meta) },
                )
            }
        }
    }
}

private fun resolveRowPosterStyle(
    config: LayoutRowConfig?,
    cornerRadiusDp: Int,
): PosterCardStyle {
    val base = PosterCardDefaults.Style.copy(cornerRadius = cornerRadiusDp.dp)
    if (config == null) return base
    val width = config.cardWidthDp.dp
    val height = if (config.cardStyle == LayoutCardStyle.LANDSCAPE) {
        width * (9f / 16f)
    } else {
        width * 1.5f
    }
    return base.copy(width = width, height = height)
}

// ── Tile cards (Grid variant) ───────────────────────────────────────────────

@Composable
private fun AddonCatalogCard(
    tile: AddonCatalogTile,
    cardShape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(cardShape),
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.BackgroundCard,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = cardShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!tile.addonLogoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = tile.addonLogoUrl,
                    contentDescription = tile.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cardShape),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = tile.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = NuvioColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                )
                Text(
                    text = tile.addonName,
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CollectionFolderCard(
    tile: CollectionFolderTile,
    cardShape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(cardShape),
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.BackgroundCard,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = cardShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!tile.coverImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = tile.coverImageUrl,
                    contentDescription = tile.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cardShape),
                    contentScale = ContentScale.Crop,
                )
            } else if (!tile.coverEmoji.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = tile.coverEmoji, fontSize = 48.sp)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                Text(
                    text = tile.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = NuvioColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioColors.TextSecondary,
        )
    }
}

@Composable
private fun NoRowsConfiguredEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.tv.material3.Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = NuvioColors.TextTertiary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "No rows added yet",
                style = MaterialTheme.typography.titleMedium,
                color = NuvioColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Go to Settings → Appearance → Rows to add content.",
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioColors.TextSecondary,
            )
        }
    }
}
