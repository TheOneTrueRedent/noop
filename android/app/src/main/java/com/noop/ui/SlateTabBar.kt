package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R

// MARK: - Slate tab bar (fork-only)
//
// The Slate layout's bottom bar: a rounded pill of four tabs — Home · Health · Trends · More — with the
// Coach as a separate round button beside it (shown only while Coach is on). Same routes as the shipped
// bar; only the set and the drawing differ. "More" stays lit for any screen reached through More.

/** The four pill tabs, in order. A function so a JVM test can pin the set. */
internal fun slateTabs(): List<Destination> =
    listOf(Destination.Today, Destination.Health, Destination.Trends, Destination.More)

@Composable
internal fun SlateTabBar(
    current: Destination,
    onTabSelected: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = slateTabs()
    val pill = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = Metrics.space10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .widthIn(max = 420.dp)
                .clip(pill)
                .background(Palette.surfaceRaised.copy(alpha = 0.97f), pill)
                .border(0.5.dp, Palette.hairline, pill)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { dest ->
                val active = when (dest) {
                    Destination.More -> tabs.none { it == current } && current != Destination.Coach
                    else -> current == dest
                }
                SlateTab(
                    icon = when (dest) {
                        Destination.Today -> Icons.Filled.Home
                        Destination.Health -> Icons.Filled.MonitorHeart
                        Destination.Trends -> Icons.AutoMirrored.Filled.TrendingUp
                        else -> Icons.Filled.Menu
                    },
                    label = uiString(when (dest) {
                        Destination.Today -> R.string.slate_tab_home
                        Destination.Health -> R.string.nav_health
                        Destination.Trends -> R.string.nav_trends
                        else -> R.string.nav_more
                    }),
                    active = active,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected(dest) },
                )
            }
        }
        if (BottomBarStyleStore.coachEnabled) {
            val coachActive = current == Destination.Coach
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Palette.surfaceRaised.copy(alpha = 0.97f))
                    .border(1.5.dp, if (coachActive) Palette.accent else Palette.accent.copy(alpha = 0.55f), CircleShape)
                    .clickable { onTabSelected(Destination.Coach) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = uiString(R.string.nav_coach), tint = Palette.textPrimary,
                    modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SlateTab(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Palette.textPrimary else Palette.textTertiary,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) Palette.textPrimary else Palette.textTertiary,
            maxLines = 1,
        )
    }
}
