@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutScreenScope
import com.nuvio.tv.ui.theme.NuvioColors

// ── Public entry point ───────────────────────────────────────────────────────

/**
 * Which parts of the layout/rows settings to render. The Settings hub embeds
 * the same composable in two places — under "Appearance > Layout" (picker +
 * per-screen controls) and "Appearance > Rows" (rows list + Add Row).
 */
enum class NewLayoutContentMode { ALL, LAYOUT_ONLY, ROWS_ONLY }

@Composable
fun NewLayoutSettingsContent(
    initialFocusRequester: FocusRequester? = null,
    viewModel: NewLayoutSettingsViewModel = hiltViewModel(),
    mode: NewLayoutContentMode = NewLayoutContentMode.ALL,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Four scoped pickers replace the single "Add Row" entry point on
    // HOME / MOVIES / TV. Only one is visible at a time.
    var showCatalogPicker by remember { mutableStateOf(false) }
    var showTmdbPicker by remember { mutableStateOf(false) }
    var showTraktPicker by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }

    val showLayout = mode != NewLayoutContentMode.ROWS_ONLY
    // Detail Page is no longer a scope pill — it now lives as its own
    // standalone Appearance sub-item (Task 6). If a user had it selected
    // from a prior session, snap back to Home so the panel doesn't render
    // blank.
    androidx.compose.runtime.LaunchedEffect(uiState.selectedScope) {
        if (uiState.selectedScope == LayoutScreenScope.DETAIL) {
            viewModel.selectScope(LayoutScreenScope.HOME)
        }
    }
    val isCollectionsScope = uiState.selectedScope == LayoutScreenScope.COLLECTIONS
    val showRows = mode != NewLayoutContentMode.LAYOUT_ONLY
    // The 4-button picker bar only makes sense for the content surfaces.
    val showScopedAddButtons = showRows && !isCollectionsScope

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "scope_pills") {
            ScopePills(
                selected = uiState.selectedScope,
                onSelect = viewModel::selectScope,
                firstPillFocusRequester = initialFocusRequester,
                // Detail Page is no longer a scope — it's a standalone
                // Appearance sub-item (Task 6).
                showDetailPage = false,
            )
        }
        if (showLayout) {
            item(key = "layout_section") {
                LayoutSection(
                    selected = uiState.layout,
                    onSelect = viewModel::setLayout,
                )
            }
            // Per-layout settings: each layout type has its own short list
            // of toggles. Everything else (poster labels, hide unreleased,
            // focused-poster expand/delay/mute, card width / corner radius)
            // lives under Settings → Appearance → Global and the Rows tab.
            item(key = "per_layout_settings") {
                when (uiState.layout) {
                    HomeLayout.MODERN -> ModernLayoutSettings(
                        fullscreenHero = uiState.fullscreenHero,
                        onFullscreenHeroChange = viewModel::setFullscreenHero,
                    )
                    HomeLayout.GRID -> GridLayoutSettings(
                        showHero = uiState.showHeroSection,
                        onShowHeroChange = viewModel::setShowHeroSection,
                        heroCatalogKeys = uiState.heroCatalogKeys.toSet(),
                        availableHeroCatalogs = uiState.availableHeroCatalogs,
                        onToggleHeroCatalog = viewModel::toggleHeroCatalog,
                    )
                    HomeLayout.CLASSIC -> ClassicLayoutSettings(
                        focusItemGradient = uiState.focusItemGradient,
                        onFocusItemGradientChange = viewModel::setFocusItemGradient,
                        showHero = uiState.showHeroSection,
                        onShowHeroChange = viewModel::setShowHeroSection,
                        heroCatalogKeys = uiState.heroCatalogKeys.toSet(),
                        availableHeroCatalogs = uiState.availableHeroCatalogs,
                        onToggleHeroCatalog = viewModel::toggleHeroCatalog,
                    )
                    HomeLayout.SPOTLIGHT -> SpotlightLayoutSettings(
                        showHeroCarousel = uiState.showHeroSection,
                        onShowHeroCarouselChange = viewModel::setShowHeroSection,
                        heroCatalogKeys = uiState.heroCatalogKeys.toSet(),
                        availableHeroCatalogs = uiState.availableHeroCatalogs,
                        onToggleHeroCatalog = viewModel::toggleHeroCatalog,
                    )
                }
            }
        }
        if (showRows) {
            if (showLayout) {
                item(key = "rows_header") {
                    Text(
                        text = "Rows",
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item(key = "card_orientation_row") {
                CardOrientationToggle(
                    landscape = uiState.landscapePostersDefault,
                    onChange = viewModel::setLandscapePostersDefault,
                )
            }
            if (showScopedAddButtons) {
                // "Follow addons order" lives at the top of the Rows
                // section per Task 4 — flipping ON auto-populates from
                // installed addons in manifest order; an explicit
                // Auto-populate button is also offered when OFF so the
                // user can refresh without losing the manual flag.
                item(key = "follow_addons_order_section") {
                    FollowAddonsOrderSection(
                        checked = uiState.followAddonsOrder,
                        onCheckedChange = viewModel::setFollowAddonsOrder,
                        onAutoPopulate = viewModel::autoPopulateFromAddons,
                        hasRows = uiState.rows.isNotEmpty(),
                        onClearAll = viewModel::clearAllRows,
                    )
                }
            }
            if (uiState.rows.isEmpty()) {
                item(key = "rows_empty") {
                    Text(
                        text = "No rows yet — pick a source below to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioColors.TextSecondary,
                    )
                }
            } else {
                items(items = uiState.rows, key = { it.id }) { row ->
                    RowItem(
                        row = row,
                        canMoveUp = uiState.rows.first() != row,
                        canMoveDown = uiState.rows.last() != row,
                        onMoveUp = { viewModel.moveRow(row.id, -1) },
                        onMoveDown = { viewModel.moveRow(row.id, +1) },
                        onToggleStyle = {
                            val next = if (row.cardStyle == LayoutCardStyle.POSTER) {
                                LayoutCardStyle.LANDSCAPE
                            } else LayoutCardStyle.POSTER
                            viewModel.setRowCardStyle(row.id, next)
                        },
                        onWidthChange = { viewModel.setRowCardWidth(row.id, it) },
                        onToggleEnabled = { viewModel.toggleRowEnabled(row.id) },
                        onRemove = { viewModel.removeRow(row.id) },
                    )
                }
            }
            if (showScopedAddButtons) {
                item(key = "add_row_buttons") {
                    AddRowButtonBar(
                        onAddCatalog = { showCatalogPicker = true },
                        onAddTmdb = { showTmdbPicker = true },
                        onAddTrakt = { showTraktPicker = true },
                        onAddCollection = { showCollectionPicker = true },
                        onAddContinueWatching = { viewModel.addContinueWatchingRow() },
                        continueWatchingAlreadyAdded = uiState.rows.any {
                            it.kind == com.nuvio.tv.domain.model.LayoutRowKind.CONTINUE_WATCHING
                        },
                    )
                }
            }
        }
    }

    val existingRowIds = remember(uiState.rows) { uiState.rows.map { it.id }.toSet() }

    if (showCatalogPicker) {
        CatalogPickerDialog(
            sources = uiState.availableSources,
            existingRowIds = existingRowIds,
            onSelect = { source -> viewModel.addRow(source) },
            onDismiss = { showCatalogPicker = false },
            scope = uiState.selectedScope,
        )
    }
    if (showTmdbPicker) {
        TmdbSourcePickerDialog(
            existingRowIds = existingRowIds,
            onAddDiscover = { mediaType, sortBy, genre, year, name ->
                viewModel.addTmdbDiscoverRow(mediaType, sortBy, genre, year, name)
            },
            onAddNetwork = { id, mediaType, name ->
                viewModel.addTmdbNetworkRow(id, mediaType, name)
            },
            onDismiss = { showTmdbPicker = false },
        )
    }
    if (showTraktPicker) {
        TraktPickerDialog(
            sources = uiState.availableSources,
            existingRowIds = existingRowIds,
            onSelect = { source -> viewModel.addRow(source) },
            onDismiss = { showTraktPicker = false },
        )
    }
    if (showCollectionPicker) {
        CollectionPickerDialog(
            collections = uiState.availableCollections,
            existingRowIds = existingRowIds,
            onSelectFolder = { collectionId, folderId, name ->
                viewModel.addCollectionFolderRow(collectionId, folderId, name)
            },
            onDismiss = { showCollectionPicker = false },
        )
    }
}

// ── Scope pills ──────────────────────────────────────────────────────────────

@Composable
private fun ScopePills(
    selected: LayoutScreenScope,
    onSelect: (LayoutScreenScope) -> Unit,
    firstPillFocusRequester: FocusRequester?,
    showDetailPage: Boolean = true,
) {
    val scopes = remember(showDetailPage) {
        if (showDetailPage) {
            LayoutScreenScope.entries.toList()
        } else {
            LayoutScreenScope.entries.filter { it != LayoutScreenScope.DETAIL }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        scopes.forEachIndexed { index, scope ->
            ScopePill(
                label = scope.displayName,
                isSelected = scope == selected,
                onClick = { onSelect(scope) },
                focusRequester = if (index == 0) firstPillFocusRequester else null,
            )
        }
    }
}

@Composable
private fun ScopePill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = when {
            isSelected -> NuvioColors.TextPrimary
            isFocused  -> Color.White.copy(alpha = 0.16f)
            else       -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "scopePillBg",
    )
    val textColor = if (isSelected) NuvioColors.Background else NuvioColors.TextPrimary
    Card(
        onClick = onClick,
        modifier = Modifier
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
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
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ── Layout picker section ────────────────────────────────────────────────────

@Composable
private fun LayoutSection(
    selected: HomeLayout,
    onSelect: (HomeLayout) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Layout",
            style = MaterialTheme.typography.titleSmall,
            color = NuvioColors.TextSecondary,
        )
        // 4 layouts side by side — width tightened from 180dp → 156dp
        // so Classic / Grid / Modern / Spotlight all fit inside the
        // settings right pane without horizontal scroll.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeLayout.entries.forEach { layout ->
                LayoutCard(
                    layout = layout,
                    isSelected = layout == selected,
                    showLivePreview = true,
                    onClick = { onSelect(layout) },
                    onFocused = {},
                    modifier = Modifier.width(156.dp),
                )
            }
        }
    }
}

