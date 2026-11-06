package com.noop.sahha

import com.noop.data.DailyMetric
import com.noop.data.WhoopRepository

/**
 * Fork-only (Saħħa): the Fitness Age window without an unfinished today (a night in progress, or a day only
 * minutes old), by the same rule as the Home Assistant receiver ([SettledDay]), so a near-empty day never
 * pushes a complete one out of the seven and the app and HA read the same week.
 */
suspend fun settledFitnessDays(
    repo: WhoopRepository, importedDeviceId: String, days: List<DailyMetric>, today: String,
    nowSeconds: Long, tzOffsetSeconds: Long,
): List<DailyMetric> {
    val localHour = (Math.floorMod(nowSeconds + tzOffsetSeconds, 86_400L) / 3600L).toInt()
    val from = nowSeconds - SettledDay.LOOKBACK_S - 86_400L
    val sleeps = runCatching {
        repo.sleepSessionsUnion(importedDeviceId, from, nowSeconds) +
            repo.computedSleepSessionsUnion(importedDeviceId, from, nowSeconds)
    }.getOrDefault(emptyList()).map { (it.startTsAdjusted ?: it.startTs) to it.endTs }
    return SettledDay.holdBackToday(days, { it.day }, today, SettledDay.todaySettled(sleeps, nowSeconds, localHour))
}

/**
 * [settledFitnessDays] for the analysis pass, which knows its day's local midnight but not the time. The
 * hold-back only applies when the pass is scoring the live day (the wall clock falls inside it); a pass
 * replayed for an earlier day keeps every row.
 */
suspend fun settledFitnessDaysForPass(
    repo: WhoopRepository, importedDeviceId: String, days: List<DailyMetric>, today: String,
    todayLocalMidnight: Long, tzOffsetSeconds: Long, wallSeconds: Long = System.currentTimeMillis() / 1000L,
): List<DailyMetric> {
    if (wallSeconds < todayLocalMidnight || wallSeconds >= todayLocalMidnight + 86_400L) return days
    return settledFitnessDays(repo, importedDeviceId, days, today, wallSeconds, tzOffsetSeconds)
}
