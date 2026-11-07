package com.noop.sahha

/**
 * Fork-only (Saħħa): last night's sleeping heart rate, read against the person's own recent nights, and
 * the morning-after alcohol question. Pure: no Android, no clock, no I/O.
 *
 * The nightly value is NOOP's own `rhr_primary_session` (PrimarySessionRestingHR, #1169): the mean of the
 * valid HR samples in the main sleep. The Home Assistant receiver computes the same rule, so the two agree.
 * The lowest 5-minute average uses the receiver's buckets too: 5-minute windows from sleep onset, each
 * needing at least 3 samples.
 *
 * The alcohol question is asked when the night looks like one, never as a diagnosis. That means a sleeping
 * HR at least [ASK_ELEVATED_BPM] above normal together with either a late low (after [LATE_LOW_SHARE] of
 * the night) or an HRV at least [ASK_HRV_DROP] below its own norm. A rise of [ASK_ALONE_BPM] or more is
 * enough on its own.
 */
object SleepingHr {
    const val NORM_NIGHTS = 7
    const val NORM_MIN = 3
    const val BUCKET_S = 300L
    const val ASK_ELEVATED_BPM = 3.0
    const val ASK_ALONE_BPM = 5.0
    const val LATE_LOW_SHARE = 0.6
    const val ASK_HRV_DROP = 0.15

    data class Summary(val day: String, val value: Double, val normal: Double?, val nightsInNormal: Int) {
        val vsNormal: Double? get() = normal?.let { value - it }
    }

    /** [nightly] is (wake day, mean) ascending; the newest night is read against up to 7 before it. */
    fun summary(nightly: List<Pair<String, Double>>): Summary? {
        val sorted = nightly.sortedBy { it.first }
        val (day, value) = sorted.lastOrNull() ?: return null
        val prev = sorted.dropLast(1).takeLast(NORM_NIGHTS).map { it.second }
        return Summary(day, value, if (prev.size >= NORM_MIN) prev.average() else null, prev.size)
    }

    /** Where the night's lowest 5-minute average fell, as a share of the night (0 = onset, 1 = wake). */
    fun lowestShare(samples: List<Pair<Long, Int>>, start: Long, end: Long): Double? {
        if (end <= start) return null
        val buckets = HashMap<Long, MutableList<Int>>()
        for ((t, b) in samples) if (t in start until end && b in 30..220) buckets.getOrPut((t - start) / BUCKET_S) { mutableListOf() } += b
        val full = buckets.filterValues { it.size >= 3 }.mapValues { it.value.average() }
        val k = full.entries.minWithOrNull(compareBy({ it.value }, { it.key }))?.key ?: return null
        val mid = start + k * BUCKET_S + BUCKET_S / 2
        return (mid - start).toDouble() / (end - start)
    }

    /** Whether to ask about alcohol this morning. [hrvDrop] is the fraction HRV fell below its norm. */
    fun shouldAskAboutAlcohol(s: Summary?, lowestShare: Double?, hrvDrop: Double?): Boolean {
        val up = s?.vsNormal ?: return false
        if (up >= ASK_ALONE_BPM) return true
        if (up < ASK_ELEVATED_BPM) return false
        return (lowestShare != null && lowestShare >= LATE_LOW_SHARE) || (hrvDrop != null && hrvDrop >= ASK_HRV_DROP)
    }
}
