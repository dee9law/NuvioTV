package com.nuvio.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioColors

// ── Sizing constants ─────────────────────────────────────────────────────────

private val OverlayWidth = 280.dp
private val ScrimColor = Color.Black.copy(alpha = 0.40f)
private val PanelBackground = Color.Black.copy(alpha = 0.85f)
private val PanelBorderColor = Color.White.copy(alpha = 0.10f)
private val DividerColor = Color.White.copy(alpha = 0.10f)
private val PanelShape = RoundedCornerShape(
    topStart = 0.dp, topEnd = 0.dp,
    bottomStart = 16.dp, bottomEnd = 16.dp,
)
private const val AnimationDurationMs = 250

/**
 * Identifies which built-in destination an overlay row navigates to. The
 * caller wires each to a real route in their nav graph.
 */
enum class ProfileOverlayDestination {
    SEARCH, DISCOVER, MY_STUFF, SETTINGS, MANAGE_PROFILES,
    /**
     * Opens the Pill Channels picker (formerly the "+ Networks" button on
     * the TopBar). Caller is responsible for popping the actual dropdown
     * popup — this destination just signals intent and dismisses the
     * overlay.
     */
    PILL_CHANNELS,
}

/**
 * Glassmorphism overlay panel that cascades down from the top-left corner.
 * Used by the Modern feel as the unified profile + secondary-nav surface
 * (Search / Discover / My Stuff / Settings + demoted pills).
 *
 * Self-contained: takes [visible] + [onDismiss] + per-row callbacks. The
 * caller decides when to show it (avatar Select or D-pad Left at carousel
 * index 0 — wired in F6).
 *
 * Background uses a solid 0.85-alpha black panel. True hardware blur
 * (RenderEffect, Android 12+) is intentionally NOT used here — TV builds
 * skip the cost for predictable frame pacing across devices.
 */
@Composable
fun ProfileOverlay(
    visible: Boolean,
    profileName: String?,
    profileColorHex: String?,
    profileAvatarUrl: String?,
    onDismiss: () -> Unit,
    onNavigate: (ProfileOverlayDestination) -> Unit,
    hiddenItems: List<ProfileOverlayHiddenItem> = emptyList(),
    onHiddenItemSelected: (ProfileOverlayHiddenItem) -> Unit = {},
    // D3: Long-press on a built-in row promotes that destination's pill
    // back to the TopBar (mirror of the "+ Hidden Items" click flow).
    // MainActivity routes this through CategoryPillsViewModel.promote.
    onPromoteBuiltin: (ProfileOverlayDestination) -> Unit = {},
    // D3: Long-press on a hidden item also promotes — same destination
    // as the click-promote flow but discoverable from any pill in the
    // section.
    onPromoteHiddenItem: (ProfileOverlayHiddenItem) -> Unit = {},
    // Gate flags wire each built-in destination row to whether the
    // corresponding [CategoryPill] is currently demoted to the drawer.
    // When a pill is promoted to the TopBar its overlay row hides — the
    // user reaches it via the pill instead.
    showSearch: Boolean = true,
    showDiscover: Boolean = true,
    showMyStuff: Boolean = true,
    showSettings: Boolean = true,
    showPillChannels: Boolean = true,
) {
    // BackHandler is composition-scoped, so it must mount/unmount with the
    // overlay rather than living above AnimatedVisibility.
    if (visible) {
        BackHandler(enabled = true, onBack = onDismiss)
    }

    // Scrim — covers the whole screen, dismiss on tap, fades with the panel.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(AnimationDurationMs, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(AnimationDurationMs, easing = FastOutSlowInEasing)),
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(AnimationDurationMs, easing = FastOutSlowInEasing),
        ) { fullHeight -> -fullHeight / 4 } + fadeIn(
            tween(AnimationDurationMs, easing = FastOutSlowInEasing),
        ),
        exit = slideOutVertically(
            animationSpec = tween(AnimationDurationMs, easing = FastOutSlowInEasing),
        ) { fullHeight -> -fullHeight / 4 } + fadeOut(
            tween(AnimationDurationMs, easing = FastOutSlowInEasing),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProfileOverlayPanel(
                profileName = profileName,
                profileColorHex = profileColorHex,
                profileAvatarUrl = profileAvatarUrl,
                onDismiss = onDismiss,
                onNavigate = onNavigate,
                hiddenItems = hiddenItems,
                onHiddenItemSelected = onHiddenItemSelected,
                onPromoteBuiltin = onPromoteBuiltin,
                onPromoteHiddenItem = onPromoteHiddenItem,
                showSearch = showSearch,
                showDiscover = showDiscover,
                showMyStuff = showMyStuff,
                showSettings = showSettings,
                showPillChannels = showPillChannels,
            )
        }
    }
}

