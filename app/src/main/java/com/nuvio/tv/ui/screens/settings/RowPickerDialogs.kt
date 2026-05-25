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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.LayoutRowKind
import com.nuvio.tv.ui.theme.NuvioColors

// ── + Catalog ───────────────────────────────────────────────────────────────
//
// Reuses the existing [AddRowPickerDialog] but pre-filters [sources] to ADDON
// rows only — Trakt and Collections have their own dedicated pickers.

@Composable
fun CatalogPickerDialog(
    sources: List<CatalogSourceOption>,
    existingRowIds: Set<String>,
    onSelect: (CatalogSourceOption) -> Unit,
    onDismiss: () -> Unit,
    scope: com.nuvio.tv.domain.model.LayoutScreenScope = com.nuvio.tv.domain.model.LayoutScreenScope.HOME,
) {
    val addonOnly = remember(sources, scope) {
        sources.filter { it.kind == LayoutRowKind.ADDON }.let { addons ->
            when (scope) {
                com.nuvio.tv.domain.model.LayoutScreenScope.MOVIES ->
                    addons.filter { it.apiType.equals("movie", ignoreCase = true) }
                com.nuvio.tv.domain.model.LayoutScreenScope.TV ->
                    addons.filter { it.apiType.equals("series", ignoreCase = true) }
                else -> addons
            }
        }
    }
    AddRowPickerDialog(
        sources = addonOnly,
        existingRowIds = existingRowIds,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

// ── + TMDB Source ───────────────────────────────────────────────────────────
//
// Two sections in one dialog. The Discover section lets the user build a
// `/discover/movie` or `/discover/tv` query; the Networks section is a flat
// list of well-known TMDB network/provider ids. Both append to the active
// scope via the parent screen's onAdd callbacks.

private data class TmdbNetworkOption(val id: Int, val label: String)

private val TMDB_NETWORK_OPTIONS = listOf(
    TmdbNetworkOption(213, "Netflix"),
    TmdbNetworkOption(49, "HBO"),
    TmdbNetworkOption(2739, "Disney+"),
    TmdbNetworkOption(1024, "Amazon Prime Video"),
    TmdbNetworkOption(453, "Hulu"),
    TmdbNetworkOption(2552, "Apple TV+"),
    TmdbNetworkOption(4330, "Paramount+"),
    TmdbNetworkOption(3186, "HBO Max"),
    TmdbNetworkOption(174, "AMC"),
    TmdbNetworkOption(67, "Showtime"),
    TmdbNetworkOption(64, "Discovery"),
    TmdbNetworkOption(56, "Cartoon Network"),
)

private val DISCOVER_MEDIA_OPTIONS = listOf("movie" to "Movies", "tv" to "TV Shows")
private val DISCOVER_SORT_OPTIONS = listOf(
    "popularity.desc" to "Popularity",
    "primary_release_date.desc" to "Release Date",
    "vote_average.desc" to "Rating",
)

@Composable
fun TmdbSourcePickerDialog(
    existingRowIds: Set<String>,
    onAddDiscover: (mediaType: String, sortBy: String, genre: String?, year: String?, displayName: String) -> Unit,
    onAddNetwork: (networkId: Int, mediaType: String, displayName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler { onDismiss() }
        var mediaType by remember { mutableStateOf("movie") }
        var sortBy by remember { mutableStateOf("popularity.desc") }
        var genre by remember { mutableStateOf("") }
        var year by remember { mutableStateOf("") }
        val firstFr = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            repeat(4) { withFrameNanos { } }
            runCatching { firstFr.requestFocus() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(640.dp)
                    .height(560.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(NuvioColors.BackgroundCard),
            ) {
                Text(
                    text = "Add TMDB Source",
                    style = MaterialTheme.typography.titleLarge,
                    color = NuvioColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
                )
                Text(
                    text = "Discover query or a popular network / provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header_discover") {
                        SectionHeaderText("Discover")
                    }
                    item(key = "discover_media") {
                        SegmentedRow(
                            label = "Media",
                            options = DISCOVER_MEDIA_OPTIONS,
                            selected = mediaType,
                            onSelect = { mediaType = it },
                            firstFocusRequester = firstFr,
                        )
                    }
                    item(key = "discover_sort") {
                        SegmentedRow(
                            label = "Sort by",
                            options = DISCOVER_SORT_OPTIONS,
                            selected = sortBy,
                            onSelect = { sortBy = it },
                        )
                    }
                    item(key = "discover_genre") {
                        InlineTextField(
                            label = "Genre id",
                            placeholder = "Optional, e.g. 28",
                            value = genre,
                            onValueChange = { genre = it.filter { ch -> ch.isDigit() } },
                        )
                    }
                    item(key = "discover_year") {
                        InlineTextField(
                            label = "Year",
                            placeholder = "Optional, e.g. 2026",
                            value = year,
                            onValueChange = { year = it.filter { ch -> ch.isDigit() }.take(4) },
                        )
                    }
                    item(key = "discover_add") {
                        AddCompactButton(
                            label = "Add Discover Row",
                            onClick = {
                                val mediaLabel = DISCOVER_MEDIA_OPTIONS
                                    .firstOrNull { it.first == mediaType }?.second ?: mediaType
                                val sortLabel = DISCOVER_SORT_OPTIONS
                                    .firstOrNull { it.first == sortBy }?.second ?: sortBy
                                val display = buildString {
                                    append(mediaLabel)
                                    append(" · ")
                                    append(sortLabel)
                                    if (genre.isNotBlank()) append(" · genre $genre")
                                    if (year.isNotBlank()) append(" · $year")
                                }
                                onAddDiscover(
                                    mediaType,
                                    sortBy,
                                    genre.ifBlank { null },
                                    year.ifBlank { null },
                                    display,
                                )
                                onDismiss()
                            },
                        )
                    }
                    item(key = "header_networks") {
                        Spacer(modifier = Modifier.height(6.dp))
                        SectionHeaderText("Networks & Providers")
                    }
                    items(items = TMDB_NETWORK_OPTIONS, key = { "net_${it.id}" }) { net ->
                        val rowId = "tmdb_network|${net.id}|$mediaType"
                        val existing = rowId in existingRowIds
                        PickerListItem(
                            title = net.label,
                            subtitle = "id ${net.id} · ${if (mediaType == "tv") "TV" else "Movies"}",
                            isExisting = existing,
                            onClick = {
                                if (!existing) {
                                    onAddNetwork(net.id, mediaType, net.label)
                                    onDismiss()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── + Trakt List ────────────────────────────────────────────────────────────
//
// Reuses the in-memory Trakt stub entries already surfaced by the legacy
// AddRowPickerDialog (Watchlist / Recommended / Trending Movies / Trending
// Shows). User Trakt lists aren't yet exposed by the codebase; when wiring
// for that lands later, just expand the [sources] filter upstream.

@Composable
fun TraktPickerDialog(
    sources: List<CatalogSourceOption>,
    existingRowIds: Set<String>,
    onSelect: (CatalogSourceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val traktOnly = remember(sources) { sources.filter { it.kind == LayoutRowKind.TRAKT } }
    AddRowPickerDialog(
        sources = traktOnly,
        existingRowIds = existingRowIds,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

// ── + Collection ────────────────────────────────────────────────────────────

@Composable
fun CollectionPickerDialog(
    collections: List<Collection>,
    existingRowIds: Set<String>,
    onSelectFolder: (collectionId: String, folderId: String, displayName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler { onDismiss() }
        val firstFr = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            repeat(4) { withFrameNanos { } }
            runCatching { firstFr.requestFocus() }
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
                    .height(520.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(NuvioColors.BackgroundCard),
            ) {
                Text(
                    text = "Add Collection",
                    style = MaterialTheme.typography.titleLarge,
                    color = NuvioColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
                )
                Text(
                    text = "Pick a folder from one of your collections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                )
                if (collections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No collections yet. Create one under Settings → Collections.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioColors.TextSecondary,
                        )
                    }
                    return@Column
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    var globalIndex = 0
                    collections.forEach { collection ->
                        item(key = "ch_${collection.id}") {
                            SectionHeaderText(collection.title)
                        }
                        if (collection.folders.isEmpty()) {
                            item(key = "ch_${collection.id}_empty") {
                                Text(
                                    text = "No folders in this collection.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NuvioColors.TextSecondary,
                                    modifier = Modifier.padding(start = 18.dp, bottom = 4.dp),
                                )
                            }
                        }
                        itemsIndexed(
                            items = collection.folders,
                            key = { _, f -> "f_${collection.id}_${f.id}" },
                        ) { _, folder ->
                            val rowId = "collection|${collection.id}|${folder.id}"
                            val existing = rowId in existingRowIds
                            val isFirst = (globalIndex == 0)
                            globalIndex++
                            PickerListItem(
                                title = folder.title,
                                subtitle = collection.title,
                                isExisting = existing,
                                focusRequester = if (isFirst) firstFr else null,
                                onClick = {
                                    if (!existing) {
                                        onSelectFolder(collection.id, folder.id, folder.title)
                                        onDismiss()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Shared subcomponents ────────────────────────────────────────────────────

@Composable
private fun SectionHeaderText(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = NuvioColors.TextSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SegmentedRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    firstFocusRequester: FocusRequester? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NuvioColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { idx, (value, lbl) ->
                ChipButton(
                    label = lbl,
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    focusRequester = if (idx == 0) firstFocusRequester else null,
                )
            }
        }
    }
}

@Composable
private fun InlineTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NuvioColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .onFocusChanged { focused = it.isFocused || it.hasFocus },
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = NuvioColors.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(NuvioColors.FocusRing),
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val container = when {
        selected -> NuvioColors.FocusBackground
        focused -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(containerColor = container, focusedContainerColor = container),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, NuvioColors.FocusRing),
                shape = shape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NuvioColors.TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun AddCompactButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
            shape = CardDefaults.shape(shape),
            colors = CardDefaults.colors(
                containerColor = if (focused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f),
                focusedContainerColor = Color.White.copy(alpha = 0.18f),
            ),
            border = CardDefaults.border(
                border = Border.None,
                focusedBorder = Border(
                    border = BorderStroke(2.dp, NuvioColors.FocusRing),
                    shape = shape,
                ),
            ),
            scale = CardDefaults.scale(focusedScale = 1f),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                color = NuvioColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PickerListItem(
    title: String,
    subtitle: String,
    isExisting: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = { if (!isExisting) onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isExisting) 0.45f else 1f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(
            containerColor = if (focused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
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
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
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
