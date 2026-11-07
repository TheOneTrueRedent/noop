package com.noop.sahha

import com.noop.analytics.AutoWorkoutDetector
import com.noop.data.HrSample
import com.noop.data.StepSample

/**
 * Fork-only (Saħħa): automatic tracking of walks, runs and other exercise.
 *
 * Upstream's [AutoWorkoutDetector] only SUGGESTS heart-rate bouts (≥ resting + 30 bpm for 12 min), which
 * an ordinary walk rarely reaches. This adds the missing half, walks and runs from the strap's own step
 * counter (@57) and activity class (@63: 0 still, 1 walk, 2 run), and decides which finished bouts the app
 * records by itself. Pure: no Android, no I/O, no clock. All times are unix seconds.
 *
 * The rules, deliberately simple so a wrong save is obvious and the user can delete it:
 *  - Steps are bucketed per minute from positive counter deltas (u16 wrap handled), divided by the
 *    profile's ticks-per-step calibration. A delta across a gap longer than [MAX_DELTA_GAP_SEC] is
 *    discarded, because it cannot be placed in time.
 *  - A minute is ON FOOT when it has ≥ [WALK_SPM] steps, or ≥ [CLASSED_MIN_SPM] steps and most of its
 *    samples are classed walk/run by the strap.
 *  - On-foot minutes join into a bout across gaps of up to [GAP_MIN] minutes. A bout of at least [MIN_MIN]
 *    minutes is a Run when most on-foot minutes are classed run or the mean cadence is ≥ [RUN_SPM],
 *    otherwise a Walk.
 *  - A heart-rate bout from [AutoWorkoutDetector] that overlaps no walk or run is an exercise bout.
 *  - Only FINISHED bouts are returned: the stream that found the bout must carry on for [SETTLE_SEC]
 *    beyond its end, so a sync that stops mid-walk never saves half a walk.
 *  - Anything overlapping a saved workout or an [excluded] span (already tracked, or deleted after being
 *    tracked) is dropped.
 */
object AutoActivityTracker {
    const val SPORT_WALK = "Walk"
    const val SPORT_RUN = "Run"
    const val SPORT_EXERCISE = "Workout"

    const val WALK_SPM = 60.0
    const val CLASSED_MIN_SPM = 20.0
    const val RUN_SPM = 140.0
    const val GAP_MIN = 2
    const val MIN_MIN = 10
    const val MAX_DELTA_GAP_SEC = 300L
    const val SETTLE_SEC = 300L

    data class Bout(val startSec: Long, val endSec: Long, val sport: String, val avgBpm: Int?, val steps: Int?)

    private class Minute(var steps: Double = 0.0, var classed: Int = 0, var running: Int = 0, var samples: Int = 0)

    private fun minutes(steps: List<StepSample>, ticksPerStep: Double): Map<Long, Minute> {
        val out = HashMap<Long, Minute>()
        val sorted = steps.sortedBy { it.ts }
        val divisor = if (ticksPerStep > 0.0) ticksPerStep.coerceAtLeast(0.5) else 1.0
        for (i in sorted.indices) {
            val s = sorted[i]
            val m = out.getOrPut(Math.floorDiv(s.ts, 60L)) { Minute() }
            m.samples++
            if (s.activityClass == 1 || s.activityClass == 2) m.classed++
            if (s.activityClass == 2) m.running++
            if (i == 0) continue
            val prev = sorted[i - 1]
            if (s.ts - prev.ts > MAX_DELTA_GAP_SEC || s.ts == prev.ts) continue
            val delta = Math.floorMod(s.counter - prev.counter, 65_536)
            // A delta above 4 steps a second is a counter reset, not walking.
            if (delta > 4 * (s.ts - prev.ts)) continue
            m.steps += delta / divisor
        }
        return out
    }

    private fun Minute.onFoot(): Boolean =
        steps >= WALK_SPM || (steps >= CLASSED_MIN_SPM && classed * 2 > samples)

    /** Walks and runs from the step stream, finished bouts only. */
    fun footBouts(steps: List<StepSample>, ticksPerStep: Double): List<Bout> {
        if (steps.size < 2) return emptyList()
        val byMinute = minutes(steps, ticksPerStep)
        val active = byMinute.filterValues { it.onFoot() }.keys.sorted()
        val lastTs = steps.maxOf { it.ts }
        val bouts = mutableListOf<Bout>()
        var i = 0
        while (i < active.size) {
            var j = i
            while (j + 1 < active.size && active[j + 1] - active[j] <= GAP_MIN + 1) j++
            val startMin = active[i]
            val endMin = active[j] + 1
            if (endMin - startMin >= MIN_MIN) {
                val members = active.subList(i, j + 1).map { byMinute.getValue(it) }
                val total = members.sumOf { it.steps }
                val cadence = total / members.size
                val runShare = members.count { it.running * 2 > it.samples }.toDouble() / members.size
                val sport = if (runShare >= 0.5 || cadence >= RUN_SPM) SPORT_RUN else SPORT_WALK
                val start = startMin * 60L
                val end = endMin * 60L
                if (lastTs >= end + SETTLE_SEC) bouts += Bout(start, end, sport, null, total.toInt())
            }
            i = j + 1
        }
        return bouts
    }

    /** Mean of the HR samples inside [start, end), or null when there are none. */
    fun meanBpm(hr: List<HrSample>, start: Long, end: Long): Int? {
        var sum = 0L
        var n = 0
        for (s in hr) if (s.ts in start until end && s.bpm in 25..250) { sum += s.bpm; n++ }
        return if (n == 0) null else Math.round(sum.toDouble() / n).toInt()
    }

    private fun overlaps(a0: Long, a1: Long, b0: Long, b1: Long) = a0 < b1 && b0 < a1

    /**
     * Every finished bout worth recording, oldest first: walks and runs from [steps], then heart-rate
     * [hrBouts] that overlap none of them. [saved] and [excluded] spans (start to end) suppress a bout.
     */
    fun track(
        steps: List<StepSample>,
        hr: List<HrSample>,
        hrBouts: List<AutoWorkoutDetector.DetectedWorkout>,
        ticksPerStep: Double,
        saved: List<Pair<Long, Long>>,
        excluded: List<Pair<Long, Long>>,
    ): List<Bout> {
        val foot = footBouts(steps, ticksPerStep).map { it.copy(avgBpm = meanBpm(hr, it.startSec, it.endSec)) }
        val lastHr = hr.maxOfOrNull { it.ts } ?: Long.MIN_VALUE
        val exercise = hrBouts
            .filter { b -> foot.none { overlaps(b.startSec, b.endSec, it.startSec, it.endSec) } }
            .filter { lastHr >= it.endSec + SETTLE_SEC }
            .map { Bout(it.startSec, it.endSec, SPORT_EXERCISE, it.avgBpm, null) }
        val blocked = saved + excluded
        return (foot + exercise)
            .filter { b -> blocked.none { (s, e) -> overlaps(b.startSec, b.endSec, s, e) } }
            .sortedBy { it.startSec }
    }
}
