@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.ui.theme.NuvioColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Global fallback layout settings + app-wide display preferences. This is the
 * floor of the per-row / per-screen / global resolution hierarchy. When a
 * screen has no scope-specific override set, these values apply.
 *
 * The "Display Preferences" and "Focused Poster" sections used to live under
 * the per-pill Layout settings; they were moved here when Layout was
 * simplified to just the per-layout-type toggles (Fullscreen Hero / Show
 * Hero / Focus Item Gradient).
 */
data class GlobalSettingsUiState(
    val layout: HomeLayout = HomeLayout.MODERN,
    val cardStyle: LayoutCardStyle = LayoutCardStyle.POSTER,
    val focusedPosterTrailerEnabled: Boolean = false,
    val focusedPosterTrailerTarget: FocusedPosterTrailerPlaybackTarget =
        FocusedPosterTrailerPlaybackTarget.HERO_MEDIA,
    // Display preferences (formerly View Options under Layout)
    val posterLabelsEnabled: Boolean = true,
    val catalogAddonNameEnabled: Boolean = true,
    val catalogTypeSuffixEnabled: Boolean = true,
    val hideUnreleasedContent: Boolean = false,
    val searchDiscoverEnabled: Boolean = true,
    val focusHighlightEnabled: Boolean = true,
    val posterGlowEnabled: Boolean = true,
    val cardFocusStyle: com.nuvio.tv.domain.model.CardFocusStyle =
        com.nuvio.tv.domain.model.CardFocusStyle.ACCENT,
    // Focused-poster expand (formerly under Layout's Focused Poster section)
    val focusedPosterExpandEnabled: Boolean = true,
    val focusedPosterExpandDelaySeconds: Int = 3,
    val focusedPosterTrailerMuted: Boolean = true,
)

