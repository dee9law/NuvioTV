package com.nuvio.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioColors

/**
 * Shared accent-fill focus highlight for D-pad navigation.
 *
 * Applies an animated background fill — `NuvioColors.Secondary` when the
 * element has focus, transparent otherwise — over the element's content.
 * Pair with a `Modifier.clickable {}` or focusable container so the element
 * actually receives focus.
 *
 * Does NOT add a border. Designed for plain buttons, list rows, dialog
 * actions, and toggle rows where the prior styling was border-only or
 * absent. TopNavigationBar pills and SideRail items have their own focus
 * systems and should not adopt this modifier.
 */
fun Modifier.accentFocusHighlight(
    shape: Shape = RoundedCornerShape(8.dp),
    enabled: Boolean = true,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val accent = NuvioColors.Secondary
    val target = if (focused && enabled) accent else Color.Transparent
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "accentFocusFill",
    )
    onFocusChanged { focused = it.isFocused || it.hasFocus }
        .clip(shape)
        .background(color = animated, shape = shape)
}

/**
 * Focusable row with a Switch on the right and the accent-fill highlight
 * applied to the whole row. The entire row toggles the switch on D-pad
 * Center / Enter — fixes the "no visible focus state on toggles" issue.
 */
@Composable
fun AccentToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // ON  → label uses the theme accent so the user can scan which
    //       toggles are active at a glance, even when the switch handle
    //       sits off-screen on small TVs.
    // OFF → label dims so a wall of disabled options reads as muted
    //       rather than "is this active or not?" ambiguous.
    val accent = NuvioColors.Secondary
    val titleColor = when {
        !enabled -> NuvioColors.TextPrimary.copy(alpha = 0.4f)
        checked -> accent
        else -> NuvioColors.TextSecondary
    }
    val subtitleColor = when {
        !enabled -> NuvioColors.TextSecondary.copy(alpha = 0.4f)
        checked -> NuvioColors.TextSecondary
        else -> NuvioColors.TextSecondary.copy(alpha = 0.6f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .accentFocusHighlight(enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                )
            }
        }
        // Switch is visual-only here — the entire row owns focus so the
        // accent fill marks the row as a single D-pad stop instead of
        // letting focus disappear into the bare switch handle.
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

/**
 * Focusable plain row (title + optional trailing slot) with the accent-fill
 * highlight. Use for settings list items, dialog action buttons, addon
 * action buttons, etc.
 */
@Composable
fun AccentActionRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .accentFocusHighlight(enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioColors.TextPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary,
                )
            }
        }
        trailing?.invoke()
    }
}
