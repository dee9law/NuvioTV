@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.Feel
import com.nuvio.tv.ui.theme.NuvioColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Picker for the app-shell navigation style. Selecting a card writes the
 * choice through to [LayoutPreferenceDataStore.navigationFeel]; the shell
 * recomposes on the spot once anything downstream observes that flow.
 */
@HiltViewModel
class NavigationFeelViewModel @Inject constructor(
    private val prefs: LayoutPreferenceDataStore,
) : ViewModel() {

    val feel: StateFlow<Feel> = prefs.navigationFeel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Feel.MODERN)

    val topBarEnabled: StateFlow<Boolean> = prefs.topBarEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val modernTopBarEnabled: StateFlow<Boolean> = prefs.modernTopBarEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setFeel(feel: Feel) = viewModelScope.launch {
        prefs.setNavigationFeel(feel)
    }

    fun setTopBarEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setTopBarEnabled(enabled)
    }

    fun setModernTopBarEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setModernTopBarEnabled(enabled)
    }
}

@Composable
fun NavigationFeelContent(viewModel: NavigationFeelViewModel = hiltViewModel()) {
    val selected by viewModel.feel.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "feel_header") {
            SettingsDetailHeader(
                title = "Navigation Feel",
                subtitle = "Choose your preferred navigation style. Changes apply immediately.",
            )
        }
        item(key = "feel_cards") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FeelCard(
                    label = "Modern",
                    description = "Unified top bar with profile menu — no side rail.",
                    selected = selected == Feel.MODERN,
                    onSelect = { viewModel.setFeel(Feel.MODERN) },
                    preview = { ModernFeelPreview() },
                    modifier = Modifier.width(220.dp),
                )
                FeelCard(
                    label = "Legacy",
                    description = "Side rail + top bar (the previous Prime-style layout).",
                    selected = selected == Feel.LEGACY,
                    onSelect = { viewModel.setFeel(Feel.LEGACY) },
                    preview = { LegacyFeelPreview() },
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }
}

// ── Card ────────────────────────────────────────────────────────────────────

@Composable
private fun FeelCard(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val accent = NuvioColors.Secondary
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> accent
            focused -> accent.copy(alpha = 0.6f)
            else -> NuvioColors.Border
        },
        animationSpec = tween(160),
        label = "feelCardBorder",
    )

    Card(
        onClick = onSelect,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.BackgroundCard,
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, accent),
                shape = shape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Visual preview region — schematic representation of the shell.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp),
            ) {
                preview()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) accent else NuvioColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = NuvioColors.TextSecondary,
            )
        }
    }
}

// ── Schematic previews ──────────────────────────────────────────────────────

/** Modern: one unified TopBar with a small avatar dot at the far left. */
@Composable
private fun ModernFeelPreview() {
    val accent = NuvioColors.Secondary
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // TopBar mock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.4f)),
                )
            }
        }
        // Content rows
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }
    }
}

/** Legacy: vertical SideRail on the left + TopBar at top. */
@Composable
private fun LegacyFeelPreview() {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // SideRail mock
        Column(
            modifier = Modifier
                .width(16.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f)),
                )
            }
        }
        // Right column: top bar + rows
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // TopBar mock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(width = 16.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.4f)),
                    )
                }
            }
            // Content rows
            repeat(2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                )
            }
        }
    }
}
