package com.cues.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cues.app.ui.nav.CuesRoutes
import com.cues.app.ui.theme.CuesShape
import com.cues.app.ui.theme.CuesType
import com.cues.app.ui.theme.cuesTokens

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(CuesRoutes.NOW, "Now", Icons.Filled.Bolt),
    TabSpec(CuesRoutes.INSIGHTS, "Insights", Icons.Filled.Insights),
    TabSpec(CuesRoutes.ASK, "Ask", Icons.Filled.Chat),
    TabSpec(CuesRoutes.RECEIPTS, "Receipts", Icons.Filled.Receipt),
    TabSpec(CuesRoutes.WORKBENCH, "Workbench", Icons.Filled.Tune),
)

/** The 5-tab bottom bar (redesign plan §2), icon + label per nav-label-icon (never icon-only). */
@Composable
fun CuesBottomNav(currentRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = cuesTokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(t.island)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == currentRoute
            TabItem(tab.label, tab.icon, selected, Modifier.weight(1f)) { onSelect(tab.route) }
        }
    }
}

@Composable
private fun RowScope.TabItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = cuesTokens
    val haptics = com.cues.app.ui.theme.rememberCuesHaptics()
    val color = if (selected) t.doYellow else t.inkSlate
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(vertical = 4.dp)
            .clickableNoIndicationPublic {
                if (!selected) haptics.tap()
                onClick()
            },
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.height(22.dp))
        Text(label, style = CuesType.labelSmall, color = color, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * The top bar shown on every tab: logo mark, screen name, the ON-DEVICE trust
 * chip and a Checks shortcut (redesign plan §2).
 */
@Composable
fun CuesTopBar(
    title: String,
    onOpenChecks: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = cuesTokens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .height(28.dp)
                    .padding(end = 8.dp)
                    .background(t.doYellow, CircleShape)
                    .padding(6.dp),
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF0B0C10), modifier = Modifier.height(16.dp))
            }
            androidx.compose.foundation.layout.Column {
                Text("CUES", style = CuesType.label, color = t.inkPrimary)
                Text(title.uppercase(), style = CuesType.labelSmall, color = t.inkSlate)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailing?.invoke()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(t.island, CuesShape.pill)
                    .clickableNoIndicationPublic(onOpenChecks)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = "Checks & settings", tint = t.inkSecondary, modifier = Modifier.height(16.dp))
            }
        }
    }
}
