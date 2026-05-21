@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
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
        ) { args ->
            // `combine` with > 5 sources returns Array<Boolean>; unpack
            // by index. Order must match the producer list above.
            booleanArrayOf(args[0], args[1], args[2], args[3], args[4], args[5])
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
    fun setPosterGlowEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setPosterGlowEnabled(enabled)
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
        // Poster Glow / Card Focus Style / Focused-poster settings moved
        // to Settings → Appearance → Cards (Task 5). See
        // [CardsSettingsContent].
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "siderail_header") {
            SettingsDetailHeader(
                title = "Side Rail",
                subtitle = "Legacy-feel left rail navigation entries. More controls coming soon.",
            )
        }
        item(key = "siderail_show_discover") {
            GlobalSection(title = "Entries") {
                GlobalToggleRow(
                    title = "Show Discover",
                    subtitle = "Surface the Discover entry in the side rail / overlay.",
                    checked = state.searchDiscoverEnabled,
                    onCheckedChange = viewModel::setSearchDiscoverEnabled,
                )
            }
        }
    }
}

// ── Cards sub-item ──────────────────────────────────────────────────────────
//
// Holds every card-related toggle / chip that used to live in Global. Same
// ViewModel/DataStore — just lifted into its own pane so Global stays slim.

@Composable
fun CardsSettingsContent(viewModel: GlobalSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "cards_header") {
            SettingsDetailHeader(
                title = "Cards",
                subtitle = "How content cards look and behave when focused.",
            )
        }
        item(key = "cards_focus_section") {
            GlobalSection(title = "Focus highlight") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlobalToggleRow(
                        title = "Poster Glow",
                        subtitle = "Soft coloured halo behind focused cards, sampled from " +
                            "the poster's dominant colour. Works with either focus border style.",
                        checked = state.posterGlowEnabled,
                        onCheckedChange = viewModel::setPosterGlowEnabled,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Card Focus Style",
                            style = MaterialTheme.typography.titleSmall,
                            color = NuvioColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Border treatment for focused cards. Bloom samples the poster " +
                                "colour for a tight luminous edge.",
                            style = MaterialTheme.typography.labelMedium,
                            color = NuvioColors.TextSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.nuvio.tv.domain.model.CardFocusStyle.entries.forEach { style ->
                                ChoicePill(
                                    label = style.displayLabel,
                                    isSelected = style == state.cardFocusStyle,
                                    onClick = {
                                        if (style != state.cardFocusStyle) viewModel.cycleCardFocusStyle()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "cards_focused_poster_section") {
            GlobalSection(title = "Focused poster") {
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
                    GlobalToggleRow(
                        title = "Expand to backdrop",
                        subtitle = "On focus, expand the poster into a full backdrop tile.",
                        checked = state.focusedPosterExpandEnabled,
                        onCheckedChange = viewModel::setFocusedPosterExpandEnabled,
                    )
                    if (state.focusedPosterExpandEnabled) {
                        Text(
                            text = "Expand delay: ${state.focusedPosterExpandDelaySeconds}s",
                            style = MaterialTheme.typography.titleSmall,
                            color = NuvioColors.TextSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 1, 2, 3, 5).forEach { seconds ->
                                ChoicePill(
                                    label = if (seconds == 0) "Instant" else "${seconds}s",
                                    isSelected = state.focusedPosterExpandDelaySeconds == seconds,
                                    onClick = { viewModel.setFocusedPosterExpandDelaySeconds(seconds) },
                                )
                            }
                        }
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
        modifier = Modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
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
