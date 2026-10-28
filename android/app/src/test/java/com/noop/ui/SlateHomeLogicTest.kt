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
}