// ── Row width presets (per-row "Width" dropdown) ────────────────────────────

private val CardWidthOptions = listOf(
    "Compact"  to 104,
    "Dense"    to 112,
    "Standard" to 120,
    "Balanced" to 126,
    "Comfort"  to 134,
    "Large"    to 140,
)

// ── Per-layout settings ──────────────────────────────────────────────────────
//
// Each layout type only renders its own short toggle list. The Hero Catalogs
// multi-picker is shared between Grid and Classic, gated on the Show Hero
// Section toggle being ON.

@Composable
private fun ModernLayoutSettings(
    fullscreenHero: Boolean,
    onFullscreenHeroChange: (Boolean) -> Unit,
) {
    LayoutSettingsToggleRow(
        title = "Fullscreen Hero Backdrop",
        subtitle = "Hero artwork expands edge-to-edge behind the entire screen, with rows fading over it.",
        checked = fullscreenHero,
        onCheckedChange = onFullscreenHeroChange,
    )
}

@Composable
private fun GridLayoutSettings(
    showHero: Boolean,
    onShowHeroChange: (Boolean) -> Unit,
    heroCatalogKeys: Set<String>,
    availableHeroCatalogs: List<HeroCatalogChoice>,
    onToggleHeroCatalog: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LayoutSettingsToggleRow(
            title = "Show Hero Section",
            subtitle = "Display a hero carousel at the top of the screen.",
            checked = showHero,
            onCheckedChange = onShowHeroChange,
        )
        if (showHero) {
            HeroCatalogsPicker(
                selectedKeys = heroCatalogKeys,
                catalogs = availableHeroCatalogs,
                onToggle = onToggleHeroCatalog,
            )
        }
    }
}

