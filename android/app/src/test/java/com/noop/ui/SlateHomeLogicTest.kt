package com.noop.ui

import com.noop.analytics.Baselines
import com.noop.analytics.VitalBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlateHomeLogicTest {

    @Test
    fun calibrationChecklistFollowsTheRealGates() {
        val fresh = slateCalibration(nights = 0, stressAvailable = false, rangesPersonal = false)
        assertFalse(fresh.chargeReady)
        assertEquals(Baselines.minNightsSeed, fresh.chargeNightsLeft)
        assertFalse(fresh.stableReady)

        val seeded = slateCalibration(nights = Baselines.minNightsSeed, stressAvailable = true, rangesPersonal = false)
        assertTrue(seeded.chargeReady)
        assertEquals(0, seeded.chargeNightsLeft)
        assertTrue(seeded.stressReady)
        assertFalse(seeded.rangesReady)

        assertTrue(slateCalibration(SLATE_STABLE_BASELINE_NIGHTS, true, true).stableReady)
        assertEquals(0, slateCalibration(-3, false, false).nights)
    }

    @Test
    fun healthStatusIsCalibratingUntilAPersonalBandExists() {
        val pop = VitalBands.Result(VitalBands.Band.IN_RANGE, VitalBands.Basis.POPULATION, 3)
        val personalIn = VitalBands.Result(VitalBands.Band.IN_RANGE, VitalBands.Basis.PERSONAL, 20)
        val personalOut = VitalBands.Result(VitalBands.Band.OUT_OF_RANGE, VitalBands.Basis.PERSONAL, 20)
        val none = VitalBands.Result(VitalBands.Band.NO_DATA, VitalBands.Basis.POPULATION, 0)

        assertEquals(SlateHealthStatus.NoData, slateHealthStatus(listOf(none)))
        assertEquals(SlateHealthStatus.Calibrating, slateHealthStatus(listOf(pop, none)))
        assertEquals(SlateHealthStatus.Banded(0, 2), slateHealthStatus(listOf(personalIn, pop, none)))
        assertEquals(SlateHealthStatus.Banded(1, 2), slateHealthStatus(listOf(personalIn, personalOut)))
    }

    @Test
    fun sleepTonightIsWakeMinusNeedAcrossMidnight() {
        // Midsleep 03:30, typical 8 h -> usual wake 07:30; need 8 h -> bedtime 23:30.
        val (bed, wake) = slateSleepTonight(3 * 3600L + 1800L, 480.0, 480.0)!!
        assertEquals(7 * 3600 + 1800, wake)
        assertEquals(23 * 3600 + 1800, bed)
        assertNull(slateSleepTonight(null, 480.0, 480.0))
        assertNull(slateSleepTonight(3600L, null, 480.0))
    }

    @Test
    fun tabBarIsHomeHealthTrendsMore() {
        assertEquals(
            listOf(Destination.Today, Destination.Health, Destination.Trends, Destination.More),
            slateTabs(),
        )
    }

    @Test
    fun restorativeAndPercentHelpers() {
        val d = com.noop.data.DailyMetric(deviceId = "x", day = "2026-10-27", totalSleepMin = 400.0, deepMin = 80.0, remMin = 80.0)
        assertEquals(40.0, slateRestorativePct(d)!!, 1e-9)
        assertNull(slateRestorativePct(d.copy(remMin = null)))
        assertEquals(88.0, slatePercent(0.88)!!, 1e-9)
        assertEquals(88.0, slatePercent(88.0)!!, 1e-9)
    }

    private fun d(day: String, rhr: Int? = null, strain: Double? = null, sleep: Double? = null) =
        com.noop.data.DailyMetric(deviceId = "x", day = day, restingHr = rhr, strain = strain, totalSleepMin = sleep)

    @Test
    fun windowStatsIncludeTheSelectedDayAndSkipGaps() {
        val rows = listOf(
            d("2026-09-27", sleep = 999.0),          // 31 days before: outside a 30-day window
            d("2026-09-28", sleep = 400.0),
            d("2026-10-10", sleep = null),
            d("2026-10-27", sleep = 500.0),          // the selected day itself counts
            d("2026-10-28", sleep = 999.0),          // after the selected day: excluded
        )
        val st = slateWindowStats(rows, "2026-10-27") { it.totalSleepMin }
        assertEquals(2, st.count)
        assertEquals(450.0, st.mean!!, 1e-9)
        assertEquals(SlateWindowStats(null, 0), slateWindowStats(rows, "not-a-day") { it.totalSleepMin })
    }

    @Test
    fun signedDifferencesUseATrueMinusAndNoSignOnZero() {
        assertEquals("+14", slateSigned(14.2, 0))
        assertEquals("\u22126", slateSigned(-6.0, 0))
        assertEquals("0", slateSigned(0.3, 0))
        assertEquals("\u22120.2", slateSigned(-0.2, 1).replace(',', '.'))
    }

    @Test
    fun activityTileDurationIsHoursColonMinutes() {
        assertEquals("9:05", slateClockDuration(545.0))
        assertEquals("0:42", slateClockDuration(42.0))
        assertNull(slateClockDuration(0.0))
        assertNull(slateClockDuration(null))
    }

    @Test
    fun factorPositionRunsWorseToBetterInEitherDirection() {
        assertEquals(0.5f, slateFactorPosition(420.0, 300.0, 540.0)!!, 1e-6f)
        assertEquals(1f, slateFactorPosition(40.0, 80.0, 45.0)!!, 1e-6f)      // lower RHR is better
        assertEquals(0f, slateFactorPosition(90.0, 80.0, 45.0)!!, 1e-6f)
        assertNull(slateFactorPosition(null, 0.0, 1.0))
        assertNull(slateFactorPosition(1.0, 2.0, 2.0))
    }

    @Test
    fun fitnessTermsAreTheTwoTermsOfTheNesFormula() {
        // Male, RHR median 76, five active days at mean Effort 60 -> PA index 5.0 x 2.0 = 10.
        val gate = (21..27).map { day ->
            d("2026-10-$day", rhr = if (day == 21) null else listOf(70, 76, 80, 76, 74, 78)[day - 22],
                strain = if (day <= 25) 60.0 else 10.0)
        }
        val t = slateFitnessTerms(gate, 38.0, "male")!!
        assertEquals(0.155 * (76.0 - 65.0) / 0.296, t.restingHrYears, 1e-9)
        assertEquals(-0.226 * (10.0 - 5.0) / 0.296, t.activityYears, 1e-9)
        assertNull(slateFitnessTerms(gate.take(3), 38.0, "male"))          // too few RHR nights
        assertNull(slateFitnessTerms(gate, 38.0, ""))                       // sex not set
    }

    @Test
    fun paceOfAgingIsTheSlopeOnceHalfAYearExists() {
        val start = java.time.LocalDate.parse("2026-01-03")
        // Fitness Age rising half a year per calendar year.
        val pts = (0 until 26).map { w ->
            val day = start.plusWeeks(w.toLong())
            day.toString() to 30.0 + 0.5 * java.time.temporal.ChronoUnit.DAYS.between(start, day) / 365.25
        }
        assertEquals(0.5, slatePaceOfAging(pts)!!, 1e-9)
        assertNull(slatePaceOfAging(pts.take(19)))                          // too few weeks
        val steep = pts.map { (k, v) -> k to v * 20.0 }
        assertEquals(3.0, slatePaceOfAging(steep)!!, 1e-9)                  // clamped
    }

    @Test
    fun activeDaysPerWeekCountsEffortAtTheFitnessAgeCut() {
        val rows = (1..14).map { day -> d("2026-10-%02d".format(day), strain = if (day % 2 == 0) 45.0 else 12.0) }
        assertEquals(3.5, slateActiveDaysPerWeek(rows, "2026-10-14", 14)!!, 1e-9)
        assertNull(slateActiveDaysPerWeek(emptyList(), "2026-10-14", 30))
    }
}
