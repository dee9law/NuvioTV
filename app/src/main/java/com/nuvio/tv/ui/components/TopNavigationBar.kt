package com.nuvio.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.domain.model.CategoryPillDisplayMode
import com.nuvio.tv.ui.theme.NuvioColors
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

// ── Colors (local to this component) ────────────────────────────────────────

private val NavBarBg         = Color.Transparent
private val DividerColor     = Color.White.copy(alpha = 0.30f)
private val PillFocusedBg    = Color.White.copy(alpha = 0.14f)
private val PillFocusBorder  = Color.White.copy(alpha = 0.45f)
private val TextIdle         = Color.White.copy(alpha = 0.75f)

private val PillShape     = RoundedCornerShape(20.dp)
// Reduced from 60.dp to sit the bar closer to the top edge (Change 5). 54.dp is
// the floor: the tallest pill (channel logo + caption + underline) measures
// ~52.5dp, so anything lower risks clipping fetched channel logos.
private val NavBarHeight  = 54.dp

// ── Public data class ────────────────────────────────────────────────────────

/**
 * Represents a channel in Zone 2 of [TopNavigationBar].
 *
 * @param id            Stable unique key used as LazyRow item key.
 * @param name          Display label (always rendered as a caption beneath
 *                      the logo image, or as the only content when neither
 *                      [logoResId] nor [titleLogoUrl] are set).
 * @param logoResId     Optional drawable resource for a white channel logo.
 * @param titleLogoUrl  Optional remote URL of a transparent title-treatment
 *                      logo (mirrors the folder's `titleLogoUrl` from
 *                      collections.json). Loaded via Coil when present.
 * @param brandColor    Pill background color when this channel is selected.
 */
data class ChannelTab(
    val id: String,
    val name: String,
    val logoResId: Int? = null,
    val titleLogoUrl: String? = null,
    val brandColor: Color,
)

// ── Main composable ──────────────────────────────────────────────────────────