@Composable
private fun SpotlightLayoutSettings(
    showHeroCarousel: Boolean,
    onShowHeroCarouselChange: (Boolean) -> Unit,
    heroCatalogKeys: Set<String>,
    availableHeroCatalogs: List<HeroCatalogChoice>,
    onToggleHeroCatalog: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LayoutSettingsToggleRow(
            title = "Show Hero Carousel",
            subtitle = "When on, the mapped hero catalogs become the default view at launch. " +
                "When off, the first row is focused immediately.",
            checked = showHeroCarousel,
            onCheckedChange = onShowHeroCarouselChange,
        )
        if (showHeroCarousel) {
            HeroCatalogsPicker(
                selectedKeys = heroCatalogKeys,
                catalogs = availableHeroCatalogs,
                onToggle = onToggleHeroCatalog,
            )
        }
    }
}

@Composable
private fun ClassicLayoutSettings(
    focusItemGradient: Boolean,
    onFocusItemGradientChange: (Boolean) -> Unit,
    showHero: Boolean,
    onShowHeroChange: (Boolean) -> Unit,
    heroCatalogKeys: Set<String>,
    availableHeroCatalogs: List<HeroCatalogChoice>,
    onToggleHeroCatalog: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LayoutSettingsToggleRow(
            title = "Focus Item Gradient",
            subtitle = "Blend the focused item's artwork colors into the right side of the screen.",
            checked = focusItemGradient,
            onCheckedChange = onFocusItemGradientChange,
        )
        LayoutSettingsToggleRow(
            title = "Show Hero Section",
            subtitle = "Display a hero carousel at the top of the screen.",
            checked = showHero,
            onCheckedChange = onShowHeroChange,
        )
        if (showHero) {
            HeroCatalogsPicker(
                selectedKeys = heroCatalogKeys,
                catalogs = availableHeroCatalogs,
                onToggle = onToggleHeroCatalog,
            )
        }
    }
}

