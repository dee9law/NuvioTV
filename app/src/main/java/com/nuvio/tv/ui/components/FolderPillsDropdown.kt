package com.nuvio.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.screens.home.FolderPillOption

private val Background    = Color(0xFF101418).copy(alpha = 0.95f)
private val ItemShape     = RoundedCornerShape(10.dp)
private val ContainerShape = RoundedCornerShape(14.dp)
private val FocusBorder   = Color.White.copy(alpha = 0.55f)
private val FocusedItemBg = Color.White.copy(alpha = 0.16f)
private val EnabledTint   = Color(0xFF4A90D9)

/**
 * TV-friendly dropdown listing every folder from every collection, grouped by
 * collection title, with a per-row toggle. Used by the top-bar + button to
 * let users enable/disable individual folder pills on the channel rail.
 *
 * Changes are committed immediately via [onToggle] — the host typically
 * forwards to [com.nuvio.tv.ui.screens.home.ChannelRailViewModel.togglePill],
 * which persists to DataStore. Dismiss with Back or tap outside.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FolderPillsDropdown(
    options: List<FolderPillOption>,
    onToggle: (FolderPillOption) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset = IntOffset.Zero,
) {
    if (options.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    Popup(
        alignment = Alignment.TopEnd,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BackHandler(enabled = true) { onDismiss() }

        val firstItemFr = remember { FocusRequester() }
        val lastItemFr = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            repeat(4) { withFrameNanos { } }
            runCatching { firstItemFr.requestFocus() }
        }

        // Group options by collection to render section headers above each
        // contiguous run of folders. Preserves the upstream order — the VM
        // emits options in collection × folder order already.
        val groupedKeys = options.groupBy { it.collectionId }.keys.toList()
        val firstItemCollectionId = options.firstOrNull()?.collectionId
        val lastOption = options.lastOrNull()

        Box(
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 460.dp)
                .clip(ContainerShape)
                .background(Background)
                .padding(vertical = 8.dp, horizontal = 8.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                groupedKeys.forEach { collectionId ->
                    val rows = options.filter { it.collectionId == collectionId }
                    val collectionTitle = rows.first().collectionTitle
                    item(key = "header_$collectionId") {
                        SectionHeader(text = collectionTitle)
                    }
                    items(
                        items = rows,
                        key = { row -> "row_${row.collectionId}_${row.folderId}" },
                    ) { row ->
                        val isFirst = collectionId == firstItemCollectionId &&
                            row.folderId == rows.first().folderId
                        val isLast = lastOption != null &&
                            row.collectionId == lastOption.collectionId &&
                            row.folderId == lastOption.folderId
                        FolderRow(
                            option = row,
                            focusRequester = if (isFirst) firstItemFr else null,
                            secondaryFocusRequester = if (isLast) lastItemFr else null,
                            wrapUpTo = if (isFirst) lastItemFr else null,
                            wrapDownTo = if (isLast) firstItemFr else null,
                            onClick = { onToggle(row) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.55f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FolderRow(
    option: FolderPillOption,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    secondaryFocusRequester: FocusRequester? = null,
    wrapUpTo: FocusRequester? = null,
    wrapDownTo: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "folderPillRowScale",
    )
    val bg by animateColorAsState(
        targetValue = if (isFocused) FocusedItemBg else Color.Transparent,
        animationSpec = tween(140),
        label = "folderPillRowBg",
    )
    // Use [Surface] instead of [Card] for the row's clickable container —
    // Surface's `onClick` is the canonical TV-Material 3 path for D-pad
    // Select and is more reliable inside a `Popup` than Card.onClick.  This
    // is what makes the pill toggle actually fire when the user presses OK
    // (the bug the user reported: "D-pad select on a toggle row [doesn't]
    // call channelRailVm.togglePill").
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .then(
                if (secondaryFocusRequester != null) Modifier.focusRequester(secondaryFocusRequester)
                else Modifier
            )
            .then(
                if (wrapUpTo != null || wrapDownTo != null) {
                    Modifier.focusProperties {
                        if (wrapUpTo != null) up = wrapUpTo
                        if (wrapDownTo != null) down = wrapDownTo
                    }
                } else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape = ItemShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg,
            pressedContainerColor = bg,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, FocusBorder),
                shape = ItemShape,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        color = if (option.enabled) EnabledTint else Color.White.copy(alpha = 0.18f),
                        shape = CircleShape,
                    ),
            )
            // Title logo when the folder JSON provides one — matches the
            // channel-pill treatment (G-A) so picker rows look like the
            // pills they govern.
            if (!option.titleLogoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = option.titleLogoUrl,
                    contentDescription = option.folderTitle,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .heightIn(max = 18.dp)
                        .widthIn(max = 64.dp),
                )
            }
            Text(
                text = option.folderTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 4.dp).run { this },
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            if (option.enabled) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Enabled",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// LazyListScope.items helper — local alias so we don't need an extra import.
@Suppress("FunctionName")
private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.items(
    items: List<T>,
    noinline key: (item: T) -> Any,
    crossinline itemContent: @Composable (T) -> Unit,
) {
    items(items.size, key = { key(items[it]) }) { idx -> itemContent(items[idx]) }
}
