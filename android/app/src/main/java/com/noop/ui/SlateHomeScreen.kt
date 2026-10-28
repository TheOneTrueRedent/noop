package com.noop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.Baselines
import com.noop.analytics.ClockFormat
import com.noop.analytics.RestScorer
import com.noop.analytics.StrainScorer
import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import com.noop.data.JournalEntry
import com.noop.data.WorkoutRow
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// MARK: - Slate Home (fork-only)
//
// The Home screen of the Slate layout: a calm, single-column day screen laid out like the reference
// recording the fork owner supplied — date pill, three outline dials, a "getting to know you" card while
// baselines build, Health / Stress monitor tiles, My Day (daily review, sleep for tonight, today's
// activities, journal week) and My Dashboard (metric rows against their 30-day average).
//
// Display-only and NOOP's own numbers throughout: every figure comes from the SAME helpers Today and the
// Coupled view read (resolveTodayRow, lastScoredRecoveryDay, recoveryCalibrationNights, RestScorer,
// StrainScorer.effectiveEffort, StressModel, latestVitals/VitalBands, sleepNeedForDay), so a number here
// cannot disagree with the same number on NOOP's own screens. Drill-downs open NOOP's existing screens.

/** Nights after which personal ranges (Health monitor banding) replace population ranges. Matches the
 *  14-trusted-night personal-baseline gate VitalBands / V5HealthSignals use. */
internal const val SLATE_STABLE_BASELINE_NIGHTS = 14

/** The calibration checklist state, pure so a JVM test pins it. */
internal data class SlateCalibration(
    val nights: Int,
    val chargeReady: Boolean,
    val chargeNightsLeft: Int,
    val rangesReady: Boolean,
    val stressReady: Boolean,
    val stableReady: Boolean,
)

internal fun slateCalibration(nights: Int, stressAvailable: Boolean, rangesPersonal: Boolean): SlateCalibration {
    val n = nights.coerceAtLeast(0)
    return SlateCalibration(
        nights = n,
        chargeReady = n >= Baselines.minNightsSeed,
        chargeNightsLeft = (Baselines.minNightsSeed - n).coerceAtLeast(0),
        rangesReady = rangesPersonal,
        stressReady = stressAvailable,
        stableReady = n >= SLATE_STABLE_BASELINE_NIGHTS,
    )
}

/** Health monitor tile state from the banded vitals. Calibrating until any vital is banded personally. */
internal sealed interface SlateHealthStatus {
    data object Calibrating : SlateHealthStatus
    data object NoData : SlateHealthStatus
    data class Banded(val outside: Int, val total: Int) : SlateHealthStatus
}

internal fun slateHealthStatus(results: List<VitalBands.Result>): SlateHealthStatus {
    val withData = results.filter { it.band != VitalBands.Band.NO_DATA }
    if (withData.isEmpty()) return SlateHealthStatus.NoData
    if (withData.none { it.basis == VitalBands.Basis.PERSONAL }) return SlateHealthStatus.Calibrating
    return SlateHealthStatus.Banded(withData.count { it.band == VitalBands.Band.OUT_OF_RANGE }, withData.size)
}

/** Recommended bedtime and usual wake as local seconds-of-day, from the learned midsleep and the need. */
internal fun slateSleepTonight(habitualMidsleepSec: Long?, typicalSleepMin: Double?, needMin: Double): Pair<Int, Int>? {
    val mid = habitualMidsleepSec ?: return null
    val typical = typicalSleepMin?.takeIf { it > 0 } ?: return null
    val day = 86_400
    val wake = Math.floorMod((mid + (typical * 30.0).roundToInt()).toInt(), day)   // midsleep + half a night
    val bed = Math.floorMod(wake - (needMin * 60.0).roundToInt(), day)
    return bed to wake
}

private fun secondsOfDayLabel(sec: Int, is24h: Boolean): String {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, sec / 3600)
    cal.set(java.util.Calendar.MINUTE, (sec % 3600) / 60)
    return SimpleDateFormat(ClockFormat.hourMinutePattern(is24h), Locale.getDefault()).format(cal.time)
}