/**
 * Amazon-Prime-Video–style top navigation bar with two focus zones:
 *
 *  Zone 1 — fixed category tabs (Home / Movies / TV Shows / Collections)
 *  Zone 2 — horizontally scrollable channel tabs (Pill Channels)
 *
 * Pill Channels management (the per-folder visibility dropdown) used to
 * live as a "+" button at the right edge of the bar — it now lives in
 * the Profile Overlay (Modern feel) and the SideRail (Legacy feel) so
 * channel pills can scroll free to the right screen edge.
 *
 * Focus moves left/right within a zone, and crosses zone boundaries
 * naturally because both zones live in the same [Row]. D-pad Down from
 * any item passes focus to whatever is below the bar in the layout.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TopNavigationBar(
    categories: List<String> = listOf("Home", "Movies", "TV Shows", "Collections"),
    channels: List<ChannelTab>,
    selectedCategoryIndex: Int,
    selectedChannelIndex: Int?,      // null when a category is the active selection
    onCategorySelected: (Int) -> Unit,
    onChannelSelected: (Int) -> Unit,
    firstTabFocusRequester: FocusRequester? = null,
    onCategoryLongPress: ((Int) -> Unit)? = null,
    collectionContextLabel: String? = null,
    // ── Modern-feel-only profile slot ────────────────────────────────────
    // When [isModernFeel] is true the bar renders a focusable profile
    // avatar at the far left in place of the (removed) SideRail entry.
    // D-pad Right from the avatar focuses the first category pill; Select
    // invokes [onProfileClick] (wired to the Profile Overlay in F6).
    isModernFeel: Boolean = false,
    profileName: String? = null,
    profileColorHex: String? = null,
    profileAvatarUrl: String? = null,
    onProfileClick: () -> Unit = {},
    // Parallel list to [categories] — when non-null and the same size,
    // each pill prepends the matching icon left of its label. Modern feel
    // sets these; Legacy passes null so its pills stay text-only.
    categoryIcons: List<ImageVector>? = null,
    /**
     * Parallel list to [categories]: when index i is true, that pill
     * renders only its icon (no label). Off-screen TopBar settings
     * uses this to let users compact specific pills.
     */
    categoryIconsOnly: List<Boolean>? = null,
    /**
     * Parallel list to [categories]: when index i is true, that pill
     * renders only its label (no icon). Mutually exclusive with the
     * same-index entry of [categoryIconsOnly] — when both are true
     * for the same pill, icons-only wins.
     */
    categoryTextOnly: List<Boolean>? = null,
    /**
     * `true` → render the channel-pill rail (default). `false` →
     * suppress the rail entirely (Modern feel hides the rail when the
     * user has demoted the CHANNELS pill to the Profile Overlay).
     */
    channelRailVisible: Boolean = true,
    /**
     * Display mode for each channel pill in the rail — controlled by
     * the CHANNELS pill's setting in TopBar settings. Defaults to
     * icon + text (logo + caption) which is the original behavior.
     */
    channelDisplayMode: CategoryPillDisplayMode = CategoryPillDisplayMode.ICON_AND_TEXT,
    // ── Modern-feel-only Edit Mode (F10) ─────────────────────────────────
    // When [editMode] is true every category pill renders a static accent
    // border (the "moveable" cue) and the focused pill is treated as
    // grabbed: D-pad Left/Right swaps it with its neighbor, D-pad Down
    // demotes it to the drawer, Back exits edit mode. A Done button is
    // rendered after the category zone.
    editMode: Boolean = false,
    onEditSwap: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onEditDemote: (index: Int) -> Unit = {},
    onExitEditMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val contentFr = LocalContentFocusRequester.current
    // Internal focus requesters for loop wrap-around (G-C):
    //   - avatarFr      : the Modern-only profile avatar (leftmost element)
    //   - firstCategoryFr: first category pill (leftmost in Legacy)
    val avatarFr = remember { FocusRequester() }
    val firstCategoryFr = remember { FocusRequester() }
    val wrapScope = rememberCoroutineScope()
    // Channel LazyRow plumbing — declared up-front so the wrap helpers
    // (used by the avatar and first-category-pill) can reference it
    // without forward declarations.
    val listState = rememberLazyListState()
    var lastFocusedChannel by remember { mutableIntStateOf(selectedChannelIndex ?: 0) }
    val channelFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    fun channelFr(index: Int) = channelFocusRequesters.getOrPut(index) { FocusRequester() }

    // Wrap right-edge of category zone → leftmost element of channel zone.
    // Wrap left-edge of channel/category zone → rightmost (last channel pill).
    val wrapToLastChannel: () -> Unit = {
        if (channels.isNotEmpty()) {
            wrapScope.launch {
                val target = channels.lastIndex
                listState.scrollToItem(target)
                // One frame for the LazyRow to compose the now-visible
                // last item before requesting focus on it.
                withFrameNanos { }
                runCatching { channelFr(target).requestFocus() }
            }
        }
    }
    val wrapToLeftmostBarItem: () -> Unit = {
        runCatching {
            if (isModernFeel) avatarFr.requestFocus() else firstCategoryFr.requestFocus()
        }
    }
    val wrapToFirstChannel: () -> Unit = {
        if (channels.isNotEmpty()) {
            wrapScope.launch {
                listState.scrollToItem(0)
                withFrameNanos { }
                runCatching { channelFr(0).requestFocus() }
            }
        }
    }
    // ── Carousel takeover (Task B1/B2) ──────────────────────────────────────
    // When focus is inside the channel-pill LazyRow, slide the Main Section
    // (avatar + category pills + divider) off-screen and let the carousel
    // expand to fill the bar's full width.
    //
    // Back behaviour is two-step:
    //  1. If focus is deeper than the first channel pill (lastFocusedChannel
    //     != 0) → scroll the LazyRow to index 0 and request focus there.
    //  2. From index 0 (or already there) → snap back to the Main Section
    //     and land on the previously-active category pill.
    var focusInCarousel by remember { mutableStateOf(false) }
    BackHandler(enabled = focusInCarousel) {
        if (lastFocusedChannel > 0 && channels.isNotEmpty()) {
            wrapScope.launch {
                listState.scrollToItem(0)
                withFrameNanos { }
                runCatching { channelFr(0).requestFocus() }
                lastFocusedChannel = 0
            }
        } else {
            focusInCarousel = false
            wrapScope.launch {
                repeat(3) { withFrameNanos { } }
                runCatching { firstTabFocusRequester?.requestFocus() }
            }
        }
    }
    // FIX 6: Modern feel uses a minimal 6dp leading inset so the profile avatar
    // starts close to the left screen edge (just enough that the avatar's focus
    // ring isn't clipped by the bezel). Legacy keeps the original 36dp leading
    // clearance for SideRail-era alignment.
    val isModernFeelForTopBar = com.nuvio.tv.LocalIsModernFeel.current
    val topBarLeading = if (isModernFeelForTopBar) 6.dp else 36.dp
    // FIX 1: Modern feel runs the channel LazyRow fully to the right screen edge
    // (no trailing pad) so the last channel isn't clipped/half-visible.
    val topBarTrailing = 0.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .background(NavBarBg)
            // Change 4: no start padding on the bar itself — the leading inset
            // moves onto the Main Section (avatar + categories) only, so the
            // channel-pill LazyRow can run edge-to-edge from the screen's left
            // edge during a carousel takeover.
            .padding(end = topBarTrailing)
            // FIX 5/2: top-align the pills (the bar background is transparent, so
            // the visible bar IS the pills) with only a 2dp flush pad — the dash
            // sits ~2dp from the top screen edge without clipping logos/text.
            .padding(top = 2.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    runCatching { contentFr.requestFocus() }.isSuccess
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.Top,
    ) {
        // ── Main Section (avatar + categories + divider) ────────────────────
        // Wrapped in AnimatedVisibility so the whole zone slides + fades out
        // when the user is deep in the carousel, letting the LazyRow's
        // weight(1f) inherit the freed layout space and span edge-to-edge.
        AnimatedVisibility(
            visible = !focusInCarousel,
            enter = slideInHorizontally(
                animationSpec = tween(300),
                initialOffsetX = { -it / 2 },
            ) + fadeIn(tween(300)),
            exit = slideOutHorizontally(
                animationSpec = tween(300),
                targetOffsetX = { -it / 2 },
            ) + fadeOut(tween(300)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = topBarLeading),
            ) {
        // ── Zone 0 (Modern only): Profile avatar ─────────────────────────────
        if (isModernFeel) {
            ProfileAvatarButton(
                name = profileName.orEmpty(),
                colorHex = profileColorHex ?: "#1E88E5",
                avatarUrl = profileAvatarUrl,
                onClick = onProfileClick,
                focusRequester = avatarFr,
                // Avatar is leftmost — D-pad Left wraps to the last channel
                // pill (scroll-into-view first since LazyRow may have it
                // offscreen).
                onWrapLeft = null,
            )
            Spacer(Modifier.width(12.dp))
        }
        // ── Zone 1: Category tabs ────────────────────────────────────────────
        // Bind firstTabFocusRequester (= LocalNavBarFocusRequester) to the
        // currently active category tab so that any caller invoking
        // `navBarFr.requestFocus()` (e.g. D-pad Up from content) lands on the
        // active tab — not just the first one and not on whichever tab happens
        // to be horizontally closest via spatial focus search.
        val noTabIsActive = selectedChannelIndex == null &&
            (selectedCategoryIndex < 0 || selectedCategoryIndex >= categories.size)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            categories.forEachIndexed { index, label ->
                // key(label) gives each pill a stable composition identity
                // across reorders, so Compose moves the existing Card
                // rather than recreating it. Focus, internal state, and
                // the per-pill FR all follow the pill to its new position
                // (G-D smoothness requirement: "focus stays on the grabbed
                // pill after the swap").
                key(label) {
                    val isActiveCategory =
                        selectedChannelIndex == null && index == selectedCategoryIndex
                    val isFirstCategory = index == 0
                    CategoryTabItem(
                        text = label,
                        leadingIcon = categoryIcons?.getOrNull(index),
                        iconsOnly = categoryIconsOnly?.getOrNull(index) == true,
                        textOnly = categoryTextOnly?.getOrNull(index) == true,
                        isSelected = isActiveCategory,
                        onClick = { onCategorySelected(index) },
                        onLongClick = onCategoryLongPress?.let { fire -> { fire(index) } },
                        focusRequester = when {
                            isActiveCategory -> firstTabFocusRequester
                            // Fallback: if nothing is active, still attach the
                            // requester to index 0 so navBarFr.requestFocus() works.
                            noTabIsActive && index == 0 -> firstTabFocusRequester
                            else -> null
                        },
                        // Secondary FR — always attached to the first category
                        // pill regardless of selection state, so loop wrap-around
                        // from the right edge has a stable target.
                        secondaryFocusRequester = if (isFirstCategory) firstCategoryFr else null,
                        // Modern feel stacks icon over label so the bar holds
                        // more pills + channel pills in the same width. Legacy
                        // keeps the original horizontal pill layout.
                        verticalLayout = isModernFeel,
                        editMode = editMode,
                        onEditKey = if (editMode) { keyPressed ->
                            when (keyPressed) {
                                Key.DirectionLeft -> {
                                    // Edit-mode wrap stays inside the category
                                    // group — never spills into channel pills
                                    // (per spec G-C wrap-categories-only).
                                    val target = if (index > 0) index - 1 else categories.lastIndex
                                    if (target != index) onEditSwap(index, target)
                                    true
                                }
                                Key.DirectionRight -> {
                                    val target = if (index < categories.lastIndex) index + 1 else 0
                                    if (target != index) onEditSwap(index, target)
                                    true
                                }
                                Key.DirectionDown -> {
                                    onEditDemote(index)
                                    true
                                }
                                // Select / OK = "drop" — exits edit mode and
                                // persists the new order (G-D requirement
                                // "Select/OK on a grabbed pill drops it in
                                // place").
                                Key.DirectionCenter, Key.Enter -> {
                                    onExitEditMode()
                                    true
                                }
                                Key.Back, Key.Escape -> {
                                    onExitEditMode()
                                    true
                                }
                                else -> false
                            }
                        } else null,
                        onWrapLeft = if (isFirstCategory && isModernFeel) {
                            { runCatching { avatarFr.requestFocus() } }
                        } else null,
                    )
                }
            }
            // Done button — only when in edit mode. Sits right after the
            // last category pill so the user can exit by D-pad Right then
            // Select instead of fumbling for the Back button.
            AnimatedVisibility(
                visible = editMode,
                enter = fadeIn(tween(140)),
                exit = fadeOut(tween(140)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    DoneEditPill(onClick = onExitEditMode)
                }
            }
        }
            } // end of Main Section inner Row
        } // end of AnimatedVisibility (Main Section)

        if (channelRailVisible) {
            // Spacer + divider hide alongside the Main Section so the LazyRow
            // can run truly edge-to-edge during a carousel takeover.
            AnimatedVisibility(
                visible = !focusInCarousel,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // FIX 7: tighten the category→channel gap so the two zones read
                    // as one continuous row (was 16dp each side of the divider).
                    Spacer(Modifier.width(8.dp))
                    NavDivider()
                    Spacer(Modifier.width(8.dp))
                }
            }

            // ── Zone 2: Channel tabs (scrollable) ────────────────────────────
            // When a collection is active, prefix the rail with the collection
            // name so the user knows which collection's folders they're
            // browsing.
            if (collectionContextLabel != null) {
                Text(
                    text = collectionContextLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(end = 10.dp),
                )
            }

            LazyRow(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .focusRestorer { channelFr(lastFocusedChannel) }
                    .focusGroup()
                    // Drive the carousel takeover: whenever any channel pill
                    // takes focus, flip the Main Section out so the rail
                    // expands. When focus leaves naturally (navigation,
                    // wrap, Back redirect), the Main Section slides back.
                    .onFocusChanged { focusInCarousel = it.hasFocus },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                // Modern feel: the outer Row already pads 16dp on both
                // edges, so the LazyRow itself needs no buffer. Legacy:
                // outer Row has no trailing pad, so the LazyRow keeps
                // a 12dp end inset so the last pill doesn't kiss the
                // screen bezel.
                contentPadding = if (isModernFeelForTopBar) {
                    PaddingValues(start = 0.dp, end = 0.dp)
                } else {
                    // Change 4: no leading inset so channel pills start flush
                    // with the screen edge. Keep the 12dp trailing inset so the
                    // last pill doesn't kiss the bezel.
                    PaddingValues(start = 0.dp, end = 12.dp)
                },
            ) {
                itemsIndexed(
                    items = channels,
                    key = { _, ch -> ch.id },
                ) { index, channel ->
                    val isActiveChannel = index == selectedChannelIndex
                    val isLastChannel = index == channels.lastIndex
                    val isFirstChannel = index == 0
                    ChannelTabItem(
                        channel = channel,
                        displayMode = channelDisplayMode,
                        isSelected = isActiveChannel,
                        focusRequester = channelFr(index),
                        secondaryFocusRequester = if (isActiveChannel) firstTabFocusRequester else null,
                        onFocused = { lastFocusedChannel = index },
                        onClick = { onChannelSelected(index) },
                        onWrapRight = if (isLastChannel) wrapToFirstChannel else null,
                        onWrapLeft = if (isFirstChannel) wrapToLastChannel else null,
                    )
                }
            }
        }

        // Networks/Pill-Channels manage button removed (Task G-A) — the
        // channel-pill LazyRow now extends to the right screen edge.
        // Pill-channels management now lives in the Profile Overlay
        // (Modern feel) and SideRail (Legacy feel).
    } // Row
}

