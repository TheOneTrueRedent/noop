package com.noop.sahha

import com.noop.analytics.AutoWorkoutDetector
import com.noop.data.HrSample
import com.noop.data.StepSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fork-only: [AutoActivityTracker] finds finished walks/runs from the step stream and nothing else. */
class AutoActivityTrackerTest {
    private val t0 = 1_800_000_000L // minute-aligned

    /** A step stream sampled every 10 s from [start] for [minutes], at [spm] steps a minute and [cls]. */
    private fun stream(start: Long, minutes: Int, spm: Int, cls: Int?, counter0: Int = 0): List<StepSample> {
        val out = mutableListOf<StepSample>()
        var c = counter0
        for (i in 0 until minutes * 6) {
            out += StepSample("w", start + i * 10L, c and 0xFFFF, cls)
            c += spm / 6
        }
        return out
    }

    private fun hr(start: Long, end: Long, bpm: Int) = (start until end step 5).map { HrSample("w", it, bpm) }

    @Test fun walkIsFoundAndLabelledWithItsHeartRate() {
        val steps = stream(t0, 10, 0, 0) + stream(t0 + 600, 25, 108, 1, 0) + stream(t0 + 2100, 10, 0, 0, 2700)
        val bouts = AutoActivityTracker.track(steps, hr(t0, t0 + 2700, 96), emptyList(), 1.0, emptyList(), emptyList())
        assertEquals(1, bouts.size)
        val b = bouts[0]
        assertEquals(AutoActivityTracker.SPORT_WALK, b.sport)
        assertEquals(t0 + 600, b.startSec)
        assertEquals(t0 + 2100, b.endSec)
        assertEquals(96, b.avgBpm)
    }

    @Test fun runIsLabelledByClassOrCadence() {
        val steps = stream(t0, 15, 162, 2) + stream(t0 + 900, 10, 0, 0, 15 * 162)
        val bouts = AutoActivityTracker.footBouts(steps, 1.0)
        assertEquals(listOf(AutoActivityTracker.SPORT_RUN), bouts.map { it.sport })
    }

    @Test fun shortBriefOrUnfinishedWalksAreIgnored() {
        // 8 minutes: too short.
        assertTrue(AutoActivityTracker.footBouts(stream(t0, 8, 110, 1) + stream(t0 + 480, 10, 0, 0, 8 * 110), 1.0).isEmpty())
        // 20 minutes but the stream stops at the end: not finished, so not saved yet.
        assertTrue(AutoActivityTracker.footBouts(stream(t0, 20, 110, 1), 1.0).isEmpty())
    }

    @Test fun pausesUpToTwoMinutesStayInOneWalk() {
        val steps = stream(t0, 8, 110, 1) + stream(t0 + 480, 2, 0, 0, 880) + stream(t0 + 600, 8, 110, 1, 880) +
            stream(t0 + 1080, 10, 0, 0, 1760)
        val bouts = AutoActivityTracker.footBouts(steps, 1.0)
        assertEquals(1, bouts.size)
        assertEquals(t0, bouts[0].startSec)
        assertEquals(t0 + 1080, bouts[0].endSec)
    }

    @Test fun counterWrapIsHandled() {
        val steps = stream(t0, 15, 110, 1, 65_000) + stream(t0 + 900, 10, 0, 0, (65_000 + 15 * 108) and 0xFFFF)
        assertEquals(1, AutoActivityTracker.footBouts(steps, 1.0).size)
    }

    @Test fun savedOrExcludedSpansAndOverlappingHrBoutsAreDropped() {
        val steps = stream(t0, 20, 110, 1) + stream(t0 + 1200, 30, 0, 0, 20 * 108)
        val heart = hr(t0, t0 + 3000, 120)
        val hrWalk = AutoWorkoutDetector.DetectedWorkout(t0 + 60, t0 + 1100, 120, 130, 17)
        val hrGym = AutoWorkoutDetector.DetectedWorkout(t0 + 1500, t0 + 2400, 140, 160, 15)
        val all = AutoActivityTracker.track(steps, heart, listOf(hrWalk, hrGym), 1.0, emptyList(), emptyList())
        assertEquals(listOf(AutoActivityTracker.SPORT_WALK, AutoActivityTracker.SPORT_EXERCISE), all.map { it.sport })
        val excluded = AutoActivityTracker.track(steps, heart, listOf(hrWalk, hrGym), 1.0,
            saved = listOf(t0 + 1600 to t0 + 1700), excluded = listOf(t0 + 100 to t0 + 200))
        assertTrue(excluded.isEmpty())
    }

    @Test fun ticksPerStepCalibrationScalesCadence() {
        // 110 ticks a minute at 2 ticks per step is 55 steps a minute: below the walking floor when unclassed.
        val steps = stream(t0, 15, 110, null) + stream(t0 + 900, 10, 0, null, 15 * 108)
        assertEquals(1, AutoActivityTracker.footBouts(steps, 1.0).size)
        assertTrue(AutoActivityTracker.footBouts(steps, 2.0).isEmpty())
    }
}
