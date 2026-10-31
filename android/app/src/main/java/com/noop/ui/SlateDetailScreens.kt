package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// MARK: - Slate detail screens (fork-only)
//
// The score detail screen reached from each Home dial, laid out to the reference "Recovery" screenshot:
// the selected day's date, one big ring with the wordmark, then a card of contributor rows, each showing
// the day's value, its 30-day average and recorded-day count, and the difference from that average. And the
// Health monitor, laid out to its reference: a full-width live heart-rate panel on a grid (big BPM, HR zone,
// recent curve), then resp / SpO2 / RHR / HRV / skin-temp tiles with a range chip each.
//
// Every number is NOOP's own (the same DailyMetric row, RestScorer, StrainScorer, latestVitals and VitalBands
// the stock screens read). The only arithmetic added here is the 30-day mean / count / difference, which is
// display-only and pinned by [slateWindowStats] and its JVM test.

internal const val SLATE_DETAIL_ROUTE = "slate_detail/{kind}/{day}"
internal const val SLATE_HEALTH_MONITOR_ROUTE = "slate_health_monitor"
internal const val SLATE_FULL_HEALTH_ROUTE = "slate_full_health"
internal fun slateDetailRoute(kind: String, day: String) = "slate_detail/$kind/$day"

/** Mean and recorded-day count over the [span] days ending on (and including) [day]. */
internal data class SlateWindowStats(val mean: Double?, val count: Int)

internal fun slateWindowStats(
    rows: List<DailyMetric>,
    day: String,
    span: Int = 30,
    select: (DailyMetric) -> Double?,
): SlateWindowStats {
    val end = runCatching { LocalDate.parse(day) }.getOrNull() ?: return SlateWindowStats(null, 0)
    val start = end.minusDays((span - 1).toLong()).toString()
    val values = rows.asSequence().filter { it.day in start..day }.mapNotNull(select).toList()
    return SlateWindowStats(values.takeIf { it.isNotEmpty() }?.average(), values.size)
}

