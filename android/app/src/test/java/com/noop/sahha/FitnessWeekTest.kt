package com.noop.sahha

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FitnessWeekTest {
    private val start = LocalDate.parse("2026-10-01")

    /** [n] days from [start]; day i is active (strain 60) when [active] says so, resting HR 70. */
    private fun days(n: Int, active: (Int) -> Boolean) = (0 until n).map { i ->
        DailyMetric(deviceId = "d", day = start.plusDays(i.toLong()).toString(), restingHr = 70,
            strain = if (active(i)) 60.0 else 12.0)
    }

    @Test fun todayNeverCounts() {
        val d = days(30) { true }
        val today = d.last().day
        assertTrue(FitnessWeek.gate(d, today).none { it.day >= today })
        assertEquals(7, FitnessWeek.gate(d, today).size)
    }

    @Test fun gateCarriesTheRoundedHabitAtTheUsualEffort() {
        // Five active days in every seven, for four weeks.
        val d = days(28) { it % 7 < 5 }
        val g = FitnessWeek.gate(d, start.plusDays(28).toString())
        assertEquals(5, g.count { (it.strain ?: 0.0) >= 30.0 })
        assertTrue(g.filter { (it.strain ?: 0.0) >= 30.0 }.all { it.strain == 60.0 })
        assertEquals(listOf(70), g.mapNotNull { it.restingHr }.distinct())
    }

    @Test fun oneDayLeavingTheWindowNoLongerSwingsTheResult() {
        // Four active days a week on average, unevenly spread: the last seven alone would swing between 3
        // and 5, the usual week does not.
        val pattern = (0 until 35).map { it % 2 == 0 || it % 7 == 3 }
        val d = days(35) { pattern[it] }
        val values = (29..35).map { end ->
            FitnessWeek.fitnessAge(FitnessWeek.gate(d, start.plusDays(end.toLong()).toString()), 39.0, "male")
        }
        assertTrue(values.all { it != null })
        assertTrue(values.maxOf { it!! } - values.minOf { it!! } < 0.5)
    }

    @Test fun roundingMatchesTheReceiver() {
        assertEquals(4, FitnessWeek.Habit(4.49, 60.0, 28).roundedDays)
        assertEquals(5, FitnessWeek.Habit(4.5, 60.0, 28).roundedDays)
    }

    @Test fun weeklyHistoryIsOneValuePerSaturdayAfterFourWeeks() {
        val d = days(120) { it % 7 < 4 }
        val h = FitnessWeek.weeklyHistory(d, LocalDate.parse("1987-11-18"), "male", start.plusDays(120))
        assertNotNull(h.firstOrNull())
        assertTrue(h.all { LocalDate.parse(it.first).dayOfWeek == java.time.DayOfWeek.SATURDAY })
        assertTrue(LocalDate.parse(h.first().first) >= start.plusDays(27))
        assertEquals(h.size, h.map { it.first }.distinct().size)
    }
}