@HiltViewModel
class GlobalSettingsViewModel @Inject constructor(
    private val prefs: LayoutPreferenceDataStore,
) : ViewModel() {

    val uiState: StateFlow<GlobalSettingsUiState> = combine(
        combine(
            prefs.globalLayout,
            prefs.globalCardStyle,
            prefs.globalFocusedPosterTrailerEnabled,
            prefs.globalFocusedPosterTrailerTarget,
        ) { layout, cardStyle, trailerEnabled, trailerTarget ->
            arrayOf<Any?>(layout, cardStyle, trailerEnabled, trailerTarget)
        },
        combine(
            prefs.posterLabelsEnabled,
            prefs.catalogAddonNameEnabled,
            prefs.catalogTypeSuffixEnabled,
            prefs.hideUnreleasedContent,
            prefs.searchDiscoverEnabled,
            prefs.posterGlowEnabled,
            prefs.focusHighlightEnabled,
        ) { args ->
            // `combine` with > 5 sources returns Array<Boolean>; unpack
            // by index. Order must match the producer list above.
            booleanArrayOf(args[0], args[1], args[2], args[3], args[4], args[5], args[6])
        },
        combine(
            prefs.focusedPosterBackdropExpandEnabled,
            prefs.focusedPosterBackdropExpandDelaySeconds,
            prefs.focusedPosterBackdropTrailerMuted,
            prefs.cardFocusStyle,
        ) { expandEnabled, expandDelay, muted, focusStyle ->
            arrayOf<Any?>(expandEnabled, expandDelay, muted, focusStyle)
        },
    ) { core, display, focusedExpand ->
        GlobalSettingsUiState(
            layout = core[0] as HomeLayout,
            cardStyle = core[1] as LayoutCardStyle,
            focusedPosterTrailerEnabled = core[2] as Boolean,
            focusedPosterTrailerTarget = core[3] as FocusedPosterTrailerPlaybackTarget,
            posterLabelsEnabled = display[0],
            catalogAddonNameEnabled = display[1],
            catalogTypeSuffixEnabled = display[2],
            hideUnreleasedContent = display[3],
            searchDiscoverEnabled = display[4],
            posterGlowEnabled = display[5],
            focusHighlightEnabled = display[6],
            focusedPosterExpandEnabled = focusedExpand[0] as Boolean,
            focusedPosterExpandDelaySeconds = focusedExpand[1] as Int,
            focusedPosterTrailerMuted = focusedExpand[2] as Boolean,
            cardFocusStyle = focusedExpand[3] as com.nuvio.tv.domain.model.CardFocusStyle,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSettingsUiState())

    fun setLayout(layout: HomeLayout) = viewModelScope.launch { prefs.setGlobalLayout(layout) }
    fun setCardStyle(style: LayoutCardStyle) = viewModelScope.launch { prefs.setGlobalCardStyle(style) }
    fun setTrailerEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setGlobalFocusedPosterTrailerEnabled(enabled)
    }
    fun setTrailerTarget(target: FocusedPosterTrailerPlaybackTarget) = viewModelScope.launch {
        prefs.setGlobalFocusedPosterTrailerTarget(target)
    }
    // Display preferences
    fun setPosterLabelsEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setPosterLabelsEnabled(enabled)
    }
    fun setCatalogAddonNameEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setCatalogAddonNameEnabled(enabled)
    }
    fun setCatalogTypeSuffixEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setCatalogTypeSuffixEnabled(enabled)
    }
    fun setHideUnreleasedContent(enabled: Boolean) = viewModelScope.launch {
        prefs.setHideUnreleasedContent(enabled)
    }
    fun setSearchDiscoverEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSearchDiscoverEnabled(enabled)
    }
    val sideRailSearchVisible = prefs.sideRailSearchVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val sideRailDiscoverVisible = prefs.sideRailDiscoverVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val sideRailMyStuffVisible = prefs.sideRailMyStuffVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val sideRailPillChannelsVisible = prefs.sideRailPillChannelsVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val sideRailSettingsVisible = prefs.sideRailSettingsVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setSideRailItemVisible(id: String, visible: Boolean) = viewModelScope.launch {
        prefs.setSideRailItemVisible(id, visible)
    }
    val sideRailOrder = prefs.sideRailOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            com.nuvio.tv.data.local.LayoutPreferenceDataStore.DEFAULT_SIDE_RAIL_ORDER)
    val sideRailDisplayModes = prefs.sideRailDisplayModes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    fun moveSideRailItem(id: String, direction: Int) = viewModelScope.launch {
        val current = sideRailOrder.value.toMutableList()
        val idx = current.indexOf(id)
        if (idx < 0) return@launch
        val target = (idx + direction).coerceIn(0, current.lastIndex)
        if (target == idx) return@launch
        current.removeAt(idx)
        current.add(target, id)
        prefs.setSideRailOrder(current)
    }
    fun cycleSideRailDisplayMode(id: String) = viewModelScope.launch {
        val modes = sideRailDisplayModes.value
        val current = modes[id] ?: "icon_and_text"
        val next = when (current) {
            "icon_and_text" -> "icon_only"
            "icon_only" -> "text_only"
            else -> "icon_and_text"
        }
        prefs.setSideRailDisplayMode(id, next)
    }
    fun setPosterGlowEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setPosterGlowEnabled(enabled)
    }
    fun setFocusHighlightEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setFocusHighlightEnabled(enabled)
    }
    fun cycleCardFocusStyle() = viewModelScope.launch {
        prefs.setCardFocusStyle(uiState.value.cardFocusStyle.next())
    }
    // Focused poster expand
    fun setFocusedPosterExpandEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setFocusedPosterBackdropExpandEnabled(enabled)
    }
    fun setFocusedPosterExpandDelaySeconds(seconds: Int) = viewModelScope.launch {
        prefs.setFocusedPosterBackdropExpandDelaySeconds(seconds)
    }
    fun setFocusedPosterTrailerMuted(muted: Boolean) = viewModelScope.launch {
        prefs.setFocusedPosterBackdropTrailerMuted(muted)
    }
}

@Composable
fun GlobalSettingsContent(viewModel: GlobalSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "global_header") {
            SettingsDetailHeader(
                title = "Global",
                subtitle = "Fallback layout values and app-wide display preferences.",
            )
        }
        item(key = "global_layout_section") {
            GlobalSection(title = "Layout") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeLayout.entries.forEach { layout ->
                        ChoicePill(
                            label = layout.displayLabel(),
                            isSelected = layout == state.layout,
                            onClick = { viewModel.setLayout(layout) },
                        )
                    }
                }
            }
        }
        item(key = "global_cardstyle_section") {
            GlobalSection(title = "Poster card style") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LayoutCardStyle.entries.forEach { style ->
                        ChoicePill(
                            label = if (style == LayoutCardStyle.POSTER) "Poster" else "Landscape",
                            isSelected = style == state.cardStyle,
                            onClick = { viewModel.setCardStyle(style) },
                        )
                    }
                }
            }
        }
        item(key = "global_display_prefs_section") {
            GlobalSection(title = "Display preferences") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlobalToggleRow(
                        title = "Poster Labels",
                        subtitle = "Show titles beneath catalog posters.",
                        checked = state.posterLabelsEnabled,
                        onCheckedChange = viewModel::setPosterLabelsEnabled,
                    )
                    GlobalToggleRow(
                        title = "Show Addon Name",
                        subtitle = "Annotate each catalog row with the addon it came from.",
                        checked = state.catalogAddonNameEnabled,
                        onCheckedChange = viewModel::setCatalogAddonNameEnabled,
                    )
                    GlobalToggleRow(
                        title = "Catalog Type Suffix",
                        subtitle = "Append \" – Movie\" / \" – Series\" to each catalog title.",
                        checked = state.catalogTypeSuffixEnabled,
                        onCheckedChange = viewModel::setCatalogTypeSuffixEnabled,
                    )
                    GlobalToggleRow(
                        title = "Hide Unreleased",
                        subtitle = "Filter out items whose release date is in the future.",
                        checked = state.hideUnreleasedContent,
                        onCheckedChange = viewModel::setHideUnreleasedContent,
                    )
                    GlobalToggleRow(
                        title = "Show Discover",
                        subtitle = "Surface the Discover side rail entry.",
                        checked = state.searchDiscoverEnabled,
                        onCheckedChange = viewModel::setSearchDiscoverEnabled,
                    )
                }
            }
        }
        // Poster Glow + Card Focus Style moved to Settings → Appearance →
        // Theme; trailer autoplay moved to Settings → Appearance → Trailers
        // ([TrailersSettingsContent]); expand-to-backdrop moved to the Rows
        // Manager's per-scope/per-row expand controls. The "Cards" pane is gone.
    }
}