private const val SLATE_DASH = "–"

@Composable
fun SlateHomeScreen(
    vm: AppViewModel,
    onOpenSettings: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenMetric: (String) -> Unit,
    onOpenStress: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenWorkouts: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenTrends: () -> Unit,
) {
    val context = LocalContext.current
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val chargeBaselines by vm.chargeBaselines.collectAsStateWithLifecycle()
    val is24h = remember { ClockPrefs.uses24Hour(context) }
    val effortScale = remember { UnitPrefs.effortScale(context) }
    val hrvEpoch = remember { NoopPrefs.of(context).getLong(Baselines.hrvBaselineEpochKey, 0L).toDouble() }

    // Day selection: 0 = today, n = n banked days back. Clamped to the banked history.
    var dayOffset by remember { mutableIntStateOf(0) }
    val logicalKey = remember { logicalDayKeyNow() }
    val localKey = remember { LocalDate.now().toString() }
    val todayRow = remember(today, days, logicalKey, localKey) { resolveTodayRow(days, logicalKey, localKey) ?: today }
    val pastRows = remember(days, todayRow) { days.filter { todayRow == null || it.day < todayRow.day }.asReversed() }
    val maxOffset = pastRows.size
    val row: DailyMetric? = if (dayOffset == 0) todayRow else pastRows.getOrNull(dayOffset - 1)
    val isToday = dayOffset == 0
    val rowKey = row?.day ?: logicalKey

    val calibrationNights = remember(chargeBaselines, row, hrvEpoch, isToday) {
        if (!isToday) null else recoveryCalibrationNights(
            chargeBaselines?.hrvHistory?.values.orEmpty(), chargeBaselines?.hrvHistory?.dayKeys.orEmpty(),
            hasRecovery = row?.recovery != null, hrvBaselineEpoch = hrvEpoch,
        )
    }
    val validNights = remember(chargeBaselines, hrvEpoch) {
        Baselines.foldHistory(
            chargeBaselines?.hrvHistory?.values.orEmpty(), chargeBaselines?.hrvHistory?.dayKeys.orEmpty(),
            Baselines.hrvCfg, hrvEpoch,
        ).nValid
    }
    val carried = remember(days, rowKey, row, calibrationNights, isToday, logicalKey, localKey) {
        lastScoredRecoveryDay(
            days = days, selectedDayKey = rowKey, isToday = isToday,
            todayScored = row?.recovery != null, isCalibrating = calibrationNights != null,
            today = maxOf(logicalKey, localKey),
        )
    }
    val charge = row?.recovery ?: if (isToday) carried?.recovery else null

    // Rest score: imported sleep_performance when present, else NOOP's Rest composite (Coupled/Today rule).
    var importedPerf by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var importedNeed by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var habitualMidsleepSec by remember { mutableStateOf<Long?>(null) }
    var stressScore by remember { mutableStateOf<Double?>(null) }
    var workouts by remember { mutableStateOf<List<WorkoutRow>>(emptyList()) }
    var journal by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    LaunchedEffect(days) {
        suspend fun load(key: String) = runCatching {
            vm.repo.metricSeries("my-whoop", key, "0000-00-00", "9999-99-99")
        }.getOrDefault(emptyList()).associate { it.day to it.value }
        importedPerf = load("sleep_performance")
        importedNeed = load("sleep_need_min")
        habitualMidsleepSec = runCatching { vm.repo.habitualMidsleepSec(vm.activeStrapId) }.getOrNull()
        stressScore = runCatching {
            withContext(Dispatchers.Default) {
                val stored = vm.repo.metricSeries("my-whoop", "stress", "0000-01-01", "9999-12-31")
                    .associate { it.day to it.value.coerceIn(0.0, 3.0) }
                StressModel.build(days, stored)?.score
            }
        }.getOrNull()
        val now = System.currentTimeMillis() / 1000L
        val midnight = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
        workouts = runCatching {
            WorkoutEditing.dedupCrossSource(
                vm.repo.workoutsUnion(vm.deviceId, midnight, now) +
                    vm.repo.workouts("apple-health", midnight, now) +
                    vm.repo.workouts("health-connect", midnight, now),
            ).sortedByDescending { it.startTs }
        }.getOrDefault(emptyList())
        journal = runCatching {
            vm.repo.journal(JOURNAL_DEVICE_ID, journalDayKey(6L), journalDayKey(0L))
        }.getOrDefault(emptyList())
    }
    val rest = row?.let { importedPerf[it.day] ?: RestScorer.restFromDaily(it) }
    val effortRaw = row?.strain?.let { StrainScorer.effectiveEffort(live = null, stored = it) }
    val effort = effortRaw?.let { UnitFormatter.effortValue(it, effortScale) }
    val effortMax = if (effortScale == EffortScale.WHOOP) 21.0 else 100.0

    val tempUnit = remember { UnitPrefs.temperature(context) }
    val vitals = remember(days, tempUnit) { latestVitals(days, tempUnit) }
    val healthStatus = slateHealthStatus(vitals.filter { it.key in setOf("resp", "rhr", "hrv", "spo2", "skin") }.map { it.banding })
    val rangesPersonal = vitals.any { it.banding.basis == VitalBands.Basis.PERSONAL && it.banding.band != VitalBands.Band.NO_DATA }
    val calibration = slateCalibration(validNights, stressScore != null, rangesPersonal)

    var showChargeBreakdown by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val pinThresholdPx = with(density) { 230.dp.toPx() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(Palette.surfaceBase)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = statusTop + Metrics.space8)
                .padding(horizontal = Metrics.space16)
                .padding(bottom = Metrics.space24 + BottomBarStyleStore.barHeightForContent()),
            verticalArrangement = Arrangement.spacedBy(Metrics.gap),
        ) {
            // Header: avatar · date pill.
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                ProfileAvatar(
                    size = 32.dp,
                    modifier = Modifier.align(Alignment.CenterStart).clip(CircleShape).clickable(onClick = onOpenSettings),
                    contentDescription = uiString(R.string.slate_open_settings),
                )
                SlateDatePill(
                    label = when (dayOffset) {
                        0 -> uiString(R.string.nav_today).uppercase()
                        else -> runCatching { LocalDate.parse(rowKey) }.getOrNull()
                            ?.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
                            ?.uppercase() ?: rowKey
                    },
                    canBack = dayOffset < maxOffset,
                    canForward = dayOffset > 0,
                    onBack = { dayOffset = (dayOffset + 1).coerceAtMost(maxOffset) },
                    onForward = { dayOffset = (dayOffset - 1).coerceAtLeast(0) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Text(
                uiString(R.string.app_name),
                style = NoopType.overline.copy(letterSpacing = 4.sp),
                color = Palette.textTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            // The three dials.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SlateDial(
                    label = uiString(R.string.trends_rest),
                    valueText = rest?.let { "${it.roundToInt()}%" },
                    fraction = (rest ?: 0.0) / 100.0,
                    color = DomainTheme.Rest.color,
                    onClick = onOpenSleep,
                )
                SlateDial(
                    label = uiString(R.string.trends_charge),
                    valueText = charge?.let { "${it.roundToInt()}%" },
                    pending = charge == null && calibrationNights != null,
                    fraction = (charge ?: 0.0) / 100.0,
                    color = charge?.let { Palette.recoveryColor(it) } ?: DomainTheme.Charge.color,
                    onClick = { showChargeBreakdown = true },
                )
                SlateDial(
                    label = uiString(R.string.trends_effort),
                    valueText = effort?.let { String.format(Locale.getDefault(), "%.1f", it) },
                    fraction = (effort ?: 0.0) / effortMax,
                    color = DomainTheme.Effort.color,
                    onClick = { onOpenMetric(HERO_EFFORT_METRIC_KEY) },
                )
            }

            if (isToday && !calibration.stableReady) SlateCalibrationCard(calibration)

            if (isToday && row?.recovery == null && calibrationNights == null && carried == null) {
                SlateCard {
                    Text(uiString(R.string.slate_waiting_title), style = NoopType.headline, color = Palette.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(uiString(R.string.slate_waiting_body), style = NoopType.subhead, color = Palette.textSecondary)
                }
            }

            if (isToday) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Metrics.gap)) {
                    SlateMonitorTile(
                        title = uiString(R.string.slate_health_monitor),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenHealth,
                    ) {
                        when (val s = healthStatus) {
                            SlateHealthStatus.Calibrating -> SlateStatusLine(Icons.Filled.HourglassEmpty,
                                uiString(R.string.l10n_today_screen_calibrating_37c2c9bd).uppercase(), Palette.textSecondary)
                            SlateHealthStatus.NoData -> SlateStatusLine(Icons.Filled.HourglassEmpty,
                                uiString(R.string.slate_no_data), Palette.textSecondary)
                            is SlateHealthStatus.Banded -> if (s.outside == 0) {
                                SlateStatusLine(Icons.Filled.Check, uiString(R.string.slate_all_in_range), Palette.statusPositive)
                            } else {
                                SlateStatusLine(Icons.Filled.MonitorHeart,
                                    uiString(R.string.slate_outside_range, s.outside), Palette.statusWarning)
                            }
                        }
                    }
                    SlateMonitorTile(
                        title = uiString(R.string.slate_stress_monitor),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenStress,
                    ) {
                        val s = stressScore
                        if (s == null) {
                            Text(uiString(R.string.slate_no_data), style = NoopType.caption, color = Palette.textSecondary)
                        } else {
                            val band = StressBand.forScore(s)
                            val tint = when (band) {
                                StressBand.Low -> Palette.statusPositive
                                StressBand.Medium -> Palette.statusWarning
                                StressBand.High -> Palette.statusCritical
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.background(Palette.surfaceInset, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(String.format(Locale.getDefault(), "%.1f", s),
                                        style = NoopType.subhead.copy(fontWeight = FontWeight.Bold), color = Palette.textPrimary)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(uiString(when (band) {
                                    StressBand.Low -> R.string.slate_stress_low
                                    StressBand.Medium -> R.string.slate_stress_medium
                                    StressBand.High -> R.string.slate_stress_high
                                }).uppercase(), style = NoopType.overline, color = tint)
                            }
                        }
                    }
                }

                // My Day
                Row(Modifier.fillMaxWidth().padding(top = Metrics.space8), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiString(R.string.slate_my_day), style = NoopType.title2, color = Palette.textPrimary,
                        modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Palette.textPrimary)
                            .clickable(onClick = onOpenLive),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = uiString(R.string.action_start_workout),
                            tint = Palette.surfaceBase)
                    }
                }

                if (BottomBarStyleStore.coachEnabled) {
                    SlateCard(
                        modifier = Modifier.border(1.dp, Palette.accent.copy(alpha = 0.45f), RoundedCornerShape(Metrics.cardRadius)),
                        onClick = onOpenCoach,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Palette.surfaceInset),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Palette.accent,
                                    modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(uiString(R.string.slate_daily_review), style = NoopType.headline, color = Palette.textPrimary)
                                Text(uiString(R.string.slate_daily_review_sub), style = NoopType.caption, color = Palette.textSecondary)
                            }
                            SlateChevron()
                        }
                    }
                }

                // Sleep for tonight
                val needMin = sleepNeedForDay(row, days, importedNeed)
                val typical = days.takeLast(14).mapNotNull { it.totalSleepMin }.filter { it > 0 }
                    .takeIf { it.isNotEmpty() }?.average()
                val tonight = slateSleepTonight(habitualMidsleepSec, typical, needMin)
                SlateCard(onClick = onOpenSleep) {
                    SlateCardTitle(uiString(R.string.slate_sleep_tonight), chevron = true)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SlateTimeBlock(Icons.Filled.Bedtime, tonight?.first?.let { secondsOfDayLabel(it, is24h) },
                            uiString(R.string.slate_recommended_bedtime))
                        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(1.dp).background(Palette.hairlineStrong))
                        SlateTimeBlock(Icons.Filled.WbSunny, tonight?.second?.let { secondsOfDayLabel(it, is24h) },
                            uiString(R.string.slate_usual_wake))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(uiString(R.string.slate_sleep_need, hoursMinutes(needMin)), style = NoopType.caption,
                        color = Palette.textTertiary)
                }

                // Today's activities
                SlateCard {
                    SlateCardTitle(uiString(R.string.slate_todays_activities), chevron = false)
                    Spacer(Modifier.height(8.dp))
                    if (workouts.isEmpty()) {
                        Text(uiString(R.string.slate_no_activities), style = NoopType.subhead, color = Palette.textSecondary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            workouts.take(4).forEach { w -> SlateWorkoutRow(w, effortScale, is24h, onOpenWorkouts) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SlateWideButton(Icons.Filled.PlayCircleOutline, uiString(R.string.action_start_workout), onOpenLive)
                }

                // My journal: the last seven days, ticked where anything was logged.
                SlateCard(onClick = onOpenJournal) {
                    SlateCardTitle(uiString(R.string.slate_my_journal), chevron = true)
                    Spacer(Modifier.height(10.dp))
                    val logged = journal.map { it.day }.toSet()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (back in 6 downTo 0) {
                            val key = journalDayKey(back.toLong())
                            val date = runCatching { LocalDate.parse(key) }.getOrNull()
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    date?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
                                        ?.take(2)?.uppercase() ?: "",
                                    style = NoopType.overline, color = Palette.textTertiary,
                                )
                                Spacer(Modifier.height(6.dp))
                                val done = key in logged
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape)
                                        .background(if (done) Palette.statusPositive else Color.Transparent)
                                        .border(1.5.dp, if (done) Palette.statusPositive else Palette.hairlineStrong, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.surfaceBase,
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SlateWideButton(Icons.Filled.Lightbulb, uiString(R.string.slate_behaviour_insights), onOpenInsights)
                }
            }

            // My Dashboard: each metric against its 30-day average.
            Row(Modifier.fillMaxWidth().padding(top = Metrics.space8), verticalAlignment = Alignment.CenterVertically) {
                Text(uiString(R.string.slate_my_dashboard), style = NoopType.title2, color = Palette.textPrimary,
                    modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.metricPurple)
                        .clickable(onClick = onOpenTrends).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(uiString(R.string.nav_trends).uppercase(), style = NoopType.overline, color = Palette.textPrimary)
                }
            }
            val window = remember(days, rowKey) { days.filter { it.day < rowKey }.takeLast(30) }
            fun avg(sel: (DailyMetric) -> Double?): Double? =
                window.mapNotNull(sel).takeIf { it.isNotEmpty() }?.average()
            SlateMetricRow(Icons.Filled.MonitorHeart, uiString(R.string.slate_hrv_full),
                row?.avgHrv?.let { "${it.roundToInt()} ms" }, avg { it.avgHrv }?.let { "${it.roundToInt()} ms" }) {
                onOpenMetric("hrv")
            }
            SlateMetricRow(Icons.Filled.FavoriteBorder, uiString(R.string.trends_resting_hr_full),
                row?.restingHr?.let { "$it bpm" }, avg { it.restingHr?.toDouble() }?.let { "${it.roundToInt()} bpm" }) {
                onOpenMetric("rhr")
            }
            SlateMetricRow(Icons.Filled.Air, uiString(R.string.slate_respiratory_rate),
                row?.respRateBpm?.let { String.format(Locale.getDefault(), "%.1f", it) },
                avg { it.respRateBpm }?.let { String.format(Locale.getDefault(), "%.1f", it) }) {
                onOpenMetric("resp")
            }
            SlateMetricRow(Icons.Filled.Favorite, uiString(R.string.today_metric_blood_oxygen),
                row?.spo2Pct?.let { "${it.roundToInt()}%" }, avg { it.spo2Pct }?.let { "${it.roundToInt()}%" }) {
                onOpenMetric("spo2")
            }
            SlateMetricRow(Icons.AutoMirrored.Filled.DirectionsRun, uiString(R.string.l10n_today_screen_steps_cdde4f20),
                row?.steps?.let { "%,d".format(it) }, avg { it.steps?.toDouble() }?.let { "%,d".format(it.roundToInt()) }) {
                onOpenMetric("steps_est")
            }
            SlateMetricRow(Icons.Filled.LocalFireDepartment, uiString(R.string.l10n_today_screen_calories_3e62ecfe),
                row?.activeKcalEst?.let { "%,d".format(it.roundToInt()) },
                avg { it.activeKcalEst }?.let { "%,d".format(it.roundToInt()) }) {
                onOpenMetric("active_kcal")
            }
        }

        // Pinned mini-dials once the big dials scroll away.
        AnimatedVisibility(
            visible = scroll.value > pinThresholdPx,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Palette.surfaceBase.copy(alpha = 0.96f))
                    .padding(top = statusTop + 6.dp, bottom = 10.dp, start = Metrics.space16, end = Metrics.space16),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SlateMiniDial(uiString(R.string.trends_rest), rest?.let { "${it.roundToInt()}%" }, DomainTheme.Rest.color)
                SlateMiniDial(uiString(R.string.trends_charge), charge?.let { "${it.roundToInt()}%" },
                    charge?.let { Palette.recoveryColor(it) } ?: DomainTheme.Charge.color)
                SlateMiniDial(uiString(R.string.trends_effort),
                    effort?.let { String.format(Locale.getDefault(), "%.1f", it) }, DomainTheme.Effort.color)
            }
        }
    }

    if (showChargeBreakdown) {
        Dialog(
            onDismissRequest = { showChargeBreakdown = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ChargeBreakdownSheet(
                days = days,
                chargeBaselines = chargeBaselines,
                displayDay = row,
                carriedDay = if (isToday) carried else null,
                showReadiness = true,
                onClose = { showChargeBreakdown = false },
                onHowCalculated = { showChargeBreakdown = false },
            )
        }
    }
}

