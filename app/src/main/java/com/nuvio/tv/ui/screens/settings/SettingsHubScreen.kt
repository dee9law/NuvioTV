@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val LeftRailWidth = 300.dp
private val PaneHorizontalPadding = 24.dp
private val PaneVerticalPadding = 20.dp
// Open-canvas right pane (no card/border) — medium-tight padding so content
// owns the full pane without feeling cramped or airy.
private val RightPaneHorizontalPadding = 28.dp
private val RightPaneVerticalPadding = 18.dp
private val LeftRailCardSpacing = 10.dp
private val CategoryCardShape = RoundedCornerShape(SettingsSecondaryCardRadius)
private val SubItemIndent = 12.dp

/**
 * Two-panel Apple-TV-style Settings hub with a single-expand cascade left
 * rail. Each top-level category is rendered as its own enclosed card (rounded
 * border + subtle elevation). Tapping a category toggles its expansion in
 * place — only one category can be open at a time, so opening Playback
 * automatically collapses Appearance.
 *
 * Selecting a sub-item either renders its content in the right pane (Content
 * type) or invokes a navigation lambda for sub-screens (NavAction type — e.g.
 * Addons, Trakt). The right pane itself is wrapped in an enclosed workspace
 * surface so it visually matches the category cards.
 */
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onNavigateToAuthQrSignIn: () -> Unit,
    onNavigateToManageProfiles: () -> Unit,
    onNavigateToSupportersContributors: () -> Unit,
    onNavigateToLicensesAttributions: () -> Unit,
    onNavigateToAddons: () -> Unit,
    onNavigateToTrakt: () -> Unit,
    onNavigateToCollections: () -> Unit,
) {
    BackHandler { onBack() }

    val categories = remember { settingsCategories() }
    // Single-expand accordion: at most one category open at a time. Tapping
    // a different category collapses the previous one. Tapping the same
    // category collapses it.
    var expandedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedContentSubId by remember { mutableStateOf("") }

    val backFocusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(containerColor = NuvioColors.Background),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            LeftRail(
                categories = categories,
                expandedCategoryId = expandedCategoryId,
                selectedContentSubId = selectedContentSubId,
                onToggleCategory = { id ->
                    expandedCategoryId = if (id == expandedCategoryId) null else id
                },
                onSelectContentSub = { catId, subId ->
                    // Auto-expand the parent so the visible selection stays
                    // anchored to its category card even if the user came
                    // back later with a collapsed rail.
                    expandedCategoryId = catId
                    selectedContentSubId = subId
                },
                onNavAction = { action ->
                    when (action) {
                        NavTarget.ADDONS -> onNavigateToAddons()
                        NavTarget.TRAKT -> onNavigateToTrakt()
                        NavTarget.COLLECTIONS -> onNavigateToCollections()
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
                onNavigateToLicensesAttributions = onNavigateToLicensesAttributions,
            )
        }
    }
}

// ── Left rail (multi-expand cascade, each category is its own card) ─────────

@Composable
private fun LeftRail(
    categories: List<HubCategory>,
    expandedCategoryId: String?,
    selectedContentSubId: String,
    onToggleCategory: (String) -> Unit,
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

        // One LazyColumn item per category card. Each card holds the
        // category header row plus, when expanded, the indented sub-items.
        // Single-expand accordion: only one card is open at a time.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(LeftRailCardSpacing),
        ) {
            items(items = categories, key = { it.id }) { category ->
                val isExpanded = category.id == expandedCategoryId
                val isDirect = category.directContentId != null
                CategoryCard(
                    category = category,
                    isExpanded = isExpanded,
                    selectedContentSubId = selectedContentSubId,
                    onToggleCategory = {
                        if (isDirect) {
                            // Direct categories never expand — clicking
                            // routes straight to their content.
                            onSelectContentSub(category.id, category.directContentId!!)
                        } else {
                            onToggleCategory(category.id)
                        }
                    },
                    onSelectSub = { sub ->
                        when (sub) {
                            is HubSubItem.Content -> onSelectContentSub(category.id, sub.id)
                            is HubSubItem.NavAction -> onNavAction(sub.target)
                        }
                    },
                    upFocus = if (category == categories.first()) backFocusRequester else null,
                )
            }
        }
    }
}

/**
 * Enclosed card containing one settings category. Renders the category row
 * always; renders its sub-items below when expanded. The card itself has a
 * rounded border + elevated background so the rail reads as a stack of
 * distinct boxes rather than a flat list.
 */
