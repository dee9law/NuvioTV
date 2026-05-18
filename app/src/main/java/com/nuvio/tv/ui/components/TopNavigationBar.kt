package com.nuvio.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.ui.theme.NuvioColors
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
 * Amazon-Prime-Video–style top navigation bar with three focus zones:
 *
 *  Zone 1 — fixed category tabs (Home / Movies / TV Shows / Collections)
 *  Zone 2 — horizontally scrollable channel tabs
 *  Zone 3 — fixed "Subscriptions" button
 *
 * Focus moves left/right within a zone, and crosses zone boundaries naturally
 * because all three zones live in the same [Row].  D-pad Down from any item
 * passes focus to whatever is below the bar in the layout.
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
    onNetworksClick: () -> Unit,
    firstTabFocusRequester: FocusRequester? = null,
    onCategoryLongPress: ((Int) -> Unit)? = null,
    collectionContextLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val contentFr = LocalContentFocusRequester.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .background(NavBarBg)
            .padding(horizontal = 36.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    runCatching { contentFr.requestFocus() }.isSuccess
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                val isActiveCategory =
                    selectedChannelIndex == null && index == selectedCategoryIndex
                CategoryTabItem(
                    text = label,
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
                )
            }
        }

        Spacer(Modifier.width(16.dp))
        NavDivider()
        Spacer(Modifier.width(16.dp))

        // ── Zone 2: Channel tabs (scrollable) ────────────────────────────────
        // When a collection is active, prefix the rail with the collection name
        // so the user knows which collection's folders they're browsing.
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

        val listState = rememberLazyListState()

        // Track the most-recently focused channel index so focusRestorer can
        // land on it when the user re-enters the zone from left or right.
        var lastFocusedChannel by remember { mutableIntStateOf(selectedChannelIndex ?: 0) }

        // One FocusRequester per channel, lazily created and stable across recomposition.
        val channelFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
        fun channelFr(index: Int) = channelFocusRequesters.getOrPut(index) { FocusRequester() }

        LazyRow(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .focusRestorer { channelFr(lastFocusedChannel) }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            itemsIndexed(
                items = channels,
                key = { _, ch -> ch.id },
            ) { index, channel ->
                val isActiveChannel = index == selectedChannelIndex
                ChannelTabItem(
                    channel = channel,
                    isSelected = isActiveChannel,
                    focusRequester = channelFr(index),
                    secondaryFocusRequester = if (isActiveChannel) firstTabFocusRequester else null,
                    onFocused = { lastFocusedChannel = index },
                    onClick = { onChannelSelected(index) },
                )
            }
        }

        Spacer(Modifier.width(16.dp))
        NavDivider()
        Spacer(Modifier.width(16.dp))

        // ── Zone 3: Networks picker ──────────────────────────────────────────
        NetworksItem(onClick = onNetworksClick)
    }
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
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "catScale",
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
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
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, PillFocusBorder),
                shape = PillShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
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
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> channel.brandColor
            isFocused  -> PillFocusedBg
            else       -> Color.Transparent
        },
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

    Card(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
            .focusRequester(focusRequester)
            .then(
                if (secondaryFocusRequester != null) Modifier.focusRequester(secondaryFocusRequester)
                else Modifier
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
                border = BorderStroke(1.5.dp, PillFocusBorder),
                shape = PillShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        // Logo above caption when titleLogoUrl is provided; text-only otherwise.
        // Both children are horizontally centered so an asymmetric logo still
        // looks balanced inside the pill regardless of caption width.
        if (!channel.titleLogoUrl.isNullOrBlank()) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = channel.titleLogoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
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
        } else {
            Text(
                text = channel.name,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = textAlpha),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NetworksItem(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isFocused) PillFocusedBg else Color.Transparent,
        animationSpec = tween(150),
        label = "subsBg",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.75f,
        animationSpec = tween(150),
        label = "subsAlpha",
    )

    Card(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(PillShape),
        colors = CardDefaults.colors(
            containerColor = bgColor,
            focusedContainerColor = bgColor,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, PillFocusBorder),
                shape = PillShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White.copy(alpha = contentAlpha),
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "Networks",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = contentAlpha),
                fontWeight = FontWeight.Medium,
            )
        }
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
