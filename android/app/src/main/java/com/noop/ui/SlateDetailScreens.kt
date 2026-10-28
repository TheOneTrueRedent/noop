package com.noop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.RestScorer
import com.noop.analytics.StrainScorer
import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

// MARK: - Slate detail screens (fork-only)
//
// Step 2 of the Slate layout: the score detail screen reached from each Home dial (one big dial, a list
// of contributor rows each coloured Insufficient / Sufficient / Optimal, a legend, and a "more about"
// card that opens NOOP's own full screen), and the Health monitor (live heart rate over today's curve,
// then resp / resting HR / HRV / SpO2 / skin-temp tiles with their range captions).
//
// Every number is NOOP's own (the same DailyMetric row, RestScorer, StrainScorer, latestVitals and
// VitalBands the stock screens read). The ONLY new judgement is the three-step contributor colouring,
// which is display-only, never fed back into a score, and pinned by [slateBand] + its JVM test.

internal const val SLATE_DETAIL_ROUTE = "slate_detail/{kind}"
internal const val SLATE_HEALTH_MONITOR_ROUTE = "slate_health_monitor"
internal fun slateDetailRoute(kind: String) = "slate_detail/$kind"

/** The three display tiers of a contributor row. */
internal enum class SlateTier { INSUFFICIENT, SUFFICIENT, OPTIMAL }

/**
 * Display tier for a contributor value. [higherIsBetter] picks the direction; [sufficient] and
 * [optimal] are the inclusive lower edges (or upper edges when lower is better). null value -> null.
 */
internal fun slateBand(value: Double?, sufficient: Double, optimal: Double, higherIsBetter: Boolean = true): SlateTier? {
    val v = value ?: return null
    return if (higherIsBetter) {
        when {
            v >= optimal -> SlateTier.OPTIMAL
            v >= sufficient -> SlateTier.SUFFICIENT
            else -> SlateTier.INSUFFICIENT
        }
    } else {
        when {
            v <= optimal -> SlateTier.OPTIMAL
            v <= sufficient -> SlateTier.SUFFICIENT
            else -> SlateTier.INSUFFICIENT
        }
    }
}

/** Deep + REM as a share of total sleep, 0..100. */
internal fun slateRestorativePct(d: DailyMetric?): Double? {
    val total = d?.totalSleepMin?.takeIf { it > 0 } ?: return null
    val deep = d.deepMin ?: return null
    val rem = d.remMin ?: return null
    return (deep + rem) / total * 100.0
}

/** Efficiency stored as 0..1 or 0..100; always returns 0..100. */
internal fun slatePercent(v: Double?): Double? = v?.let { if (it <= 1.5) it * 100.0 else it }

/** Today's value against the trailing-30-day mean, as a tier (for Charge's vitals, where "normal" is personal). */
internal fun slateVsBaseline(value: Double?, mean: Double?, higherIsBetter: Boolean): SlateTier? {
    if (value == null || mean == null || mean == 0.0) return null
    val ratio = value / mean
    return if (higherIsBetter) slateBand(ratio, 0.90, 1.0) else slateBand(ratio, 1.10, 1.0, higherIsBetter = false)
}

@Composable
private fun SlateTier?.tint(): Color = when (this) {
    SlateTier.OPTIMAL -> Palette.statusPositive
    SlateTier.SUFFICIENT -> Palette.textSecondary
    SlateTier.INSUFFICIENT -> Palette.statusWarning
    null -> Palette.textTertiary
}

private const val DASH = "–"

private data class ContributorRow(val icon: ImageVector, val label: String, val value: String?, val tier: SlateTier?)