// MARK: - Pieces

@Composable
private fun SlateDatePill(
    label: String,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null,
            tint = if (canBack) Palette.textPrimary else Palette.textTertiary,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable(enabled = canBack, onClick = onBack).padding(4.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Palette.surfaceRaised)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(label, style = NoopType.overline.copy(letterSpacing = 1.5.sp), color = Palette.textPrimary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = if (canForward) Palette.textPrimary else Palette.textTertiary,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable(enabled = canForward, onClick = onForward).padding(4.dp))
    }
}

@Composable
private fun SlateDial(
    label: String,
    valueText: String?,
    fraction: Double,
    color: Color,
    onClick: () -> Unit,
    pending: Boolean = false,
    diameter: Dp = 92.dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            GlowRing(
                fraction = if (valueText == null) 0f else fraction.toFloat(),
                value = 0.0,
                color = color,
                diameter = diameter,
                lineWidth = 5.dp,
                showsLabel = false,
            )
            Text(
                valueText ?: if (pending) "…" else SLATE_DASH,
                style = NoopType.title2.copy(fontWeight = FontWeight.Bold),
                color = Palette.textPrimary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.2.sp), color = Palette.textPrimary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.textPrimary,
                modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SlateMiniDial(label: String, valueText: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).border(2.dp, if (valueText == null) Palette.hairlineStrong else color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(listOfNotNull(valueText, label.uppercase()).joinToString(" "), style = NoopType.overline, color = Palette.textPrimary)
    }
}

