package com.noop.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.data.JournalEntry
import com.noop.sahha.SleepingHr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

// MARK: - Sleeping heart rate and the morning-after check-in (fork-only, Saħħa)
//
// The nightly value is NOOP's stored `rhr_primary_session` (see com.noop.sahha.SleepingHr). The check-in
// card appears on today's Home only when the night looks like a drink, and only until it is answered or
// skipped. An answer is written to the journal question NOOP already uses ("Did you drink any alcohol?")
// under today's key, which is how the journal attributes last night (JournalLog.kt). From there the
// behaviour insights pick it up like any other journal answer.

private const val SLEEPING_HR_KEY = "rhr_primary_session"
internal const val SAHHA_ALCOHOL_QUESTION = "Did you drink any alcohol?"

private suspend fun loadSleepingHr(vm: AppViewModel): SleepingHr.Summary? = withContext(Dispatchers.IO) {
    val from = LocalDate.now().minusDays(14).toString()
    runCatching { vm.repo.metricSeriesComputedUnion(vm.activeStrapId, SLEEPING_HR_KEY, from, "9999-12-31") }
        .getOrDefault(emptyList()).map { it.day to it.value }.let { SleepingHr.summary(it) }
}

@Composable
internal fun SahhaSleepingHrCard(vm: AppViewModel) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    var s by remember { mutableStateOf<SleepingHr.Summary?>(null) }
    LaunchedEffect(days) { s = loadSleepingHr(vm) }
    val summary = s ?: return
    SlateSurface {
        Text(uiString(R.string.sahha_sleeping_hr).uppercase(), style = SlateType.labelCaps, color = SlateUi.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(summary.value.roundToInt().toString(), style = SlateType.num(40.sp), color = SlateUi.textPrimary)
            Text(uiString(R.string.sahha_bpm), style = SlateType.body, color = SlateUi.textSecondary)
        }
        val vs = summary.vsNormal
        Text(
            when {
                vs == null -> uiPlural(R.plurals.sahha_sleeping_hr_building, SleepingHr.NORM_MIN - summary.nightsInNormal,
                    SleepingHr.NORM_MIN - summary.nightsInNormal)
                kotlin.math.abs(vs) < 1.0 -> uiString(R.string.sahha_sleeping_hr_normal)
                vs > 0 -> uiString(R.string.sahha_sleeping_hr_above, String.format(Locale.getDefault(), "%.1f", vs))
                else -> uiString(R.string.sahha_sleeping_hr_below, String.format(Locale.getDefault(), "%.1f", -vs))
            },
            style = SlateType.body,
            color = if (vs != null && vs >= SleepingHr.ASK_ELEVATED_BPM) SlateUi.warning else SlateUi.positive,
        )
        Spacer(Modifier.height(4.dp))
        Text(uiString(R.string.sahha_sleeping_hr_note), style = SlateType.bodySmall, color = SlateUi.textTertiary)
    }
}

private object CheckInPrefs {
    private const val FILE = "sahha_checkin"
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun skipped(ctx: Context, day: String) = p(ctx).getString("skipped", null) == day
    fun skip(ctx: Context, day: String) { p(ctx).edit().putString("skipped", day).apply() }
}

/** The morning-after card: shown only when the night looks like a drink and today has no answer yet. */
@Composable
internal fun SahhaAlcoholCheckIn(vm: AppViewModel, night: Pair<Long, Long>?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val today = journalDayKey(0)
    var ask by remember { mutableStateOf(false) }
    var above by remember { mutableStateOf(0.0) }
    LaunchedEffect(days, night) {
        ask = false
        if (CheckInPrefs.skipped(context, today)) return@LaunchedEffect
        val answered = withContext(Dispatchers.IO) {
            runCatching { vm.repo.journal(JOURNAL_DEVICE_ID, today, today) }.getOrDefault(emptyList())
                .any { normJournalKey(it.question) == normJournalKey(SAHHA_ALCOHOL_QUESTION) }
        }
        if (answered) return@LaunchedEffect
        val s = loadSleepingHr(vm) ?: return@LaunchedEffect
        if (s.day != LocalDate.now().toString()) return@LaunchedEffect
        val share = night?.let { (a, b) ->
            withContext(Dispatchers.IO) {
                runCatching { vm.repo.hrSamplesUnion(vm.deviceId, a, b, limit = 200_000) }.getOrDefault(emptyList())
                    .map { it.ts to it.bpm }.let { SleepingHr.lowestShare(it, a, b) }
            }
        }
        val todayRow = days.lastOrNull { it.day == today }
        val hrvNorm = days.filter { it.day < today }.takeLast(30).mapNotNull { it.avgHrv }.takeIf { it.size >= 5 }?.average()
        val drop = todayRow?.avgHrv?.let { h -> hrvNorm?.let { n -> if (n > 0) (n - h) / n else null } }
        if (SleepingHr.shouldAskAboutAlcohol(s, share, drop)) {
            above = s.vsNormal ?: 0.0
            ask = true
        }
    }
    if (!ask) return
    fun answer(yes: Boolean) {
        ask = false
        scope.launch(Dispatchers.IO) {
            runCatching { vm.repo.upsertJournal(listOf(JournalEntry(JOURNAL_DEVICE_ID, today, SAHHA_ALCOHOL_QUESTION, yes))) }
        }
    }
    SlateSurface {
        Text(uiString(R.string.sahha_checkin_title).uppercase(), style = SlateType.labelCaps, color = SlateUi.warning)
        Spacer(Modifier.height(4.dp))
        Text(uiString(R.string.sahha_checkin_reason, String.format(Locale.getDefault(), "%.1f", above)),
            style = SlateType.body, color = SlateUi.textPrimary)
        Text(uiString(R.string.sahha_checkin_question), style = SlateType.cardTitle.copy(fontSize = 18.sp),
            color = SlateUi.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { answer(true) }) { Text(uiString(R.string.sahha_yes), style = SlateType.chip, color = SlateUi.energy) }
            TextButton(onClick = { answer(false) }) { Text(uiString(R.string.sahha_no), style = SlateType.chip, color = SlateUi.energy) }
            TextButton(onClick = { CheckInPrefs.skip(context, today); ask = false }) {
                Text(uiString(R.string.sahha_skip), style = SlateType.chip, color = SlateUi.textSecondary)
            }
        }
    }
}
