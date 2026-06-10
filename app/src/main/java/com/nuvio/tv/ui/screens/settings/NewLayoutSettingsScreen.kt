@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableStateMapOf
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.CollectionSource
import com.nuvio.tv.domain.model.FOLDER_LAYOUT_METADATA_KEY
import com.nuvio.tv.domain.model.FOLDER_LAYOUT_VALUE_ROWS
import com.nuvio.tv.domain.model.FOLDER_LAYOUT_VALUE_TABS
import com.nuvio.tv.domain.model.LayoutRowKey
import com.nuvio.tv.domain.model.SRC_OFF_METADATA_PREFIX
import com.nuvio.tv.domain.model.SRC_STYLE_METADATA_PREFIX
import com.nuvio.tv.domain.model.SRC_WIDTH_METADATA_PREFIX
import com.nuvio.tv.domain.model.TmdbCollectionSource
import com.nuvio.tv.domain.model.TraktCollectionSource
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.domain.model.ContinueWatchingFilter
import com.nuvio.tv.domain.model.continueWatchingFilter
import com.nuvio.tv.domain.model.continueWatchingStyle
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutRowKind
import com.nuvio.tv.domain.model.isTraktCatalogRow
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
    onNavigateToCollectionEditor: (String) -> Unit = {},
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

    if (mode == NewLayoutContentMode.ROWS_ONLY) {
        // New Rows Manager: fixed top bar (scope pills + global actions
        // toolbar + follow-addons), a scrollable row list, and a fixed
        // bottom "+ Add" bar. The legacy ALL / LAYOUT_ONLY modes keep the
        // single-LazyColumn layout below.
        RowsManagerContent(
            uiState = uiState,
            viewModel = viewModel,
            initialFocusRequester = initialFocusRequester,
            onAddCatalog = { showCatalogPicker = true },
            onAddTmdb = { showTmdbPicker = true },
            onAddTrakt = { showTraktPicker = true },
            onAddCollection = { showCollectionPicker = true },
            onNavigateToCollectionEditor = onNavigateToCollectionEditor,
        )
    } else LazyColumn(
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
                // For You has no configurable layout; keep it out of the
                // layout picker (it's managed in the Rows Manager).
                showForYou = false,
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
                    // Modern (State 2) and Immersive (State 1) have no per-layout
                    // toggles. The old "Fullscreen Hero Backdrop" switch is gone —
                    // fullscreen is now its own standalone Immersive layout.
                    HomeLayout.MODERN, HomeLayout.IMMERSIVE -> Unit
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
                            // CW rows cycle their own orientation set (Poster /
                            // Card / Wide); all other rows cycle the generic
                            // card style (Poster / Landscape / Cinema).
                            if (row.kind.continueWatchingFilter != null) {
                                viewModel.setContinueWatchingStyle(
                                    row.id, nextContinueWatchingStyle(row.continueWatchingStyle),
                                )
                            } else {
                                viewModel.setRowCardStyle(row.id, nextCardStyle(row.cardStyle))
                            }
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
                        onAddContinueWatching = { viewModel.addContinueWatchingRow(ContinueWatchingFilter.SERIES) },
                        continueWatchingAlreadyAdded = uiState.rows.any {
                            it.kind.continueWatchingFilter == ContinueWatchingFilter.SERIES
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
            onAddRows = viewModel::addRows,
            onDeleteAll = {
                viewModel.deleteRowsOfKinds(setOf(com.nuvio.tv.domain.model.LayoutRowKind.ADDON))
            },
            followOrder = uiState.followAddonsOrder,
            onToggleFollowOrder = { viewModel.setFollowAddonsOrder(!uiState.followAddonsOrder) },
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
            onAddRows = viewModel::addRows,
            onDeleteAll = {
                viewModel.deleteRowsOfKinds(
                    setOf(
                        com.nuvio.tv.domain.model.LayoutRowKind.TMDB_DISCOVER,
                        com.nuvio.tv.domain.model.LayoutRowKind.TMDB_NETWORK,
                    ),
                )
            },
        )
    }
    if (showTraktPicker) {
        TraktPickerDialog(
            sources = uiState.availableSources,
            existingRowIds = existingRowIds,
            onSelect = { source -> viewModel.addRow(source) },
            onDismiss = { showTraktPicker = false },
            onAddRows = viewModel::addRows,
            onDeleteAll = {
                viewModel.deleteRowsOfKinds(setOf(com.nuvio.tv.domain.model.LayoutRowKind.TRAKT))
            },
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
            onAddRows = viewModel::addRows,
            onDeleteAll = {
                viewModel.deleteRowsOfKinds(setOf(com.nuvio.tv.domain.model.LayoutRowKind.COLLECTION))
            },
        )
    }
}

// ── Rows Manager (ROWS_ONLY) ────────────────────────────────────────────────
//
// Fixed top bar (scope pills + global actions toolbar + follow-addons), a
// scrollable row list in the middle, and a fixed bottom "+ Add" bar. The old
// standalone "Card Orientation" toggle is gone — orientation is now a global
// toolbar action that writes every row's cardStyle.

