package com.noop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.Baselines
import com.noop.analytics.ClockFormat
import com.noop.analytics.RestScorer
import com.noop.analytics.StrainScorer
import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import com.noop.data.JournalEntry
import com.noop.data.SleepSession
import com.noop.data.WorkoutRow
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// MARK: - Slate Home (fork-only)
//
// The Home screen of the Slate layout, laid out to the fork owner's reference screenshot: avatar + streak,
// date pill, strap battery; the wordmark; three thick dials; a Vitals update card that links to the Health
// monitor; My Day (Daily Outlook, Daily features, Activities with last night's sleep and today's workouts,
// Add / Start activity) and My Dashboard (metric rows against their 30-day average).
//
// Display-only and NOOP's own numbers throughout: every figure comes from the SAME helpers Today and the
// Coupled view read (resolveTodayRow, lastScoredRecoveryDay, recoveryCalibrationNights, RestScorer,
// StrainScorer.effectiveEffort, StressModel, latestVitals/VitalBands, sleepNeedForDay, mainSleepSpan), so a
// number here cannot disagree with the same number on NOOP's own screens. Drill-downs open NOOP's screens.

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

/** Health monitor state from the banded vitals. Calibrating until any vital is banded personally. */
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

/** "9:05" — whole minutes as hours:minutes, the activity-tile duration format. */
internal fun slateClockDuration(minutes: Double?): String? {
    val m = minutes?.takeIf { it > 0 }?.roundToInt() ?: return null
    return String.format(Locale.ROOT, "%d:%02d", m / 60, m % 60)
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
    onOpenDetail: (kind: String, day: String) -> Unit,
    onOpenHealthMonitor: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    val context = LocalContext.current
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val chargeBaselines by vm.chargeBaselines.collectAsStateWithLifecycle()
    val streaks by vm.streaks.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val is24h = remember { ClockPrefs.uses24Hour(context) }
    val effortScale = remember { UnitPrefs.effortScale(context) }
    val hrvEpoch = remember { NoopPrefs.of(context).getLong(Baselines.hrvBaselineEpochKey, 0L).toDouble() }

    // Day selection: 0 = today, n = n banked days back. Clamped to the banked history.
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
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

    var importedPerf by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var importedNeed by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var habitualMidsleepSec by remember { mutableStateOf<Long?>(null) }
    var stressScore by remember { mutableStateOf<Double?>(null) }
    var sleeps by remember { mutableStateOf<List<SleepSession>>(emptyList()) }
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
        // Sleep blocks: imported + computed-only, the SAME resolution SleepScreen / Coupled use.
        sleeps = runCatching {
            val now = System.currentTimeMillis() / 1000L
            val imported = vm.repo.sleepSessionsUnion(vm.activeStrapId, now - 60L * 86_400L, now)
            val computed = vm.repo.computedSleepSessionsUnion(vm.activeStrapId, now - 60L * 86_400L, now)
            val importedEnds = imported.map { it.endTs }.toHashSet()
            (imported + computed.filter { it.endTs !in importedEnds }).sortedBy { it.effectiveStartTs }
        }.getOrDefault(emptyList())
        journal = runCatching {
            vm.repo.journal(JOURNAL_DEVICE_ID, journalDayKey(6L), journalDayKey(0L))
        }.getOrDefault(emptyList())
    }
    // The selected day's workouts (local midnight to midnight; today stops at now).
    var workouts by remember { mutableStateOf<List<WorkoutRow>>(emptyList()) }
    LaunchedEffect(days, rowKey) {
        val date = runCatching { LocalDate.parse(rowKey) }.getOrNull() ?: LocalDate.now()
        val from = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val to = minOf(from + 86_400L, System.currentTimeMillis() / 1000L)
        workouts = runCatching {
            WorkoutEditing.dedupCrossSource(
                vm.repo.workoutsUnion(vm.deviceId, from, to) +
                    vm.repo.workouts("apple-health", from, to) +
                    vm.repo.workouts("health-connect", from, to),
            ).sortedBy { it.startTs }
        }.getOrDefault(emptyList())
    }
    val night = remember(sleeps, rowKey, habitualMidsleepSec) {
        mainSleepSpan(sleeps.filter { localDayString(it.endTs) == rowKey }, habitualMidsleepSec)
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

    var featuresOpen by rememberSaveable { mutableStateOf(false) }
    var activitiesOpen by rememberSaveable { mutableStateOf(true) }

    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val pinThresholdPx = with(density) { 260.dp.toPx() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(SlateUi.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = statusTop + 8.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = SLATE_BAR_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: avatar + streak · date pill · strap battery.
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                Row(
                    Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(50)).background(SlateUi.cardBlue)
                        .clickable(onClick = onOpenSettings).padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(size = 40.dp, contentDescription = uiString(R.string.slate_open_settings))
                    if (streaks.current > 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.LocalFireDepartment,
                            contentDescription = uiPlural(R.plurals.slate_streak_days, streaks.current, streaks.current),
                            tint = SlateUi.flame, modifier = Modifier.size(22.dp))
                        Text(streaks.current.toString(), style = SlateType.chip, color = SlateUi.textPrimary)
                    }
                }
                SlateDatePill(
                    label = when (dayOffset) {
                        0 -> uiString(R.string.nav_today).uppercase()
                        else -> runCatching { LocalDate.parse(rowKey) }.getOrNull()
                            ?.format(slateDateFormat(SLATE_SKELETON_DAY))
                            ?.uppercase() ?: rowKey
                    },
                    canBack = dayOffset < maxOffset,
                    canForward = dayOffset > 0,
                    onBack = { dayOffset = (dayOffset + 1).coerceAtMost(maxOffset) },
                    onForward = { dayOffset = (dayOffset - 1).coerceAtLeast(0) },
                    modifier = Modifier.align(Alignment.Center),
                )
                SlateStrapBattery(
                    percent = live.batteryPct?.roundToInt(),
                    connected = live.connected,
                    onClick = onOpenDevices,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Text(
                uiString(R.string.app_name).uppercase(),
                style = SlateType.heading.copy(letterSpacing = 3.sp),
                color = SlateUi.textPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            // The three dials.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SlateDial(
                    label = uiString(R.string.trends_rest),
                    value = rest?.roundToInt()?.toString(),
                    percent = true,
                    fraction = (rest ?: 0.0) / 100.0,
                    color = SlateUi.sleepBlue,
                    onClick = { onOpenDetail("rest", rowKey) },
                )
                SlateDial(
                    label = uiString(R.string.trends_charge),
                    value = charge?.roundToInt()?.toString(),
                    percent = true,
                    pending = charge == null && calibrationNights != null,
                    fraction = (charge ?: 0.0) / 100.0,
                    color = charge?.let { Palette.recoveryColor(it) } ?: DomainTheme.Charge.color,
                    onClick = { onOpenDetail("charge", rowKey) },
                )
                SlateDial(
                    label = uiString(R.string.trends_effort),
                    value = effort?.let { String.format(Locale.getDefault(), "%.1f", it) },
                    percent = false,
                    fraction = (effort ?: 0.0) / effortMax,
                    color = SlateUi.strainBlue,
                    onClick = { onOpenDetail("effort", rowKey) },
                )
            }

            if (isToday && !calibration.stableReady) SlateCalibrationCard(calibration)

            if (isToday && row?.recovery == null && calibrationNights == null && carried == null) {
                SlateSurface {
                    Text(uiString(R.string.slate_waiting_title), style = SlateType.cardTitle, color = SlateUi.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(uiString(R.string.slate_waiting_body), style = SlateType.body, color = SlateUi.textSecondary)
                }
            }

            SlateVitalsUpdateCard(healthStatus, onOpenHealthMonitor)

            // My Day
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiString(R.string.slate_my_day), style = SlateType.heading, color = SlateUi.textPrimary,
                    modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(SlateUi.textPrimary)
                        .clickable(onClick = onOpenWorkouts),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = uiString(R.string.slate_add_activity),
                        tint = SlateUi.bgBottom, modifier = Modifier.size(32.dp))
                }
            }

            if (BottomBarStyleStore.coachEnabled) {
                Row(
                    Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(22.dp))
                        .background(Brush.horizontalGradient(listOf(SlateUi.outlookWarm, SlateUi.outlookCool)))
                        .clickable(onClick = onOpenCoach).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = SlateUi.textSecondary,
                        modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(uiString(R.string.slate_daily_outlook), style = SlateType.cardTitle, color = SlateUi.textPrimary,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = SlateUi.warning, modifier = Modifier.size(28.dp))
                }
            }

            // Daily features: tonight's sleep plan, the journal week and behaviour insights, folded away.
            SlateSurface(color = SlateUi.cardBlue, padding = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().clickable { featuresOpen = !featuresOpen }
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(uiString(R.string.slate_daily_features), style = SlateType.cardTitle, color = SlateUi.textPrimary,
                        modifier = Modifier.weight(1f))
                    Text(uiString(if (featuresOpen) R.string.slate_close else R.string.slate_open).uppercase(),
                        style = SlateType.labelCaps.copy(fontSize = 14.sp, letterSpacing = 0.6.sp), color = SlateUi.link)
                    Icon(
                        if (featuresOpen) Icons.Filled.KeyboardArrowUp else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null, tint = SlateUi.link, modifier = Modifier.size(18.dp),
                    )
                }
                if (featuresOpen) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SlateSleepTonight(row, days, importedNeed, habitualMidsleepSec, is24h, onOpenSleep)
                        SlateJournalWeek(journal, onOpenJournal)
                        SlateWideButton(Icons.Filled.Lightbulb, uiString(R.string.slate_behaviour_insights), onOpenInsights)
                    }
                }
            }

            // Activities: the night that ended on the selected day, then that day's workouts.
            SlateSurface(radius = 26.dp, padding = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().clickable { activitiesOpen = !activitiesOpen }
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(uiString(R.string.slate_activities).uppercase(), style = SlateType.sectionCaps,
                        color = SlateUi.textPrimary, modifier = Modifier.weight(1f))
                    Icon(if (activitiesOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = null, tint = SlateUi.textSecondary, modifier = Modifier.size(26.dp))
                }
                if (activitiesOpen) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val fmt = remember(is24h) { SimpleDateFormat(ClockFormat.hourMinutePattern(is24h), Locale.getDefault()) }
                        if (night != null) {
                            SlateActivityRow(
                                tileColor = SlateUi.sleepBlue,
                                icon = Icons.Filled.Bedtime,
                                tileValue = slateClockDuration(row?.totalSleepMin ?: (night.second - night.first) / 60.0),
                                label = uiString(R.string.nav_sleep),
                                start = fmt.format(Date(night.first * 1000L)),
                                end = fmt.format(Date(night.second * 1000L)),
                                onClick = onOpenSleep,
                            )
                        }
                        workouts.forEach { w ->
                            SlateActivityRow(
                                tileColor = SlateUi.strainBlue,
                                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                                tileValue = w.strain?.let { UnitFormatter.effortDisplay(it, effortScale) },
                                label = w.sport,
                                start = fmt.format(Date(w.startTs * 1000L)),
                                end = fmt.format(Date(w.endTs * 1000L)),
                                onClick = onOpenWorkouts,
                            )
                        }
                        if (night == null && workouts.isEmpty()) {
                            Text(uiString(R.string.slate_no_activities), style = SlateType.body, color = SlateUi.textSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SlateActionButton(Icons.Filled.Add, uiString(R.string.slate_add_activity), onOpenWorkouts,
                                Modifier.weight(1f))
                            SlateActionButton(Icons.Outlined.Timer, uiString(R.string.slate_start_activity), onOpenLive,
                                Modifier.weight(1f))
                        }
                    }
                }
            }

            // My Dashboard: each metric against its 30-day average.
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiString(R.string.slate_my_dashboard), style = SlateType.heading, color = SlateUi.textPrimary,
                    modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenTrends).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(uiString(R.string.nav_trends).uppercase(),
                        style = SlateType.labelCaps.copy(fontSize = 14.sp, letterSpacing = 0.6.sp), color = SlateUi.link)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SlateUi.link,
                        modifier = Modifier.size(18.dp))
                }
            }
            val window = remember(days, rowKey) { days.filter { it.day < rowKey }.takeLast(30) }
            fun avg(sel: (DailyMetric) -> Double?): Double? =
                window.mapNotNull(sel).takeIf { it.isNotEmpty() }?.average()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SlateUi.card)) {
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
                    avg { it.activeKcalEst }?.let { "%,d".format(it.roundToInt()) }, last = true) {
                    onOpenMetric("active_kcal")
                }
            }
        }

        // Pinned mini-dials once the big dials scroll away.
        AnimatedVisibility(
            visible = scroll.value > pinThresholdPx,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().background(SlateUi.bgTop.copy(alpha = 0.97f))
                    .padding(top = statusTop + 6.dp, bottom = 10.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SlateMiniDial(uiString(R.string.trends_rest), rest?.let { "${it.roundToInt()}%" }, SlateUi.sleepBlue)
                SlateMiniDial(uiString(R.string.trends_charge), charge?.let { "${it.roundToInt()}%" },
                    charge?.let { Palette.recoveryColor(it) } ?: DomainTheme.Charge.color)
                SlateMiniDial(uiString(R.string.trends_effort),
                    effort?.let { String.format(Locale.getDefault(), "%.1f", it) }, SlateUi.strainBlue)
            }
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
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(SlateUi.card).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null,
            tint = if (canBack) SlateUi.textPrimary else SlateUi.textTertiary,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(enabled = canBack, onClick = onBack).padding(6.dp))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(SlateUi.raised).padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(label, style = SlateType.labelCaps.copy(fontSize = 13.sp), color = SlateUi.textPrimary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = if (canForward) SlateUi.textPrimary else SlateUi.textTertiary,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(enabled = canForward, onClick = onForward).padding(6.dp))
    }
}

