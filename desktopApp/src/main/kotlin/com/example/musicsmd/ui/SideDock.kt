package com.example.musicsmd.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsmd.nav.Screen
import com.example.musicsmd.support.DietCokeDockButton
import com.example.musicsmd.ui.components.GlassPanel
import com.example.musicsmd.ui.theme.GlassFillStrong
import com.example.musicsmd.ui.theme.LocalMusicSmPalette

/**
 * The mobile app's landscape `SideDock`, after Apple Music's iPad sidebar: one vertical pane of
 * Liquid Glass docked to the start edge holding search and every top-level tab.
 *
 * The selection is one puck that travels between items and is drawn rather than laid out, so a tab
 * change costs repaints of this node instead of recompositions. A detail screen matches no item, so
 * the puck stays on the item it was opened from ([current] is already collapsed to its tab).
 *
 * Desktop has three tabs mobile reaches from its Library/Home screens instead (Downloads, Local,
 * Stats); they sit in the dock too because a column has height to spare.
 */
@Composable
fun SideDock(
    current: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    onSupport: (() -> Unit)? = null,
) {
    val items = remember {
        listOf(
            DockItem(Screen.Search, "Search", Icons.Filled.Search, Icons.Outlined.Search),
            DockItem(Screen.Home, "Listen", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
            DockItem(Screen.Library, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
            DockItem(Screen.Downloads, "Downloads", Icons.Filled.Download, Icons.Outlined.Download),
            DockItem(Screen.LocalMusic, "Local", Icons.Filled.Folder, Icons.Outlined.Folder),
            DockItem(Screen.Stats, "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart),
            DockItem(Screen.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
        )
    }
    var selected by remember {
        mutableIntStateOf(items.indexOfFirst { it.screen == current }.coerceAtLeast(1))
    }
    LaunchedEffect(current) {
        val match = items.indexOfFirst { it.screen == current }
        if (match >= 0) selected = match
    }

    val travel = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) {
        travel.animateTo(
            selected.toFloat(),
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }

    val puckFill = LocalMusicSmPalette.current.surfaceLowest.copy(alpha = PUCK_ALPHA)

    GlassPanel(
        modifier = modifier.width(DOCK_WIDTH),
        shape = RoundedCornerShape(DOCK_CORNER),
        tint = GlassFillStrong,
        liquid = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = DOCK_PADDING)
                .drawWithCache {
                    val itemHeight = ITEM_HEIGHT.toPx()
                    val insetX = PUCK_INSET_X.toPx()
                    val insetY = PUCK_INSET_Y.toPx()
                    val puckSize = Size(size.width - insetX * 2f, itemHeight - insetY * 2f)
                    val corner = CornerRadius(PUCK_CORNER.toPx())
                    val rimStroke = Stroke(PUCK_RIM_WIDTH.toPx())
                    // Fixed to the dock, not the puck, so the puck moves under a single light.
                    val rim = Brush.linearGradient(
                        0.0f to Color.White.copy(alpha = 0.34f),
                        0.6f to Color.White.copy(alpha = 0.06f),
                        1.0f to Color.White.copy(alpha = 0.18f),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind {
                        if (puckSize.minDimension <= 0f) return@onDrawBehind
                        val topLeft = Offset(insetX, travel.value * itemHeight + insetY)
                        drawRoundRect(puckFill, topLeft, puckSize, corner)
                        drawRoundRect(
                            brush = rim,
                            topLeft = topLeft,
                            size = puckSize,
                            cornerRadius = corner,
                            style = rimStroke,
                            blendMode = BlendMode.Plus,
                        )
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEachIndexed { index, item ->
                DockEntry(
                    item = item,
                    selected = index == selected,
                    onClick = { onSelect(item.screen) },
                    modifier = Modifier.height(ITEM_HEIGHT),
                )
            }
            // Below the tabs, so the puck (drawn by index from the top) never lands on it.
            if (onSupport != null) {
                DietCokeDockButton(onClick = onSupport, modifier = Modifier.height(ITEM_HEIGHT))
            }
        }
    }
}

private class DockItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/** Own composable so each colour animation invalidates one entry, not the whole dock. */
@Composable
private fun DockEntry(
    item: DockItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        LocalMusicSmPalette.current.onSurface.copy(alpha = 0.6f)
    }
    val color by animateColorAsState(targetValue = target, label = "dockItemColor")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PUCK_INSET_X)
            .clip(RoundedCornerShape(PUCK_CORNER))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val DOCK_WIDTH = 84.dp
private val DOCK_CORNER = 32.dp
private val DOCK_PADDING = 8.dp
private val ITEM_HEIGHT = 60.dp
private val PUCK_INSET_X = 6.dp
private val PUCK_INSET_Y = 3.dp
private val PUCK_CORNER = 22.dp
private val PUCK_RIM_WIDTH = 0.8.dp

/** Matches the bottom bar's puck on mobile, so both chromes read as the same glass. */
private const val PUCK_ALPHA = 0.55f