@Composable
private fun RowsManagerContent(
    uiState: NewLayoutUiState,
    viewModel: NewLayoutSettingsViewModel,
    initialFocusRequester: FocusRequester?,
    onAddCatalog: () -> Unit,
    onAddTmdb: () -> Unit,
    onAddTrakt: () -> Unit,
    onAddCollection: () -> Unit,
    onNavigateToCollectionEditor: (String) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isCollectionsScope = uiState.selectedScope == LayoutScreenScope.COLLECTIONS
    // Collections rows are managed elsewhere; this scope shows the list only.
    val showScopedControls = !isCollectionsScope
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    // Destructive LEVEL 2/3 accordion data deletes are confirmed first.
    var pendingFolderDelete by remember {
        mutableStateOf<Pair<Collection, CollectionFolder>?>(null)
    }
    var pendingSourceDelete by remember {
        mutableStateOf<Triple<Collection, CollectionFolder, Int>?>(null)
    }

    // Global style/size reflect the first row when rows exist, else the global
    // landscape default / Balanced. Header taps apply to every row.
    val globalStyle = uiState.rows.firstOrNull()?.cardStyle
        ?: if (uiState.landscapePostersDefault) LayoutCardStyle.LANDSCAPE else LayoutCardStyle.POSTER
    val globalWidthDp = uiState.rows.firstOrNull()?.cardWidthDp
        ?: CardWidthOptions.first { it.first == "Balanced" }.second
    val hasRows = uiState.rows.isNotEmpty()
    val allEnabled = hasRows && uiState.rows.all { it.enabled }
    // Size column hidden globally only when every row is CINEMA (single fixed size).
    val allCinema = hasRows && uiState.rows.all { it.cardStyle == LayoutCardStyle.CINEMA }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── FIXED TOP ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Row 1 — scope tabs
            ScopePills(
                selected = uiState.selectedScope,
                onSelect = viewModel::selectScope,
                firstPillFocusRequester = initialFocusRequester,
                showDetailPage = false,
            )
            if (showScopedControls) {
                val traktSignedIn by viewModel.traktSignedIn.collectAsStateWithLifecycle()
                // Row 2 — source pills (moved up from the old bottom bar)
                SourcePillsRow(
                    onAddCatalog = onAddCatalog,
                    onAddTmdb = onAddTmdb,
                    traktSignedIn = traktSignedIn,
                    upNextAdded = uiState.rows.any {
                        it.kind.continueWatchingFilter == ContinueWatchingFilter.UP_NEXT
                    },
                    onAddUpNext = { viewModel.addContinueWatchingRow(ContinueWatchingFilter.UP_NEXT) },
                    onTraktSignInRequired = {
                        Toast.makeText(context, "Sign in to Trakt first (Settings → Trakt)", Toast.LENGTH_SHORT).show()
                    },
                    onAddTraktCatalog = { viewModel.addTraktCatalogRow(it) },
                    traktCatalogExisting = uiState.rows
                        .mapNotNull { it.kind.takeIf { k -> k.isTraktCatalogRow } }
                        .toSet(),
                    onAddCollection = onAddCollection,
                    onAddCw = { viewModel.addContinueWatchingRow(it) },
                    cwExisting = uiState.rows.mapNotNull { it.kind.continueWatchingFilter }.toSet(),
                    onMdbListComingSoon = {
                        Toast.makeText(context, "MDBList rows — coming soon", Toast.LENGTH_SHORT).show()
                    },
                )
                // Row 3 — column header doubling as global per-column actions
                ColumnHeaderRow(
                    style = globalStyle,
                    widthDp = globalWidthDp,
                    allCinema = allCinema,
                    expandEnabled = uiState.expandBackdropEnabled,
                    allEnabled = allEnabled,
                    hasRows = hasRows,
                    onToggleStyle = {
                        // Cycle every row to the next style: Poster → Landscape
                        // → Cinema → Poster.
                        val next = nextCardStyle(globalStyle)
                        viewModel.setAllRowsCardStyle(next)
                        viewModel.setLandscapePostersDefault(next == LayoutCardStyle.LANDSCAPE)
                    },
                    onSelectSize = { viewModel.setAllRowsCardWidth(it) },
                    onToggleExpand = {
                        viewModel.setExpandBackdropEnabled(!uiState.expandBackdropEnabled)
                    },
                    onToggleAll = { viewModel.setAllRowsEnabled(!allEnabled) },
                    onDeleteAll = { showDeleteAllConfirm = true },
                    onResortToAddonOrder = { viewModel.resortToAddonOrder() },
                )
                // Follow Addons Order + Clear All now live inside the Catalog
                // picker only (its "Follow Order" toggle + "Delete All").
            }
        }

        // ── SCROLLABLE MIDDLE ───────────────────────────────────────────────
        val rowsListState = rememberLazyListState()
        val rowsScope = rememberCoroutineScope()
        val firstRowFr = remember { FocusRequester() }
        val lastRowFr = remember { FocusRequester() }
        // Collections render as 3-level accordion blocks: the scope's
        // collection-kind rows group by collection id into one display unit
        // anchored at the first row's position. The COLLECTIONS scope lists
        // every collection as a block (its rows attached when present).
        val collectionsById = remember(uiState.availableCollections) {
            uiState.availableCollections.associateBy { it.id }
        }
        val displayUnits = remember(uiState.rows, uiState.availableCollections, isCollectionsScope) {
            buildManagerDisplayUnits(
                rows = uiState.rows,
                collectionsById = collectionsById,
                isCollectionsScope = isCollectionsScope,
                allCollections = uiState.availableCollections,
            )
        }
        // "addon|<id>|<type>|<catalogId>" → catalog display name, for LEVEL 3.
        val addonCatalogNames = remember(uiState.availableSources) {
            uiState.availableSources
                .filter { it.kind == LayoutRowKind.ADDON }
                .associate { it.id to it.name }
        }
        // Accordion expand/collapse — UI-only, never persisted.
        val expandedBlocks = remember { mutableStateMapOf<String, Boolean>() }
        val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
        val lastRowIndex = displayUnits.lastIndex
        // Focus retention: per-index ✕ requesters + the index pending refocus
        // after a delete (so focus stays in the list, on the next row).
        val removeFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
        var pendingRemoveFocusIndex by remember { mutableStateOf<Int?>(null) }
        androidx.compose.runtime.LaunchedEffect(displayUnits.size, pendingRemoveFocusIndex) {
            val target = pendingRemoveFocusIndex ?: return@LaunchedEffect
            if (displayUnits.isEmpty()) { pendingRemoveFocusIndex = null; return@LaunchedEffect }
            val clamped = target.coerceIn(0, displayUnits.lastIndex)
            withFrameNanos { }
            runCatching { removeFocusRequesters[clamped]?.requestFocus() }
            pendingRemoveFocusIndex = null
        }
        LazyColumn(
            state = rowsListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (displayUnits.isEmpty()) {
                item(key = "rows_empty") {
                    Text(
                        text = if (showScopedControls) {
                            "No rows yet — add one from the bar above."
                        } else {
                            "No collections yet — create one under Settings → Extensions → Collections."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioColors.TextSecondary,
                    )
                }
            } else {
                itemsIndexed(items = displayUnits, key = { _, it -> it.key }) { index, unit ->
                    when (unit) {
                        is ManagerDisplayUnit.Flat -> {
                            val row = unit.row
                            ManagerRowItem(
                                row = row,
                                canMoveUp = index != 0,
                                canMoveDown = index != lastRowIndex,
                                onMoveUp = { viewModel.moveRow(row.id, -1) },
                                onMoveDown = { viewModel.moveRow(row.id, +1) },
                                onToggleStyle = {
                                    // CW rows cycle their own orientation set (Poster /
                                    // Card / Wide); all other rows cycle the generic
                                    // card style (Poster / Landscape / Cinema).
                                    if (row.kind.continueWatchingFilter != null) {
                                        viewModel.setContinueWatchingStyle(
                                            row.id, nextContinueWatchingStyle(row.continueWatchingStyle),
                                        )
                                    } else {
                                        viewModel.setRowCardStyle(row.id, nextCardStyle(row.cardStyle))
                                    }
                                },
                                onWidthChange = { viewModel.setRowCardWidth(row.id, it) },
                                onCycleExpand = {
                                    viewModel.setRowExpandEnabled(row.id, nextExpandState(row.expandEnabled))
                                },
                                onToggleEnabled = { viewModel.toggleRowEnabled(row.id) },
                                onRemove = {
                                    // Keep focus in the list: the row that shifts into
                                    // this index regains focus after the delete.
                                    pendingRemoveFocusIndex = index
                                    viewModel.removeRow(row.id)
                                },
                                removeFocusRequester = removeFocusRequesters.getOrPut(index) { FocusRequester() },
                                // First row UP escapes upward to the controls above the
                                // table (column header → source pills → scope tabs)
                                // instead of looping to the last row. null = no UP
                                // interception, so default focus search moves up out of
                                // the table.
                                onWrapPrev = null,
                                onWrapNext = if (index == lastRowIndex) {
                                    {
                                        rowsScope.launch {
                                            rowsListState.scrollToItem(0)
                                            withFrameNanos { }
                                            runCatching { firstRowFr.requestFocus() }
                                        }
                                    }
                                } else null,
                                orientationFocusRequester = when (index) {
                                    0 -> firstRowFr
                                    lastRowIndex -> lastRowFr
                                    else -> null
                                },
                            )
                        }
                        is ManagerDisplayUnit.CollectionBlock -> {
                            CollectionBlockItem(
                                unit = unit,
                                isCollectionsScope = isCollectionsScope,
                                expanded = expandedBlocks[unit.collectionId] == true,
                                onToggleExpanded = {
                                    expandedBlocks[unit.collectionId] =
                                        expandedBlocks[unit.collectionId] != true
                                },
                                expandedFolders = expandedFolders,
                                canMoveUp = index != 0,
                                canMoveDown = index != lastRowIndex,
                                onMoveBlock = { dir ->
                                    if (isCollectionsScope) {
                                        viewModel.moveCollection(unit.collectionId, dir)
                                    } else {
                                        viewModel.moveCollectionBlock(unit.collectionId, dir)
                                    }
                                },
                                onEditCollection = { onNavigateToCollectionEditor(unit.collectionId) },
                                onToggleBlockEnabled = {
                                    viewModel.setCollectionBlockEnabled(
                                        unit.collectionId, !unit.blockEnabled,
                                    )
                                },
                                onRemoveBlock = {
                                    pendingRemoveFocusIndex = index
                                    viewModel.removeCollectionBlock(unit.collectionId)
                                },
                                addonCatalogNames = addonCatalogNames,
                                viewModel = viewModel,
                                onRequestFolderDelete = { c, fo -> pendingFolderDelete = c to fo },
                                onRequestSourceDelete = { c, fo, i ->
                                    pendingSourceDelete = Triple(c, fo, i)
                                },
                                headerFocusRequester = if (index == 0) firstRowFr else null,
                            )
                        }
                    }
                }
            }
        }
        // Source pills moved to the fixed top (Row 2) — no bottom bar.
    }

    val folderDelete = pendingFolderDelete
    if (folderDelete != null) {
        com.nuvio.tv.ui.components.NuvioDialog(
            onDismiss = { pendingFolderDelete = null },
            title = "Delete folder \"${folderDelete.second.title}\"?",
            subtitle = "This removes the folder (and its catalogs) from the " +
                "\"${folderDelete.first.title}\" collection everywhere — not just this screen.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.deleteFolderFromCollection(folderDelete.first, folderDelete.second.id)
                        pendingFolderDelete = null
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF7A2C2C),
                        focusedContainerColor = Color(0xFFAA3C3C),
                    ),
                ) { Text("Delete Folder") }
                Button(
                    onClick = { pendingFolderDelete = null },
                    colors = ButtonDefaults.colors(containerColor = NuvioColors.BackgroundCard),
                ) { Text("Cancel") }
            }
        }
    }
    val sourceDelete = pendingSourceDelete
    if (sourceDelete != null) {
        com.nuvio.tv.ui.components.NuvioDialog(
            onDismiss = { pendingSourceDelete = null },
            title = "Remove this catalog?",
            subtitle = "This removes the catalog from the \"${sourceDelete.second.title}\" folder " +
                "everywhere — not just this screen.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.deleteSourceFromFolder(
                            sourceDelete.first, sourceDelete.second.id, sourceDelete.third,
                        )
                        pendingSourceDelete = null
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF7A2C2C),
                        focusedContainerColor = Color(0xFFAA3C3C),
                    ),
                ) { Text("Remove Catalog") }
                Button(
                    onClick = { pendingSourceDelete = null },
                    colors = ButtonDefaults.colors(containerColor = NuvioColors.BackgroundCard),
                ) { Text("Cancel") }
            }
        }
    }
    if (showDeleteAllConfirm) {
        com.nuvio.tv.ui.components.NuvioDialog(
            onDismiss = { showDeleteAllConfirm = false },
            title = "Delete all rows?",
            subtitle = "This removes every row from this scope.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.clearAllRows()
                        showDeleteAllConfirm = false
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF7A2C2C),
                        focusedContainerColor = Color(0xFFAA3C3C),
                    ),
                ) { Text("Delete All") }
                Button(
                    onClick = { showDeleteAllConfirm = false },
                    colors = ButtonDefaults.colors(containerColor = NuvioColors.BackgroundCard),
                ) { Text("Cancel") }
            }
        }
    }
}

