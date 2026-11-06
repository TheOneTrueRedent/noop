package com.noop.sahha

/**
 * Fork-only (Saħħa): whether today's daily row describes a finished day yet. The same rule as the Home
 * Assistant receiver's `today_settled` (vitals.py), so the app and HA read the same seven days.
 *
 * Today counts once a main sleep (3 h or more) ended at least 30 minutes ago and within the last 18 h, or
 * once it is past local midday. Before then today's row is a night still in progress or a day that has
 * barely started, and letting it into a seven-day window pushes out a complete day. Pure: no clock, no I/O.
 */
object SettledDay {
    const val MAIN_SLEEP_MIN_S = 3L * 3600L
    const val SETTLE_AFTER_WAKE_S = 30L * 60L
    const val LOOKBACK_S = 18L * 3600L
    const val SETTLE_LOCAL_HOUR = 12

    /** [sleeps] are (start, end) in epoch seconds, start already adjusted where the person edited it. */
    fun todaySettled(sleeps: List<Pair<Long, Long>>, nowSec: Long, localHour: Int): Boolean {
        if (localHour >= SETTLE_LOCAL_HOUR) return true
        return sleeps.any { (start, end) ->
            end - start >= MAIN_SLEEP_MIN_S && end in (nowSec - LOOKBACK_S)..(nowSec - SETTLE_AFTER_WAKE_S)
        }
    }

    /** [days] without the rows dated [today] or later while today is not settled. */
    fun <T> holdBackToday(days: List<T>, dayOf: (T) -> String, today: String, settled: Boolean): List<T> =
        if (settled) days else days.filter { dayOf(it) < today }
}
