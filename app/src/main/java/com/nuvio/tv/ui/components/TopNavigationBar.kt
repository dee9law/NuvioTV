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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
private val NavBarHeight  = 60.dp

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
    // Modern feel: minimal 16dp buffer on both edges so pills don't
    // kiss the TV bezel and the focus highlight has room to render
    // without clipping. Legacy keeps the original 36dp leading clearance
    // for SideRail-era alignment, no trailing pad (channels run to
    // the right edge of the screen).
    val isModernFeelForTopBar = com.nuvio.tv.LocalIsModernFeel.current
    val topBarLeading = if (isModernFeelForTopBar) 16.dp else 36.dp
    val topBarTrailing = if (isModernFeelForTopBar) 16.dp else 0.dp

    // ── Brand glow state for selected channel pill ──────────────────────
    var glowRootX by remember { mutableFloatStateOf(0f) }
    var glowWidthPx by remember { mutableFloatStateOf(0f) }
    var glowRawColor by remember { mutableStateOf(Color.Transparent) }
    var barRootX by remember { mutableFloatStateOf(0f) }
    val animatedGlowX by animateFloatAsState(
        targetValue = glowRootX - barRootX,
        animationSpec = tween(300),
        label = "glowX"
    )
    val animatedGlowWidth by animateFloatAsState(
        targetValue = glowWidthPx,
        animationSpec = tween(300),
        label = "glowW"
    )
    val animatedGlowColor by animateColorAsState(
        targetValue = glowRawColor,
        animationSpec = tween(300),
        label = "glowColor"
    )
    val showGlow = selectedChannelIndex != null && glowWidthPx > 0f

    Box(modifier = modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .background(NavBarBg)
            .padding(start = topBarLeading, end = topBarTrailing)
            .onGloballyPositioned { barRootX = it.positionInRoot().x }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    runCatching { contentFr.requestFocus() }.isSuccess
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.width(16.dp))
                    NavDivider()
                    Spacer(Modifier.width(16.dp))
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
                    PaddingValues(start = 2.dp, end = 12.dp)
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
                        onGlowPositionReported = if (isActiveChannel) { rootX, widthPx, color ->
                            glowRootX = rootX
                            glowWidthPx = widthPx
                            glowRawColor = color
                        } else null,
                    )
                }
            }
        }

        // Networks/Pill-Channels manage button removed (Task G-A) — the
        // channel-pill LazyRow now extends to the right screen edge.
        // Pill-channels management now lives in the Profile Overlay
        // (Modern feel) and SideRail (Legacy feel).
    } // Row

    // ── Brand glow overlay at top screen edge (channel pills only) ──
    if (showGlow && animatedGlowWidth > 0f) {
        val glowHeightDp = 50.dp
        val dashHeightDp = 3.dp
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(glowHeightDp)
                .align(Alignment.TopStart)
        ) {
            val dashH = dashHeightDp.toPx()
            val glowH = glowHeightDp.toPx()
            // Horizontal dash at y=0
            drawRect(
                color = animatedGlowColor,
                topLeft = Offset(animatedGlowX, 0f),
                size = Size(animatedGlowWidth, dashH)
            )
            // Soft downward glow gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        animatedGlowColor.copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    startY = dashH,
                    endY = glowH
                ),
                topLeft = Offset(animatedGlowX, dashH),
                size = Size(animatedGlowWidth, glowH - dashH)
            )
        }
    }
    } // Box
}