// ── Row Manager column widths ───────────────────────────────────────────────
//
// Shared by [ColumnHeaderRow] and [ManagerRowItem] so the header labels/actions
// line up vertically with each row's controls (the header IS the column).

private val RowOrderColWidth = 84.dp
private val RowShapeColWidth = 42.dp
private val RowToggleColWidth = 56.dp
private val RowRemoveColWidth = 42.dp

// ── Row 2 — source pills ────────────────────────────────────────────────────

@Composable
private fun SourcePillsRow(
    onAddCatalog: () -> Unit,
    onAddTmdb: () -> Unit,
    traktSignedIn: Boolean,
    upNextAdded: Boolean,
    onAddUpNext: () -> Unit,
    onTraktSignInRequired: () -> Unit,
    onAddTraktCatalog: (LayoutRowKind) -> Unit,
    traktCatalogExisting: Set<LayoutRowKind>,
    onAddCollection: () -> Unit,
    onAddCw: (ContinueWatchingFilter) -> Unit,
    cwExisting: Set<ContinueWatchingFilter>,
    onMdbListComingSoon: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Fixed order: Catalogs | TMDB | MDBList | Trakt | Continue Watching | Collections.
        AddRowChip(label = "Catalogs", onClick = onAddCatalog)
        AddRowChip(label = "TMDB", onClick = onAddTmdb)
        // MDBList rows have no backing source yet — visible placeholder only.
        ComingSoonChip(label = "MDBList", onClick = onMdbListComingSoon)
        // "+ Trakt" opens a submenu (gated behind Trakt sign-in).
        TraktAddPill(
            signedIn = traktSignedIn,
            upNextAdded = upNextAdded,
            onAddUpNext = onAddUpNext,
            onSignInRequired = onTraktSignInRequired,
            onAddTraktCatalog = onAddTraktCatalog,
            traktCatalogExisting = traktCatalogExisting,
        )
        // "+ Continue Watching" opens a Series / Movies submenu.
        ContinueWatchingAddPill(existing = cwExisting, onAdd = onAddCw)
        AddRowChip(label = "Collections", onClick = onAddCollection)
    }
}

/**
 * "+ Trakt" pill. Gated behind Trakt sign-in (toast otherwise). Opens a submenu
 * of Trakt-backed rows: Up Next (CW-derived) plus the six Trakt catalog rows
 * (Watchlist / New / Recommended). Each option dims once added (max one each).
 */
