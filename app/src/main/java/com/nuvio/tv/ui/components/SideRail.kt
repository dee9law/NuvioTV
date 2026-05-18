package com.nuvio.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.LocalContentFocusRequester

// ── Sizing ────────────────────────────────────────────────────────────────────
//
// Collapsed footprint stays at 48dp so content layout is stable; the visible
// icons themselves are flush-left within the 8dp inset. Expanded panel slides
// over content at 168dp (sleeker than the original 220dp) without re-flowing
// anything below it.

private val CollapsedWidth      = 48.dp
private val ExpandedWidth       = 168.dp
private val CollapsedInset      = 8.dp
private val IconSizeDp          = 22.dp
private val ItemHeight          = 44.dp
private val ItemInnerHPadding   = 8.dp
private val ItemInnerVPadding   = 4.dp
private val IconLabelGap        = 14.dp
private val ItemSpacing         = 4.dp
private val SectionGap          = 28.dp
private val OuterVPadding       = 24.dp
private val AvatarCollapsedSize = 28.dp
private val AvatarExpandedSize  = 36.dp
private val ActiveHighlightShape = RoundedCornerShape(8.dp)
private val FadeEdgeWidth       = 40.dp
private val ExpandDurationMs    = 300

// ── Nav-item descriptors ──────────────────────────────────────────────────────

/**
 * Identifies which rail item is currently active so it renders with the
 * subtle white-10% highlight. Mapped from the current nav route in
 * [MainActivity] — no business logic lives in the rail itself.
 */
enum class SideRailItem { Profile, Search, Home, Discover, MyStuff, PillChannels, Settings }

// ── Public composable ─────────────────────────────────────────────────────────

