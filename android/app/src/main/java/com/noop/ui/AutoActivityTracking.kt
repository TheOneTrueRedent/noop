package com.noop.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.AutoWorkoutDetector
import com.noop.data.WhoopRepository
import com.noop.sahha.AutoActivityTracker
import com.noop.ingest.ActivityFileImporter
import com.noop.ingest.LiftingImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fork-only (Saħħa): records finished walks, runs and exercise bouts as workouts by itself, using
 * [AutoActivityTracker]. ON by default in this fork (Settings → Auto-track walks and exercise turns it
 * off). Each recorded bout is written through the same manual-workout path the Workouts screen uses, so
 * it gets HR, strain and calories from the strap and can be edited, relabelled or deleted like any other.
 * A recorded span is remembered here, so deleting the workout does not bring it back on the next pass.
 */
object AutoActivityPrefs {
    private const val FILE = "sahha_auto_activity"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TRACKED = "tracked"
    private const val KEEP_SEC = 30L * 86_400L

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, true)
    fun setEnabled(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_ENABLED, on).apply() }

    fun tracked(ctx: Context): List<Pair<Long, Long>> =
        prefs(ctx).getStringSet(KEY_TRACKED, emptySet()).orEmpty().mapNotNull { t ->
            val (a, b) = t.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val s = a.toLongOrNull() ?: return@mapNotNull null
            val e = b.toLongOrNull() ?: return@mapNotNull null
            s to e
        }

    fun remember(ctx: Context, start: Long, end: Long, nowSec: Long) {
        val keep = tracked(ctx).filter { it.second >= nowSec - KEEP_SEC } + (start to end)
        prefs(ctx).edit().putStringSet(KEY_TRACKED, keep.map { "${it.first}:${it.second}" }.toSet()).apply()
    }
}

/** How far back each pass looks. Two days covers a phone that was away from the strap overnight. */
private const val AUTO_TRACK_DAYS_BACK = 2L

/** Runs one tracking pass and saves what it finds. Returns the number of workouts recorded. */
suspend fun runAutoActivityTracking(vm: AppViewModel, context: Context): Int {
    if (!AutoActivityPrefs.enabled(context)) return 0
    val now = System.currentTimeMillis() / 1000L
    val from = now - AUTO_TRACK_DAYS_BACK * 86_400L
    val repo = vm.repo
    val bouts = withContext(Dispatchers.IO) {
        val hr = repo.hrSamplesUnion(vm.deviceId, from, now, limit = 200_000)
        // The same raw-source union hrSamplesUnion reads (active strap, other registered WHOOPs, canonical).
        val sourceIds = WhoopRepository.rawWhoopSourceIdsFor(
            vm.deviceId, repo.pairedDevices().filter { it.brand.equals("WHOOP", ignoreCase = true) }.map { it.id },
        )
        val steps = sourceIds.flatMap { repo.stepSamples(it, from, now, limit = 200_000) }
            .distinctBy { it.ts }.sortedBy { it.ts }
        val saved = mergeAutoDetectSavedRows(
            whoopRows = repo.workoutsUnion(vm.deviceId, from, now),
            computedRows = repo.detectedWorkoutsUnion(vm.deviceId, from, now),
            appleRows = repo.workouts("apple-health", from, now),
            healthConnectRows = repo.workouts("health-connect", from, now),
            liftingRows = repo.workouts(LiftingImporter.SOURCE_ID, from, now),
            activityFileRows = repo.workouts(ActivityFileImporter.SOURCE_ID, from, now),
        ).map { it.startTs to it.endTs }
        val restingHr = vm.recentDays.value.lastOrNull { it.restingHr != null }?.restingHr
        val hrBouts = if (hr.size < 2) emptyList() else
            AutoWorkoutDetector.detect(hr = hr, restingHR = restingHr, gravity = emptyList(), savedWorkouts = saved)
        val dismissed = repo.dismissedDetectedUnion(vm.deviceId).map { it.startTs to it.endTs } +
            AutoWorkoutPrefs.dismissed(context).mapNotNull { t ->
                val p = t.split(':'); if (p.size == 2) (p[0].toLongOrNull() ?: 0L) to (p[1].toLongOrNull() ?: 0L) else null
            }
        AutoActivityTracker.track(
            steps = steps, hr = hr, hrBouts = hrBouts,
            ticksPerStep = ProfileStore.from(context).stepTicksPerStep,
            saved = saved, excluded = AutoActivityPrefs.tracked(context) + dismissed,
        )
    }
    var recorded = 0
    for (b in bouts) {
        val row = WorkoutEditing.buildManualRowFromSpan(
            deviceId = vm.deviceId, startSeconds = b.startSec, endSeconds = b.endSec,
            sport = b.sport, avgHr = b.avgBpm, energyKcal = null, nowSeconds = now,
        ) ?: continue
        AutoActivityPrefs.remember(context, b.startSec, b.endSec, now)
        vm.saveManualWorkout(row)
        recorded++
    }
    return recorded
}

/** Composed once at the app root: re-runs the pass whenever the day data refreshes (after each sync). */
@Composable
fun AutoActivityTrackingEffect(vm: AppViewModel) {
    val context = LocalContext.current
    val days by vm.recentDays.collectAsStateWithLifecycle()
    LaunchedEffect(days) { runCatching { runAutoActivityTracking(vm, context) } }
}