@Composable
fun SlateScoreDetailScreen(
    vm: AppViewModel,
    kind: String,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    val context = LocalContext.current
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val effortScale = remember { UnitPrefs.effortScale(context) }
    val logicalKey = remember { logicalDayKeyNow() }
    val localKey = remember { LocalDate.now().toString() }
    val row = remember(today, days) { resolveTodayRow(days, logicalKey, localKey) ?: today }
    var importedPerf by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var importedNeed by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(days) {
        suspend fun load(key: String) = runCatching {
            vm.repo.metricSeries("my-whoop", key, "0000-00-00", "9999-99-99")
        }.getOrDefault(emptyList()).associate { it.day to it.value }
        importedPerf = load("sleep_performance")
        importedNeed = load("sleep_need_min")
    }
    val window = remember(days, row) { days.filter { row == null || it.day < row.day }.takeLast(30) }
    fun mean(sel: (DailyMetric) -> Double?) = window.mapNotNull(sel).takeIf { it.isNotEmpty() }?.average()

    val title: String
    val heroLabel: String
    val heroValue: String?
    val heroFraction: Double
    val heroColor: Color
    val rows: List<ContributorRow>
    val moreText: String
    val emptyText: String?
    when (kind) {
        "charge" -> {
            val charge = row?.recovery
            title = uiString(R.string.trends_charge)
            heroLabel = uiString(R.string.trends_charge)
            heroValue = charge?.let { "${it.roundToInt()}%" }
            heroFraction = (charge ?: 0.0) / 100.0
            heroColor = charge?.let { Palette.recoveryColor(it) } ?: DomainTheme.Charge.color
            val rest = row?.let { importedPerf[it.day] ?: RestScorer.restFromDaily(it) }
            rows = listOf(
                ContributorRow(Icons.Filled.MonitorHeart, uiString(R.string.slate_hrv_full),
                    row?.avgHrv?.let { "${it.roundToInt()} ms" }, slateVsBaseline(row?.avgHrv, mean { it.avgHrv }, true)),
                ContributorRow(Icons.Filled.FavoriteBorder, uiString(R.string.trends_resting_hr_full),
                    row?.restingHr?.let { "$it bpm" },
                    slateVsBaseline(row?.restingHr?.toDouble(), mean { it.restingHr?.toDouble() }, false)),
                ContributorRow(Icons.Filled.Air, uiString(R.string.slate_respiratory_rate),
                    row?.respRateBpm?.let { String.format(Locale.getDefault(), "%.1f", it) },
                    slateVsBaseline(row?.respRateBpm, mean { it.respRateBpm }, false)),
                ContributorRow(Icons.Filled.Bedtime, uiString(R.string.trends_rest),
                    rest?.let { "${it.roundToInt()}%" }, slateBand(rest, 70.0, 85.0)),
            )
            moreText = uiString(R.string.slate_more_about_charge)
            emptyText = if (charge == null) uiString(R.string.slate_waiting_body) else null
        }
        "effort" -> {
            val effortRaw = row?.strain?.let { StrainScorer.effectiveEffort(live = null, stored = it) }
            val effort = effortRaw?.let { UnitFormatter.effortValue(it, effortScale) }
            val max = if (effortScale == EffortScale.WHOOP) 21.0 else 100.0
            title = uiString(R.string.trends_effort)
            heroLabel = uiString(R.string.trends_effort)
            heroValue = effort?.let { String.format(Locale.getDefault(), "%.1f", it) }
            heroFraction = (effort ?: 0.0) / max
            heroColor = DomainTheme.Effort.color
            val meanEffort = mean { it.strain }
            rows = listOf(
                ContributorRow(Icons.Filled.Timeline, uiString(R.string.trends_effort),
                    effortRaw?.let { UnitFormatter.effortDisplay(it, effortScale) },
                    slateVsBaseline(effortRaw, meanEffort, true)),
                ContributorRow(Icons.Filled.LocalFireDepartment, uiString(R.string.l10n_today_screen_calories_3e62ecfe),
                    row?.activeKcalEst?.let { "%,d".format(it.roundToInt()) },
                    slateVsBaseline(row?.activeKcalEst, mean { it.activeKcalEst }, true)),
                ContributorRow(Icons.Filled.Favorite, uiString(R.string.l10n_today_screen_steps_cdde4f20),
                    row?.steps?.let { "%,d".format(it) },
                    slateVsBaseline(row?.steps?.toDouble(), mean { it.steps?.toDouble() }, true)),
                ContributorRow(Icons.Filled.Schedule, uiString(R.string.nav_workouts),
                    row?.exerciseCount?.toString(), null),
            )
            moreText = uiString(R.string.slate_more_about_effort)
            emptyText = if (effort == null) uiString(R.string.slate_no_effort_yet) else null
        }
        else -> {
            val rest = row?.let { importedPerf[it.day] ?: RestScorer.restFromDaily(it) }
            val need = sleepNeedForDay(row, days, importedNeed)
            val hoursVsNeed = row?.totalSleepMin?.let { it / need * 100.0 }
            val eff = slatePercent(row?.efficiency)
            val restorative = slateRestorativePct(row)
            title = uiString(R.string.nav_today)
            heroLabel = uiString(R.string.trends_rest)
            heroValue = rest?.let { "${it.roundToInt()}%" }
            heroFraction = (rest ?: 0.0) / 100.0
            heroColor = DomainTheme.Rest.color
            rows = listOf(
                ContributorRow(Icons.Filled.Schedule, uiString(R.string.slate_hours_vs_need),
                    hoursVsNeed?.let { "${it.roundToInt()}%" }, slateBand(hoursVsNeed, 70.0, 85.0)),
                ContributorRow(Icons.Filled.Bedtime, uiString(R.string.slate_sleep_efficiency),
                    eff?.let { "${it.roundToInt()}%" }, slateBand(eff, 75.0, 85.0)),
                ContributorRow(Icons.Filled.Thermostat, uiString(R.string.slate_restorative_sleep),
                    restorative?.let { "${it.roundToInt()}%" }, slateBand(restorative, 30.0, 40.0)),
                ContributorRow(Icons.Filled.GraphicEq, uiString(R.string.slate_disturbances),
                    row?.disturbances?.toString(), slateBand(row?.disturbances?.toDouble(), 10.0, 5.0, higherIsBetter = false)),
            )
            moreText = uiString(R.string.slate_more_about_sleep)
            emptyText = if (row?.totalSleepMin == null) uiString(R.string.slate_no_sleep_yet) else null
        }
    }

    SlateDetailScaffold(title = title.uppercase(), onBack = onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            GlowRing(
                fraction = if (heroValue == null) 0f else heroFraction.toFloat(),
                value = 0.0, color = heroColor, diameter = 220.dp, lineWidth = 12.dp, showsLabel = false,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiString(R.string.app_name), style = NoopType.overline.copy(letterSpacing = 4.sp), color = Palette.textTertiary)
                Spacer(Modifier.height(10.dp))
                Text(heroValue ?: DASH, style = NoopType.title1.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold),
                    color = Palette.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(heroLabel.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.6.sp), color = Palette.textPrimary)
            }
        }
        val shape = RoundedCornerShape(Metrics.cardRadius)
        Column(Modifier.fillMaxWidth().clip(shape).background(Palette.surfaceRaised, shape).padding(vertical = 4.dp)) {
            rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Palette.hairline))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(r.icon, contentDescription = null, tint = Palette.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(r.label.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.2.sp), color = Palette.textPrimary,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.value ?: DASH, style = NoopType.headline.copy(fontWeight = FontWeight.Bold), color = r.tier.tint())
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(8.dp)).background(Palette.surfaceInset)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SlateLegendDot(SlateTier.INSUFFICIENT, uiString(R.string.slate_tier_insufficient))
                SlateLegendDot(SlateTier.SUFFICIENT, uiString(R.string.slate_tier_sufficient))
                SlateLegendDot(SlateTier.OPTIMAL, uiString(R.string.slate_tier_optimal))
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(shape).background(Palette.surfaceRaised, shape)
                .clickable(onClick = onMore).padding(Metrics.cardPadding),
        ) {
            if (emptyText != null) {
                Text(emptyText, style = NoopType.body, color = Palette.textPrimary)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(moreText.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.4.sp), color = Palette.accent)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Palette.accent,
                    modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SlateLegendDot(tier: SlateTier, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(3.dp).background(tier.tint()))
        Spacer(Modifier.width(6.dp))
        Text(label, style = NoopType.caption, color = Palette.textSecondary)
    }
}