/**
 * Amazon Prime Video–style side rail.
 *
 * Collapsed (no focus): fully transparent, icons-only, flush to the left edge
 * at [CollapsedInset]. Profile is a small avatar circle at the top.
 *
 * Expanded (any child focused): overlay panel slides out over the content at
 * full alpha-0.92 black with a right-edge gradient fade. Width + label alpha
 * animate together over [ExpandDurationMs] ms with FastOutSlowInEasing.
 * Content underneath stays static — the rail never pushes layout.
 *
 * @param activeItem      which nav row gets the subtle 10%-white highlight
 * @param onExpandedChange notified whenever the rail's focus expansion flips
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SideRail(
    onSearchClick: () -> Unit,
    onHomeClick: () -> Unit,
    onDiscoverClick: () -> Unit,
    onMyStuffClick: () -> Unit,
    onSettingsClick: () -> Unit,
    profileName: String?,
    profileColorHex: String?,
    profileAvatarUrl: String?,
    showDiscover: Boolean = true,
    onProfileClick: () -> Unit = {},
    onPillChannelsClick: () -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null,
    activeItem: SideRailItem? = null,
    onExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
    LaunchedEffect(hasFocus) { latestOnExpandedChange(hasFocus) }

    // Width and content alpha animate as a single visual story so the labels
    // and the panel arrive together; using one duration + easing keeps them
    // perfectly synced.
    val animatedWidth by animateDpAsState(
        targetValue = if (hasFocus) ExpandedWidth else CollapsedWidth,
        animationSpec = tween(ExpandDurationMs, easing = FastOutSlowInEasing),
        label = "railWidth",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        animationSpec = tween(ExpandDurationMs, easing = FastOutSlowInEasing),
        label = "railLabelAlpha",
    )

    // Black panel + right-edge transparent fade. We draw both as backgrounds on
    // a Box so the gradient sits on top of the solid colour, producing a soft
    // dissolve into the content underneath.
    val panelColor = Color(0xFF000000).copy(alpha = 0.92f)
    val edgeFade = remember(panelColor) {
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.7f to Color.Transparent,
                1f to Color.Transparent.copy(alpha = 0f),
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(animatedWidth)
            .focusGroup()
            .onFocusChanged { hasFocus = it.hasFocus },
    ) {
        // Background panel — only painted when expanded so the collapsed state
        // is truly transparent (no card, no blur).
        if (hasFocus || labelAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(animatedWidth)
                    .alpha(labelAlpha)
                    .background(panelColor),
            )
            // Right-edge gradient fade (last ~40dp dissolves into transparent).
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(FadeEdgeWidth)
                    .offset(x = animatedWidth - FadeEdgeWidth)
                    .alpha(labelAlpha)
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to panelColor,
                                1f to Color.Transparent,
                            ),
                        ),
                    ),
            )
        }

        // Resolve which row gets the rail-entry FocusRequester so that
        // `LocalSideRailController.openSideRail()` lands focus on the user's
        // current section instead of the Profile header. Falls back to Home
        // when there's no active match (or when the active route — e.g.
        // Discover — is gated off below).
        val focusTarget: SideRailItem = when (activeItem) {
            SideRailItem.Profile,
            SideRailItem.Search,
            SideRailItem.Home,
            SideRailItem.MyStuff,
            SideRailItem.PillChannels,
            SideRailItem.Settings -> activeItem
            SideRailItem.Discover -> if (showDiscover) SideRailItem.Discover else SideRailItem.Home
            null -> SideRailItem.Home
        }
        fun requesterFor(item: SideRailItem): FocusRequester? =
            if (item == focusTarget) firstItemFocusRequester else null

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(vertical = OuterVPadding),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start,
        ) {
            ProfileHeader(
                name = profileName,
                colorHex = profileColorHex,
                avatarUrl = profileAvatarUrl,
                expanded = hasFocus,
                labelAlpha = labelAlpha,
                isActive = activeItem == SideRailItem.Profile,
                onClick = onProfileClick,
                focusRequester = requesterFor(SideRailItem.Profile),
            )
            Spacer(modifier = Modifier.size(SectionGap))
            RailItem(
                icon = Icons.Default.Search,
                label = "Search",
                expanded = hasFocus,
                labelAlpha = labelAlpha,
                isActive = activeItem == SideRailItem.Search,
                onClick = onSearchClick,
                focusRequester = requesterFor(SideRailItem.Search),
            )
            RailItem(
                icon = Icons.Default.Home,
                label = "Home",
                expanded = hasFocus,
                labelAlpha = labelAlpha,
                isActive = activeItem == SideRailItem.Home,
                onClick = onHomeClick,
                focusRequester = requesterFor(SideRailItem.Home),
            )
            if (showDiscover) {
                RailItem(
                    icon = Icons.Default.Explore,
                    label = "Discover",
                    expanded = hasFocus,
                    labelAlpha = labelAlpha,
                    isActive = activeItem == SideRailItem.Discover,
                    onClick = onDiscoverClick,
                    focusRequester = requesterFor(SideRailItem.Discover),
                )
            }
            RailItem(
                icon = Icons.Default.Bookmark,
                label = "My Stuff",
                expanded = hasFocus,
                labelAlpha = labelAlpha,
                isActive = activeItem == SideRailItem.MyStuff,
                onClick = onMyStuffClick,
                focusRequester = requesterFor(SideRailItem.MyStuff),
            )
            RailItem(
                icon = Icons.Default.Tune,
                label = "Pill Channels",
                expanded = hasFocus,
                labelAlpha = labelAlpha,
                isActive = activeItem == SideRailItem.PillChannels,
                onClick = onPillChannelsClick,
                focusRequester = requesterFor(SideRailItem.PillChannels),
            )
            RailItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                expanded = hasFocus,
                labelAlpha = labelAlpha,
                isActive = activeItem == SideRailItem.Settings,
                onClick = onSettingsClick,
                focusRequester = requesterFor(SideRailItem.Settings),
            )
        }
    }
}

// ── Profile header ────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfileHeader(
    name: String?,
    colorHex: String?,
    avatarUrl: String?,
    expanded: Boolean,
    labelAlpha: Float,
    isActive: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val contentFr = LocalContentFocusRequester.current
    val interactionSource = remember { MutableInteractionSource() }
    val avatarSize = if (expanded) AvatarExpandedSize else AvatarCollapsedSize
    val animatedAvatarSize by animateDpAsState(
        targetValue = avatarSize,
        animationSpec = tween(ExpandDurationMs, easing = FastOutSlowInEasing),
        label = "avatarSize",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ItemHeight)
            .padding(start = CollapsedInset)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusProperties { right = contentFr }
            .clip(ActiveHighlightShape)
            .background(railItemHighlight(isActive, isFocused, labelAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatarCircle(
            name = name.orEmpty(),
            colorHex = colorHex ?: "#1E88E5",
            size = animatedAvatarSize,
            avatarImageUrl = avatarUrl,
            modifier = Modifier.scale(if (isFocused) 1.05f else 1f),
        )
        Spacer(modifier = Modifier.size(IconLabelGap))
        Text(
            text = name.orEmpty(),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(labelAlpha),
        )
    }
}

// ── Rail item (icon + animated label, with optional active highlight) ────────

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    labelAlpha: Float,
    isActive: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val contentFr = LocalContentFocusRequester.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ItemHeight)
            .padding(start = CollapsedInset)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusProperties { right = contentFr }
            .clip(ActiveHighlightShape)
            .background(railItemHighlight(isActive, isFocused, labelAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = ItemInnerHPadding, vertical = ItemInnerVPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused || isActive) Color.White else Color.White.copy(alpha = 0.75f),
            modifier = Modifier
                .size(IconSizeDp)
                .scale(if (isFocused) 1.05f else 1f),
        )
        Spacer(modifier = Modifier.size(IconLabelGap))
        Text(
            text = label,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(labelAlpha),
        )
    }
}

/**
 * Subtle 10%-white pill behind the active item — only when the rail is
 * expanded enough for the user to see it (labelAlpha > 0). Focused state
 * doesn't draw an extra background; the icon scale + text brightness carry
 * the focus signal.
 */
private fun railItemHighlight(isActive: Boolean, isFocused: Boolean, labelAlpha: Float): Color {
    return when {
        isActive -> Color.White.copy(alpha = 0.10f * labelAlpha)
        isFocused -> Color.White.copy(alpha = 0.06f * labelAlpha)
        else -> Color.Transparent
    }
}