/**
 * One demoted pill rendered in the overlay's "Hidden Items" section. The
 * caller decides how to act on a selection (in F10 this re-promotes the
 * pill back to the TopBar).
 */
data class ProfileOverlayHiddenItem(
    val id: String,
    val label: String,
)

// ── Panel ───────────────────────────────────────────────────────────────────

@Composable
private fun ProfileOverlayPanel(
    profileName: String?,
    profileColorHex: String?,
    profileAvatarUrl: String?,
    onDismiss: () -> Unit,
    onNavigate: (ProfileOverlayDestination) -> Unit,
    hiddenItems: List<ProfileOverlayHiddenItem>,
    onHiddenItemSelected: (ProfileOverlayHiddenItem) -> Unit,
    onPromoteBuiltin: (ProfileOverlayDestination) -> Unit,
    onPromoteHiddenItem: (ProfileOverlayHiddenItem) -> Unit,
    showSearch: Boolean,
    showDiscover: Boolean,
    showMyStuff: Boolean,
    showSettings: Boolean,
    showPillChannels: Boolean,
) {
    val firstRowFocus = remember { FocusRequester() }
    // Loop-wrap focus requesters — `wrapTopFr` is the very first focusable
    // row (Profile header), `wrapBottomFr` is the last focusable row
    // (either the last hidden item or, when no hidden items, the
    // Pill Channels row). Wired into focusProperties on the boundary rows
    // so D-pad Down on last → first and Up on first → last.
    val wrapTopFr = remember { FocusRequester() }
    val wrapBottomFr = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    // Auto-focus the first menu row when the overlay first appears so D-pad
    // navigation has a clear starting point.
    LaunchedEffect(Unit) {
        runCatching { firstRowFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .widthIn(max = OverlayWidth)
            .width(OverlayWidth)
            // 85% of screen height + a vertical scroll lets every overlay
            // row reach the user even when all four overlay-default pills
            // (Search / Discover / My Stuff / Settings) PLUS Pill Channels
            // PLUS a few demoted category pills are present — without this
            // the panel cut off the lower half of the list.
            .fillMaxHeight(0.85f)
            .clip(PanelShape)
            .background(PanelBackground)
            .border(width = 1.dp, color = PanelBorderColor, shape = PanelShape)
            // D-pad Right anywhere inside the panel dismisses — returns focus
            // to wherever the TopBar last had it.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Profile header is the topmost focusable — wrap-top target so
        // pressing Up at the very bottom (Pill Channels / hidden items)
        // loops here.
        ProfileHeaderRow(
            name = profileName,
            colorHex = profileColorHex,
            avatarUrl = profileAvatarUrl,
            onClick = {
                onNavigate(ProfileOverlayDestination.MANAGE_PROFILES)
            },
            wrapTopFr = wrapTopFr,
            wrapUpTo = wrapBottomFr,
        )
        HorizontalDivider()
        // Build the visible-row list up front so we can attach focus
        // wrappers (first-row autofocus, last-row wrap-down) without
        // hardcoding which builtin sits at each end. Promoted pills
        // (e.g. user moved SEARCH to the TopBar) hide their overlay row.
        data class BuiltinRow(
            val key: String,
            val icon: ImageVector,
            val label: String,
            val destination: ProfileOverlayDestination,
        )
        val builtins = buildList {
            if (showSearch) add(BuiltinRow("search", Icons.Default.Search, "Search", ProfileOverlayDestination.SEARCH))
            if (showDiscover) add(BuiltinRow("discover", Icons.Default.Explore, "Discover", ProfileOverlayDestination.DISCOVER))
            if (showMyStuff) add(BuiltinRow("my_stuff", Icons.Default.Bookmark, "My Stuff", ProfileOverlayDestination.MY_STUFF))
            if (showSettings) add(BuiltinRow("settings", Icons.Default.Settings, "Settings", ProfileOverlayDestination.SETTINGS))
            if (showPillChannels) add(BuiltinRow("pill_channels", Icons.Default.Tune, "Pill Channels", ProfileOverlayDestination.PILL_CHANNELS))
        }
        val lastBuiltinIsTail = hiddenItems.isEmpty()
        builtins.forEachIndexed { index, row ->
            val isFirst = index == 0
            val isLastVisibleOverall = lastBuiltinIsTail && index == builtins.lastIndex
            OverlayMenuRow(
                icon = row.icon,
                label = row.label,
                focusRequester = if (isFirst) firstRowFocus else null,
                onClick = { onNavigate(row.destination) },
                onLongClick = { onPromoteBuiltin(row.destination) },
                wrapBottomFr = if (isLastVisibleOverall) wrapBottomFr else null,
                wrapDownTo = if (isLastVisibleOverall) wrapTopFr else null,
            )
        }
        if (hiddenItems.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Hidden Items",
                style = MaterialTheme.typography.labelSmall,
                color = NuvioColors.TextSecondary,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
            )
            hiddenItems.forEachIndexed { index, item ->
                val isLast = index == hiddenItems.lastIndex
                OverlayHiddenItemRow(
                    item = item,
                    onClick = { onHiddenItemSelected(item) },
                    onLongClick = { onPromoteHiddenItem(item) },
                    wrapBottomFr = if (isLast) wrapBottomFr else null,
                    wrapDownTo = if (isLast) wrapTopFr else null,
                )
            }
        }
    }
}