@Composable
private fun SlateCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Metrics.cardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surfaceRaised, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Metrics.cardPadding),
    ) { content() }
}

@Composable
private fun SlateCardTitle(text: String, chevron: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.4.sp), color = Palette.textPrimary,
            modifier = Modifier.weight(1f))
        if (chevron) SlateChevron()
    }
}

@Composable
private fun SlateChevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.textSecondary,
        modifier = Modifier.size(18.dp))
}

@Composable
private fun SlateCalibrationCard(c: SlateCalibration) {
    SlateCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(uiString(R.string.slate_getting_to_know_you).uppercase(), style = NoopType.overline.copy(letterSpacing = 1.2.sp),
                color = Palette.textPrimary, modifier = Modifier.weight(1f))
            Text(uiString(R.string.slate_night_of, c.nights.coerceAtMost(SLATE_STABLE_BASELINE_NIGHTS), SLATE_STABLE_BASELINE_NIGHTS),
                style = NoopType.caption, color = Palette.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        SlateCheckItem(uiString(R.string.slate_check_charge), c.chargeReady,
            trailing = if (!c.chargeReady) uiString(R.string.slate_nights_to_go, c.chargeNightsLeft) else null)
        SlateCheckItem(uiString(R.string.slate_check_ranges), c.rangesReady)
        SlateCheckItem(uiString(R.string.slate_stress_monitor), c.stressReady)
        SlateCheckItem(uiString(R.string.slate_check_stable, SLATE_STABLE_BASELINE_NIGHTS), c.stableReady)
        Spacer(Modifier.height(8.dp))
        Text(uiString(R.string.slate_wear_hint), style = NoopType.caption, color = Palette.textTertiary)
    }
}