// ── Private sub-composables ──────────────────────────────────────────────────

/**
 * FIX 2: soft rectangular bloom radiating DOWNWARD from a TOP selection dash.
 * Drawn behind the dash Box (in positive-Y below it, so it overlaps the gap
 * toward the text/icon without adding layout height), strongest at the dash and
 * fading to transparent ~9dp below — like a thin LED strip casting light down.
 * Exact dash width (no halo), additive on top of the dash. No-op when inactive.
 */
private fun Modifier.dashDownGlow(color: Color, active: Boolean): Modifier =
    if (!active) this
    else this.drawBehind {
        val glowHeightPx = 9.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.5f), Color.Transparent),
                startY = size.height,
                endY = size.height + glowHeightPx,
            ),
            topLeft = Offset(0f, size.height),
            size = Size(size.width, glowHeightPx),
        )
    }

/**
 * FIX 3 (refined): subtle dark contrast wash behind the selected/focused pill,
 * strictly constrained to the DASH width (not the full pill / bar). A vertical
 * gradient (dark at the dash, fading to transparent toward the bottom) sized to
 * [dashWidth] and centred horizontally, drawn behind the pill content so the
 * dash + downward glow read against it. No-op when inactive or width unknown.
 */
private fun Modifier.pillContrastGradient(active: Boolean, dashWidth: Dp): Modifier =
    if (!active) this
    else this.drawBehind {
        val w = if (dashWidth > 0.dp) dashWidth.toPx() else return@drawBehind
        val left = (size.width - w) / 2f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                startY = 0f,
                endY = size.height,
            ),
            topLeft = Offset(left, 0f),
            size = Size(w, size.height),
        )
    }

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun CategoryTabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    /** Second FR attached unconditionally — used for loop wrap targets. */
    secondaryFocusRequester: FocusRequester? = null,
    leadingIcon: ImageVector? = null,
    /**
     * `true` → render icon on top, label below in a compact Column
     * (Modern feel). `false` → side-by-side Row (Legacy). Has no
     * effect when [leadingIcon] is null.
     */
    verticalLayout: Boolean = false,
    /**
     * `true` → hide the text label and render only the icon. Only
     * effective when [leadingIcon] is non-null; otherwise the label
     * still shows so the pill remains identifiable.
     */
    iconsOnly: Boolean = false,
    /**
     * `true` → hide the leading icon and render only the text label.
     * Takes precedence over [verticalLayout]. If both [iconsOnly] and
     * this are set the icons-only path wins (renderer falls through
     * to the icon-only branch first).
     */
    textOnly: Boolean = false,
    editMode: Boolean = false,
    onEditKey: ((Key) -> Boolean)? = null,
    /** Loop-wrap handler invoked on D-pad Left when there's nothing
     *  to the left of this pill. Fires before the default focus search. */
    onWrapLeft: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    // Tracks whether a long-press has already fired for the current key hold.
    // Used to suppress the corresponding KeyUp so Card.onClick doesn't also fire.
    var longPressFired by remember { mutableStateOf(false) }

    // FIX 2/6: no capsule background/border. Selection & focus are signalled
    // only by the TOP dash + downward glow + text colour. Three states:
    //   focused/hovered  -> GRAY dash + glow + text
    //   active/selected   -> ACCENT dash + glow + text
    //   idle              -> no dash, normal text
    val accentColor = NuvioColors.Secondary
    val grayColor = NuvioColors.TextSecondary
    val indicatorActive = isSelected || isFocused
    // FIX 1: the active (selected) pill always wins → stays ACCENT even when it's
    // also the focused pill. Gray is only for a focused-but-not-selected pill.
    val indicatorColor = when {
        isSelected -> accentColor
        isFocused  -> grayColor
        else       -> Color.Transparent
    }
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> accentColor
            isFocused  -> grayColor
            else       -> TextIdle
        },
        animationSpec = tween(150),
        label = "catText",
    )
    val isGrabbed = editMode && isFocused
    val scale by animateFloatAsState(
        targetValue = when {
            isGrabbed -> 1.12f
            isFocused -> 1.04f
            else -> 1f
        },
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "catScale",
    )
    // Edit-mode "moveable" cue: static accent border on every pill so the
    // user can see at a glance which surface accepts reorder gestures.
    val editBorderColor = if (editMode) accentColor.copy(alpha = 0.45f) else PillFocusBorder

    // Dash width tracks the measured pill width; height is always reserved so
    // toggling selection never shifts layout.
    val density = LocalDensity.current
    var catUnderlineWidth by remember { mutableStateOf(0.dp) }

    // FIX 3: dark contrast wash constrained to the dash width (active/focused).
    // FIX 2: scale the whole pill (dash + content) together on focus.
    Box(modifier = Modifier.scale(scale).pillContrastGradient(indicatorActive, catUnderlineWidth)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // FIX 2: TOP dash + downward glow.
        Box(
            modifier = Modifier
                .width(if (catUnderlineWidth > 0.dp) catUnderlineWidth else 1.dp)
                .height(2.5.dp)
                .dashDownGlow(indicatorColor, indicatorActive)
                .background(
                    color = if (indicatorActive) indicatorColor else Color.Transparent,
                    shape = RoundedCornerShape(1.5.dp),
                ),
        )
        Spacer(Modifier.height(2.dp))
    Card(
        onClick = onClick,
        modifier = Modifier
            .onGloballyPositioned { catUnderlineWidth = with(density) { it.size.width.toDp() } }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .then(
                if (secondaryFocusRequester != null) Modifier.focusRequester(secondaryFocusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            // Loop-wrap left handler — fires only when this pill receives
            // a Left key and no edit-mode handler swallows it first.
            .then(
                if (onWrapLeft != null) Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && !editMode) {
                        onWrapLeft()
                        true
                    } else false
                } else Modifier
            )
            // Edit-mode key handler intercepts L/R/Down/Back before the
            // long-press logic; runs only when this pill is the grabbed
            // (focused-in-editMode) one.
            .then(
                if (onEditKey != null) Modifier.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    onEditKey(event.key)
                } else Modifier
            )
            .then(
                if (onLongClick != null) Modifier.onPreviewKeyEvent { event ->
                    val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter
                    if (!isCenter) return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            // KeyEvent.repeatCount == 1 fires once the system's
                            // long-press threshold elapses (~500ms) while the
                            // key is held. That's our cue to fire the long-press.
                            if (!longPressFired && event.nativeKeyEvent.repeatCount == 1) {
                                longPressFired = true
                                onLongClick()
                            }
                            // Consume key autorepeats after long-press fired so
                            // Card doesn't also see them as clicks.
                            longPressFired
                        }
                        KeyEventType.KeyUp -> {
                            val wasLong = longPressFired
                            longPressFired = false
                            // Consume KeyUp only if long-press already fired —
                            // otherwise pass through so Card.onClick can run.
                            wasLong
                        }
                        else -> false
                    }
                } else Modifier
            ),
        shape = CardDefaults.shape(PillShape),
        colors = CardDefaults.colors(
            // FIX 2/6: no capsule background on any state.
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = CardDefaults.border(
            // FIX 2/6: no focus border either. The edit-mode reorder cues are
            // kept (static border in edit mode; accent border on the grabbed pill).
            border = if (editMode) Border(
                border = BorderStroke(1.dp, editBorderColor),
                shape = PillShape,
            ) else Border.None,
            focusedBorder = if (isGrabbed) Border(
                border = BorderStroke(2.dp, accentColor),
                shape = PillShape,
            ) else Border.None,
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        // Icons-only short-circuit: render the leading icon centered in
        // a square pill, no label. Falls back to the standard layouts if
        // there's no icon to show (otherwise the pill would be empty).
        if (iconsOnly && leadingIcon != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = text,
                    tint = textColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (textOnly) {
            // Text-only: render the label centered in the pill, no icon.
            // Falls back to the standard horizontal/vertical layouts when
            // there's no label to show.
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                style = MaterialTheme.typography.titleSmall,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            )
        } else if (verticalLayout && leadingIcon != null) {
            // Compact stacked layout — icon on top, label below — used by
            // Modern feel so more pills fit in the same bar width.
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (leadingIcon != null) 8.dp else 0.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    } // Card
    } // Column
    } // Box
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelTabItem(
    channel: ChannelTab,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    secondaryFocusRequester: FocusRequester? = null,
    /** Loop-wrap handler for D-pad Right when this is the last channel. */
    onWrapRight: (() -> Unit)? = null,
    /** Loop-wrap handler for D-pad Left when this is the first channel. */
    onWrapLeft: (() -> Unit)? = null,
    /**
     * Per-channel display mode driven by the CHANNELS pill's setting in
     * TopBar settings. ICON_AND_TEXT shows logo + caption (default);
     * ICON_ONLY hides the caption when a logo is present; TEXT_ONLY
     * hides the logo. Falls back to text when there's no logo to show.
     */
    displayMode: CategoryPillDisplayMode = CategoryPillDisplayMode.ICON_AND_TEXT,
) {
    var isFocused by remember { mutableStateOf(false) }
    var logoLoadFailed by remember(channel.titleLogoUrl) { mutableStateOf(false) }

    // FIX 2/6: no capsule. Selection & focus are signalled by a TOP dash +
    // downward glow + caption colour.
    // FIX 4/5: channel pills keep their BRAND colour for the dash + glow (never
    // orange, never gray) — gray is reserved for the left category pills only.
    // The brand colour is artwork-backed (sampled from the logo) with the
    // channel's declared brandColor as fallback, exactly as before the redesign.
    val density = LocalDensity.current
    var underlineWidth by remember { mutableStateOf(0.dp) }
    val indicatorActive = isSelected || isFocused
    val hasLogoUrl = !channel.titleLogoUrl.isNullOrBlank() && !logoLoadFailed
    val brandColor = rememberArtworkBackedGlowColor(
        imageUrl = channel.titleLogoUrl,
        fallbackSeed = channel.id,
        enabled = hasLogoUrl,
        fallbackColor = channel.brandColor,
    )
    val indicatorColor = if (indicatorActive) brandColor else Color.Transparent
    val channelTextColor by animateColorAsState(
        targetValue = if (indicatorActive) Color.White else Color.White.copy(alpha = 0.65f),
        animationSpec = tween(150),
        label = "channelText",
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "channelScale",
    )
    val showLogo = hasLogoUrl && displayMode != CategoryPillDisplayMode.TEXT_ONLY
    val showText = !(hasLogoUrl && displayMode == CategoryPillDisplayMode.ICON_ONLY)
    // Top-dash width: logo-only pills have no caption to measure, so track the
    // uniform 48dp logo box; otherwise track the measured caption width.
    val channelDashWidth = if (showLogo && !showText) 48.dp
        else if (underlineWidth > 0.dp) underlineWidth else 1.dp

    Box(modifier = Modifier.scale(scale).pillContrastGradient(indicatorActive, channelDashWidth)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // FIX 2: dash at the TOP of the pill + glow radiating DOWNWARD. Height is
        // always reserved so toggling selection never shifts the bar vertically.
        Box(
            modifier = Modifier
                .width(channelDashWidth)
                .height(2.5.dp)
                .dashDownGlow(indicatorColor, indicatorActive)
                .background(
                    color = if (indicatorActive) indicatorColor else Color.Transparent,
                    shape = RoundedCornerShape(1.5.dp),
                ),
        )
        Spacer(Modifier.height(2.dp))
    Card(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .then(
                if (secondaryFocusRequester != null) Modifier.focusRequester(secondaryFocusRequester)
                else Modifier
            )
            .then(
                if (onWrapRight != null) Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                        onWrapRight()
                        true
                    } else false
                } else Modifier
            )
            .then(
                if (onWrapLeft != null) Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        onWrapLeft()
                        true
                    } else false
                } else Modifier
            )
            .onFocusChanged {
                val nowFocused = it.isFocused || it.hasFocus
                isFocused = nowFocused
                if (nowFocused) onFocused()
            },
        shape = CardDefaults.shape(PillShape),
        colors = CardDefaults.colors(
            // Change 1: no capsule background on any state.
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = CardDefaults.border(
            // Change 1: no capsule border/outline on any state either.
            border = Border.None,
            focusedBorder = Border.None,
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        // Change 6: every channel logo renders inside the SAME 48×24 box with
        // ContentScale.Fit — small logos scale up, large logos scale down, all
        // to one uniform footprint.
        val uniformLogo: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = channel.titleLogoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    onState = { state ->
                        if (state is coil3.compose.AsyncImagePainter.State.Error) {
                            logoLoadFailed = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (showLogo && showText) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                uniformLogo()
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = channelTextColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.onGloballyPositioned {
                        underlineWidth = with(density) { it.size.width.toDp() }
                    },
                )
            }
        } else if (showLogo) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                uniformLogo()
            }
        } else {
            // FIX 8: no logo → render the name centred inside the SAME 24dp box a
            // logo would occupy (and the same 6dp vertical padding as logo-only
            // pills) so a text-only channel's name sits at the same vertical
            // centre as logo pills instead of clinging to the top.
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = channelTextColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.onGloballyPositioned {
                            underlineWidth = with(density) { it.size.width.toDp() }
                        },
                    )
                }
            }
        }
    } // Card
    } // Column
    } // Box
}