@Composable
private fun TraktAddPill(
    signedIn: Boolean,
    upNextAdded: Boolean,
    onAddUpNext: () -> Unit,
    onSignInRequired: () -> Unit,
    onAddTraktCatalog: (LayoutRowKind) -> Unit,
    traktCatalogExisting: Set<LayoutRowKind>,
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        AddRowChip(
            label = "Trakt",
            onClick = { if (signedIn) expanded = true else onSignInRequired() },
        )
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(density) { 46.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                BackHandler { expanded = false }
                val firstFr = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    runCatching { firstFr.requestFocus() }
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF101418).copy(alpha = 0.96f))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SubmenuItem(
                        label = "Up Next",
                        added = upNextAdded,
                        focusRequester = firstFr,
                        onClick = {
                            if (!upNextAdded) onAddUpNext()
                            expanded = false
                        },
                    )
                    // The six Trakt catalog rows — all functional now.
                    listOf(
                        "Recommended Shows" to LayoutRowKind.TRAKT_RECOMMENDED_SHOWS,
                        "Recommended Movies" to LayoutRowKind.TRAKT_RECOMMENDED_MOVIES,
                        "Watchlist Shows" to LayoutRowKind.TRAKT_WATCHLIST_SHOWS,
                        "Watchlist Movies" to LayoutRowKind.TRAKT_WATCHLIST_MOVIES,
                        "New Episodes" to LayoutRowKind.TRAKT_NEW_EPISODES,
                        "New Movies" to LayoutRowKind.TRAKT_NEW_MOVIES,
                    ).forEach { (label, kind) ->
                        SubmenuItem(
                            label = label,
                            added = kind in traktCatalogExisting,
                            focusRequester = null,
                            onClick = {
                                if (kind !in traktCatalogExisting) onAddTraktCatalog(kind)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * "+ Continue Watching" pill that opens a small Series / Movies submenu.
 * Each option is dimmed + non-adding once that variant exists (max one each).
 */
@Composable
private fun ContinueWatchingAddPill(
    existing: Set<ContinueWatchingFilter>,
    onAdd: (ContinueWatchingFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        AddRowChip(label = "Continue Watching", onClick = { expanded = true })
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(density) { 46.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                BackHandler { expanded = false }
                val firstFr = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    runCatching { firstFr.requestFocus() }
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF101418).copy(alpha = 0.96f))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val options = listOf(
                        "Series" to ContinueWatchingFilter.SERIES,
                        "Movies" to ContinueWatchingFilter.MOVIES,
                        "Both" to ContinueWatchingFilter.BOTH,
                    )
                    options.forEachIndexed { index, (label, filter) ->
                        SubmenuItem(
                            label = label,
                            added = filter in existing,
                            focusRequester = if (index == 0) firstFr else null,
                            onClick = {
                                if (filter !in existing) onAdd(filter)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A single row in an "add source" submenu; dims + shows a check when added. */
@Composable
private fun SubmenuItem(
    label: String,
    added: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    comingSoon: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 180.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (added || comingSoon) NuvioColors.TextSecondary else NuvioColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (added) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Added",
                    tint = NuvioColors.FocusRing,
                    modifier = Modifier.size(14.dp),
                )
            } else if (comingSoon) {
                Text(
                    text = "Soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioColors.TextSecondary.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun ComingSoonChip(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(19.dp)
    Button(
        onClick = onClick,
        modifier = Modifier.height(38.dp),
        shape = ButtonDefaults.shape(shape = shape),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            focusedContainerColor = Color.White.copy(alpha = 0.10f),
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(border = BorderStroke(1.5.dp, NuvioColors.FocusRing), shape = shape),
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
                tint = NuvioColors.TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = NuvioColors.TextSecondary.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Coming Soon",
                style = MaterialTheme.typography.labelSmall,
                color = NuvioColors.TextSecondary.copy(alpha = 0.45f),
            )
        }
    }
}

// ── Row 3 — column header (label + per-column global action) ────────────────

@Composable
private fun ColumnHeaderRow(
    style: LayoutCardStyle,
    widthDp: Int,
    allCinema: Boolean,
    expandEnabled: Boolean,
    allEnabled: Boolean,
    hasRows: Boolean,
    onToggleStyle: () -> Unit,
    onSelectSize: (Int) -> Unit,
    onToggleExpand: () -> Unit,
    onToggleAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onResortToAddonOrder: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Row A — text labels, one per column.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderLabel(text = "Name", modifier = Modifier.weight(1f))
            HeaderLabelCell(text = "Order", width = RowOrderColWidth)
            HeaderLabelCell(text = "Orient", width = RowShapeColWidth)
            HeaderLabelCell(text = "Size", width = RowShapeColWidth)
            HeaderLabelCell(text = "Expand", width = RowShapeColWidth)
            HeaderLabelCell(text = "On/Off", width = RowToggleColWidth)
            HeaderLabelCell(text = "Delete", width = RowRemoveColWidth)
        }
        // Row B — global action buttons under their labels.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.width(RowOrderColWidth), contentAlignment = Alignment.Center) {
                IconChipButton(
                    icon = Icons.Default.Sort,
                    contentDesc = "Sort all rows to addon order",
                    enabled = hasRows,
                    onClick = onResortToAddonOrder,
                )
            }
            Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
                StyleShapeButton(style = style, enabled = hasRows, onClick = onToggleStyle)
            }
            Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
                // When every row is CINEMA the size control doesn't apply — hide
                // the global Size button (the column box stays for alignment).
                if (!allCinema) {
                    SizeShapeButton(widthDp = widthDp, enabled = hasRows, onSelect = onSelectSize)
                }
            }
            Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
                // Header expand is the per-scope on/off floor (no "follow" state).
                ExpandShapeButton(state = expandEnabled, onClick = onToggleExpand)
            }
            Box(modifier = Modifier.width(RowToggleColWidth), contentAlignment = Alignment.Center) {
                ToggleAllHeaderChip(allEnabled = allEnabled, enabled = hasRows, onClick = onToggleAll)
            }
            Box(modifier = Modifier.width(RowRemoveColWidth), contentAlignment = Alignment.Center) {
                IconChipButton(
                    icon = Icons.Default.DeleteSweep,
                    contentDesc = "Delete all rows",
                    enabled = hasRows,
                    onClick = onDeleteAll,
                )
            }
        }
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = NuvioColors.TextSecondary,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HeaderLabelCell(text: String, width: Dp) {
    Box(modifier = Modifier.width(width), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = NuvioColors.TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToggleAllHeaderChip(allEnabled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ShapeChip(onClick = onClick, enabled = enabled, active = allEnabled) {
        Icon(
            imageVector = if (allEnabled) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
            contentDescription = "Toggle all rows",
            tint = if (!enabled) {
                NuvioColors.TextSecondary.copy(alpha = 0.4f)
            } else if (allEnabled) NuvioColors.Secondary else NuvioColors.TextSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── Per-row item (manager) ──────────────────────────────────────────────────

@Composable
private fun ManagerRowItem(
    row: LayoutRowConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleStyle: () -> Unit,
    onWidthChange: (Int) -> Unit,
    onCycleExpand: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRemove: () -> Unit,
    // Edge-wrap: supplied only on the first row (onWrapPrev) and last row
    // (onWrapNext); [orientationFocusRequester] is the wrap target for this row.
    onWrapPrev: (() -> Unit)? = null,
    onWrapNext: (() -> Unit)? = null,
    orientationFocusRequester: FocusRequester? = null,
    // Per-index requester on the ✕ button, used to restore focus into the list
    // after a delete (the row that shifts into this slot regains focus).
    removeFocusRequester: FocusRequester? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NuvioColors.BackgroundCard)
            .dpadLoopWrap(onPrev = onWrapPrev, onNext = onWrapNext),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.enabled) NuvioColors.TextPrimary else NuvioColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Columns align with ColumnHeaderRow via the shared width constants.
            Box(modifier = Modifier.width(RowOrderColWidth), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                }
            }
            val isContinueWatching = row.kind.continueWatchingFilter != null
            val isTraktCatalog = row.kind.isTraktCatalogRow
            Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
                if (isContinueWatching) {
                    CwStyleShapeButton(
                        style = row.continueWatchingStyle,
                        enabled = true,
                        onClick = onToggleStyle,
                        focusRequester = orientationFocusRequester,
                    )
                } else {
                    StyleShapeButton(
                        style = row.cardStyle,
                        enabled = true,
                        onClick = onToggleStyle,
                        focusRequester = orientationFocusRequester,
                    )
                }
            }
            Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
                // CINEMA is a single fixed size — the size picker doesn't apply,
                // so it's hidden (the column box stays for alignment). CW always
                // shows the size picker (all three CW orientations are resizable).
                if (row.cardStyle != LayoutCardStyle.CINEMA) {
                    SizeShapeButton(widthDp = row.cardWidthDp, enabled = true, onSelect = onWidthChange)
                }
            }
            Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
                // Continue Watching + Trakt catalog rows have no expand-to-backdrop
                // mechanic — hide the chip (the column box stays for alignment).
                if (!isContinueWatching && !isTraktCatalog) {
                    ExpandShapeButton(state = row.expandEnabled, onClick = onCycleExpand)
                }
            }
            Box(modifier = Modifier.width(RowToggleColWidth), contentAlignment = Alignment.Center) {
                // Accent focus ring so the toggle reads as focused under D-pad,
                // matching the expand/delete chips (a bare Switch shows none).
                var switchFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (switchFocused) {
                                Modifier.border(2.dp, NuvioColors.FocusRing, RoundedCornerShape(20.dp))
                            } else Modifier,
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Switch(
                        checked = row.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.onFocusChanged {
                            switchFocused = it.isFocused || it.hasFocus
                        },
                    )
                }
            }
            Box(modifier = Modifier.width(RowRemoveColWidth), contentAlignment = Alignment.Center) {
                IconChipButton(
                    icon = Icons.Default.Close,
                    contentDesc = "Remove row",
                    enabled = true,
                    onClick = onRemove,
                    focusRequester = removeFocusRequester,
                )
            }
        }
    }
}

