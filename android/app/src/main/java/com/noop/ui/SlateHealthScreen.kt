package com.noop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Psychology
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.FitnessAgeEngine
import com.noop.data.DailyMetric
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// MARK: - Slate Health tab (fork-only)
//
// The Health tab of the Slate layout, laid out to the fork owner's reference screenshot: Fitness Age as a
// glowing organic orb with the value, how far it sits from the calendar age and when it was last updated;
// Pace of aging; then factor cards grouped Sleep / Strain / Fitness, each with its 30-day average, a
// twelve-step worse→better bar marked with the 6-month (▼) and 30-day (▲) averages, and the factor's age
// impact. Below the reference's visible area: links to the Health monitor, Stress and NOOP's full Health hub.
//
// The headline is NOOP's authoritative stored weekly "fitness_age" — this screen only reads it. The age
// impact of resting HR and activity re-applies FitnessAgeEngine to the SAME last-7-merged-days gate the
// weekly value uses, holding the other input at the population reference, so the two impacts are exactly
// the two terms of the published formula. Factors the model does not use show "— years", as in the
// reference. Pace of aging is the least-squares slope of the stored weekly values (years per year), shown
// only once half a year of weekly values exists. All display-only; nothing is written back.

/** Where a value sits on a factor's worse→better scale, 0..1; null without a value. */
internal fun slateFactorPosition(value: Double?, worse: Double, better: Double): Float? {
    val v = value ?: return null
    if (worse == better) return null
    return ((v - worse) / (better - worse)).coerceIn(0.0, 1.0).toFloat()
}

/** The two Fitness Age terms in years (positive = adds years), from the gate the weekly value uses. */
internal data class SlateFitnessTerms(val restingHrYears: Double, val activityYears: Double)

internal fun slateFitnessTerms(gate: List<DailyMetric>, age: Double, sex: String): SlateFitnessTerms? {
    val rhrs = gate.mapNotNull { it.restingHr }.map { it.toDouble() }.sorted()
    if (age <= 0 || sex.isBlank() || rhrs.size < FitnessAgeEngine.minCoverageDays) return null
    val rhr = if (rhrs.size % 2 == 1) rhrs[rhrs.size / 2] else (rhrs[rhrs.size / 2 - 1] + rhrs[rhrs.size / 2]) / 2.0
    val strains = gate.mapNotNull { it.strain }.filter { it >= 30.0 }
    val pai = FitnessAgeEngine.physicalActivityIndexFromStrain(strains.size, if (strains.isEmpty()) 0.0 else strains.average())
    val refRhr = FitnessAgeEngine.restingHRReference
    val refPai = FitnessAgeEngine.paiReference
    val base = FitnessAgeEngine.fitnessAge(age, sex, refRhr, refPai)
    return SlateFitnessTerms(
        restingHrYears = FitnessAgeEngine.fitnessAge(age, sex, rhr, refPai) - base,
        activityYears = FitnessAgeEngine.fitnessAge(age, sex, refRhr, pai) - base,
    )
}

/** Pace of aging: least-squares slope of weekly Fitness Age against time, in years per year. Null until
 *  [minPoints] values spanning at least [minSpanDays] exist. Clamped to −1..3. */
internal fun slatePaceOfAging(points: List<Pair<String, Double>>, minPoints: Int = 20, minSpanDays: Long = 168): Double? {
    val dated = points.mapNotNull { (d, v) -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { it to v } }
        .sortedBy { it.first }
    if (dated.size < minPoints) return null
    val first = dated.first().first
    if (ChronoUnit.DAYS.between(first, dated.last().first) < minSpanDays) return null
    val xs = dated.map { ChronoUnit.DAYS.between(first, it.first) / 365.25 }
    val ys = dated.map { it.second }
    val mx = xs.average()
    val my = ys.average()
    val sxx = xs.sumOf { (it - mx) * (it - mx) }
    if (sxx <= 0.0) return null
    val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
    return slope.coerceIn(-1.0, 3.0)
}