@Composable
private fun CategoryCard(
    category: HubCategory,
    isExpanded: Boolean,
    selectedContentSubId: String,
    onToggleCategory: () -> Unit,
    onSelectSub: (HubSubItem) -> Unit,
    upFocus: FocusRequester?,
) {
    val isDirect = category.directContentId != null
    // Direct categories are highlighted as "expanded" whenever their
    // content is the one currently showing in the right pane — gives
    // the user the same visual feedback as a selected sub-item row
    // would, without the cascade.
    val directSelected = isDirect && category.directContentId == selectedContentSubId
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CategoryCardShape)
            .background(NuvioColors.BackgroundCard)
            .border(
                width = 1.dp,
                color = NuvioColors.Border,
                shape = CategoryCardShape,
            )
            .padding(vertical = 6.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CategoryRailRow(
            category = category,
            isExpanded = if (isDirect) directSelected else isExpanded,
            // Direct categories never render a caret — they're not a
            // cascade, just a leaf row.
            showCaret = !isDirect,
            onSelect = onToggleCategory,
            upFocus = upFocus,
        )
        if (!isDirect && isExpanded) {
            category.subItems.forEach { sub ->
                SubItemRailRow(
                    sub = sub,
                    isSelected = when (sub) {
                        is HubSubItem.Content -> sub.id == selectedContentSubId
                        is HubSubItem.NavAction -> false
                    },
                    onSelect = { onSelectSub(sub) },
                )
            }
        }
    }
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
    isExpanded: Boolean,
    onSelect: () -> Unit,
    upFocus: FocusRequester?,
    /**
     * Whether to draw the caret indicator on the right of the row.
     * Cascade categories (subItems list) show it; direct-content
     * categories (Advanced, About) don't.
     */
    showCaret: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(SettingsSecondaryCardRadius - 4.dp)
    val accent = NuvioColors.Secondary
    // Focused row gets a solid accent fill with white text/icons so D-pad
    // navigation is unambiguous (the prior transparent focus was invisible
    // on the light background). Expanded but unfocused rows keep the accent
    // tint on text/icon only.
    val containerColor by animateColorAsState(
        targetValue = if (focused) accent else Color.Transparent,
        animationSpec = tween(160),
        label = "categoryRowBg",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            focused -> Color.White
            isExpanded -> accent
            else -> NuvioColors.TextSecondary
        },
        animationSpec = tween(160),
        label = "categoryRowText",
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            focused -> Color.White
            isExpanded -> accent
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
            containerColor = containerColor,
            focusedContainerColor = containerColor,
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
                fontWeight = if (focused || isExpanded) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (showCaret) {
                // Caret rotates to indicate expansion state — open ↓, closed →
                val caret = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight
                Icon(
                    imageVector = caret,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            }
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
    val rowShape = RoundedCornerShape(SettingsSecondaryCardRadius - 6.dp)
    val accent = NuvioColors.Secondary
    val containerColor by animateColorAsState(
        targetValue = when {
            focused -> accent
            isSelected -> accent.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "subItemRowBg",
    )
    val textColor = when {
        focused -> Color.White
        isSelected -> accent
        else -> NuvioColors.TextSecondary
    }

    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SubItemIndent, end = 4.dp)
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(rowShape),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
        ),
        border = CardDefaults.border(
            border = if (isSelected && !focused) Border(
                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                shape = rowShape,
            ) else Border.None,
            focusedBorder = Border.None,
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
            sub.leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = sub.label,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (sub is HubSubItem.NavAction) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = textColor,
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
    onNavigateToLicensesAttributions: () -> Unit,
) {
    // Open canvas — content renders directly on the background (no card /
    // border / surface). Medium-tight padding so it owns the full pane without
    // feeling boxed in or cramped.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = RightPaneHorizontalPadding, vertical = RightPaneVerticalPadding),
    ) {
        if (contentSubId.isBlank()) {
            // No category opened yet — leave blank so the left rail draws
            // the user's attention.
            return@Box
        }
        SubItemContent(
            contentSubId = contentSubId,
            onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn,
            onNavigateToManageProfiles = onNavigateToManageProfiles,
            onNavigateToSupportersContributors = onNavigateToSupportersContributors,
            onNavigateToLicensesAttributions = onNavigateToLicensesAttributions,
        )
    }
}