// ── Shape controls (orientation / size / expand) ────────────────────────────

/** Cycle order for the per-row expand chip: follow-global → on → off. */
private fun nextExpandState(current: Boolean?): Boolean? = when (current) {
    null -> true
    true -> false
    false -> null
}

/** Focusable square chip hosting a drawn glyph (no text), shared by all shape buttons. */
@Composable
private fun ShapeChip(
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    chipSize: Dp = 38.dp,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val container = when {
        !enabled -> Color.White.copy(alpha = 0.04f)
        isFocused -> Color.White.copy(alpha = 0.18f)
        active -> NuvioColors.Secondary.copy(alpha = 0.20f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .size(chipSize)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(containerColor = container, focusedContainerColor = container),
        border = CardDefaults.border(
            border = if (active) Border(
                border = BorderStroke(1.dp, NuvioColors.Secondary),
                shape = RoundedCornerShape(12.dp),
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

/** Cycle order for the card-style selector: Poster → Landscape → Cinema → Poster. */
private fun nextCardStyle(current: LayoutCardStyle): LayoutCardStyle = when (current) {
    LayoutCardStyle.POSTER -> LayoutCardStyle.LANDSCAPE
    LayoutCardStyle.LANDSCAPE -> LayoutCardStyle.CINEMA
    LayoutCardStyle.CINEMA -> LayoutCardStyle.POSTER
}

/** Cycle order for the Continue Watching orientation: Poster → Card → Wide → Poster. */
private fun nextContinueWatchingStyle(
    current: ContinueWatchingCardStyle,
): ContinueWatchingCardStyle = when (current) {
    ContinueWatchingCardStyle.POSTER -> ContinueWatchingCardStyle.CARD
    ContinueWatchingCardStyle.CARD -> ContinueWatchingCardStyle.WIDE
    ContinueWatchingCardStyle.WIDE -> ContinueWatchingCardStyle.POSTER
}

/**
 * Plain-rectangle glyph for a Continue Watching orientation:
 *  - Poster → tall narrow rectangle (portrait).
 *  - Card   → 16:9 landscape rectangle.
 *  - Wide   → ultra-wide flat strip (artwork+text strip).
 */
@Composable
private fun CwStyleGlyph(style: ContinueWatchingCardStyle, tint: Color) {
    val (w, h) = when (style) {
        ContinueWatchingCardStyle.POSTER -> 14.dp to 21.dp
        ContinueWatchingCardStyle.CARD -> 22.dp to 13.dp
        ContinueWatchingCardStyle.WIDE -> 28.dp to 11.dp
    }
    Box(
        modifier = Modifier
            .size(width = w, height = h)
            .clip(RoundedCornerShape(3.dp))
            .background(tint),
    )
}

@Composable
private fun CwStyleShapeButton(
    style: ContinueWatchingCardStyle,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    ShapeChip(onClick = onClick, enabled = enabled, focusRequester = focusRequester) {
        CwStyleGlyph(
            style = style,
            tint = if (enabled) NuvioColors.TextPrimary else NuvioColors.TextSecondary.copy(alpha = 0.4f),
        )
    }
}

/**
 * Plain-rectangle glyph whose proportions communicate the card style:
 *  - Poster    → tall narrow rectangle (2:3).
 *  - Landscape → wide flat rectangle (~16:10).
 *  - Cinema    → ultra-wide rectangle (16:9), noticeably wider than Landscape.
 * No icons or frame detail — the proportion alone signals the style.
 */
@Composable
private fun StyleGlyph(style: LayoutCardStyle, tint: Color) {
    val (w, h) = when (style) {
        LayoutCardStyle.POSTER -> 14.dp to 21.dp
        LayoutCardStyle.LANDSCAPE -> 22.dp to 14.dp
        LayoutCardStyle.CINEMA -> 28.dp to 16.dp
    }
    Box(
        modifier = Modifier
            .size(width = w, height = h)
            .clip(RoundedCornerShape(3.dp))
            .background(tint),
    )
}

@Composable
private fun StyleShapeButton(
    style: LayoutCardStyle,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    ShapeChip(onClick = onClick, enabled = enabled, focusRequester = focusRequester) {
        StyleGlyph(
            style = style,
            tint = if (enabled) NuvioColors.TextPrimary else NuvioColors.TextSecondary.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun SizeGlyph(widthDp: Int, tint: Color) {
    val minW = CardWidthOptions.first().second
    val maxW = CardWidthOptions.last().second
    val frac = ((widthDp - minW).toFloat() / (maxW - minW).coerceAtLeast(1)).coerceIn(0f, 1f)
    val vw = (11 + frac * 12).dp
    Box(
        modifier = Modifier
            .size(width = vw, height = vw * 1.4f)
            .clip(RoundedCornerShape(3.dp))
            .background(tint),
    )
}

@Composable
private fun SizeShapeButton(widthDp: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ShapeChip(onClick = { if (enabled) expanded = true }, enabled = enabled) {
            SizeGlyph(
                widthDp = widthDp,
                tint = if (enabled) NuvioColors.TextPrimary else NuvioColors.TextSecondary.copy(alpha = 0.4f),
            )
        }
        if (expanded) {
            SizePopover(
                selectedWidthDp = widthDp,
                onSelect = { onSelect(it); expanded = false },
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun SizePopover(
    selectedWidthDp: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BackHandler { onDismiss() }
        // Vertical loop wrap among the size options (all visible — no scroll).
        val firstFr = remember { FocusRequester() }
        val lastFr = remember { FocusRequester() }
        val lastIndex = CardWidthOptions.lastIndex
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101418).copy(alpha = 0.96f))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CardWidthOptions.forEachIndexed { index, (label, value) ->
                SizePopoverItem(
                    label = label,
                    widthDp = value,
                    selected = value == selectedWidthDp,
                    onClick = { onSelect(value) },
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstFr) else Modifier)
                        .then(if (index == lastIndex) Modifier.focusRequester(lastFr) else Modifier)
                        .dpadLoopWrap(
                            onPrev = if (index == 0) {
                                { runCatching { lastFr.requestFocus() } }
                            } else null,
                            onNext = if (index == lastIndex) {
                                { runCatching { firstFr.requestFocus() } }
                            } else null,
                        ),
                )
            }
        }
    }
}

@Composable
private fun SizePopoverItem(
    label: String,
    widthDp: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 150.dp)
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                SizeGlyph(
                    widthDp = widthDp,
                    tint = if (selected) NuvioColors.FocusRing else NuvioColors.TextPrimary,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NuvioColors.FocusRing,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * Expand state glyph. Global toolbar passes a non-null [state] (on/off); per-row
 * chips pass nullable [state] where null = "follow the per-scope global".
 */
@Composable
private fun ExpandShapeButton(state: Boolean?, onClick: () -> Unit) {
    val icon = when (state) {
        true -> Icons.Default.OpenInFull
        false -> Icons.Default.CloseFullscreen
        null -> Icons.Default.AutoMode
    }
    val tint = when (state) {
        true -> NuvioColors.Secondary
        false -> NuvioColors.TextSecondary
        null -> NuvioColors.TextSecondary.copy(alpha = 0.7f)
    }
    ShapeChip(onClick = onClick, active = state == true) {
        Icon(
            imageVector = icon,
            contentDescription = "Expand",
            tint = tint,
            modifier = Modifier.size(18.dp),
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
    // For You is a fixed pure-rows canvas with no configurable layout — hide it
    // from the layout-picker scope tabs (ALL / LAYOUT_ONLY). It still appears in
    // the Rows Manager (ROWS_ONLY), which passes true.
    showForYou: Boolean = true,
) {
    val scopes = remember(showDetailPage, showForYou) {
        LayoutScreenScope.entries.filter {
            (showDetailPage || it != LayoutScreenScope.DETAIL) &&
                (showForYou || it != LayoutScreenScope.FOR_YOU)
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
        // 4 layouts evenly distributed across the full pane width — each card
        // gets equal weight so Classic / Grid / Modern / Spotlight are the same
        // size (Spotlight was previously clipped by fixed-width overflow).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeLayout.entries.forEach { layout ->
                LayoutCard(
                    layout = layout,
                    isSelected = layout == selected,
                    showLivePreview = true,
                    onClick = { onSelect(layout) },
                    onFocused = {},
                    modifier = Modifier.weight(1f),
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
    heroCatalogKeys: Set<String>,
    availableHeroCatalogs: List<HeroCatalogChoice>,
    onToggleHeroCatalog: (String) -> Unit,
) {
    // "Show Hero Carousel" is hidden for Spotlight — the hero carousel is
    // intrinsic to the layout, so the toggle is redundant. Hero Catalogs
    // picker stays so the user can still choose which catalogs feed it.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCatalogsPicker(
            selectedKeys = heroCatalogKeys,
            catalogs = availableHeroCatalogs,
            onToggle = onToggleHeroCatalog,
        )
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
            // Horizontal loop wrap: last pill + Right → first; first + Left → last.
            val scope = rememberCoroutineScope()
            val rowState = rememberLazyListState()
            val firstFr = remember { FocusRequester() }
            val lastFr = remember { FocusRequester() }
            val lastIndex = catalogs.lastIndex
            androidx.compose.foundation.lazy.LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                itemsIndexed(items = catalogs, key = { _, c -> c.key }) { index, catalog ->
                    ChoicePill(
                        label = catalog.name,
                        isSelected = catalog.key in selectedKeys,
                        onClick = { onToggle(catalog.key) },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstFr) else Modifier)
                            .then(if (index == lastIndex) Modifier.focusRequester(lastFr) else Modifier)
                            .dpadLoopWrap(
                                horizontal = true,
                                onPrev = if (index == 0) {
                                    {
                                        scope.launch {
                                            rowState.scrollToItem(lastIndex)
                                            withFrameNanos { }
                                            runCatching { lastFr.requestFocus() }
                                        }
                                    }
                                } else null,
                                onNext = if (index == lastIndex) {
                                    {
                                        scope.launch {
                                            rowState.scrollToItem(0)
                                            withFrameNanos { }
                                            runCatching { firstFr.requestFocus() }
                                        }
                                    }
                                } else null,
                            ),
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
            // CINEMA is a single fixed size — the width picker doesn't apply.
            if (row.cardStyle != LayoutCardStyle.CINEMA) {
                LabeledDropdown(
                    label = "Width",
                    options = CardWidthOptions,
                    selectedValue = row.cardWidthDp,
                    onValueChange = onWidthChange,
                    compact = true,
                )
            }
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
            text = when (style) {
                LayoutCardStyle.POSTER -> "Poster"
                LayoutCardStyle.LANDSCAPE -> "Landscape"
                LayoutCardStyle.CINEMA -> "Cinema"
            },
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
    focusRequester: FocusRequester? = null,
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
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
            if (hasRows) {
                AutoPopulateButton(
                    label = "Clear All",
                    onClick = { showClearConfirm = true },
                    containerColor = Color(0xFF5A1C1C),
                    focusedContainerColor = Color(0xFF7A2C2C),
                )
            } else {
                AutoPopulateButton(
                    label = "Populate All",
                    onClick = onAutoPopulate,
                    containerColor = NuvioColors.Secondary.copy(alpha = 0.20f),
                    focusedContainerColor = NuvioColors.Secondary.copy(alpha = 0.35f),
                )
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

// ── Collections accordion (3-level: collection → folder → catalog) ──────────
//
// Collection-kind rows group into one display unit per collection. LEVEL 1
// header controls act on the block's rows in this scope; LEVEL 2 visibility /
// layout and LEVEL 3 display config persist on the per-folder rows; LEVEL 2/3
// reorder + delete mutate the collection itself (same CRUD the Collection
// Manager screen uses). Expand state is UI-only.

internal sealed interface ManagerDisplayUnit {
    val key: String

    data class Flat(val row: LayoutRowConfig) : ManagerDisplayUnit {
        override val key: String get() = row.id
    }

    data class CollectionBlock(
        val collectionId: String,
        val title: String,
        val rows: List<LayoutRowConfig>,
        val collection: Collection?,
    ) : ManagerDisplayUnit {
        override val key: String get() = "colblock_$collectionId"
        val blockEnabled: Boolean get() = rows.isEmpty() || rows.any { it.enabled }
    }
}

internal fun buildManagerDisplayUnits(
    rows: List<LayoutRowConfig>,
    collectionsById: Map<String, Collection>,
    isCollectionsScope: Boolean,
    allCollections: List<Collection>,
): List<ManagerDisplayUnit> {
    if (isCollectionsScope) {
        // The Collections scope manages the collections themselves — one
        // block per collection in data order, scope rows attached by id.
        val rowsByCid = rows
            .filter { it.kind == LayoutRowKind.COLLECTION }
            .groupBy { LayoutRowKey.collectionIdFrom(it.id) }
        return allCollections.map { c ->
            ManagerDisplayUnit.CollectionBlock(
                collectionId = c.id,
                title = c.title,
                rows = rowsByCid[c.id].orEmpty(),
                collection = c,
            )
        }
    }
    val units = mutableListOf<ManagerDisplayUnit>()
    val blockIndexByCid = mutableMapOf<String, Int>()
    rows.forEach { row ->
        val cid = if (row.kind == LayoutRowKind.COLLECTION) {
            LayoutRowKey.collectionIdFrom(row.id)
        } else null
        if (cid == null) {
            units += ManagerDisplayUnit.Flat(row)
            return@forEach
        }
        val at = blockIndexByCid[cid]
        if (at != null) {
            val block = units[at] as ManagerDisplayUnit.CollectionBlock
            units[at] = block.copy(rows = block.rows + row)
        } else {
            val collection = collectionsById[cid]
            blockIndexByCid[cid] = units.size
            units += ManagerDisplayUnit.CollectionBlock(
                collectionId = cid,
                title = collection?.title ?: row.name,
                rows = listOf(row),
                collection = collection,
            )
        }
    }
    return units
}

/** Source display name for LEVEL 3 rows. */
private fun collectionSourceDisplayName(
    source: CollectionSource,
    addonCatalogNames: Map<String, String>,
): String = when (source) {
    is AddonCatalogCollectionSource -> {
        val base = addonCatalogNames["addon|${source.addonId}|${source.type}|${source.catalogId}"]
            ?: source.catalogId
        if (source.genre.isNullOrBlank()) base else "$base • ${source.genre}"
    }
    is TmdbCollectionSource -> source.title.ifBlank { "TMDB ${source.sourceType.name.lowercase()}" }
    is TraktCollectionSource -> source.title.ifBlank { "Trakt list ${source.traktListId}" }
}

@Composable
internal fun CollectionBlockItem(
    unit: ManagerDisplayUnit.CollectionBlock,
    isCollectionsScope: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    expandedFolders: MutableMap<String, Boolean>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveBlock: (Int) -> Unit,
    onEditCollection: () -> Unit,
    onToggleBlockEnabled: () -> Unit,
    onRemoveBlock: () -> Unit,
    addonCatalogNames: Map<String, String>,
    viewModel: NewLayoutSettingsViewModel,
    onRequestFolderDelete: (Collection, CollectionFolder) -> Unit,
    onRequestSourceDelete: (Collection, CollectionFolder, Int) -> Unit,
    headerFocusRequester: FocusRequester? = null,
) {
    val collection = unit.collection
    val folderRowsByFid = remember(unit.rows) {
        unit.rows
            .mapNotNull { row -> LayoutRowKey.folderIdFrom(row.id)?.let { it to row } }
            .toMap()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NuvioColors.BackgroundCard),
    ) {
        Column {
            // ── LEVEL 1 — collection header ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Chevron + title form one focusable that toggles expansion.
                val chevronRotation by animateFloatAsState(
                    targetValue = if (expanded) 0f else -90f,
                    animationSpec = tween(150),
                    label = "blockChevron",
                )
                var headerFocused by remember { mutableStateOf(false) }
                Card(
                    onClick = onToggleExpanded,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (headerFocusRequester != null) {
                                Modifier.focusRequester(headerFocusRequester)
                            } else Modifier,
                        )
                        .onFocusChanged { headerFocused = it.isFocused || it.hasFocus },
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = CardDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.10f),
                    ),
                    border = CardDefaults.border(
                        border = Border.None,
                        focusedBorder = Border(
                            border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                            shape = RoundedCornerShape(8.dp),
                        ),
                    ),
                    scale = CardDefaults.scale(focusedScale = 1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = NuvioColors.TextSecondary,
                            modifier = Modifier.size(20.dp).rotate(chevronRotation),
                        )
                        Text(
                            text = unit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (unit.blockEnabled) NuvioColors.TextPrimary else NuvioColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Collection",
                            style = MaterialTheme.typography.labelSmall,
                            color = NuvioColors.TextSecondary.copy(alpha = 0.7f),
                        )
                    }
                }
                Box(modifier = Modifier.width(RowOrderColWidth), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconChipButton(
                            icon = Icons.Default.ArrowUpward,
                            contentDesc = "Move collection up",
                            enabled = canMoveUp,
                            onClick = { onMoveBlock(-1) },
                        )
                        IconChipButton(
                            icon = Icons.Default.ArrowDownward,
                            contentDesc = "Move collection down",
                            enabled = canMoveDown,
                            onClick = { onMoveBlock(+1) },
                        )
                    }
                }
                Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
                    IconChipButton(
                        icon = Icons.Default.Edit,
                        contentDesc = "Edit collection",
                        enabled = collection != null,
                        onClick = onEditCollection,
                    )
                }
                Box(modifier = Modifier.width(RowToggleColWidth), contentAlignment = Alignment.Center) {
                    // COLLECTIONS scope: blocks are the collections themselves —
                    // there is no row to enable/disable, the tab always shows
                    // every collection. Hide the switch there.
                    if (!isCollectionsScope) {
                        ManagerSwitch(checked = unit.blockEnabled, onCheckedChange = { onToggleBlockEnabled() })
                    }
                }
                Box(modifier = Modifier.width(RowRemoveColWidth), contentAlignment = Alignment.Center) {
                    if (!isCollectionsScope) {
                        IconChipButton(
                            icon = Icons.Default.Close,
                            contentDesc = "Remove collection row",
                            enabled = true,
                            onClick = onRemoveBlock,
                        )
                    }
                }
            }

            // ── LEVEL 2/3 — folders + catalogs ─────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    if (collection == null) {
                        Text(
                            text = "Collection no longer exists — remove this row.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioColors.TextSecondary,
                            modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                        )
                    } else if (collection.folders.isEmpty()) {
                        Text(
                            text = "No folders in this collection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioColors.TextSecondary,
                            modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                        )
                    } else {
                        collection.folders.forEachIndexed { fIdx, folder ->
                            val folderRow = folderRowsByFid[folder.id]
                            val folderKey = "${unit.collectionId}|${folder.id}"
                            val folderExpanded = expandedFolders[folderKey] == true
                            CollectionFolderManagerRow(
                                folder = folder,
                                folderRow = folderRow,
                                // With NO per-folder rows configured the pipeline
                                // shows every folder; once ANY exist, only folders
                                // with an enabled row render.
                                defaultVisible = folderRowsByFid.isEmpty(),
                                isCollectionsScope = isCollectionsScope,
                                expanded = folderExpanded,
                                onToggleExpanded = {
                                    expandedFolders[folderKey] = expandedFolders[folderKey] != true
                                },
                                canMoveUp = fIdx != 0,
                                canMoveDown = fIdx != collection.folders.lastIndex,
                                onMove = { dir -> viewModel.moveFolderInCollection(collection, folder.id, dir) },
                                onSelectLayout = { value -> viewModel.setFolderLayout(collection, folder.id, value) },
                                onEdit = onEditCollection,
                                onToggleVisible = { visible ->
                                    viewModel.setFolderRowVisible(collection, folder.id, visible)
                                },
                                onDelete = { onRequestFolderDelete(collection, folder) },
                            )
                            AnimatedVisibility(visible = folderExpanded) {
                                Column {
                                    if (folder.sources.isEmpty()) {
                                        Text(
                                            text = "No catalogs in this folder.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NuvioColors.TextSecondary,
                                            modifier = Modifier.padding(start = 64.dp, top = 2.dp, bottom = 4.dp),
                                        )
                                    } else {
                                        folder.sources.forEachIndexed { sIdx, source ->
                                            CollectionSourceManagerRow(
                                                name = collectionSourceDisplayName(source, addonCatalogNames),
                                                srcKey = collectionSourceKey(source),
                                                folderRow = folderRow,
                                                canMoveUp = sIdx != 0,
                                                canMoveDown = sIdx != folder.sources.lastIndex,
                                                onMove = { dir ->
                                                    viewModel.moveSourceInFolder(collection, folder.id, sIdx, dir)
                                                },
                                                onSelectStyle = { style ->
                                                    viewModel.setSourceStyle(
                                                        collection, folder.id, collectionSourceKey(source), style,
                                                    )
                                                },
                                                onSelectWidth = { width ->
                                                    viewModel.setSourceWidth(
                                                        collection, folder.id, collectionSourceKey(source), width,
                                                    )
                                                },
                                                onToggleEnabled = { enabled ->
                                                    viewModel.setSourceEnabled(
                                                        collection, folder.id, collectionSourceKey(source), enabled,
                                                    )
                                                },
                                                onDelete = { onRequestSourceDelete(collection, folder, sIdx) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** LEVEL 2 — one folder inside an expanded collection block. */
@Composable
private fun CollectionFolderManagerRow(
    folder: CollectionFolder,
    folderRow: LayoutRowConfig?,
    defaultVisible: Boolean,
    isCollectionsScope: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onSelectLayout: (String?) -> Unit,
    onEdit: () -> Unit,
    onToggleVisible: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    // Mirrors the pipeline's visibility rule (see collectionFolderVisibility).
    val visible = folderRow?.enabled ?: defaultVisible
    val layoutOverride = folderRow?.metadata?.get(FOLDER_LAYOUT_METADATA_KEY)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f))
            .padding(start = 28.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "├─",
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioColors.TextSecondary.copy(alpha = 0.5f),
        )
        val chevronRotation by animateFloatAsState(
            targetValue = if (expanded) 0f else -90f,
            animationSpec = tween(150),
            label = "folderChevron",
        )
        Card(
            onClick = onToggleExpanded,
            modifier = Modifier.weight(1f),
            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.10f),
            ),
            border = CardDefaults.border(
                border = Border.None,
                focusedBorder = Border(
                    border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                    shape = RoundedCornerShape(8.dp),
                ),
            ),
            scale = CardDefaults.scale(focusedScale = 1f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = NuvioColors.TextSecondary,
                    modifier = Modifier.size(18.dp).rotate(chevronRotation),
                )
                if (!folder.coverEmoji.isNullOrBlank()) {
                    Text(text = folder.coverEmoji!!, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (visible) NuvioColors.TextPrimary else NuvioColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(modifier = Modifier.width(RowOrderColWidth), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconChipButton(
                    icon = Icons.Default.ArrowUpward,
                    contentDesc = "Move folder up",
                    enabled = canMoveUp,
                    onClick = { onMove(-1) },
                )
                IconChipButton(
                    icon = Icons.Default.ArrowDownward,
                    contentDesc = "Move folder down",
                    enabled = canMoveDown,
                    onClick = { onMove(+1) },
                )
            }
        }
        Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
            // The folder's presentation override is a single canonical value —
            // editable only from the COLLECTIONS scope tab, which is the scope
            // FolderDetail reads. Other scopes manage visibility/order only.
            if (isCollectionsScope) {
                FolderLayoutShapeButton(current = layoutOverride, onSelect = onSelectLayout)
            }
        }
        Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
            IconChipButton(
                icon = Icons.Default.Edit,
                contentDesc = "Edit folder",
                enabled = true,
                onClick = onEdit,
            )
        }
        Box(modifier = Modifier.width(RowToggleColWidth), contentAlignment = Alignment.Center) {
            if (!isCollectionsScope) {
                ManagerSwitch(checked = visible, onCheckedChange = { onToggleVisible(!visible) })
            }
        }
        Box(modifier = Modifier.width(RowRemoveColWidth), contentAlignment = Alignment.Center) {
            IconChipButton(
                icon = Icons.Default.Close,
                contentDesc = "Delete folder from collection",
                enabled = true,
                onClick = onDelete,
            )
        }
    }
}

/** LEVEL 3 — one catalog (source) inside an expanded folder. */
@Composable
private fun CollectionSourceManagerRow(
    name: String,
    srcKey: String,
    folderRow: LayoutRowConfig?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onSelectStyle: (LayoutCardStyle) -> Unit,
    onSelectWidth: (Int) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val meta = folderRow?.metadata.orEmpty()
    val style = meta[SRC_STYLE_METADATA_PREFIX + srcKey]
        ?.let { raw -> runCatching { LayoutCardStyle.valueOf(raw) }.getOrNull() }
        ?: LayoutCardStyle.POSTER
    val widthDp = meta[SRC_WIDTH_METADATA_PREFIX + srcKey]?.toIntOrNull() ?: 126
    val enabled = meta[SRC_OFF_METADATA_PREFIX + srcKey] == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.015f))
            .padding(start = 52.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "├─",
            style = MaterialTheme.typography.bodySmall,
            color = NuvioColors.TextSecondary.copy(alpha = 0.4f),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) NuvioColors.TextPrimary else NuvioColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.width(RowOrderColWidth), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconChipButton(
                    icon = Icons.Default.ArrowUpward,
                    contentDesc = "Move catalog up",
                    enabled = canMoveUp,
                    onClick = { onMove(-1) },
                )
                IconChipButton(
                    icon = Icons.Default.ArrowDownward,
                    contentDesc = "Move catalog down",
                    enabled = canMoveDown,
                    onClick = { onMove(+1) },
                )
            }
        }
        Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
            StyleShapeButton(
                style = style,
                enabled = true,
                onClick = { onSelectStyle(nextCardStyle(style)) },
            )
        }
        Box(modifier = Modifier.width(RowShapeColWidth), contentAlignment = Alignment.Center) {
            if (style != LayoutCardStyle.CINEMA) {
                SizeShapeButton(widthDp = widthDp, enabled = true, onSelect = onSelectWidth)
            }
        }
        Box(modifier = Modifier.width(RowToggleColWidth), contentAlignment = Alignment.Center) {
            ManagerSwitch(checked = enabled, onCheckedChange = { onToggleEnabled(!enabled) })
        }
        Box(modifier = Modifier.width(RowRemoveColWidth), contentAlignment = Alignment.Center) {
            IconChipButton(
                icon = Icons.Default.Close,
                contentDesc = "Remove catalog from folder",
                enabled = true,
                onClick = onDelete,
            )
        }
    }
}

/** Focus-ringed Switch matching the flat-row toggle treatment. */
@Composable
private fun ManagerSwitch(checked: Boolean, onCheckedChange: () -> Unit) {
    var switchFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (switchFocused) {
                    Modifier.border(2.dp, NuvioColors.FocusRing, RoundedCornerShape(20.dp))
                } else Modifier,
            )
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            modifier = Modifier.onFocusChanged {
                switchFocused = it.isFocused || it.hasFocus
            },
        )
    }
}