/**
 * Compact "Done" pill rendered after the category zone while Edit Mode
 * is active. Select → exits edit mode and persists the current order.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DoneEditPill(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val accent = NuvioColors.Secondary
    Card(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(PillShape),
        colors = CardDefaults.colors(
            containerColor = if (isFocused) accent else accent.copy(alpha = 0.25f),
            focusedContainerColor = accent,
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, accent),
                shape = PillShape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = PillShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
    ) {
        Text(
            text = "Done",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.titleSmall,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.92f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Focusable circular profile avatar at the far left of the Modern TopBar.
 * Press Select → opens the Profile Overlay (wired in F6 via [onClick]).
 * Focused state shows an accent ring so D-pad position stays visible.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfileAvatarButton(
    name: String,
    colorHex: String,
    avatarUrl: String?,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onWrapLeft: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            // Change 7: shrunk from 36dp so the avatar visually matches the
            // category nav icons instead of dominating the left side.
            .size(26.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .then(
                if (onWrapLeft != null) Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        onWrapLeft()
                        true
                    } else false
                } else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(androidx.compose.foundation.shape.CircleShape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, com.nuvio.tv.ui.theme.NuvioColors.Secondary),
                shape = androidx.compose.foundation.shape.CircleShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1.06f),
    ) {
        ProfileAvatarCircle(
            name = name,
            colorHex = colorHex,
            // Change 7: 22dp circle inside the 26dp card (2dp inset all round)
            // so the avatar sits at roughly nav-icon size.
            size = 22.dp,
            avatarImageUrl = avatarUrl,
            modifier = Modifier.padding(2.dp),
        )
    }
}

@Composable
private fun NavDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(DividerColor),
    )
}

// ── Sample data (useful for previews / tests) ────────────────────────────────

val SampleChannelTabs = listOf(
    ChannelTab("netflix",       "Netflix",        brandColor = Color(0xFFE50914)),
    ChannelTab("prime",         "Prime",          brandColor = Color(0xFF00BCD4)),
    ChannelTab("disney",        "Disney+",        brandColor = Color(0xFF113CCF)),
    ChannelTab("hulu",          "Hulu",           brandColor = Color(0xFF1CE783)),
    ChannelTab("appletv",       "Apple TV+",      brandColor = Color(0xFFE0E0E0)),
    ChannelTab("hbomax",        "Max",            brandColor = Color(0xFF9B59B6)),
    ChannelTab("paramount",     "Paramount+",     brandColor = Color(0xFF0066CC)),
    ChannelTab("hallmark",      "Hallmark",       brandColor = Color(0xFF8B5CF6)),
    ChannelTab("discovery",     "Discovery+",     brandColor = Color(0xFF0066FF)),
    ChannelTab("history",       "HISTORY",        brandColor = Color(0xFFD4A843)),
    ChannelTab("curiosity",     "CuriosityStream",brandColor = Color(0xFF2196F3)),
    ChannelTab("crunchyroll",   "Crunchyroll",    brandColor = Color(0xFFF47521)),
)
