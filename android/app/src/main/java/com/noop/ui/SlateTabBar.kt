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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R

// MARK: - Slate tab bar (fork-only)
//
// The Slate layout's bottom bar, drawn to the reference: a tall rounded pill of four outlined tabs — Home ·
// Health · Trends · More (the reference's fourth tab is a cloud Community, which NOOP has no counterpart
// for) — with the Coach as a separate rounded button with a gradient rim (shown only while Coach is on). Same routes as the shipped
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
    val pill = RoundedCornerShape(34.dp)
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .widthIn(max = 460.dp)
                .height(76.dp)
                .clip(pill)
                .background(
                    Brush.verticalGradient(listOf(SlateUi.tabBar.copy(alpha = 0.98f), SlateUi.bgMid.copy(alpha = 0.98f))),
                    pill,
                )
                .border(1.dp, SlateUi.raised.copy(alpha = 0.6f), pill)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { dest ->
                val active = when (dest) {
                    Destination.More -> tabs.none { it == current } && current != Destination.Coach
                    else -> current == dest
                }
                SlateTab(
                    icon = when (dest) {
                        Destination.Today -> Icons.Outlined.Home
                        Destination.Health -> Icons.Outlined.MonitorHeart
                        Destination.Trends -> Icons.AutoMirrored.Outlined.TrendingUp
                        else -> Icons.Outlined.Menu
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
            val shape = RoundedCornerShape(30.dp)
            Box(
                Modifier
                    .size(76.dp)
                    .clip(shape)
                    .background(Brush.radialGradient(listOf(SlateUi.coachCore, SlateUi.bgMid)), shape)
                    .border(
                        if (coachActive) 2.dp else 1.5.dp,
                        Brush.linearGradient(listOf(SlateUi.coachRimA, SlateUi.coachRimB)),
                        shape,
                    )
                    .clickable { onTabSelected(Destination.Coach) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(44.dp).border(2.dp, SlateUi.coachRimB, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = uiString(R.string.nav_coach),
                        tint = SlateUi.textPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun SlateTab(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) SlateUi.textPrimary else SlateUi.tabInactive,
            modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = SlateType.tab.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp),
            color = if (active) SlateUi.textPrimary else SlateUi.tabInactive,
            maxLines = 1,
        )
    }
}