@Composable
private fun SlateCheckItem(text: String, done: Boolean, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(14.dp).clip(CircleShape)
                .background(if (done) Palette.statusPositive else Color.Transparent)
                .border(1.dp, if (done) Palette.statusPositive else Palette.hairlineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.surfaceBase, modifier = Modifier.size(10.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = NoopType.subhead, color = if (done) Palette.textPrimary else Palette.textSecondary,
            modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = NoopType.caption, color = Palette.textTertiary)
    }
}

@Composable
private fun SlateMonitorTile(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Metrics.cardRadius)
    Column(
        modifier.clip(shape).background(Palette.surfaceRaised, shape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.2.sp), color = Palette.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            SlateChevron()
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SlateStatusLine(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = NoopType.overline, color = tint)
    }
}

@Composable
private fun SlateTimeBlock(icon: ImageVector, time: String?, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Palette.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(time ?: SLATE_DASH, style = NoopType.title2.copy(fontWeight = FontWeight.Bold), color = Palette.textPrimary)
        }
        Text(caption.uppercase(), style = NoopType.overline, color = Palette.textTertiary)
    }
}

@Composable
private fun SlateWideButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.surfaceInset)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.textPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.4.sp), color = Palette.textPrimary)
    }
}

@Composable
private fun SlateWorkoutRow(w: WorkoutRow, scale: EffortScale, is24h: Boolean, onClick: () -> Unit) {
    val fmt = remember(is24h) { SimpleDateFormat(ClockFormat.hourMinutePattern(is24h), Locale.getDefault()) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.surfaceInset)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(DomainTheme.Effort.color)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = Palette.textPrimary,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(w.strain?.let { UnitFormatter.effortDisplay(it, scale) } ?: SLATE_DASH,
                style = NoopType.headline, color = Palette.textPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Text(w.sport.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.2.sp), color = Palette.textPrimary,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(fmt.format(Date(w.startTs * 1000L)), style = NoopType.caption, color = Palette.textSecondary)
            Text(fmt.format(Date(w.endTs * 1000L)), style = NoopType.caption, color = Palette.textSecondary)
        }
    }
}

@Composable
private fun SlateMetricRow(
    icon: ImageVector,
    label: String,
    value: String?,
    average: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Palette.surfaceRaised, shape).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label.uppercase(), style = NoopType.overline.copy(letterSpacing = 1.2.sp), color = Palette.textPrimary,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(value ?: SLATE_DASH, style = NoopType.headline.copy(fontWeight = FontWeight.Bold), color = Palette.textPrimary)
            Text(average?.let { uiString(R.string.slate_avg_30d, it) } ?: SLATE_DASH,
                style = NoopType.footnote, color = Palette.textTertiary)
        }
    }
}
