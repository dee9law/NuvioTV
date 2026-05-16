package com.nuvio.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val focusRequester: FocusRequester? = null
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopNavBar(
    items: List<NavItem>,
    selectedRoute: String?,
    hazeState: HazeState,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(50.dp)
    Row(
        modifier = modifier
            .hazeChild(state = hazeState, shape = pillShape)
            .background(color = Color(0xCC0D1117), shape = pillShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            TopNavItem(
                item = item,
                isSelected = item.route == selectedRoute,
                onNavigate = onNavigate
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopNavItem(
    item: NavItem,
    isSelected: Boolean,
    onNavigate: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White.copy(alpha = 0.22f)
            isFocused  -> Color.White.copy(alpha = 0.10f)
            else       -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "topNavBg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "topNavScale"
    )
    val contentAlpha = if (isSelected || isFocused) 1f else 0.55f
    val itemColor = Color.White.copy(alpha = contentAlpha)
    val pillShape = RoundedCornerShape(50.dp)

    Card(
        onClick = { onNavigate(item.route) },
        modifier = Modifier
            .scale(scale)
            .then(
                if (item.focusRequester != null) Modifier.focusRequester(item.focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = CardDefaults.shape(pillShape),
        colors = CardDefaults.colors(
            containerColor = bgColor,
            focusedContainerColor = bgColor
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.40f)),
                shape = pillShape
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = itemColor,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall,
                color = itemColor
            )
        }
    }
}