/** Active days per week (Effort at or above the Fitness Age activity cut) over the [span] days ending [day]. */
internal fun slateActiveDaysPerWeek(rows: List<DailyMetric>, day: String, span: Int): Double? {
    val end = runCatching { LocalDate.parse(day) }.getOrNull() ?: return null
    val start = end.minusDays((span - 1).toLong()).toString()
    val window = rows.filter { it.day in start..day && it.strain != null }
    if (window.isEmpty()) return null
    return window.count { (it.strain ?: 0.0) >= 30.0 } * 7.0 / window.size
}

private const val DASH = "–"

private class Factor(
    val title: String,
    val format: (Double) -> String,
    val worse: Double,
    val better: Double,
    val avg30: Double?,
    val count30: Int,
    val avg180: Double?,
    val count180: Int,
    val impactYears: Double?,
)

@Composable
fun SlateHealthScreen(
    vm: AppViewModel,
    onOpenHealthMonitor: () -> Unit,
    onOpenStress: () -> Unit,
    onOpenFullHealth: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val profile = remember { ProfileStore.from(context) }
    var fitnessAge by remember { mutableStateOf<Double?>(null) }
    var updated by remember { mutableStateOf<String?>(null) }
    var weekly by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    var vo2 by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    LaunchedEffect(days) {
        val latest = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "fitness_age") }.getOrNull()
        fitnessAge = latest?.value
        updated = latest?.day
        val from = LocalDate.now().minusDays(400).toString()
        weekly = runCatching {
            vm.repo.metricSeriesComputedUnion(vm.activeStrapId, "fitness_age", from, "9999-12-31").map { it.day to it.value }
        }.getOrDefault(emptyList())
        vo2 = runCatching {
            vm.repo.metricSeriesComputedUnion(vm.activeStrapId, "vo2max_est", from, "9999-12-31").map { it.day to it.value }
        }.getOrDefault(emptyList())
    }
    val age = profile.age.toDouble()
    val sex = profile.sex
    val todayKey = remember { LocalDate.now().toString() }
    val gate = remember(days) { days.takeLast(7) }
    val terms = remember(gate, age, sex) { slateFitnessTerms(gate, age, sex) }
    val rhrNights = gate.count { it.restingHr != null }
    val pace = remember(weekly) { slatePaceOfAging(weekly) }

    fun stats(span: Int, select: (DailyMetric) -> Double?) = slateWindowStats(days, todayKey, span, select)
    fun seriesStats(series: List<Pair<String, Double>>, span: Long): Pair<Double?, Int> {
        val start = LocalDate.now().minusDays(span - 1).toString()
        val v = series.filter { it.first >= start }.map { it.second }
        return v.takeIf { it.isNotEmpty() }?.average() to v.size
    }

    val sleep30 = stats(30) { it.totalSleepMin }
    val sleep180 = stats(182) { it.totalSleepMin }
    val steps30 = stats(30) { it.steps?.toDouble() }
    val steps180 = stats(182) { it.steps?.toDouble() }
    val rhr30 = stats(30) { it.restingHr?.toDouble() }
    val rhr180 = stats(182) { it.restingHr?.toDouble() }
    val active30 = slateActiveDaysPerWeek(days, todayKey, 30)
    val active180 = slateActiveDaysPerWeek(days, todayKey, 182)
    val strainDays30 = stats(30) { it.strain }.count
    val strainDays180 = stats(182) { it.strain }.count
    val (vo230, vo2n30) = seriesStats(vo2, 30)
    val (vo2180, vo2n180) = seriesStats(vo2, 182)

    SlateScreen(title = uiString(R.string.nav_health).uppercase(), onBack = null) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(uiString(R.string.slate_fitness_age).uppercase(), style = SlateType.sectionCaps.copy(fontSize = 17.sp, letterSpacing = 3.sp),
                color = SlateUi.textPrimary, modifier = Modifier.weight(1f))
            if (updated != null) {
                Text(uiString(R.string.slate_updated_on, updated!!).uppercase(),
                    style = SlateType.labelCaps.copy(fontSize = 14.sp, letterSpacing = 0.6.sp), color = SlateUi.textSecondary)
            }
        }

        Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
            SlateFitnessOrb(Modifier.size(310.dp), lit = fitnessAge != null)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val fa = fitnessAge
                Text(fa?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: DASH, style = SlateType.num(84.sp),
                    color = SlateUi.textPrimary)
                Text(uiString(R.string.slate_fitness_age).uppercase(),
                    style = SlateType.sectionCaps.copy(fontSize = 16.sp, letterSpacing = 3.sp), color = SlateUi.textSecondary)
                Spacer(Modifier.height(6.dp))
                val line = when {
                    fa == null && (profile.age <= 0 || sex.isBlank()) -> uiString(R.string.slate_fa_add_profile)
                    fa == null -> uiPlural(R.plurals.slate_fa_nights_needed,
                        FitnessAgeEngine.nightsUntilReady(rhrNights).coerceAtLeast(1),
                        FitnessAgeEngine.nightsUntilReady(rhrNights).coerceAtLeast(1))
                    abs(fa - age) < 0.05 -> uiString(R.string.slate_fa_same)
                    fa > age -> uiString(R.string.slate_fa_older, String.format(Locale.getDefault(), "%.1f", fa - age))
                    else -> uiString(R.string.slate_fa_younger, String.format(Locale.getDefault(), "%.1f", age - fa))
                }
                Text(line, style = SlateType.cardTitle.copy(fontSize = 19.sp), color = SlateUi.deltaBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 60.dp)
                        .then(if (fa == null && profile.age <= 0) Modifier.clickable(onClick = onOpenSettings) else Modifier))
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(uiString(R.string.slate_pace_of_aging).uppercase(), style = SlateType.sectionCaps.copy(fontSize = 16.sp, letterSpacing = 3.sp),
                color = SlateUi.textSecondary, modifier = Modifier.weight(1f))
            Text(
                pace?.let { uiString(R.string.slate_pace_value, String.format(Locale.getDefault(), "%.1f", it)) }
                    ?: (DASH + " " + uiString(R.string.slate_unavailable)),
                style = SlateType.value.copy(fontSize = 17.sp), color = if (pace != null) SlateUi.textPrimary else SlateUi.textSecondary,
            )
        }

        val sleepFmt: (Double) -> String = { hoursMinutes(it) }
        val countFmt: (Double) -> String = { "%,d".format(it.roundToInt()) }
        val bpmFmt: (Double) -> String = { "${it.roundToInt()} bpm" }
        val perWeekFmt: (Double) -> String = { uiString(R.string.slate_per_week, String.format(Locale.getDefault(), "%.1f", it)) }
        val vo2Fmt: (Double) -> String = { String.format(Locale.getDefault(), "%.1f", it) }

        SlateFactorSection(uiString(R.string.nav_sleep), listOf(
            Factor(uiString(R.string.slate_hours_of_sleep), sleepFmt, 300.0, 540.0,
                sleep30.mean, sleep30.count, sleep180.mean, sleep180.count, null),
        ))
        SlateFactorSection(uiString(R.string.trends_effort), listOf(
            Factor(uiString(R.string.l10n_today_screen_steps_cdde4f20), countFmt, 2_000.0, 12_000.0,
                steps30.mean, steps30.count, steps180.mean, steps180.count, null),
            Factor(uiString(R.string.slate_active_days), perWeekFmt, 0.0, 6.0,
                active30, strainDays30, active180, strainDays180, terms?.activityYears),
        ))
        SlateFactorSection(uiString(R.string.slate_fitness), listOf(
            Factor(uiString(R.string.trends_resting_hr_full), bpmFmt, 80.0, 45.0,
                rhr30.mean, rhr30.count, rhr180.mean, rhr180.count, terms?.restingHrYears),
            Factor(uiString(R.string.slate_vo2max), vo2Fmt, 25.0, 55.0, vo230, vo2n30, vo2180, vo2n180, null),
        ))
        Text(uiString(R.string.slate_age_impact_note), style = SlateType.bodySmall, color = SlateUi.textTertiary,
            modifier = Modifier.padding(horizontal = 4.dp))

        Text(uiString(R.string.slate_monitors), style = SlateType.sectionTitle.copy(fontSize = 24.sp), color = SlateUi.textPrimary,
            modifier = Modifier.padding(top = 12.dp))
        SlateSurface(color = SlateUi.cardBlue, padding = 0.dp) {
            SlateLinkRow(Icons.Outlined.MonitorHeart, uiString(R.string.slate_health_monitor), onOpenHealthMonitor)
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(SlateUi.track))
            SlateLinkRow(Icons.Outlined.Psychology, uiString(R.string.slate_stress_monitor), onOpenStress)
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(SlateUi.track))
            SlateLinkRow(Icons.Outlined.Insights, uiString(R.string.slate_all_health_data), onOpenFullHealth)
        }
    }
}