// ── Per-folder layout picker (LEVEL 2 "Layout") ─────────────────────────────

// Unified per-folder presentation options: the two FolderDetail structures
// (Tabs / Rows) plus the FOLLOW_LAYOUT home layouts. Values are the raw
// FOLDER_LAYOUT_METADATA_KEY strings; null = Default (collection.viewMode).
private val FolderLayoutOptions: List<Pair<String, String?>> = listOf(
    "Default" to null,
    "Tabs" to FOLDER_LAYOUT_VALUE_TABS,
    "Rows" to FOLDER_LAYOUT_VALUE_ROWS,
    "Classic" to HomeLayout.CLASSIC.name,
    "Modern" to HomeLayout.MODERN.name,
    "Immersive" to HomeLayout.IMMERSIVE.name,
    "Spotlight" to HomeLayout.SPOTLIGHT.name,
    "Grid" to HomeLayout.GRID.name,
)

private fun folderLayoutGlyph(value: String?): String = when (value) {
    null -> "—"
    FOLDER_LAYOUT_VALUE_TABS -> "TB"
    FOLDER_LAYOUT_VALUE_ROWS -> "RW"
    HomeLayout.CLASSIC.name -> "CL"
    HomeLayout.MODERN.name -> "MO"
    HomeLayout.IMMERSIVE.name -> "IM"
    HomeLayout.SPOTLIGHT.name -> "SP"
    HomeLayout.GRID.name -> "GR"
    else -> "—"
}

