package com.noop.sahha

import com.noop.analytics.FitnessAgeEngine
import com.noop.data.DailyMetric
import com.noop.data.WhoopRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

/**
 * Fork-only (Saħħa): the week Fitness Age reads, measured as the person's USUAL week.
 *
 * Nes et al. 2011 scores activity from the HUNT questionnaire, which asks about a typical week (how often,
 * how hard, how long). Read off the last seven days, a single day dropping out of the window could move the
 * frequency band from 2.5 to 5.0 and the result by four years within hours. Here:
 *  - only complete days count (today never does);
 *  - resting heart rate is the median of the last [RHR_DAYS] complete days, as before;
 *  - active days per week are averaged over the last [HABIT_DAYS] complete days and rounded to a whole day,
 *    and the active-day effort is the mean over the same days. The paper's bands and coefficients are
 *    unchanged.
 * [gate] hands the engine a seven-row window carrying exactly those inputs, so the upstream Fitness Age
 * path (readiness, VO₂max, storage) runs untouched. The Home Assistant receiver applies the same rule
 * (`vitals.fitness_age`). Pure apart from [gateFor], which reads the history it needs.
 */
object FitnessWeek {
    const val RHR_DAYS = 7
    const val HABIT_DAYS = 28
    const val ACTIVE_STRAIN = 30.0

    data class Habit(val activeDaysPerWeek: Double, val meanActiveStrain: Double, val daysSeen: Int) {
        val roundedDays: Int get() = floor(activeDaysPerWeek + 0.5).toInt().coerceIn(0, 7)
    }

    fun completeDays(days: List<DailyMetric>, today: String): List<DailyMetric> =
        days.filter { it.day < today }.sortedBy { it.day }

    /** Active days per week and their mean effort over the last [HABIT_DAYS] complete days with an effort. */
    fun habit(complete: List<DailyMetric>): Habit? {
        val seen = complete.takeLast(HABIT_DAYS).mapNotNull { it.strain }
        if (seen.isEmpty()) return null
        val active = seen.filter { it >= ACTIVE_STRAIN }
        return Habit(active.size * 7.0 / seen.size, if (active.isEmpty()) 0.0 else active.average(), seen.size)
    }

    /**
     * The last [RHR_DAYS] complete days, their efforts replaced so that exactly [Habit.roundedDays] of them
     * are active at the usual active-day effort and the rest are not. Resting HR is left as recorded.
     */
    fun gate(days: List<DailyMetric>, today: String): List<DailyMetric> {
        val complete = completeDays(days, today)
        val last = complete.takeLast(RHR_DAYS)
        val h = habit(complete) ?: return last
        val k = minOf(h.roundedDays, last.size)
        // Rows that already carry an effort take the active slots first, so no day gains one it lacked
        // unless the habit needs more active days than the week has scored days.
        val order = last.indices.sortedBy { if (last[it].strain != null) 0 else 1 }
        val activeSlots = order.take(k).toSet()
        return last.mapIndexed { i, d ->
            when {
                i in activeSlots -> d.copy(strain = h.meanActiveStrain)
                d.strain != null -> d.copy(strain = 0.0)
                else -> d
            }
        }
    }

    /** Fitness Age from a [gate], the same computation the engine's weekly value makes. Null when not ready. */
    fun fitnessAge(gate: List<DailyMetric>, age: Double, sex: String): Double? {
        val rhrs = gate.mapNotNull { it.restingHr }.map { it.toDouble() }.sorted()
        if (age <= 0 || sex.isBlank() || rhrs.size < FitnessAgeEngine.minCoverageDays) return null
        val rhr = if (rhrs.size % 2 == 1) rhrs[rhrs.size / 2] else (rhrs[rhrs.size / 2 - 1] + rhrs[rhrs.size / 2]) / 2.0
        val active = gate.mapNotNull { it.strain }.filter { it >= ACTIVE_STRAIN }
        val pai = FitnessAgeEngine.physicalActivityIndexFromStrain(active.size, if (active.isEmpty()) 0.0 else active.average())
        return FitnessAgeEngine.fitnessAge(age, sex, rhr, pai)
    }

    /**
     * Fitness Age at each Saturday the history can support (a full [HABIT_DAYS] of days before it), oldest
     * first, each read with the age on that Saturday. Feeds Pace of aging, which needs months of weekly values
     * that the stored series only accumulates going forward.
     */
    fun weeklyHistory(days: List<DailyMetric>, birth: LocalDate?, sex: String, today: LocalDate): List<Pair<String, Double>> {
        if (birth == null || days.isEmpty()) return emptyList()
        val sorted = days.sortedBy { it.day }
        val first = runCatching { LocalDate.parse(sorted.first().day) }.getOrNull() ?: return emptyList()
        var sat = first.plusDays((HABIT_DAYS - 1).toLong()).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        val out = mutableListOf<Pair<String, Double>>()
        while (sat.isBefore(today)) {
            val g = gate(sorted, sat.plusDays(1).toString())
            fitnessAge(g, ExactAge.years(birth, sat), sex)?.let { out += sat.toString() to it }
            sat = sat.plusWeeks(1)
        }
        return out
    }

    /** [gate] over the stored daily history, with [fresh] rows (this pass's scores) taking precedence. */
    suspend fun gateFor(repo: WhoopRepository, importedDeviceId: String, fresh: Collection<DailyMetric>, today: String): List<DailyMetric> {
        val from = runCatching { LocalDate.parse(today).minusDays((HABIT_DAYS + 7).toLong()).toString() }.getOrDefault("")
        val byDay = LinkedHashMap<String, DailyMetric>()
        runCatching { repo.daysMerged(importedDeviceId) }.getOrDefault(emptyList())
            .filter { it.day >= from && it.day < today }.forEach { byDay[it.day] = it }
        fresh.filter { it.day >= from && it.day < today }.forEach { byDay[it.day] = it }
        return gate(byDay.values.toList(), today)
    }
}
