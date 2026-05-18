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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.CategoryPill
import com.nuvio.tv.domain.model.CategoryPillOrderEntry
import com.nuvio.tv.domain.model.Feel
import com.nuvio.tv.domain.model.PillVisibility
import com.nuvio.tv.ui.components.CategoryPillsViewModel
import com.nuvio.tv.ui.theme.NuvioColors

/**
 * Top Bar settings screen — lets the user reorder category pills,
 * toggle their display mode (icons + text vs icons only), and remove
 * them from the bar (which moves them into the Profile Overlay's
 * Hidden Items section in Modern feel).
 *
 * Currently lists the four category pills (Home / Movies / TV Shows /
 * Collections) for both feels. The spec also calls for Modern to surface
 * Search / Discover / My Stuff / Settings here as toggleable TopBar
 * pills, but that requires extending the [CategoryPill] enum and the
 * overlay's static menu to a data-driven list — a follow-up.
 *
 * Reorder writes go to the same [CategoryPillsViewModel] used by Edit
 * Mode, so the two stay in sync.
 */
@Composable
fun TopBarSettingsContent(
    feel: Feel,
    viewModel: CategoryPillsViewModel = hiltViewModel(),
) {
    val order by viewModel.order.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "topbar_header") {
            SettingsDetailHeader(
                title = "Top Bar",
                subtitle = when (feel) {
                    Feel.MODERN -> "Reorder pills, compact them to icons-only, or move them into the Profile menu."
                    Feel.LEGACY -> "Reorder pills, compact them to icons-only, or remove them from the bar."
                },
            )
        }
        items(items = order, key = { it.pill.storageId }) { entry ->
            PillRow(
                entry = entry,
                topbarCount = order.count { it.visibility == PillVisibility.TOPBAR },
                canMoveUp = order.indexOf(entry) > 0,
                canMoveDown = order.indexOf(entry) < order.lastIndex,
                onMoveUp = { viewModel.moveUp(entry.pill) },
                onMoveDown = { viewModel.moveDown(entry.pill) },
                onToggleIconsOnly = { viewModel.setIconsOnly(entry.pill, !entry.iconsOnly) },
                onToggleVisible = { visible -> viewModel.setVisible(entry.pill, visible) },
            )
        }
    }
}

// ── Row ─────────────────────────────────────────────────────────────────────

@Composable
private fun PillRow(
    entry: CategoryPillOrderEntry,
    topbarCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleIconsOnly: () -> Unit,
    onToggleVisible: (Boolean) -> Unit,
) {
    val accent = NuvioColors.Secondary
    val visible = entry.visibility == PillVisibility.TOPBAR
    val isHome = entry.pill == CategoryPill.HOME
    // Home can never be hidden; ≥2 pills must stay visible to keep the
    // bar usable, so disable the toggle when removing this one would
    // leave only one (or fewer) pills on the bar.
    val visibilityEnabled = !isHome && (!visible || topbarCount > 2)
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
        Icon(
            imageVector = iconFor(entry.pill),
            contentDescription = null,
            tint = if (visible) NuvioColors.TextPrimary else NuvioColors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = entry.pill.displayLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = if (visible) NuvioColors.TextPrimary else NuvioColors.TextSecondary,
            fontWeight = FontWeight.Medium,
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
        // Single-button display-mode toggle. Outlined when icons-only,
        // filled when icon+text. Matches the spec's "single button press
        // toggles, not a dropdown".
        DisplayModePill(
            iconsOnly = entry.iconsOnly,
            onClick = onToggleIconsOnly,
            accent = accent,
        )
        Switch(
            checked = visible,
            enabled = visibilityEnabled,
            onCheckedChange = { onToggleVisible(it) },
        )
    }
}

@Composable
private fun DisplayModePill(
    iconsOnly: Boolean,
    onClick: () -> Unit,
    accent: Color,
) {
    var focused by remember { mutableStateOf(false) }
    val containerColor = when {
        focused -> accent.copy(alpha = 0.35f)
        iconsOnly -> accent.copy(alpha = 0.18f)
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
            border = if (iconsOnly) Border(
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
                tint = if (iconsOnly) accent else NuvioColors.TextPrimary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (iconsOnly) "Icon only" else "Icon + text",
                style = MaterialTheme.typography.labelSmall,
                color = NuvioColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun IconChipButton(
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

private fun iconFor(pill: CategoryPill): ImageVector = when (pill) {
    CategoryPill.HOME -> Icons.Default.Home
    CategoryPill.MOVIES -> Icons.Default.Movie
    CategoryPill.TV_SHOWS -> Icons.Default.Tv
    CategoryPill.COLLECTIONS -> Icons.Default.Folder
}