// ── Private sub-composables ──────────────────────────────────────────────────

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

    // Selected tab is signalled by accent text color only — no background
    // pill, per the redesign. The focus highlight stays the same (faint
    // white wash + border) so D-pad position remains visible regardless of
    // which tab is currently selected.
    val accentColor = NuvioColors.Secondary
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) PillFocusedBg else Color.Transparent,
        animationSpec = tween(150),
        label = "catBg",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isFocused  -> Color.White
            isSelected -> accentColor
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
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
            containerColor = bgColor,
            focusedContainerColor = bgColor,
        ),
        border = CardDefaults.border(
            border = if (editMode) Border(
                border = BorderStroke(1.dp, editBorderColor),
                shape = PillShape,
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(if (isGrabbed) 2.dp else 1.5.dp, if (isGrabbed) accentColor else PillFocusBorder),
                shape = PillShape,
            ),
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
    onGlowPositionReported: ((rootX: Float, widthPx: Float, color: Color) -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    var logoLoadFailed by remember(channel.titleLogoUrl) { mutableStateOf(false) }

    // Resting "selected" state uses a thin underline dash (rendered as a
    // sibling Box below the Card) instead of the previous solid brand-
    // colour pill — far less visually cluttering. Focus still tints the
    // background so the D-pad position stays obvious.
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) PillFocusedBg else Color.Transparent,
        animationSpec = tween(160),
        label = "channelBg",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (isSelected || isFocused) 1f else 0.65f,
        animationSpec = tween(150),
        label = "channelAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "channelScale",
    )
    val hasLogoUrl = !channel.titleLogoUrl.isNullOrBlank() && !logoLoadFailed
    // Channel-pill focus treatment mirrors the split content-card
    // settings: Poster Glow toggle (shadow) is orthogonal to Card
    // Focus Style (Accent | Bloom). Numbers are tuned smaller than
    // poster cards — pills are tiny, so a 6-8dp shadow + 0.25 alpha
    // reads as a subtle halo.
    val pillFocusStyle = com.nuvio.tv.LocalCardFocusStyle.current
    val pillGlowEnabled = com.nuvio.tv.LocalPosterGlowEnabled.current
    val pillIsBloom = pillFocusStyle == com.nuvio.tv.domain.model.CardFocusStyle.BLOOM
    val pillNeedsArtworkColor = pillGlowEnabled || pillIsBloom
    val glowColor = rememberArtworkBackedGlowColor(
        imageUrl = channel.titleLogoUrl,
        fallbackSeed = channel.id,
        enabled = pillNeedsArtworkColor && hasLogoUrl,
    )
    val showPillGlow = hasLogoUrl && isFocused && pillGlowEnabled
    val pillShadowElevation = if (pillIsBloom) 6.dp else 8.dp
    val pillShadowColor = glowColor.copy(alpha = 0.25f)
    val pillFocusedBorderColor = if (pillIsBloom && hasLogoUrl) glowColor
        else PillFocusBorder
    val pillFocusedBorderWidth = if (pillIsBloom) 2.dp else 1.5.dp

    // Resolve brand color for the top-edge glow indicator.
    // Prefer brandColor if it's not fully transparent; else extract from logo.
    val brandGlowColor = if (channel.brandColor != Color.Transparent) {
        channel.brandColor
    } else {
        rememberArtworkBackedGlowColor(
            imageUrl = channel.titleLogoUrl,
            fallbackSeed = channel.id,
            enabled = true,
            fallbackColor = NuvioColors.Secondary,
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
            .then(
                if (onGlowPositionReported != null) Modifier.onGloballyPositioned { coords ->
                    onGlowPositionReported(
                        coords.positionInRoot().x,
                        coords.size.width.toFloat(),
                        brandGlowColor
                    )
                } else Modifier
            )
            .then(
                if (showPillGlow) {
                    Modifier.shadow(
                        elevation = pillShadowElevation,
                        shape = PillShape,
                        ambientColor = pillShadowColor,
                        spotColor = pillShadowColor,
                    )
                } else Modifier
            )
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
            containerColor = bgColor,
            focusedContainerColor = bgColor,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(pillFocusedBorderWidth, pillFocusedBorderColor),
                shape = PillShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        val showLogo = hasLogoUrl && displayMode != CategoryPillDisplayMode.TEXT_ONLY
        val showText = !(hasLogoUrl && displayMode == CategoryPillDisplayMode.ICON_ONLY)
        if (showLogo && showText) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                    modifier = Modifier
                        .heightIn(max = 20.dp)
                        .widthIn(max = 60.dp),
                )
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = textAlpha),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        } else if (showLogo) {
            // Icon-only: logo alone in a slightly taller pill to match the
            // icon-and-text variant's vertical rhythm.
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
                    modifier = Modifier
                        .heightIn(max = 24.dp)
                        .widthIn(max = 72.dp),
                )
            }
        } else {
            Text(
                text = channel.name,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = textAlpha),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    } // Card
    } // Column
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
            .size(36.dp)
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
            size = 32.dp,
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