// ── Side Rail sub-item ──────────────────────────────────────────────────────
//
// Placeholder Appearance pane for SideRail-specific settings. Currently
// hosts the Show Discover toggle (which the SideRail / Profile Overlay
// honours) and a hint about more controls landing later. Reuses the
// existing GlobalSettingsViewModel since the underlying flag is the
// same searchDiscoverEnabled.

@Composable
fun SideRailSettingsContent(viewModel: GlobalSettingsViewModel = hiltViewModel()) {
    val searchVisible by viewModel.sideRailSearchVisible.collectAsStateWithLifecycle()
    val discoverVisible by viewModel.sideRailDiscoverVisible.collectAsStateWithLifecycle()
    val myStuffVisible by viewModel.sideRailMyStuffVisible.collectAsStateWithLifecycle()
    val pillChannelsVisible by viewModel.sideRailPillChannelsVisible.collectAsStateWithLifecycle()
    val settingsVisible by viewModel.sideRailSettingsVisible.collectAsStateWithLifecycle()
    val order by viewModel.sideRailOrder.collectAsStateWithLifecycle()
    val displayModes by viewModel.sideRailDisplayModes.collectAsStateWithLifecycle()

    val labels = mapOf(
        "profile" to "Profile", "search" to "Search", "home" to "Home",
        "discover" to "Discover", "my_stuff" to "My Stuff",
        "pill_channels" to "Pill Channels", "settings" to "Settings",
    )
    val icons = mapOf(
        "profile" to Icons.Default.Home,
        "search" to Icons.Default.Search,
        "home" to Icons.Default.Home,
        "discover" to Icons.Default.Explore,
        "my_stuff" to Icons.Default.Bookmark,
        "pill_channels" to Icons.Default.Tune,
        "settings" to Icons.Default.Settings,
    )
    val visibilityMap = mapOf(
        "search" to searchVisible, "discover" to discoverVisible,
        "my_stuff" to myStuffVisible, "pill_channels" to pillChannelsVisible,
        "settings" to settingsVisible,
    )
    val canToggle = setOf("search", "discover", "my_stuff", "pill_channels")
    val visibleCount = visibilityMap.count { it.value }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "siderail_header") {
            SettingsDetailHeader(
                title = "Side Rail",
                subtitle = "Reorder items, change display mode, or hide them from the Legacy side rail.",
            )
        }
        items(order.size, key = { order[it] }) { index ->
            val id = order[index]
            val label = labels[id] ?: id
            val visible = visibilityMap[id] ?: true
            val toggleable = id in canToggle
            val visibilityEnabled = toggleable && (!visible || visibleCount > 2)
            val mode = displayModes[id] ?: "icon_and_text"
            val accent = NuvioColors.Secondary
            val isCustomMode = mode != "icon_and_text"
            val modeLabel = when (mode) {
                "icon_only" -> "Icon only"
                "text_only" -> "Text only"
                else -> "Icon + text"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NuvioColors.BackgroundCard)
                    .border(
                        width = 1.dp,
                        color = NuvioColors.Border,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val icon = icons[id]
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (visible) NuvioColors.TextPrimary else NuvioColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (visible) NuvioColors.TextPrimary else NuvioColors.TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                SideRailIconChipButton(
                    icon = Icons.Default.ArrowUpward,
                    contentDesc = "Move up",
                    enabled = index > 0,
                    onClick = { viewModel.moveSideRailItem(id, -1) },
                )
                SideRailIconChipButton(
                    icon = Icons.Default.ArrowDownward,
                    contentDesc = "Move down",
                    enabled = index < order.lastIndex,
                    onClick = { viewModel.moveSideRailItem(id, 1) },
                )
                SideRailDisplayModePill(
                    modeLabel = modeLabel,
                    isCustomMode = isCustomMode,
                    accent = accent,
                    onClick = { viewModel.cycleSideRailDisplayMode(id) },
                )
                if (toggleable) {
                    Switch(
                        checked = visible,
                        enabled = visibilityEnabled,
                        onCheckedChange = { viewModel.setSideRailItemVisible(id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SideRailIconChipButton(
    icon: ImageVector,
    contentDesc: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val containerColor = when {
        !enabled -> Color.White.copy(alpha = 0.04f)
        focused -> Color.White.copy(alpha = 0.20f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val tint = if (enabled) NuvioColors.TextPrimary else NuvioColors.TextSecondary.copy(alpha = 0.4f)
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .size(34.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(androidx.compose.foundation.shape.CircleShape),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                shape = androidx.compose.foundation.shape.CircleShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SideRailDisplayModePill(
    modeLabel: String,
    isCustomMode: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val containerColor = when {
        focused -> accent.copy(alpha = 0.35f)
        isCustomMode -> accent.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .height(34.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(RoundedCornerShape(17.dp)),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
        ),
        border = CardDefaults.border(
            border = if (isCustomMode) Border(
                border = BorderStroke(1.dp, accent),
                shape = RoundedCornerShape(17.dp),
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(17.dp),
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.TextFields,
                contentDescription = null,
                tint = if (isCustomMode) accent else NuvioColors.TextPrimary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = NuvioColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Trailers sub-item ───────────────────────────────────────────────────────
//
// Focused-poster trailer autoplay, moved out of the old "Cards" pane. Reuses
// the same GlobalSettingsViewModel / DataStore keys — only the UI location
// changed. (Poster Glow + Card Focus Style moved to Theme; Expand-to-backdrop
// moved to the Rows Manager's per-scope/per-row expand controls.)

@Composable
fun TrailersSettingsContent(viewModel: GlobalSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "trailers_header") {
            SettingsDetailHeader(
                title = "Trailers",
                subtitle = "Autoplay a trailer preview on the focused poster.",
            )
        }
        item(key = "trailers_section") {
            GlobalSection(title = "Focused poster trailer") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlobalToggleRow(
                        title = "Auto-play trailer",
                        subtitle = "Play trailer preview on focused poster.",
                        checked = state.focusedPosterTrailerEnabled,
                        onCheckedChange = viewModel::setTrailerEnabled,
                    )
                    if (state.focusedPosterTrailerEnabled) {
                        Text(
                            text = "Trailer playback target",
                            style = MaterialTheme.typography.titleSmall,
                            color = NuvioColors.TextSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusedPosterTrailerPlaybackTarget.entries.forEach { target ->
                                ChoicePill(
                                    label = target.displayLabel(),
                                    isSelected = target == state.focusedPosterTrailerTarget,
                                    onClick = { viewModel.setTrailerTarget(target) },
                                )
                            }
                        }
                        GlobalToggleRow(
                            title = "Mute trailer autoplay",
                            subtitle = "Play focused trailers silently.",
                            checked = state.focusedPosterTrailerMuted,
                            onCheckedChange = viewModel::setFocusedPosterTrailerMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = NuvioColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun GlobalToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    com.nuvio.tv.ui.components.AccentToggleRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
internal fun ChoicePill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val containerColor = when {
        isSelected -> NuvioColors.FocusBackground
        focused -> Color.White.copy(alpha = 0.12f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
        ),
        border = CardDefaults.border(
            border = if (isSelected) Border(
                border = BorderStroke(1.dp, NuvioColors.FocusRing),
                shape = shape,
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = shape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = NuvioColors.TextPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

internal fun HomeLayout.displayLabel(): String = when (this) {
    HomeLayout.MODERN -> "Modern"
    HomeLayout.CLASSIC -> "Classic"
    HomeLayout.GRID -> "Grid"
    HomeLayout.SPOTLIGHT -> "Spotlight"
}

internal fun FocusedPosterTrailerPlaybackTarget.displayLabel(): String = when (this) {
    FocusedPosterTrailerPlaybackTarget.HERO_MEDIA -> "Hero Media"
    FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD -> "Inline"
}