@Composable
private fun SlateLinkRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SlateUi.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = SlateType.value.copy(fontSize = 17.sp), color = SlateUi.textPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SlateUi.textSecondary,
            modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SlateFactorSection(title: String, factors: List<Factor>) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = SlateType.sectionTitle.copy(fontSize = 26.sp), color = SlateUi.textPrimary,
                modifier = Modifier.weight(1f))
            Text("▼ " + uiString(R.string.slate_6m_avg).uppercase(), style = SlateType.labelCaps.copy(fontSize = 13.sp, letterSpacing = 0.4.sp),
                color = SlateUi.textSecondary)
            Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(22.dp).background(SlateUi.raised))
            Text("▲ " + uiString(R.string.slate_30d_avg).uppercase(), style = SlateType.labelCaps.copy(fontSize = 13.sp, letterSpacing = 0.4.sp),
                color = SlateUi.link)
        }
        factors.forEach { SlateFactorCard(it) }
    }
}

@Composable
private fun SlateFactorCard(f: Factor) {
    SlateSurface(color = SlateUi.cardBlue, radius = 26.dp, padding = 24.dp) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(f.title.uppercase(), style = SlateType.sectionCaps.copy(fontSize = 16.sp, letterSpacing = 3.sp),
                    color = SlateUi.textPrimary)
                Spacer(Modifier.height(14.dp))
                Text(f.avg30?.let(f.format) ?: DASH, style = SlateType.value.copy(fontSize = 24.sp), color = SlateUi.textPrimary)
                Text(uiPlural(R.plurals.slate_recorded_days, f.count30, f.count30), style = SlateType.body.copy(fontSize = 16.sp),
                    color = SlateUi.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 30.dp)) {
                val impact = f.impactYears
                Text(
                    when {
                        impact == null -> DASH + " " + uiString(R.string.slate_years).uppercase()
                        else -> uiString(R.string.slate_years_signed, slateSigned(impact, 1)).uppercase()
                    },
                    style = SlateType.value.copy(fontSize = 24.sp),
                    color = when {
                        impact == null || abs(impact) < 0.05 -> SlateUi.textSecondary
                        impact > 0 -> SlateUi.warning
                        else -> SlateUi.positive
                    },
                )
                Text(uiString(R.string.slate_age_impact).uppercase(), style = SlateType.labelCaps.copy(fontSize = 15.sp, letterSpacing = 0.4.sp),
                    color = SlateUi.textSecondary)
            }
        }
        Spacer(Modifier.height(22.dp))
        SlateFactorBar(
            sixMonth = slateFactorPosition(f.avg180, f.worse, f.better),
            thirtyDay = slateFactorPosition(f.avg30, f.worse, f.better),
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                uiString(R.string.slate_6m_avg).uppercase() + " · " + uiPlural(R.plurals.slate_recorded_days, f.count180, f.count180),
                style = SlateType.labelCaps.copy(fontSize = 14.sp, letterSpacing = 0.4.sp), color = SlateUi.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(f.avg180?.let(f.format) ?: DASH, style = SlateType.value.copy(fontSize = 20.sp), color = SlateUi.textPrimary)
        }
    }
}