/** Strap battery: "55%" and a small band glyph with a connection dot. Tap opens Devices. */
@Composable
private fun SlateStrapBattery(percent: Int?, connected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(percent?.let(::slatePercentText) ?: SLATE_DASH, style = SlateType.value, color = SlateUi.textPrimary)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(width = 24.dp, height = 32.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 2.dp.toPx()
                drawRoundRect(
                    color = SlateUi.textPrimary, topLeft = Offset(size.width * 0.12f, size.height * 0.1f),
                    size = Size(size.width * 0.62f, size.height * 0.8f), cornerRadius = CornerRadius(5.dp.toPx()),
                    style = Stroke(width = w),
                )
                drawLine(SlateUi.textPrimary, Offset(size.width * 0.12f, size.height * 0.5f),
                    Offset(size.width * 0.74f, size.height * 0.5f), strokeWidth = w)
            }
            Box(
                Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape)
                    .background(if (connected) SlateUi.positive else SlateUi.textTertiary),
            )
        }
    }
}

@Composable
private fun SlateDial(
    label: String,
    value: String?,
    percent: Boolean,
    fraction: Double,
    color: Color,
    onClick: () -> Unit,
    pending: Boolean = false,
    diameter: Dp = 96.dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SlateRing(fraction = if (value == null) 0f else fraction.toFloat(), color = color, diameter = diameter, stroke = 7.dp)
            if (value == null) {
                Text(if (pending) "…" else SLATE_DASH, style = SlateType.num(34.sp), color = SlateUi.textPrimary)
            } else {
                Text(
                    buildAnnotatedString {
                        append(value)
                        if (percent) withStyle(SpanStyle(fontSize = 22.sp)) { append("%") }
                    },
                    style = SlateType.num(36.sp), color = SlateUi.textPrimary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = SlateType.labelCaps.copy(fontSize = 13.sp, letterSpacing = 2.sp),
                color = SlateUi.textPrimary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SlateUi.textSecondary,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SlateMiniDial(label: String, valueText: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).border(2.5.dp, if (valueText == null) SlateUi.track else color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(listOfNotNull(valueText, label.uppercase()).joinToString(" "), style = SlateType.labelCaps,
            color = SlateUi.textPrimary)
    }
}

/** "Vitals update": how many banded vitals sit outside the person's own range, linking to the monitor. */
@Composable
private fun SlateVitalsUpdateCard(status: SlateHealthStatus, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(SlateUi.alertCard, shape)
            .border(1.dp, SlateUi.alertBorder, shape).clickable(onClick = onOpen).padding(20.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(uiString(R.string.slate_vitals_update), style = SlateType.cardTitle, color = SlateUi.textPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                when (status) {
                    SlateHealthStatus.NoData -> uiString(R.string.slate_vitals_none)
                    SlateHealthStatus.Calibrating -> uiString(R.string.slate_vitals_calibrating)
                    is SlateHealthStatus.Banded -> if (status.outside == 0) uiString(R.string.slate_vitals_all_in)
                        else uiPlural(R.plurals.slate_vitals_outside, status.outside, status.outside)
                },
                style = SlateType.body.copy(fontSize = 16.sp, lineHeight = 24.sp), color = SlateUi.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiString(R.string.slate_view_health_monitor).uppercase(),
                    style = SlateType.labelCaps.copy(fontSize = 15.sp, letterSpacing = 1.2.sp), color = SlateUi.link)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = SlateUi.link,
                    modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.width(44.dp).clip(RoundedCornerShape(10.dp)).background(SlateUi.alertBadge).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (icon, tint) = when (status) {
                is SlateHealthStatus.Banded -> if (status.outside == 0) Icons.Filled.Check to SlateUi.textSecondary
                    else Icons.Outlined.WarningAmber to SlateUi.warning
                else -> Icons.Filled.HourglassEmpty to SlateUi.textSecondary
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                (status as? SlateHealthStatus.Banded)?.outside?.toString() ?: SLATE_DASH,
                style = SlateType.value.copy(fontSize = 17.sp), color = SlateUi.textSecondary,
            )
        }
    }
}

@Composable
private fun SlateCalibrationCard(c: SlateCalibration) {
    SlateSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(uiString(R.string.slate_getting_to_know_you).uppercase(), style = SlateType.cardCaps,
                color = SlateUi.textPrimary, modifier = Modifier.weight(1f))
            Text(uiString(R.string.slate_night_of, c.nights.coerceAtMost(SLATE_STABLE_BASELINE_NIGHTS), SLATE_STABLE_BASELINE_NIGHTS),
                style = SlateType.bodySmall, color = SlateUi.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        SlateCheckItem(uiString(R.string.slate_check_charge), c.chargeReady,
            trailing = if (!c.chargeReady) uiString(R.string.slate_nights_to_go, c.chargeNightsLeft) else null)
        SlateCheckItem(uiString(R.string.slate_check_ranges), c.rangesReady)
        SlateCheckItem(uiString(R.string.slate_stress_monitor), c.stressReady)
        SlateCheckItem(uiString(R.string.slate_check_stable, SLATE_STABLE_BASELINE_NIGHTS), c.stableReady)
        Spacer(Modifier.height(8.dp))
        Text(uiString(R.string.slate_wear_hint), style = SlateType.bodySmall, color = SlateUi.textTertiary)
    }
}

@Composable
private fun SlateCheckItem(text: String, done: Boolean, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(16.dp).clip(CircleShape)
                .background(if (done) SlateUi.positive else Color.Transparent)
                .border(1.5.dp, if (done) SlateUi.positive else SlateUi.raised, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = SlateUi.bgBottom, modifier = Modifier.size(11.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = SlateType.bodySmall, color = if (done) SlateUi.textPrimary else SlateUi.textSecondary,
            modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = SlateType.bodySmall, color = SlateUi.textTertiary)
    }
}

@Composable
private fun SlateSleepTonight(
    row: DailyMetric?,
    days: List<DailyMetric>,
    importedNeed: Map<String, Double>,
    habitualMidsleepSec: Long?,
    is24h: Boolean,
    onClick: () -> Unit,
) {
    val needMin = sleepNeedForDay(row, days, importedNeed)
    val typical = days.takeLast(14).mapNotNull { it.totalSleepMin }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
    val tonight = slateSleepTonight(habitualMidsleepSec, typical, needMin)
    SlateSurface(color = SlateUi.card, radius = 18.dp, padding = 16.dp, onClick = onClick) {
        Text(uiString(R.string.slate_sleep_tonight).uppercase(), style = SlateType.cardCaps, color = SlateUi.textPrimary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SlateTimeBlock(Icons.Filled.Bedtime, tonight?.first?.let { secondsOfDayLabel(it, is24h) },
                uiString(R.string.slate_recommended_bedtime))
            Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(1.dp).background(SlateUi.raised))
            SlateTimeBlock(Icons.Filled.WbSunny, tonight?.second?.let { secondsOfDayLabel(it, is24h) },
                uiString(R.string.slate_usual_wake))
        }
        Spacer(Modifier.height(10.dp))
        Text(uiString(R.string.slate_sleep_need, hoursMinutes(needMin)), style = SlateType.bodySmall, color = SlateUi.textTertiary)
    }
}

