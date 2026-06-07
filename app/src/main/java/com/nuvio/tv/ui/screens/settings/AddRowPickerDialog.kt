@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.LayoutRowKind
import com.nuvio.tv.ui.theme.NuvioColors
import kotlinx.coroutines.launch

/**
 * Dialog that lists every catalog source the user can add as a row, grouped
 * by source kind (Addons / Collections / Trakt). Selecting one invokes
 * [onSelect] and dismisses.
 *
 * Already-added rows are dimmed and not selectable, so the user doesn't add
 * duplicates.
 */
@Composable
fun AddRowPickerDialog(
    sources: List<CatalogSourceOption>,
    existingRowIds: Set<String>,
    onSelect: (CatalogSourceOption) -> Unit,
    onDismiss: () -> Unit,
    // Optional action bar (Populate All / Follow Order / Delete All). Rendered
    // below the title when [onPopulateAll] + [onDeleteAll] are supplied.
    onPopulateAll: (() -> Unit)? = null,
    onDeleteAll: (() -> Unit)? = null,
    deleteConfirmSubtitle: String = "This removes every row from this source.",
    followOrder: Boolean? = null,
    onToggleFollowOrder: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Action bar owns default focus. Back from the list returns focus to
        // the action bar; Back from the action bar closes the picker.
        val actionBarFr = remember { FocusRequester() }
        var actionBarHasFocus by remember { mutableStateOf(true) }
        BackHandler {
            if (actionBarHasFocus) onDismiss()
            else runCatching { actionBarFr.requestFocus() }
        }

        // Group by section in stable order: Addons (by addon name), Collections, Trakt.
        val grouped = remember(sources) {
            sources.groupBy { it.kind }.toSortedMap(compareBy { it.ordinal })
        }
        val firstItemFr = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            repeat(4) { withFrameNanos { } }
            runCatching { actionBarFr.requestFocus() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .heightInDialogContent()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NuvioColors.BackgroundCard),
            ) {
                Text(
                    text = "Add Row",
                    style = MaterialTheme.typography.titleLarge,
                    color = NuvioColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
                )
                Text(
                    text = "Pick a catalog to add to this screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                )

                if (onPopulateAll != null && onDeleteAll != null) {
                    PickerActionBar(
                        onPopulateAll = onPopulateAll,
                        onDeleteAll = onDeleteAll,
                        deleteConfirmSubtitle = deleteConfirmSubtitle,
                        followOrder = followOrder,
                        onToggleFollowOrder = onToggleFollowOrder,
                        firstButtonFocusRequester = actionBarFr,
                        onFocusChanged = { actionBarHasFocus = it },
                    )
                }

                if (sources.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No catalogs available yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioColors.TextSecondary,
                        )
                    }
                    return@Column
                }

                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val lastItemFr = remember { FocusRequester() }
                val lastOptionIndex = sources.lastIndex
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    var globalIndex = 0
                    grouped.forEach { (kind, options) ->
                        item(key = "header_${kind.name}") {
                            SectionHeader(label = sectionLabel(kind))
                        }
                        itemsIndexed(
                            items = options,
                            key = { _, opt -> "opt_${opt.id}" },
                        ) { _, option ->
                            val isExisting = option.id in existingRowIds
                            val idx = globalIndex
                            val isFirst = (idx == 0)
                            val isLast = (idx == lastOptionIndex)
                            globalIndex++
                            // Vertical loop wrap: first item Up → last; last Down → first.
                            val wrapMod = Modifier
                                .then(if (isLast) Modifier.focusRequester(lastItemFr) else Modifier)
                                .dpadLoopWrap(
                                    onPrev = if (isFirst) {
                                        {
                                            scope.launch {
                                                val total = listState.layoutInfo.totalItemsCount
                                                listState.scrollToItem((total - 1).coerceAtLeast(0))
                                                withFrameNanos { }
                                                runCatching { lastItemFr.requestFocus() }
                                            }
                                        }
                                    } else null,
                                    onNext = if (isLast) {
                                        {
                                            scope.launch {
                                                listState.scrollToItem(0)
                                                withFrameNanos { }
                                                runCatching { firstItemFr.requestFocus() }
                                            }
                                        }
                                    } else null,
                                )
                            PickerItem(
                                option = option,
                                isExisting = isExisting,
                                focusRequester = if (isFirst) firstItemFr else null,
                                modifier = wrapMod,
                                onClick = {
                                    // Multi-select: add and stay open. The item
                                    // flips to "Added" as existingRowIds updates.
                                    if (!isExisting) onSelect(option)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = NuvioColors.TextSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerItem(
    option: CatalogSourceOption,
    isExisting: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = { if (!isExisting) onClick() },
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isExisting) 0.45f else 1f)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = option.groupLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isExisting) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Added",
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioColors.TextSecondary,
                )
            }
        }
    }
}

private fun sectionLabel(kind: LayoutRowKind): String = when (kind) {
    LayoutRowKind.ADDON              -> "Addons"
    LayoutRowKind.COLLECTION         -> "Collections"
    LayoutRowKind.TRAKT              -> "Trakt"
    LayoutRowKind.TMDB_DISCOVER      -> "TMDB Discover"
    LayoutRowKind.TMDB_NETWORK       -> "TMDB Networks"
    LayoutRowKind.CONTINUE_WATCHING,
    LayoutRowKind.CONTINUE_WATCHING_SERIES -> "Continue Watching"
    LayoutRowKind.CONTINUE_WATCHING_MOVIES -> "Continue Watching Movies"
    LayoutRowKind.TRAKT_UP_NEXT      -> "Up Next"
    LayoutRowKind.TRAKT_RECOMMENDED_SHOWS -> "Recommended Shows"
    LayoutRowKind.TRAKT_RECOMMENDED_MOVIES -> "Recommended Movies"
    LayoutRowKind.TRAKT_WATCHLIST_SHOWS -> "Watchlist Shows"
    LayoutRowKind.TRAKT_WATCHLIST_MOVIES -> "Watchlist Movies"
    LayoutRowKind.TRAKT_NEW_EPISODES -> "New Episodes"
    LayoutRowKind.TRAKT_NEW_MOVIES   -> "New Movies"
}

private fun Modifier.heightInDialogContent(): Modifier = this.then(Modifier.height(520.dp))