/** Signed difference with a true minus sign: "+14", "−6", "+0.4". */
internal fun slateSigned(diff: Double, decimals: Int): String {
    val mag = if (decimals == 0) abs(diff).roundToInt().toString()
    else String.format(Locale.getDefault(), "%.${decimals}f", abs(diff))
    val zero = mag.all { it == '0' || it == '.' || it == ',' }
    return if (zero) mag else (if (diff > 0) "+" else "−") + mag
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

private const val DASH = "–"

/** One contributor row: how to read the value from a day, how to print it, and its unit words. */
private class DetailRow(
    val icon: ImageVector,
    val label: String,
    val unit: String,
    val diffUnit: String,
    val decimals: Int,
    val select: (DailyMetric) -> Double?,
    val format: (Double) -> String,
)

@Composable
fun SlateScoreDetailScreen(
    vm: AppViewModel,
    kind: String,
    day: String,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    val context = LocalContext.current
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val effortScale = remember { UnitPrefs.effortScale(context) }
    val logicalKey = remember { logicalDayKeyNow() }
    val localKey = remember { LocalDate.now().toString() }
    val todayRow = remember(today, days) { resolveTodayRow(days, logicalKey, localKey) ?: today }
    val row = remember(days, todayRow, day) { days.lastOrNull { it.day == day } ?: todayRow?.takeIf { it.day == day } }
    val isToday = day >= minOf(logicalKey, localKey)
    var importedPerf by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(days) {
        importedPerf = runCatching {
            vm.repo.metricSeries("my-whoop", "sleep_performance", "0000-00-00", "9999-99-99")
        }.getOrDefault(emptyList()).associate { it.day to it.value }
    }
    fun restOf(d: DailyMetric): Double? = importedPerf[d.day] ?: RestScorer.restFromDaily(d)

    val ms = "ms"
    val bpm = "bpm"
    val pp = uiString(R.string.slate_unit_pp)
    val heroLabel: String
    val heroValue: Double?
    val heroPercent: Boolean
    val heroFraction: Double
    val heroColor: Color
    val rows: List<DetailRow>
    val emptyText: String?
    when (kind) {
        "charge" -> {
            heroLabel = uiString(R.string.trends_charge)
            heroValue = row?.recovery
            heroPercent = true
            heroFraction = (heroValue ?: 0.0) / 100.0
            heroColor = heroValue?.let { Palette.recoveryColor(it) } ?: DomainTheme.Charge.color
            rows = listOf(
                DetailRow(Icons.Outlined.MonitorHeart, uiString(R.string.slate_hrv_full), ms, ms, 0,
                    { it.avgHrv }, { it.roundToInt().toString() }),
                DetailRow(Icons.Outlined.Timeline, uiString(R.string.trends_resting_hr_full), bpm, bpm, 0,
                    { it.restingHr?.toDouble() }, { it.roundToInt().toString() }),
                DetailRow(Icons.Outlined.Waves, uiString(R.string.slate_respiratory_rate),
                    uiString(R.string.slate_unit_breaths), uiString(R.string.slate_unit_breaths), 1,
                    { it.respRateBpm }, { String.format(Locale.getDefault(), "%.1f", it) }),
                DetailRow(Icons.Outlined.Bedtime, uiString(R.string.slate_sleep_score), "", pp, 0,
                    { restOf(it) }, { "${it.roundToInt()}%" }),
            )
            emptyText = if (heroValue == null) uiString(R.string.slate_waiting_body) else null
        }
        "effort" -> {
            val raw = row?.strain?.let { StrainScorer.effectiveEffort(live = null, stored = it) }
            val max = if (effortScale == EffortScale.WHOOP) 21.0 else 100.0
            heroLabel = uiString(R.string.trends_effort)
            heroValue = raw?.let { UnitFormatter.effortValue(it, effortScale) }
            heroPercent = false
            heroFraction = (heroValue ?: 0.0) / max
            heroColor = SlateUi.strainBlue
            rows = listOf(
                DetailRow(Icons.Outlined.Timeline, uiString(R.string.trends_effort), "", "", 1,
                    { d -> d.strain?.let { UnitFormatter.effortValue(it, effortScale) } },
                    { String.format(Locale.getDefault(), "%.1f", it) }),
                DetailRow(Icons.Outlined.LocalFireDepartment, uiString(R.string.l10n_today_screen_calories_3e62ecfe),
                    "kcal", "kcal", 0, { it.activeKcalEst }, { "%,d".format(it.roundToInt()) }),
                DetailRow(Icons.AutoMirrored.Outlined.DirectionsRun, uiString(R.string.l10n_today_screen_steps_cdde4f20),
                    "", "", 0, { it.steps?.toDouble() }, { "%,d".format(it.roundToInt()) }),
                DetailRow(Icons.Outlined.FitnessCenter, uiString(R.string.nav_workouts), "", "", 0,
                    { it.exerciseCount?.toDouble() }, { it.roundToInt().toString() }),
            )
            emptyText = if (heroValue == null) uiString(R.string.slate_no_effort_yet) else null
        }
        else -> {
            heroLabel = uiString(R.string.trends_rest)
            heroValue = row?.let { restOf(it) }
            heroPercent = true
            heroFraction = (heroValue ?: 0.0) / 100.0
            heroColor = SlateUi.sleepBlue
            rows = listOf(
                DetailRow(Icons.Outlined.Schedule, uiString(R.string.slate_hours_of_sleep), "",
                    uiString(R.string.slate_unit_min), 0, { it.totalSleepMin }, { hoursMinutes(it) }),
                DetailRow(Icons.Outlined.Bedtime, uiString(R.string.slate_sleep_efficiency), "", pp, 0,
                    { slatePercent(it.efficiency) }, { "${it.roundToInt()}%" }),
                DetailRow(Icons.Outlined.Thermostat, uiString(R.string.slate_restorative_sleep), "", pp, 0,
                    { slateRestorativePct(it) }, { "${it.roundToInt()}%" }),
                DetailRow(Icons.Outlined.Waves, uiString(R.string.slate_disturbances), "", "", 0,
                    { it.disturbances?.toDouble() }, { it.roundToInt().toString() }),
            )
            emptyText = if (row?.totalSleepMin == null) uiString(R.string.slate_no_sleep_yet) else null
        }
    }

    val date = runCatching { LocalDate.parse(day) }.getOrNull()
    val title = date?.format(slateDateFormat(SLATE_SKELETON_WEEKDAY))
        ?.uppercase() ?: day
    SlateScreen(
        title = title,
        onBack = onBack,
        trailing = {
            Icon(Icons.Outlined.Info, contentDescription = uiString(R.string.slate_more_details), tint = SlateUi.textPrimary,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onMore).padding(8.dp))
        },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
            SlateRing(fraction = if (heroValue == null) 0f else heroFraction.toFloat(), color = heroColor,
                diameter = 280.dp, stroke = 17.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiString(R.string.app_name).uppercase(), style = SlateType.heading.copy(fontSize = 26.sp, letterSpacing = 5.sp),
                    color = SlateUi.track)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (heroValue == null) AnnotatedDash else buildAnnotatedString {
                        append(if (heroPercent) heroValue.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", heroValue))
                        if (heroPercent) withStyle(SpanStyle(fontSize = 56.sp)) { append("%") }
                    },
                    style = SlateType.num(76.sp), color = SlateUi.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(heroLabel.uppercase(), style = SlateType.sectionCaps.copy(fontSize = 17.sp, letterSpacing = 2.8.sp),
                    color = SlateUi.textPrimary)
            }
        }

        if (emptyText != null) {
            SlateSurface { Text(emptyText, style = SlateType.body, color = SlateUi.textSecondary) }
        }

        SlateSurface(padding = 0.dp) {
            rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(SlateUi.track))
                val value = row?.let(r.select)
                val stats = slateWindowStats(days, day) { r.select(it) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                    Icon(r.icon, contentDescription = null, tint = SlateUi.textSecondary,
                        modifier = Modifier.padding(top = 2.dp).size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(r.label.uppercase(), style = SlateType.sectionCaps.copy(fontSize = 15.sp, letterSpacing = 2.2.sp),
                        color = SlateUi.textPrimary, modifier = Modifier.weight(1f).padding(top = 4.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.15f)) {
                        Text(value?.let(r.format) ?: DASH, style = SlateType.num(32.sp), color = SlateUi.textPrimary)
                        if (r.unit.isNotEmpty()) Text(r.unit, style = SlateType.body, color = SlateUi.textSecondary)
                        if (value == null) {
                            Text(uiString(R.string.slate_no_reading_day), style = SlateType.body, color = SlateUi.textSecondary,
                                textAlign = TextAlign.End)
                        } else {
                            val mean = stats.mean
                            if (mean != null) {
                                val avg = listOf(r.format(mean), r.diffUnit.takeIf { r.unit.isNotEmpty() }).filterNotNull()
                                    .joinToString(" ")
                                Text(uiPlural(R.plurals.slate_avg_readings, stats.count, avg, stats.count), style = SlateType.body,
                                    color = SlateUi.textSecondary, textAlign = TextAlign.End)
                                val diffText = listOf(slateSigned(value - mean, r.decimals), r.diffUnit.takeIf { it.isNotEmpty() })
                                    .filterNotNull().joinToString(" ")
                                Text(uiString(R.string.slate_vs_average, diffText), style = SlateType.body,
                                    color = SlateUi.textSecondary, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 22.dp)) {
                Text(uiString(R.string.slate_selected_vs_avg), style = SlateType.cardTitle.copy(fontSize = 18.sp),
                    color = SlateUi.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(uiString(R.string.slate_selected_vs_avg_body), style = SlateType.body, color = SlateUi.textSecondary)
            }
        }
        Text(
            uiString(if (isToday) R.string.slate_today_record else R.string.slate_historical_record, day),
            style = SlateType.body, color = SlateUi.textSecondary, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private val AnnotatedDash = buildAnnotatedString { append(DASH) }

// MARK: - Health monitor

/** The recent heart-rate window the monitor panel draws, and its bucket size. */
private const val HM_WINDOW_S = 2L * 3600L
private const val HM_BUCKET_S = 60L

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
    val zones = remember { ProfileStore.from(context).hrZoneSet }
    var curve by remember { mutableStateOf<List<Double>>(emptyList()) }
    // Re-read once a minute while the screen is open (and only then): the strap banks HR continuously.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis() / 1000L
            curve = runCatching {
                vm.repo.hrSamplesUnion(vm.activeStrapId, now - HM_WINDOW_S, now)
                    .groupBy { it.ts / HM_BUCKET_S }.toSortedMap().values.map { b -> b.map { it.bpm }.average() }
            }.getOrDefault(curve)
            delay(60_000L)
        }
    }
    val tempUnit = remember { UnitPrefs.temperature(context) }
    val vitals = remember(days, tempUnit) { latestVitals(days, tempUnit) }
    val zone = hrNow?.let { zones.zoneNumber(it.toDouble()) }

    SlateScreen(title = uiString(R.string.slate_health_monitor).uppercase(), onBack = onBack, horizontalPadding = 0.dp) {
        // Heart-rate panel: full width, on a grid.
        Box(Modifier.fillMaxWidth().height(300.dp).background(SlateUi.panel)) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 44.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(SlateUi.gridLine, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                var y = 0f
                while (y < size.height) { drawLine(SlateUi.gridLine, Offset(0f, y), Offset(size.width, y), 1f); y += step }
            }
            Row(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 12.dp)) {
                Column(Modifier.padding(start = 24.dp).width(118.dp)) {
                    Text(uiString(R.string.slate_heart_rate).uppercase(), style = SlateType.sectionCaps.copy(fontSize = 17.sp),
                        color = SlateUi.textPrimary, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(14.dp))
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = SlateUi.heartBlue, modifier = Modifier.size(40.dp))
                    Text(hrNow?.toString() ?: DASH, style = SlateType.num(64.sp), color = SlateUi.textPrimary)
                    Text(uiString(R.string.slate_bpm), style = SlateType.sectionCaps.copy(fontSize = 15.sp, letterSpacing = 1.sp),
                        color = SlateUi.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    Text(zone?.let { uiString(R.string.slate_zone_n, it) } ?: DASH, style = SlateType.cardTitle.copy(fontSize = 19.sp),
                        color = SlateUi.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (z in 1..5) {
                            Box(Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (zone != null && z <= zone) SlateUi.heartBlue else SlateUi.track))
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().padding(end = 18.dp, top = 70.dp, bottom = 40.dp)) {
                    SlateHrCurve(curve)
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val order = listOf("resp", "spo2", "rhr", "hrv", "skin")
            val shown = order.mapNotNull { k -> vitals.firstOrNull { it.key == k } }
            shown.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { v ->
                        val skinDev = if (v.key == "skin") days.lastOrNull { it.day == v.readingDay }?.skinTempDevC else null
                        SlateVitalTile(v, skinDev, tempUnit, Modifier.weight(1f)) { onOpenVital(v.key) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(uiString(R.string.slate_monitor_footer), style = SlateType.body, color = SlateUi.textSecondary,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** The recent HR line: blue stroke over a soft fill, a dashed "now" marker and an end dot. */
@Composable
private fun SlateHrCurve(values: List<Double>) {
    Canvas(Modifier.fillMaxSize()) {
        if (values.size < 2) return@Canvas
        val lo = values.min() - 12.0
        val hi = values.max() + 12.0
        val span = (hi - lo).coerceAtLeast(1.0)
        fun px(i: Int) = size.width * i / (values.size - 1).toFloat()
        fun py(v: Double) = (size.height * (1.0 - (v - lo) / span)).toFloat()
        val line = Path().apply {
            moveTo(px(0), py(values[0]))
            for (i in 1 until values.size) lineTo(px(i), py(values[i]))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(px(values.size - 1), size.height + 40.dp.toPx())
            lineTo(px(0), size.height + 40.dp.toPx())
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(SlateUi.heartBlue.copy(alpha = 0.16f), Color.Transparent)))
        drawPath(line, SlateUi.heartBlue, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val endX = px(values.size - 1)
        drawLine(SlateUi.textTertiary, Offset(endX, -70.dp.toPx()), Offset(endX, size.height + 40.dp.toPx()), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawCircle(SlateUi.heartBlue, 3.dp.toPx(), Offset(endX, py(values.last())))
    }
}

@Composable
private fun SlateVitalTile(v: Vital, skinDevC: Double?, tempUnit: TemperatureUnit, modifier: Modifier, onClick: () -> Unit) {
    val (icon, title) = when (v.key) {
        "resp" -> Icons.Outlined.Waves to uiString(R.string.slate_respiratory_rate)
        "spo2" -> Icons.Outlined.WaterDrop to uiString(R.string.slate_blood_oxygen)
        "rhr" -> Icons.Outlined.Timeline to uiString(R.string.slate_rhr_short)
        "hrv" -> Icons.Outlined.FavoriteBorder to "HRV"
        else -> Icons.Outlined.Thermostat to uiString(R.string.slate_skin_temp)
    }
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.clip(shape).background(SlateUi.card, shape).clickable(onClick = onClick).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SlateUi.textSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(title.uppercase(), style = SlateType.cardCaps.copy(fontSize = 14.sp, letterSpacing = 0.8.sp),
                color = SlateUi.textSecondary, maxLines = 2)
        }
        Spacer(Modifier.height(26.dp))
        val value = v.value
        if (value == null) {
            Box(Modifier.padding(vertical = 22.dp).width(40.dp).height(4.dp).background(SlateUi.textTertiary))
        } else {
            Text(
                buildAnnotatedString {
                    append(v.format(value))
                    withStyle(SpanStyle(fontFamily = SlateFonts.text, fontSize = 20.sp)) { append(" " + v.unit) }
                },
                style = SlateType.num(44.sp), color = SlateUi.textPrimary, maxLines = 1,
            )
        }
        if (v.key == "skin" && value != null && skinDevC != null && v.unit.contains("°") && !v.unit.contains("Δ")) {
            val dev = if (tempUnit == TemperatureUnit.FAHRENHEIT) skinDevC * 1.8 else skinDevC
            Text(slateSigned(dev, 1) + " " + v.unit.replace("°", "Δ°"), style = SlateType.body.copy(fontSize = 16.sp),
                color = SlateUi.textSecondary)
        }
        Spacer(Modifier.height(14.dp))
        val (kind, text) = when {
            value == null -> SlateChipKind.NEUTRAL to uiString(R.string.slate_no_reading)
            v.banding.band == VitalBands.Band.NO_DATA -> SlateChipKind.CALIBRATING to uiString(R.string.slate_calibrating_range)
            v.banding.band == VitalBands.Band.OUT_OF_RANGE && v.banding.basis == VitalBands.Basis.PERSONAL ->
                SlateChipKind.WARNING to uiString(R.string.slate_off_baseline)
            v.banding.band == VitalBands.Band.OUT_OF_RANGE -> SlateChipKind.WARNING to uiString(R.string.slate_outside_reference)
            v.banding.basis == VitalBands.Basis.PERSONAL -> SlateChipKind.POSITIVE to uiString(R.string.slate_in_your_range)
            else -> SlateChipKind.POSITIVE to uiString(R.string.slate_in_reference_range)
        }
        SlateChip(kind, text)
    }
}