@Composable
private fun SlateJournalWeek(journal: List<JournalEntry>, onClick: () -> Unit) {
    SlateSurface(color = SlateUi.card, radius = 18.dp, padding = 16.dp, onClick = onClick) {
        Text(uiString(R.string.slate_my_journal).uppercase(), style = SlateType.cardCaps, color = SlateUi.textPrimary)
        Spacer(Modifier.height(12.dp))
        val logged = journal.map { it.day }.toSet()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (back in 6 downTo 0) {
                val key = journalDayKey(back.toLong())
                val date = runCatching { LocalDate.parse(key) }.getOrNull()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        date?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
                            ?.take(2)?.uppercase() ?: "",
                        style = SlateType.labelCaps, color = SlateUi.textTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    val done = key in logged
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(if (done) SlateUi.positive else Color.Transparent)
                            .border(1.5.dp, if (done) SlateUi.positive else SlateUi.raised, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = SlateUi.bgBottom,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SlateTimeBlock(icon: ImageVector, time: String?, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SlateUi.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(time ?: SLATE_DASH, style = SlateType.num(26.sp), color = SlateUi.textPrimary)
        }
        Text(caption.uppercase(), style = SlateType.labelCaps.copy(fontSize = 10.sp), color = SlateUi.textTertiary)
    }
}

@Composable
private fun SlateWideButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    SlateActionButton(icon, text, onClick, Modifier.fillMaxWidth())
}

@Composable
private fun SlateActionButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(58.dp).clip(RoundedCornerShape(14.dp)).background(SlateUi.raised).clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SlateUi.textPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(text.uppercase(), style = SlateType.labelCaps.copy(fontSize = 14.sp, letterSpacing = 1.2.sp),
            color = SlateUi.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One activity: a coloured tile (icon + duration or Effort), the label, and start / end times. */
@Composable
private fun SlateActivityRow(
    tileColor: Color,
    icon: ImageVector,
    tileValue: String?,
    label: String,
    start: String,
    end: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SlateUi.raised).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(12.dp)).background(tileColor)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = SlateUi.textPrimary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
            Text(tileValue ?: SLATE_DASH, style = SlateType.num(28.sp), color = SlateUi.textPrimary, maxLines = 1)
        }
        Spacer(Modifier.width(14.dp))
        Text(label.uppercase(), style = SlateType.sectionCaps.copy(fontSize = 15.sp, letterSpacing = 1.6.sp),
            color = SlateUi.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(start, style = SlateType.body, color = SlateUi.textSecondary)
            Text(end, style = SlateType.body, color = SlateUi.textSecondary)
        }
    }
}

@Composable
private fun SlateMetricRow(
    icon: ImageVector,
    label: String,
    value: String?,
    average: String?,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = SlateUi.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(label.uppercase(), style = SlateType.cardCaps, color = SlateUi.textPrimary,
                modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Column(horizontalAlignment = Alignment.End) {
                Text(value ?: SLATE_DASH, style = SlateType.num(24.sp), color = SlateUi.textPrimary)
                Text(average?.let { uiString(R.string.slate_avg_30d, it) } ?: SLATE_DASH,
                    style = SlateType.bodySmall, color = SlateUi.textSecondary)
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(SlateUi.track))
    }
}