@Composable
private fun FolderLayoutShapeButton(current: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ShapeChip(onClick = { expanded = true }, active = current != null) {
            Text(
                text = folderLayoutGlyph(current),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (current != null) NuvioColors.Secondary else NuvioColors.TextSecondary,
            )
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                BackHandler { expanded = false }
                val firstFr = remember { FocusRequester() }
                val lastFr = remember { FocusRequester() }
                val lastIndex = FolderLayoutOptions.lastIndex
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF101418).copy(alpha = 0.96f))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FolderLayoutOptions.forEachIndexed { index, (label, value) ->
                        var isFocused by remember { mutableStateOf(false) }
                        Card(
                            onClick = { onSelect(value); expanded = false },
                            modifier = Modifier
                                .width(200.dp)
                                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                .then(if (index == 0) Modifier.focusRequester(firstFr) else Modifier)
                                .then(if (index == lastIndex) Modifier.focusRequester(lastFr) else Modifier)
                                .dpadLoopWrap(
                                    onPrev = if (index == 0) {
                                        { runCatching { lastFr.requestFocus() } }
                                    } else null,
                                    onNext = if (index == lastIndex) {
                                        { runCatching { firstFr.requestFocus() } }
                                    } else null,
                                ),
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NuvioColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (value == current) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = NuvioColors.FocusRing,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
