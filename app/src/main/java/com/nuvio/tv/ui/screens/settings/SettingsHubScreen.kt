@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioColors

private val LeftRailWidth = 280.dp
private val PaneHorizontalPadding = 24.dp
private val PaneVerticalPadding = 20.dp
private val LeftRailItemSpacing = 4.dp
private val SubItemIndent = 24.dp

/**
 * Two-panel Apple-TV-style Settings hub with a cascade left rail. The left
 * rail shows top-level categories; expanding a category reveals its sub-items
 * indented below it. Selecting a sub-item either renders its content in the
 * right pane (Content type) or invokes a navigation lambda for sub-screens
 * (NavAction type — e.g. Addons, Trakt).
 */
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onNavigateToAuthQrSignIn: () -> Unit,
    onNavigateToManageProfiles: () -> Unit,
    onNavigateToSupportersContributors: () -> Unit,
    onNavigateToAddons: () -> Unit,
    onNavigateToTrakt: () -> Unit,
) {
    BackHandler { onBack() }

    val categories = remember { settingsCategories() }
    // Start fully collapsed: no category expanded, no content panel.  The user
    // taps a category to reveal its sub-items, then taps a sub-item to load
    // its content in the right pane.  Empty string is the "nothing selected"
    // sentinel — kept non-nullable so existing equality checks compile
    // unchanged.
    var selectedCategoryId by remember { mutableStateOf("") }
    var selectedContentSubId by remember { mutableStateOf("") }

    val backFocusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = NuvioColors.Background),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            LeftRail(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                selectedContentSubId = selectedContentSubId,
                onSelectCategory = { id -> selectedCategoryId = id },
                onSelectContentSub = { catId, subId ->
                    selectedCategoryId = catId
                    selectedContentSubId = subId
                },
                onNavAction = { action ->
                    when (action) {
                        NavTarget.ADDONS -> onNavigateToAddons()
                        NavTarget.TRAKT -> onNavigateToTrakt()
                    }
                },
                onBack = onBack,
                backFocusRequester = backFocusRequester,
            )
            VerticalDivider()
            RightPane(
                contentSubId = selectedContentSubId,
                onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn,
                onNavigateToManageProfiles = onNavigateToManageProfiles,
                onNavigateToSupportersContributors = onNavigateToSupportersContributors,
            )
        }
    }
}

// ── Left rail (cascade) ─────────────────────────────────────────────────────