/** The twelve-step worse→better bar, with the 6-month average (▼, white) above and the 30-day average
 *  (▲, blue) below. */
@Composable
private fun SlateFactorBar(sixMonth: Float?, thirtyDay: Float?) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp)) {
        val w = maxWidth
        Row(Modifier.fillMaxWidth().align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SlateUi.factorRamp.forEach { c ->
                Box(Modifier.weight(1f).height(13.dp).clip(RoundedCornerShape(7.dp)).background(c))
            }
        }
        if (sixMonth != null) {
            Text("▼", style = SlateType.chip.copy(fontSize = 14.sp), color = SlateUi.textPrimary,
                modifier = Modifier.align(Alignment.TopStart).offset(x = w * sixMonth - 6.dp, y = (-4).dp))
        }
        if (thirtyDay != null) {
            Text("▲", style = SlateType.chip.copy(fontSize = 14.sp), color = SlateUi.link,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = w * thirtyDay - 6.dp, y = 4.dp))
        }
    }
}

// MARK: - The orb

private class Spark(val angle: Float, val radius: Float, val size: Float, val alpha: Float, val drift: Float)

/** Fitness Age orb: an organic, slowly breathing blob with a dark core, a bronze body, a bright rim and
 *  drifting points of light. Still (no animation) under reduced motion or the battery saver. */