// ── Rows ────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeaderRow(
    name: String?,
    colorHex: String?,
    avatarUrl: String?,
    onClick: () -> Unit,
    wrapTopFr: FocusRequester? = null,
    wrapUpTo: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val backgroundColor = if (focused) NuvioColors.Secondary.copy(alpha = 0.28f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(
                if (wrapTopFr != null) Modifier.focusRequester(wrapTopFr)
                else Modifier
            )
            .then(
                if (wrapUpTo != null) Modifier.focusProperties { up = wrapUpTo }
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileAvatarCircle(
            name = name.orEmpty(),
            colorHex = colorHex ?: "#1E88E5",
            size = 48.dp,
            avatarImageUrl = avatarUrl,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.orEmpty().ifBlank { "Profile" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Switch profile",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun OverlayMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    wrapBottomFr: FocusRequester? = null,
    wrapDownTo: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    val backgroundColor = if (focused) NuvioColors.Secondary.copy(alpha = 0.28f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .then(
                if (wrapBottomFr != null) Modifier.focusRequester(wrapBottomFr)
                else Modifier
            )
            .then(
                if (wrapDownTo != null) Modifier.focusProperties { down = wrapDownTo }
                else Modifier
            )
            // D-pad Center hold ≥ ~500ms fires onLongClick (promote-to-
            // TopBar). Mirrors the long-press detection on
            // CategoryTabItem so the overlay → bar flow feels symmetric.
            .then(
                if (onLongClick != null) Modifier.onPreviewKeyEvent { event ->
                    val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter
                    if (!isCenter) return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (!longPressFired && event.nativeKeyEvent.repeatCount == 1) {
                                longPressFired = true
                                onLongClick()
                            }
                            longPressFired
                        }
                        KeyEventType.KeyUp -> {
                            val wasLong = longPressFired
                            longPressFired = false
                            wasLong
                        }
                        else -> false
                    }
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) Color.White else Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OverlayHiddenItemRow(
    item: ProfileOverlayHiddenItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    wrapBottomFr: FocusRequester? = null,
    wrapDownTo: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    val backgroundColor = if (focused) NuvioColors.Secondary.copy(alpha = 0.28f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .then(
                if (wrapBottomFr != null) Modifier.focusRequester(wrapBottomFr)
                else Modifier
            )
            .then(
                if (wrapDownTo != null) Modifier.focusProperties { down = wrapDownTo }
                else Modifier
            )
            .then(
                if (onLongClick != null) Modifier.onPreviewKeyEvent { event ->
                    val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter
                    if (!isCenter) return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (!longPressFired && event.nativeKeyEvent.repeatCount == 1) {
                                longPressFired = true
                                onLongClick()
                            }
                            longPressFired
                        }
                        KeyEventType.KeyUp -> {
                            val wasLong = longPressFired
                            longPressFired = false
                            wasLong
                        }
                        else -> false
                    }
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Promote ${item.label} back to top bar",
            tint = NuvioColors.Secondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun HorizontalDivider() {
    Spacer(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor),
    )
}
