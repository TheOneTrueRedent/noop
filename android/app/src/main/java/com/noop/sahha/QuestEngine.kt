package com.noop.sahha

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Fork-only (Saħħa): weekly quests set from the person's own recent weeks, and a streak that counts
 * rest. Pure: no Android, no clock, no I/O.
 *
 * Quests run Monday to Sunday. Each target is the person's own average over the four weeks before this
 * one, plus one, clamped to a sensible range. A quest for data the person doesn't have (no steps, no
 * sleep) is not offered.
 *  - TRAIN: days with a workout or an Effort of at least [TRAIN_EFFORT21] (0–21 axis). Target 2–6.
 *  - HARD_EASY: a hard day (≥ [HARD_EFFORT21]) followed by an easy one (< [EASY_EFFORT21]). Target 1–2.
 *  - SLEEP: nights of at least [SLEEP_MIN] minutes asleep. Target 2–7.
 *  - STEPS: days at or above the person's own median steps, rounded to the nearest 500. Target 2–6.
 *
 * The streak: a day counts when it has a Charge score and the person did right by it. They reached the
 * low end of the recovery-based Effort band, or kept Effort inside the band on a low-Charge day (rest
 * counts), or slept at least [SLEEP_MIN] minutes.
 */
object QuestEngine {
    const val TRAIN_EFFORT21 = 10.0
    const val HARD_EFFORT21 = 14.0
    const val EASY_EFFORT21 = 10.0
    const val SLEEP_MIN = 420.0
    const val HISTORY_WEEKS = 4

    enum class Kind { TRAIN, HARD_EASY, SLEEP, STEPS }

    data class Day(
        val day: String,
        val effort21: Double?,
        val recovery: Double?,
        val sleepMin: Double?,
        val steps: Int?,
        val trained: Boolean,
    )

    data class Quest(val kind: Kind, val target: Int, val progress: Int, val stepGoal: Int? = null) {
        val done: Boolean get() = progress >= target
    }

    fun weekStart(today: LocalDate): LocalDate = today.with(DayOfWeek.MONDAY).let { if (it.isAfter(today)) it.minusWeeks(1) else it }

    private fun trainedDay(d: Day) = d.trained || (d.effort21 ?: 0.0) >= TRAIN_EFFORT21
    private fun slept(d: Day) = (d.sleepMin ?: 0.0) >= SLEEP_MIN

    private fun hardEasyPairs(days: Map<LocalDate, Day>, from: LocalDate, to: LocalDate): Int {
        var n = 0
        var d = from
        while (!d.isAfter(to.minusDays(1))) {
            val a = days[d]?.effort21
            val b = days[d.plusDays(1)]?.effort21
            if (a != null && b != null && a >= HARD_EFFORT21 && b < EASY_EFFORT21) n++
            d = d.plusDays(1)
        }
        return n
    }

    private fun median(xs: List<Int>): Double? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    fun quests(history: List<Day>, today: LocalDate): List<Quest> {
        val byDay = history.mapNotNull { d -> runCatching { LocalDate.parse(d.day) }.getOrNull()?.let { it to d } }.toMap()
        val start = weekStart(today)
        val end = start.plusDays(6)
        fun inRange(a: LocalDate, b: LocalDate) = byDay.filterKeys { !it.isBefore(a) && !it.isAfter(b) }.values
        val week = inRange(start, end)
        val pastStart = start.minusWeeks(HISTORY_WEEKS.toLong())
        val pastWeeks = (0 until HISTORY_WEEKS).map { w ->
            val a = pastStart.plusWeeks(w.toLong())
            a to a.plusDays(6)
        }
        fun avgPerWeek(count: (LocalDate, LocalDate) -> Int): Double =
            pastWeeks.map { (a, b) -> count(a, b) }.average()

        val out = mutableListOf<Quest>()
        val trainAvg = avgPerWeek { a, b -> inRange(a, b).count(::trainedDay) }
        out += Quest(Kind.TRAIN, (trainAvg.roundToInt() + 1).coerceIn(2, 6), week.count(::trainedDay))

        val pairAvg = avgPerWeek { a, b -> hardEasyPairs(byDay, a, b) }
        out += Quest(Kind.HARD_EASY, if (pairAvg >= 1.5) 2 else 1, hardEasyPairs(byDay, start, end))

        if (byDay.values.any { it.sleepMin != null }) {
            val sleepAvg = avgPerWeek { a, b -> inRange(a, b).count(::slept) }
            out += Quest(Kind.SLEEP, (sleepAvg.roundToInt() + 1).coerceIn(2, 7), week.count(::slept))
        }

        val pastSteps = byDay.filterKeys { it.isBefore(start) && !it.isBefore(pastStart) }.values.mapNotNull { it.steps }
            .filter { it > 0 }
        median(pastSteps)?.let { m ->
            val goal = (((m / 500.0).roundToInt()) * 500).coerceAtLeast(2_000)
            val avg = avgPerWeek { a, b -> inRange(a, b).count { (it.steps ?: 0) >= goal } }
            out += Quest(Kind.STEPS, (avg.roundToInt() + 1).coerceIn(2, 6), week.count { (it.steps ?: 0) >= goal }, goal)
        }
        return out
    }

    /** Whether [d] counts toward the rest-aware streak. [bandLow]/[bandHigh] are the day's Effort band (0–21). */
    fun streakDay(d: Day, bandLow: Int?, bandHigh: Int?): Boolean {
        if (d.recovery == null) return false
        val e = d.effort21
        val reachedBand = bandLow != null && e != null && e >= bandLow
        val restedWhenLow = d.recovery < 34 && bandHigh != null && (e ?: 0.0) <= bandHigh
        return reachedBand || restedWhenLow || slept(d)
    }
}