@Composable
private fun LayoutSettingsToggleRow(
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
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = NuvioColors.TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = NuvioColors.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HeroCatalogsPicker(
    selectedKeys: Set<String>,
    catalogs: List<HeroCatalogChoice>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Hero Catalogs",
            style = MaterialTheme.typography.titleSmall,
            color = NuvioColors.TextSecondary,
        )
        Text(
            text = "Pick which catalogs feed the hero carousel. Selecting none falls back to the first row.",
            style = MaterialTheme.typography.bodySmall,
            color = NuvioColors.TextSecondary,
        )
        if (catalogs.isEmpty()) {
            Text(
                text = "No catalogs available — install an addon to enable the hero.",
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioColors.TextSecondary,
            )
        } else {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                items(items = catalogs, key = { it.key }) { catalog ->
                    ChoicePill(
                        label = catalog.name,
                        isSelected = catalog.key in selectedKeys,
                        onClick = { onToggle(catalog.key) },
                    )
                }
            }
        }
    }
}

// ── Row item ─────────────────────────────────────────────────────────────────

@Composable
private fun RowItem(
    row: LayoutRowConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleStyle: () -> Unit,
    onWidthChange: (Int) -> Unit,
    onToggleEnabled: () -> Unit,
    onRemove: () -> Unit,
) {
    // Non-focusable container so D-pad lands directly on the inner controls
    // (a `Card(onClick = {})` wrapper would consume focus and break per-button
    // navigation).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NuvioColors.BackgroundCard),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.enabled) NuvioColors.TextPrimary else NuvioColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconChipButton(
                icon = Icons.Default.ArrowUpward,
                contentDesc = "Move up",
                enabled = canMoveUp,
                onClick = onMoveUp,
            )
            IconChipButton(
                icon = Icons.Default.ArrowDownward,
                contentDesc = "Move down",
                enabled = canMoveDown,
                onClick = onMoveDown,
            )
            ToggleStylePill(
                style = row.cardStyle,
                onClick = onToggleStyle,
            )
            LabeledDropdown(
                label = "Width",
                options = CardWidthOptions,
                selectedValue = row.cardWidthDp,
                onValueChange = onWidthChange,
                compact = true,
            )
            Switch(
                checked = row.enabled,
                onCheckedChange = { onToggleEnabled() },
            )
            IconChipButton(
                icon = Icons.Default.Close,
                contentDesc = "Remove row",
                enabled = true,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun ToggleStylePill(
    style: LayoutCardStyle,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(16.dp),
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = if (style == LayoutCardStyle.POSTER) "Poster" else "Landscape",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NuvioColors.TextPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun IconChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val containerColor = if (!enabled) {
        Color.White.copy(alpha = 0.04f)
    } else if (isFocused) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    val tint = if (enabled) NuvioColors.TextPrimary else NuvioColors.TextSecondary.copy(alpha = 0.4f)
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .size(36.dp)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(CircleShape),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                shape = CircleShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Labeled dropdown (popup) ─────────────────────────────────────────────────

@Composable
private fun LabeledDropdown(
    label: String,
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    compact: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.second == selectedValue }?.first ?: "Custom"

    Box {
        Card(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .widthIn(min = if (compact) 100.dp else 160.dp)
                .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
            shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
            colors = CardDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White.copy(alpha = 0.18f),
            ),
            border = CardDefaults.border(
                border = Border.None,
                focusedBorder = Border(
                    border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                    shape = RoundedCornerShape(16.dp),
                ),
            ),
            scale = CardDefaults.scale(focusedScale = 1f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!compact) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioColors.TextSecondary,
                    )
                }
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = NuvioColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            DropdownPopup(
                options = options,
                selectedValue = selectedValue,
                onSelect = {
                    onValueChange(it)
                    expanded = false
                },
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun DropdownPopup(
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BackHandler { onDismiss() }
        Column(
            modifier = Modifier
                .widthIn(min = 160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101418).copy(alpha = 0.96f))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { (label, value) ->
                DropdownOptionItem(
                    label = label,
                    selected = value == selectedValue,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun DropdownOptionItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.16f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(8.dp),
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NuvioColors.FocusRing,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Spacer(modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioColors.TextPrimary,
            )
        }
    }
}

// ── Follow addons order toggle + Auto-populate (per-scope) ─────────────────

@Composable
private fun FollowAddonsOrderSection(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onAutoPopulate: () -> Unit,
    hasRows: Boolean = false,
    onClearAll: () -> Unit = {},
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Follow addons order",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioColors.TextPrimary,
                )
                Text(
                    text = "Rows auto-populate from the addon's catalogs in manifest " +
                        "order. Overrides manual arrangement while on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (!checked) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoPopulateButton(
                    label = "Populate All",
                    onClick = onAutoPopulate,
                    containerColor = Color.White.copy(alpha = 0.10f),
                    focusedContainerColor = Color.White.copy(alpha = 0.20f),
                )
                if (hasRows) {
                    AutoPopulateButton(
                        label = "Clear All",
                        onClick = { showClearConfirm = true },
                        containerColor = Color(0xFF5A1C1C),
                        focusedContainerColor = Color(0xFF7A2C2C),
                    )
                }
            }
        }
    }
    if (showClearConfirm) {
        com.nuvio.tv.ui.components.NuvioDialog(
            onDismiss = { showClearConfirm = false },
            title = "Remove all rows?",
            subtitle = "This will clear every row from this scope.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF7A2C2C),
                        focusedContainerColor = Color(0xFFAA3C3C),
                    ),
                ) { Text("Clear All") }
                Button(
                    onClick = { showClearConfirm = false },
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioColors.BackgroundCard,
                    ),
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun AutoPopulateButton(
    label: String = "Auto-populate from addon",
    onClick: () -> Unit,
    containerColor: Color = Color.White.copy(alpha = 0.10f),
    focusedContainerColor: Color = Color.White.copy(alpha = 0.20f),
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(38.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(19.dp)),
        colors = ButtonDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = focusedContainerColor,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(19.dp),
            ),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = NuvioColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Card Orientation (global Rows-level default) ────────────────────────────
