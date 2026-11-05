package com.noop.sahha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepingHrTest {
    private fun nights(vararg v: Double) = v.mapIndexed { i, x -> "2026-11-%02d".format(i + 1) to x }

    @Test fun summaryReadsTheNewestNightAgainstUpToSevenBefore() {
        val s = SleepingHr.summary(nights(70.0, 60.0, 60.0, 61.0, 59.0, 60.0, 60.0, 60.0, 66.0))!!
        assertEquals("2026-11-09", s.day)
        assertEquals(66.0, s.value, 0.0)
        assertEquals(60.0, s.normal!!, 1e-9) // the 70 from night 1 is outside the 7-night window
        assertEquals(6.0, s.vsNormal!!, 1e-9)
        assertNull(SleepingHr.summary(nights(60.0, 61.0, 66.0))!!.normal)
    }

    @Test fun lowestShareMatchesTheReceiverBuckets() {
        val start = 1_000_000L
        val end = start + 8 * 3600
        val samples = (start until end step 60).map { t -> t to (if (t - start in 6 * 3600 until 6 * 3600 + 600) 50 else 60) }
        val share = SleepingHr.lowestShare(samples, start, end)!!
        assertEquals((6 * 3600 + 150).toDouble() / (8 * 3600), share, 1e-9)
    }

    @Test fun askOnlyWhenTheNightLooksLikeIt() {
        val warm = SleepingHr.Summary("d", 64.0, 60.0, 7)
        assertTrue(SleepingHr.shouldAskAboutAlcohol(warm, 0.75, null))
        assertTrue(SleepingHr.shouldAskAboutAlcohol(warm, 0.3, 0.2))
        assertFalse(SleepingHr.shouldAskAboutAlcohol(warm, 0.3, 0.05))
        assertTrue(SleepingHr.shouldAskAboutAlcohol(SleepingHr.Summary("d", 65.5, 60.0, 7), null, null))
        assertFalse(SleepingHr.shouldAskAboutAlcohol(SleepingHr.Summary("d", 62.0, 60.0, 7), 0.9, 0.5))
        assertFalse(SleepingHr.shouldAskAboutAlcohol(SleepingHr.Summary("d", 70.0, null, 2), 0.9, 0.5))
    }
}