@Composable
internal fun SlateDetailScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        Modifier.fillMaxSize().background(Palette.surfaceBase).verticalScroll(rememberScrollState())
            .padding(top = statusTop + Metrics.space8)
            .padding(horizontal = Metrics.space16)
            .padding(bottom = Metrics.space24 + BottomBarStyleStore.barHeightForContent()),
        verticalArrangement = Arrangement.spacedBy(Metrics.gap),
    ) {
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = uiString(R.string.slate_back),
                tint = Palette.textPrimary,
                modifier = Modifier.align(Alignment.CenterStart).size(36.dp).clip(CircleShape).clickable(onClick = onBack).padding(4.dp))
            Text(title, style = NoopType.overline.copy(letterSpacing = 1.8.sp), color = Palette.textPrimary,
                modifier = Modifier.align(Alignment.Center))
        }
        content()
    }
}

// MARK: - Health monitor

@Composable
fun SlateHealthMonitorScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenVital: (String) -> Unit,
) {
    val context = LocalContext.current
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val hrNow = displayHr(bpm, live)
    var todayCurve by remember { mutableStateOf<List<Double>>(emptyList()) }
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis() / 1000L
        val start = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
        todayCurve = runCatching {
            vm.repo.hrSamplesUnion(vm.activeStrapId, start, now)
                .groupBy { it.ts / 300L }.toSortedMap().values.map { b -> b.map { it.bpm }.average() }
        }.getOrDefault(emptyList())
    }
    val tempUnit = remember { UnitPrefs.temperature(context) }
    val vitals = remember(days, tempUnit) { latestVitals(days, tempUnit) }

    SlateDetailScaffold(title = uiString(R.string.slate_health_monitor).uppercase(), onBack = onBack) {
        Text(uiString(R.string.slate_heart_rate).uppercase(), style = NoopType.overline.copy(letterSpacing = 1.4.sp),
            color = Palette.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Palette.effortColor, modifier = Modifier.size(16.dp))
                Text(hrNow?.toString() ?: DASH, style = NoopType.title1.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                    color = Palette.textPrimary)
                Text(uiString(R.string.slate_bpm), style = NoopType.overline, color = Palette.textSecondary)
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f)) { Sparkline(values = todayCurve, color = Palette.effortColor) }
        }
        val shown = vitals.filter { it.key in setOf("resp", "rhr", "hrv", "spo2", "skin") }
        shown.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Metrics.gap)) {
                pair.forEach { v -> SlateVitalTile(v, Modifier.weight(1f)) { onOpenVital(v.key) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SlateVitalTile(v: Vital, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Metrics.cardRadius)
    val out = v.banding.band == VitalBands.Band.OUT_OF_RANGE
    Column(modifier.clip(shape).background(Palette.surfaceRaised, shape).clickable(onClick = onClick).padding(12.dp)) {
        Text(v.label.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.2.sp), color = Palette.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(v.value?.let(v.format) ?: DASH, style = NoopType.title2.copy(fontWeight = FontWeight.Bold),
                color = if (out) Palette.statusWarning else Palette.textPrimary)
            Spacer(Modifier.width(4.dp))
            Text(v.unit, style = NoopType.caption, color = Palette.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        val calibrating = v.banding.basis != VitalBands.Basis.PERSONAL
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.surfaceInset).padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (calibrating) {
                Icon(Icons.Filled.HourglassEmpty, contentDescription = null, tint = Palette.textSecondary, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                if (calibrating) uiString(R.string.slate_calibrating_range) else (v.rangeCaption ?: uiString(R.string.slate_all_in_range)),
                style = NoopType.footnote, color = Palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