//
// Floor of the per-row → global card-orientation hierarchy. Each row's
// `cardStyle` overrides this; a row left at default inherits from here.

@Composable
private fun CardOrientationToggle(
    landscape: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Card Orientation",
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioColors.TextPrimary,
            )
            Text(
                text = "Default for every row. Per-row card style still wins when set.",
                style = MaterialTheme.typography.bodySmall,
                color = NuvioColors.TextSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill(
                label = "Portrait",
                isSelected = !landscape,
                onClick = { if (landscape) onChange(false) },
            )
            ChoicePill(
                label = "Landscape",
                isSelected = landscape,
                onClick = { if (!landscape) onChange(true) },
            )
        }
    }
}

// ── Add Row button bar (4 scoped buttons) ───────────────────────────────────
//
// Replaces the legacy single "Add Row" button. Each compact pill opens its
// own picker dialog (Catalog / TMDB Source / Trakt List / Collection); the
// resulting row is stamped with the current scope's [viewContext].

@Composable
private fun AddRowButtonBar(
    onAddCatalog: () -> Unit,
    onAddTmdb: () -> Unit,
    onAddTrakt: () -> Unit,
    onAddCollection: () -> Unit,
    onAddContinueWatching: () -> Unit,
    continueWatchingAlreadyAdded: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AddRowChip(label = "Catalog", onClick = onAddCatalog)
        AddRowChip(label = "TMDB Source", onClick = onAddTmdb)
        AddRowChip(label = "Trakt List", onClick = onAddTrakt)
        AddRowChip(label = "Collection", onClick = onAddCollection)
        if (!continueWatchingAlreadyAdded) {
            AddRowChip(label = "Continue Watching", onClick = onAddContinueWatching)
        }
    }
}

