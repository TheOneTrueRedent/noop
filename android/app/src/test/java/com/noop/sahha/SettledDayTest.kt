package com.noop.sahha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettledDayTest {
    private val now = 1_000_000L

    @Test fun todayIsHeldBackJustAfterMidnight() {
        // 00:52, last main sleep ended yesterday morning (more than 18 h ago): not settled.
        val yesterday = (now - 22 * 3600 - 8 * 3600) to (now - 22 * 3600)
        assertFalse(SettledDay.todaySettled(listOf(yesterday), now, localHour = 0))
        val days = listOf("2026-11-04", "2026-11-05", "2026-11-06")
        assertEquals(listOf("2026-11-04", "2026-11-05"), SettledDay.holdBackToday(days, { it }, "2026-11-06", settled = false))
        assertEquals(days, SettledDay.holdBackToday(days, { it }, "2026-11-06", settled = true))
    }

    @Test fun aFinishedMainSleepSettlesToday() {
        val night = (now - 9 * 3600) to (now - 3600)
        assertTrue(SettledDay.todaySettled(listOf(night), now, localHour = 8))
        // Woke ten minutes ago: not yet.
        assertFalse(SettledDay.todaySettled(listOf((now - 8 * 3600) to (now - 600)), now, localHour = 8))
        // A nap under three hours does not count.
        assertFalse(SettledDay.todaySettled(listOf((now - 3 * 3600) to (now - 3600 - 1)), now, localHour = 8))
    }

    @Test fun middaySettlesRegardless() {
        assertTrue(SettledDay.todaySettled(emptyList(), now, localHour = 12))
    }
}