@Composable
private fun SlateFitnessOrb(modifier: Modifier, lit: Boolean) {
    val still = rememberPoseStill()
    val t = if (still) 0f else {
        val transition = rememberInfiniteTransition(label = "slate_orb")
        transition.animateFloat(
            initialValue = 0f, targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart),
            label = "slate_orb_phase",
        ).value
    }
    val sparks = remember {
        val rnd = Random(7)
        List(110) {
            Spark(
                angle = rnd.nextFloat() * (2 * PI).toFloat(),
                radius = sqrt(0.08f + 0.92f * rnd.nextFloat()) * 0.95f,
                size = 1.2f + rnd.nextFloat() * rnd.nextFloat() * 5.5f,
                alpha = 0.25f + rnd.nextFloat() * 0.7f,
                drift = (rnd.nextFloat() - 0.5f) * 0.6f,
            )
        }
    }
    val dim = if (lit) 1f else 0.35f
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r0 = size.minDimension / 2f * 0.9f
        fun radiusAt(theta: Float): Float = r0 * (1f +
            0.055f * sin(2 * theta + t) +
            0.04f * sin(3 * theta - 1.3f * t + 1f) +
            0.022f * sin(5 * theta + 0.7f * t + 2f))
        val n = 96
        val pts = List(n) { k ->
            val th = (2 * PI * k / n).toFloat()
            val r = radiusAt(th)
            Offset(c.x + r * cos(th), c.y + r * sin(th))
        }
        val blob = Path().apply {
            val m0 = Offset((pts[n - 1].x + pts[0].x) / 2f, (pts[n - 1].y + pts[0].y) / 2f)
            moveTo(m0.x, m0.y)
            for (k in 0 until n) {
                val p = pts[k]
                val q = pts[(k + 1) % n]
                quadraticBezierTo(p.x, p.y, (p.x + q.x) / 2f, (p.y + q.y) / 2f)
            }
            close()
        }
        clipPath(blob) {
            drawCircle(
                Brush.radialGradient(
                    0f to SlateUi.orbCore,
                    0.45f to Color(0xFF1A1611),
                    0.78f to SlateUi.orbBody.copy(alpha = dim),
                    1f to Color(0xFF8A6A3C).copy(alpha = dim),
                    center = c, radius = r0 * 1.1f,
                ),
                radius = r0 * 1.2f, center = c,
            )
            // Faint inner contours.
            for (s in listOf(0.92f, 0.84f, 0.76f)) {
                scale(s, pivot = c) { drawPath(blob, SlateUi.orbRim.copy(alpha = 0.06f * dim), style = Stroke(width = 1.5f / s)) }
            }
            sparks.forEach { sp ->
                val a = sp.angle + t * sp.drift
                val rr = radiusAt(a) * sp.radius
                val p = Offset(c.x + rr * cos(a), c.y + rr * sin(a))
                val px = sp.size * density
                drawCircle(SlateUi.orbSpark.copy(alpha = sp.alpha * 0.18f * dim), px * 2.4f, p)
                drawCircle(SlateUi.orbSpark.copy(alpha = sp.alpha * dim), px, p)
            }
        }
        drawPath(blob, SlateUi.orbRim.copy(alpha = 0.85f * dim), style = Stroke(width = 2.dp.toPx()))
    }
}