@Composable
private fun LeftRail(
    categories: List<HubCategory>,
    selectedCategoryId: String,
    selectedContentSubId: String,
    onSelectCategory: (String) -> Unit,
    onSelectContentSub: (categoryId: String, subId: String) -> Unit,
    onNavAction: (NavTarget) -> Unit,
    onBack: () -> Unit,
    backFocusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier
            .width(LeftRailWidth)
            .fillMaxHeight()
            .padding(horizontal = PaneHorizontalPadding, vertical = PaneVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LeftRailHeader(onBack = onBack, backFocusRequester = backFocusRequester)

        // Flatten the tree into a single LazyColumn so the LazyColumn's intrinsic
        // up/down focus traversal handles category↔sub-item D-pad movement.
        val rows: List<RailRow> = remember(categories, selectedCategoryId) {
            categories.flatMap { category ->
                val rows = mutableListOf<RailRow>(RailRow.Category(category))
                if (category.id == selectedCategoryId) {
                    category.subItems.forEach { sub ->
                        rows += RailRow.Sub(category, sub)
                    }
                }
                rows
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(LeftRailItemSpacing),
        ) {
            items(items = rows, key = ::railRowKey) { row ->
                when (row) {
                    is RailRow.Category -> CategoryRailRow(
                        category = row.category,
                        isSelected = row.category.id == selectedCategoryId,
                        onSelect = { onSelectCategory(row.category.id) },
                        upFocus = if (row == rows.first()) backFocusRequester else null,
                    )
                    is RailRow.Sub -> SubItemRailRow(
                        sub = row.sub,
                        isSelected = when (val s = row.sub) {
                            is HubSubItem.Content -> s.id == selectedContentSubId
                            is HubSubItem.NavAction -> false
                        },
                        onSelect = {
                            when (val s = row.sub) {
                                is HubSubItem.Content -> onSelectContentSub(row.parent.id, s.id)
                                is HubSubItem.NavAction -> onNavAction(s.target)
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun railRowKey(row: RailRow): String = when (row) {
    is RailRow.Category -> "cat|${row.category.id}"
    is RailRow.Sub -> "sub|${row.parent.id}|${row.sub.id}"
}

private sealed interface RailRow {
    data class Category(val category: HubCategory) : RailRow
    data class Sub(val parent: HubCategory, val sub: HubSubItem) : RailRow
}

@Composable
private fun LeftRailHeader(
    onBack: () -> Unit,
    backFocusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackPill(onClick = onBack, focusRequester = backFocusRequester)
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = NuvioColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BackPill(onClick: () -> Unit, focusRequester: FocusRequester) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(CircleShape),
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.BackgroundCard,
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, NuvioColors.Border),
                shape = CircleShape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = CircleShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = NuvioColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CategoryRailRow(
    category: HubCategory,
    isSelected: Boolean,
    onSelect: () -> Unit,
    upFocus: FocusRequester?,
) {
    var focused by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(SettingsSecondaryCardRadius)
    // Focus indicator is text+icon-color only — no background block, no
    // border.  Per nav spec: "replace [focus block] with text color change
    // only".  Selection state still tints (so the user can tell at a glance
    // which category is open) but uses TextPrimary, not the focus ring.
    val textColor by animateColorAsState(
        targetValue = when {
            focused -> NuvioColors.TextPrimary
            isSelected -> NuvioColors.TextPrimary
            else -> NuvioColors.TextSecondary
        },
        animationSpec = tween(160),
        label = "categoryRowText",
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            focused -> NuvioColors.TextPrimary
            isSelected -> NuvioColors.TextPrimary
            else -> NuvioColors.TextSecondary
        },
        animationSpec = tween(160),
        label = "categoryRowIcon",
    )

    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsRailItemHeight)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .then(
                if (upFocus != null && focused) {
                    Modifier.focusProperties { up = upFocus }
                } else Modifier
            ),
        shape = CardDefaults.shape(rowShape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = category.label,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                fontWeight = if (focused || isSelected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SubItemRailRow(
    sub: HubSubItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(SettingsSecondaryCardRadius)
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> NuvioColors.FocusBackground
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "subItemRowBg",
    )

    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SubItemIndent)
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(rowShape),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
        ),
        border = CardDefaults.border(
            border = if (isSelected) Border(
                border = BorderStroke(1.dp, NuvioColors.FocusRing),
                shape = rowShape,
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = rowShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = sub.label,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioColors.TextPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (sub is HubSubItem.NavAction) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = NuvioColors.TextTertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ── Divider ─────────────────────────────────────────────────────────────────

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(NuvioColors.Border.copy(alpha = 0.6f)),
    )
}

// ── Right pane (pure content) ───────────────────────────────────────────────

@Composable
private fun RightPane(
    contentSubId: String,
    onNavigateToAuthQrSignIn: () -> Unit,
    onNavigateToManageProfiles: () -> Unit,
    onNavigateToSupportersContributors: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PaneHorizontalPadding, vertical = PaneVerticalPadding),
    ) {
        SubItemContent(
            contentSubId = contentSubId,
            onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn,
            onNavigateToManageProfiles = onNavigateToManageProfiles,
            onNavigateToSupportersContributors = onNavigateToSupportersContributors,
        )
    }
}

@Composable
private fun SubItemContent(
    contentSubId: String,
    onNavigateToAuthQrSignIn: () -> Unit,
    onNavigateToManageProfiles: () -> Unit,
    onNavigateToSupportersContributors: () -> Unit,
) {
    when (contentSubId) {
        // Appearance
        "appearance.global" -> GlobalSettingsContent()
        "appearance.layout" -> NewLayoutSettingsContent(mode = NewLayoutContentMode.LAYOUT_ONLY)
        "appearance.rows" -> NewLayoutSettingsContent(mode = NewLayoutContentMode.ROWS_ONLY)
        "appearance.continue_watching" -> ContinueWatchingSettingsContent()
        "appearance.theme" -> ThemeSettingsContent()
        // Extensions
        "extensions.plugins" -> PluginsInlineWrapper()
        "extensions.tmdb" -> TmdbSettingsContent()
        "extensions.mdblist" -> MDBListSettingsContent()
        "extensions.animeskip" -> AnimeSkipSettingsContent()
        // Accounts & Sync
        "accounts.account" -> AccountSettingsInline(onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn)
        "accounts.profiles" -> ProfileSettingsContent(onManageProfiles = onNavigateToManageProfiles)
        // Playback
        "playback.main" -> PlaybackSettingsContent()
        // Advanced
        "advanced.network" -> AdvancedSettingsContent()
        "advanced.about" -> AboutSettingsContent(
            onNavigateToSupportersContributors = onNavigateToSupportersContributors
        )
        // Initial state: nothing selected. All categories start collapsed; the
        // right pane stays empty until the user opens a category and picks
        // a sub-item.
        "" -> Unit
        else -> ComingSoonPlaceholder(label = contentSubId)
    }
}

@Composable
private fun PluginsInlineWrapper() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsDetailHeader(
            title = "Plugins",
            subtitle = "Manage installed plugins and add new ones.",
        )
        SettingsGroupCard(modifier = Modifier.fillMaxSize()) {
            com.nuvio.tv.ui.screens.plugin.PluginScreenContent()
        }
    }
}

@Composable
private fun ComingSoonPlaceholder(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$label — coming soon", color = NuvioColors.TextSecondary)
    }
}

// ── Category model ──────────────────────────────────────────────────────────

private data class HubCategory(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val subItems: List<HubSubItem>,
)

private sealed interface HubSubItem {
    val id: String
    val label: String

    /** A sub-item whose content renders inline in the right pane. */
    data class Content(override val id: String, override val label: String) : HubSubItem

    /**
     * A sub-item that navigates to an external full-screen route when
     * selected. Does not change the current right-pane content.
     */
    data class NavAction(
        override val id: String,
        override val label: String,
        val target: NavTarget,
    ) : HubSubItem
}

private enum class NavTarget { ADDONS, TRAKT }

private fun settingsCategories(): List<HubCategory> = listOf(
    HubCategory(
        id = "appearance",
        label = "Appearance",
        icon = Icons.Default.Palette,
        subItems = listOf(
            HubSubItem.Content("appearance.global", "Global"),
            HubSubItem.Content("appearance.layout", "Layout"),
            HubSubItem.Content("appearance.rows", "Rows"),
            HubSubItem.Content("appearance.continue_watching", "Continue Watching"),
            // "Old Layout" removed from the Appearance cascade per spec
            // (Group 4).  The file [LayoutSettingsScreen.kt] is kept dormant
            // for now so its remaining settings (Continue Watching internals,
            // detail-page toggles) can be migrated in follow-up work
            // without a single-PR rewrite.
            HubSubItem.Content("appearance.theme", "Theme"),
        ),
    ),
    HubCategory(
        id = "extensions",
        label = "Extensions",
        icon = Icons.Default.Extension,
        subItems = listOf(
            HubSubItem.Content("extensions.plugins", "Plugins"),
            HubSubItem.NavAction("extensions.addons", "Addons", NavTarget.ADDONS),
            HubSubItem.Content("extensions.tmdb", "TMDB"),
            HubSubItem.Content("extensions.mdblist", "MDBList"),
            HubSubItem.Content("extensions.animeskip", "AnimeSkip"),
        ),
    ),
    HubCategory(
        id = "accounts",
        label = "Accounts & Sync",
        icon = Icons.Default.Person,
        subItems = listOf(
            HubSubItem.Content("accounts.account", "Account"),
            HubSubItem.Content("accounts.profiles", "Profiles"),
            HubSubItem.NavAction("accounts.trakt", "Trakt", NavTarget.TRAKT),
        ),
    ),
    HubCategory(
        id = "playback",
        label = "Playback",
        icon = Icons.Default.PlayArrow,
        subItems = listOf(
            HubSubItem.Content("playback.main", "Playback"),
        ),
    ),
    HubCategory(
        id = "advanced",
        label = "Advanced",
        icon = Icons.Default.Settings,
        subItems = listOf(
            HubSubItem.Content("advanced.network", "Network"),
            HubSubItem.Content("advanced.about", "About"),
        ),
    ),
)