@Composable
private fun AddRowChip(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(38.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(19.dp)),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White.copy(alpha = 0.20f),
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(19.dp),
            ),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = NuvioColors.TextPrimary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = NuvioColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Detail Page settings (standalone Appearance sub-item, Task 6) ───────────
//
// Used to render inside the Layout scope pill tabs; now its own pane
// under Settings → Appearance → Detail Page. Reuses
// [LayoutSettingsViewModel] because the four detail-page toggles already
// live on the global keys it exposes.

@Composable
fun DetailPageSettingsContent(
    viewModel: LayoutSettingsViewModel = hiltViewModel(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "detail_page_header") {
            SettingsDetailHeader(
                title = "Detail Page",
                subtitle = "Toggles that govern how an item's detail screen behaves.",
            )
        }
        item(key = "detail_page_body") {
            DetailPageSection(viewModel = viewModel)
        }
    }
}

@Composable
private fun DetailPageSection(
    viewModel: LayoutSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Detail Page",
            style = MaterialTheme.typography.titleSmall,
            color = NuvioColors.TextSecondary,
        )
        DetailPageToggleRow(
            title = "Blur unwatched episodes",
            subtitle = "Hide spoilers on unseen episode thumbnails.",
            checked = uiState.blurUnwatchedEpisodes,
            onCheckedChange = { enabled ->
                viewModel.onEvent(LayoutSettingsEvent.SetBlurUnwatchedEpisodes(enabled))
            },
        )
        DetailPageToggleRow(
            title = "Trailer button",
            subtitle = "Show a dedicated Trailer button on the detail page.",
            checked = uiState.detailPageTrailerButtonEnabled,
            onCheckedChange = { enabled ->
                viewModel.onEvent(LayoutSettingsEvent.SetDetailPageTrailerButtonEnabled(enabled))
            },
        )
        DetailPageToggleRow(
            title = "Prefer external metadata",
            subtitle = "Use addon-provided metadata over the cached source when available.",
            checked = uiState.preferExternalMetaAddonDetail,
            onCheckedChange = { enabled ->
                viewModel.onEvent(LayoutSettingsEvent.SetPreferExternalMetaAddonDetail(enabled))
            },
        )
        DetailPageToggleRow(
            title = "Full release date",
            subtitle = "Show the full date instead of only the year.",
            checked = uiState.showFullReleaseDate,
            onCheckedChange = { enabled ->
                viewModel.onEvent(LayoutSettingsEvent.SetShowFullReleaseDate(enabled))
            },
        )
    }
}

@Composable
private fun DetailPageToggleRow(
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