@Composable
private fun SubItemContent(
    contentSubId: String,
    onNavigateToAuthQrSignIn: () -> Unit,
    onNavigateToManageProfiles: () -> Unit,
    onNavigateToSupportersContributors: () -> Unit,
    onNavigateToLicensesAttributions: () -> Unit,
) {
    when (contentSubId) {
        // Appearance
        "appearance.feel" -> NavigationFeelContent()
        "appearance.topbar" -> {
            // Read the current Feel inline so this screen reacts to live
            // changes without piping yet another prop through SettingsHub.
            val vm: NavigationFeelViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val feel by vm.feel.collectAsStateWithLifecycle()
            TopBarSettingsContent(feel = feel)
        }
        "appearance.global" -> GlobalSettingsContent()
        "appearance.layout" -> NewLayoutSettingsContent(mode = NewLayoutContentMode.LAYOUT_ONLY)
        "appearance.rows" -> NewLayoutSettingsContent(mode = NewLayoutContentMode.ROWS_ONLY)
        "appearance.continue_watching" -> ContinueWatchingSettingsContent()
        "appearance.theme" -> ThemeSettingsContent()
        "appearance.trailers" -> TrailersSettingsContent()
        "appearance.siderail" -> SideRailSettingsContent()
        "appearance.detailpage" -> DetailPageSettingsContent()
        // Extensions
        "extensions.plugins" -> PluginsInlineWrapper()
        "extensions.tmdb" -> TmdbSettingsContent()
        "extensions.mdblist" -> MDBListSettingsContent()
        "extensions.animeskip" -> AnimeSkipSettingsContent()
        "extensions.debrid" -> DebridSettingsContent()
        // Accounts & Sync
        "accounts.account" -> AccountSettingsInline(onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn)
        "accounts.profiles" -> ProfileSettingsContent(onManageProfiles = onNavigateToManageProfiles)
        // Playback
        "playback.main" -> PlaybackSettingsContent()
        "playback.buffer" -> BufferNetworkSettingsContent()
        // Advanced
        "advanced.network" -> AdvancedSettingsContent()
        "advanced.about" -> AboutSettingsContent(
            onNavigateToSupportersContributors = onNavigateToSupportersContributors,
            onNavigateToLicensesAttributions = onNavigateToLicensesAttributions
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
    /**
     * When non-null, tapping the category directly renders the content
     * with this id in the right pane instead of expanding sub-items.
     * Used by Advanced (Network) and About — both have a single pane,
     * so a cascade would be redundant.
     */
    val directContentId: String? = null,
)

private sealed interface HubSubItem {
    val id: String
    val label: String
    /**
     * Optional leading icon. NavAction rows that point at a recognisable
     * surface (Collections, future Plugins-as-NavAction, etc.) can set
     * one to make the row visually distinct from plain Content rows.
     * Defaults to null so existing rows stay untouched.
     */
    val leadingIcon: ImageVector? get() = null

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
        val icon: ImageVector? = null,
    ) : HubSubItem {
        override val leadingIcon: ImageVector? get() = icon
    }
}

private enum class NavTarget { ADDONS, TRAKT, COLLECTIONS }

private fun settingsCategories(): List<HubCategory> = listOf(
    HubCategory(
        id = "appearance",
        label = "Appearance",
        icon = Icons.Default.Palette,
        // Order — Feel / Layout / Rows / Trailers / Top Bar / Side Rail /
        // Global / Theme / Continue Watching / Detail Page. (Cards removed —
        // its controls moved to Trailers + Theme.)
        subItems = listOf(
            HubSubItem.Content("appearance.feel", "Feel"),
            HubSubItem.Content("appearance.layout", "Layout"),
            HubSubItem.Content("appearance.rows", "Rows"),
            HubSubItem.Content("appearance.trailers", "Trailers"),
            HubSubItem.Content("appearance.topbar", "Top Bar"),
            HubSubItem.Content("appearance.siderail", "Side Rail"),
            HubSubItem.Content("appearance.global", "Global"),
            HubSubItem.Content("appearance.theme", "Theme"),
            HubSubItem.Content("appearance.continue_watching", "Continue Watching"),
            HubSubItem.Content("appearance.detailpage", "Detail Page"),
        ),
    ),
    HubCategory(
        id = "extensions",
        label = "Extensions",
        icon = Icons.Default.Extension,
        subItems = listOf(
            HubSubItem.Content("extensions.plugins", "Plugins"),
            HubSubItem.NavAction("extensions.addons", "Addons", NavTarget.ADDONS),
            HubSubItem.NavAction(
                id = "extensions.collections",
                label = "Collections",
                target = NavTarget.COLLECTIONS,
                icon = Icons.Default.Folder,
            ),
            HubSubItem.Content("extensions.tmdb", "TMDB"),
            HubSubItem.Content("extensions.mdblist", "MDBList"),
            HubSubItem.Content("extensions.animeskip", "AnimeSkip"),
            HubSubItem.Content("extensions.debrid", "Debrid"),
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
            HubSubItem.Content("playback.buffer", "Buffer & Network"),
        ),
    ),
    HubCategory(
        id = "advanced",
        label = "Advanced",
        icon = Icons.Default.Settings,
        subItems = emptyList(),
        // Direct-content: tapping Advanced shows the Network pane
        // straight in the right pane — no cascade (Task 7).
        directContentId = "advanced.network",
    ),
    HubCategory(
        id = "about",
        label = "About",
        icon = Icons.Default.Info,
        subItems = emptyList(),
        // About is the bottom-most main settings category and shows
        // its own pane directly when selected (Task 7).
        directContentId = "advanced.about",
    ),
)
