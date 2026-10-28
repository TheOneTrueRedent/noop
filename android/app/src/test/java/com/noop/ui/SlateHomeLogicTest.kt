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
    fun contributorTiersBothDirections() {
        assertEquals(SlateTier.OPTIMAL, slateBand(90.0, 70.0, 85.0))
        assertEquals(SlateTier.SUFFICIENT, slateBand(70.0, 70.0, 85.0))
        assertEquals(SlateTier.INSUFFICIENT, slateBand(69.9, 70.0, 85.0))
        assertNull(slateBand(null, 70.0, 85.0))
        // Disturbances: fewer is better.
        assertEquals(SlateTier.OPTIMAL, slateBand(5.0, 10.0, 5.0, higherIsBetter = false))
        assertEquals(SlateTier.SUFFICIENT, slateBand(8.0, 10.0, 5.0, higherIsBetter = false))
        assertEquals(SlateTier.INSUFFICIENT, slateBand(11.0, 10.0, 5.0, higherIsBetter = false))
    }

    @Test
    fun vsBaselineUsesDirection() {
        assertEquals(SlateTier.OPTIMAL, slateVsBaseline(55.0, 50.0, higherIsBetter = true))
        assertEquals(SlateTier.INSUFFICIENT, slateVsBaseline(40.0, 50.0, higherIsBetter = true))
        assertEquals(SlateTier.OPTIMAL, slateVsBaseline(55.0, 58.0, higherIsBetter = false))
        assertEquals(SlateTier.INSUFFICIENT, slateVsBaseline(70.0, 58.0, higherIsBetter = false))
        assertNull(slateVsBaseline(50.0, null, true))
    }

    @Test
    fun restorativeAndPercentHelpers() {
        val d = com.noop.data.DailyMetric(deviceId = "x", day = "2026-10-27", totalSleepMin = 400.0, deepMin = 80.0, remMin = 80.0)
        assertEquals(40.0, slateRestorativePct(d)!!, 1e-9)
        assertNull(slateRestorativePct(d.copy(remMin = null)))
        assertEquals(88.0, slatePercent(0.88)!!, 1e-9)
        assertEquals(88.0, slatePercent(88.0)!!, 1e-9)
    }
}
