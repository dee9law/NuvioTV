package com.nuvio.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.Collection

private val Background = Color(0xFF101418).copy(alpha = 0.95f)
private val ItemShape = RoundedCornerShape(10.dp)
private val ContainerShape = RoundedCornerShape(14.dp)
private val FocusBorder = Color.White.copy(alpha = 0.55f)
private val FocusedItemBg = Color.White.copy(alpha = 0.16f)

/**
 * TV-friendly dropdown listing all collection titles. Auto-focuses the first
 * item on open, navigable via D-pad up/down + center, dismissed via Back or
 * by tapping outside.
 *
 * Positioned via [offset] in pixels from the parent's top-start corner. Caller
 * is responsible for computing a sensible offset that aligns under the
 * Collections pill.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CollectionsDropdown(
    collections: List<Collection>,
    onSelect: (Collection) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset = IntOffset.Zero,
) {
    if (collections.isEmpty()) {
        // Nothing to show — auto-dismiss so the user isn't trapped on an empty popup.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BackHandler(enabled = true) { onDismiss() }

        val firstItemFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            // The popup window needs a couple frames before its decor view is
            // attached and willing to accept focus.
            repeat(4) { withFrameNanos { } }
            runCatching { firstItemFocusRequester.requestFocus() }
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(260.dp)
                .clip(ContainerShape)
                .background(Background)
                .padding(vertical = 8.dp, horizontal = 8.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = collections,
                    key = { _, c -> c.id },
                ) { index, collection ->
                    DropdownItem(
                        title = collection.title,
                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                        onClick = {
                            onSelect(collection)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DropdownItem(
    title: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "dropdownScale",
    )
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) FocusedItemBg else Color.Transparent,
        animationSpec = tween(140),
        label = "dropdownBg",
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(ItemShape),
        colors = CardDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, FocusBorder),
                shape = ItemShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}
