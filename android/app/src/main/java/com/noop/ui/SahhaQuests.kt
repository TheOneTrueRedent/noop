package com.noop.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.analytics.StreakCalculator
import com.noop.sahha.QuestEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.sin

// MARK: - Quests, the rest-aware streak and reward chests (fork-only, Saħħa)
//
// Weekly quests from [QuestEngine]. A finished quest leaves a chest; opening it adds sparks, and every
// three chests opened unlock the next orb tint. Everything is local: the opened chests, sparks and the
// chosen tint live in one SharedPreferences file and never leave the phone.

const val SAHHA_QUESTS_ROUTE = "sahha_quests"

/** Orb tints the chests unlock, in order. CHARGE (the default) is the look's own colour. */
enum class OrbTint { CHARGE, NEBULA, EMBER, AURORA }

object QuestPrefs {
    private const val FILE = "sahha_quests"
    private const val KEY_OPENED = "opened"
    private const val KEY_SPARKS = "sparks"
    private const val KEY_TINT = "tint"
    const val CHESTS_PER_TINT = 3

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun opened(ctx: Context): Set<String> = p(ctx).getStringSet(KEY_OPENED, emptySet()).orEmpty()
    fun sparks(ctx: Context): Int = p(ctx).getInt(KEY_SPARKS, 0)

    /** Opens the chest for [key] once; returns the sparks it gave, or 0 if it was already open. */
    fun open(ctx: Context, key: String, reward: Int): Int {
        val now = opened(ctx)
        if (key in now) return 0
        p(ctx).edit().putStringSet(KEY_OPENED, now + key).putInt(KEY_SPARKS, sparks(ctx) + reward).apply()
        return reward
    }

    fun unlockedTints(ctx: Context): List<OrbTint> =
        OrbTint.entries.take(1 + (opened(ctx).size / CHESTS_PER_TINT).coerceAtMost(OrbTint.entries.size - 1))

    var tint by mutableStateOf(OrbTint.CHARGE)
        private set

    fun load(ctx: Context) {
        tint = runCatching { OrbTint.valueOf(p(ctx).getString(KEY_TINT, OrbTint.CHARGE.name)!!) }.getOrDefault(OrbTint.CHARGE)
    }

    fun setTint(ctx: Context, t: OrbTint) {
        p(ctx).edit().putString(KEY_TINT, t.name).apply()
        tint = t
    }
}

/** The fixed colour stops of a non-Charge tint (high, main, deep), or null for CHARGE. */
internal fun orbTintStops(t: OrbTint): Triple<Color, Color, Color>? = when (t) {
    OrbTint.CHARGE -> null
    OrbTint.NEBULA -> Triple(Color(0xFFD9C7FF), Color(0xFF8E6CF0), Color(0xFF3A2287))
    OrbTint.EMBER -> Triple(Color(0xFFFFD7A1), Color(0xFFFF8A3D), Color(0xFF8E2E0C))
    OrbTint.AURORA -> Triple(Color(0xFFC6FFE6), Color(0xFF3DDC97), Color(0xFF0F6B4A))
}

private fun questReward(q: QuestEngine.Quest): Int = if (q.kind == QuestEngine.Kind.HARD_EASY || q.target >= 5) 25 else 10

/** Builds the engine's day list from the merged dailies plus the workouts the person logged. */
internal suspend fun loadQuestDays(vm: AppViewModel): List<QuestEngine.Day> = withContext(Dispatchers.IO) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis() / 1000L
    val from = now - 40L * 86_400L
    val trainedDays = runCatching { vm.repo.workoutsUnion(vm.deviceId, from, now) }.getOrDefault(emptyList())
        .map { java.time.Instant.ofEpochSecond(it.startTs).atZone(zone).toLocalDate().toString() }.toSet()
    vm.recentDays.value.map { d ->
        QuestEngine.Day(
            day = d.day,
            effort21 = d.strain?.let { UnitFormatter.effortValue(it, EffortScale.WHOOP) },
            recovery = d.recovery,
            sleepMin = d.totalSleepMin,
            steps = d.steps,
            trained = d.day in trainedDays,
        )
    }
}

/** The rest-aware streak over [days], ending today (or yesterday before today is scored). */
internal fun sahhaStreak(days: List<QuestEngine.Day>, today: LocalDate): StreakCalculator.Streaks =
    StreakCalculator.streaks(
        dayKeys = days.map { it.day },
        qualified = days.map { d ->
            val band = optimalStrainRange(d.recovery)
            QuestEngine.streakDay(d, band?.low, band?.high)
        },
        today = today.toString(),
    )

@Composable
fun SahhaQuestsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val recent by vm.recentDays.collectAsStateWithLifecycle()
    var days by remember { mutableStateOf<List<QuestEngine.Day>>(emptyList()) }
    var rev by remember { mutableStateOf(0) }
    LaunchedEffect(recent) { days = loadQuestDays(vm) }
    val today = LocalDate.now()
    val quests = remember(days) { QuestEngine.quests(days, today) }
    val streak = remember(days) { sahhaStreak(days, today) }
    val week = QuestEngine.weekStart(today).toString()
    val opened = remember(rev) { QuestPrefs.opened(context) }
    val sparks = remember(rev) { QuestPrefs.sparks(context) }

    SlateScreen(title = uiString(R.string.sahha_quests).uppercase(), onBack = onBack) {
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            SahhaFlame(Modifier.size(150.dp), lit = streak.current > 0)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(streak.current.toString(), style = SlateType.num(56.sp), color = SlateUi.textPrimary)
            Text(uiPlural(R.plurals.sahha_streak_days, streak.current, streak.current), style = SlateType.body,
                color = SlateUi.flame)
            Spacer(Modifier.height(4.dp))
            Text(uiString(R.string.sahha_streak_rule), style = SlateType.bodySmall, color = SlateUi.textTertiary,
                modifier = Modifier.padding(horizontal = 24.dp))
        }

        Text(uiString(R.string.sahha_this_week).uppercase(), style = SlateType.labelCaps, color = SlateUi.textSecondary,
            modifier = Modifier.padding(top = 8.dp))
        SlateSurface(padding = 0.dp) {
            quests.forEachIndexed { i, q ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(SlateUi.track))
                val key = "$week|${q.kind.name}"
                val isOpen = key in opened
                QuestRow(q, chest = when {
                    isOpen -> ChestState.OPEN
                    q.done -> ChestState.READY
                    else -> ChestState.LOCKED
                }) {
                    if (q.done && !isOpen) {
                        QuestPrefs.open(context, key, questReward(q))
                        rev++
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(uiString(R.string.sahha_sparks).uppercase(), style = SlateType.labelCaps, color = SlateUi.textSecondary,
                modifier = Modifier.weight(1f))
            Text(sparks.toString(), style = SlateType.value, color = SlateUi.warning)
        }
        Text(uiString(R.string.sahha_orb_tint).uppercase(), style = SlateType.labelCaps, color = SlateUi.textSecondary)
        val unlocked = remember(rev) { QuestPrefs.unlockedTints(context) }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OrbTint.entries.forEach { t ->
                val available = t in unlocked
                val stops = orbTintStops(t) ?: sahhaLookStops()
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(stops.first, stops.second, stops.third)))
                        .then(if (QuestPrefs.tint == t) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                        .then(if (available) Modifier.clickable { QuestPrefs.setTint(context, t) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!available) Box(Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.6f)))
                }
            }
        }
        Text(uiPlural(R.plurals.sahha_tint_unlock, QuestPrefs.CHESTS_PER_TINT, QuestPrefs.CHESTS_PER_TINT),
            style = SlateType.bodySmall, color = SlateUi.textTertiary)
    }
}

private enum class ChestState { LOCKED, READY, OPEN }

@Composable
private fun QuestRow(q: QuestEngine.Quest, chest: ChestState, onChest: () -> Unit) {
    val (icon, title) = when (q.kind) {
        QuestEngine.Kind.TRAIN -> Icons.Filled.DirectionsRun to uiPlural(R.plurals.sahha_quest_train, q.target, q.target)
        QuestEngine.Kind.HARD_EASY -> Icons.Filled.Spa to uiPlural(R.plurals.sahha_quest_hard_easy, q.target, q.target)
        QuestEngine.Kind.SLEEP -> Icons.Filled.Bedtime to uiPlural(R.plurals.sahha_quest_sleep, q.target, q.target)
        QuestEngine.Kind.STEPS -> Icons.Filled.DirectionsWalk to uiPlural(
            R.plurals.sahha_quest_steps, q.target, String.format(Locale.getDefault(), "%,d", q.stepGoal ?: 0), q.target,
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).border(1.5.dp, SlateUi.warning.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = SlateUi.warning, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = SlateType.value.copy(fontSize = 17.sp), color = SlateUi.textPrimary)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(SlateUi.track)) {
                Box(Modifier.fillMaxWidth((q.progress.toFloat() / q.target).coerceIn(0f, 1f)).height(5.dp)
                    .clip(RoundedCornerShape(3.dp)).background(SlateUi.warning))
            }
            Spacer(Modifier.height(6.dp))
            Text(uiString(R.string.sahha_quest_progress, q.progress.coerceAtMost(q.target), q.target),
                style = SlateType.bodySmall, color = SlateUi.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        SahhaChest(chest, onChest, Modifier.size(44.dp))
    }
}

/** A small chest: dim when locked, glowing when ready to open, lid up once opened. */
@Composable
private fun SahhaChest(state: ChestState, onClick: () -> Unit, modifier: Modifier) {
    val gold = Color(0xFFF2C14A)
    val wood = Color(0xFF7A5A3A)
    val label = when (state) {
        ChestState.LOCKED -> uiString(R.string.sahha_chest_locked)
        ChestState.READY -> uiString(R.string.sahha_chest_open)
        ChestState.OPEN -> uiString(R.string.sahha_chest_opened)
    }
    Canvas(
        modifier.clip(RoundedCornerShape(10.dp))
            .then(if (state == ChestState.READY) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier),
    ) {
        val a = if (state == ChestState.LOCKED) 0.35f else 1f
        val w = size.width
        val h = size.height
        if (state == ChestState.READY) {
            drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = 0.55f), Color.Transparent)), w * 0.6f, Offset(w / 2, h / 2))
        }
        val bodyTop = h * 0.48f
        drawRoundRect(wood.copy(alpha = a), topLeft = Offset(w * 0.12f, bodyTop), size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        val lidY = if (state == ChestState.OPEN) h * 0.14f else h * 0.28f
        drawRoundRect(wood.copy(alpha = a * 0.9f), topLeft = Offset(w * 0.12f, lidY), size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        drawRect(gold.copy(alpha = a), topLeft = Offset(w * 0.44f, bodyTop - h * 0.02f), size = androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.16f))
        if (state == ChestState.OPEN) {
            drawCircle(gold, w * 0.07f, Offset(w * 0.38f, h * 0.42f))
            drawCircle(gold.copy(alpha = 0.7f), w * 0.05f, Offset(w * 0.6f, h * 0.4f))
        }
    }
}

/** The streak flame: three flickering tongues, still under reduced motion or the battery saver. */
@Composable
internal fun SahhaFlame(modifier: Modifier, lit: Boolean) {
    val still = rememberPoseStill()
    val t = if (still) 0f else rememberInfiniteTransition(label = "flame").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart), label = "flame_t",
    ).value
    val alpha = if (lit) 1f else 0.3f
    Canvas(modifier) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A3D).copy(alpha = 0.35f * alpha), Color.Transparent)),
            w * 0.7f, Offset(w / 2, h * 0.62f))
        fun tongue(scale: Float, sway: Float, colors: List<Color>) {
            val cx = w / 2 + sway * w * 0.04f
            val base = h * 0.9f
            val top = h * (1f - 0.85f * scale)
            val half = w * 0.28f * scale
            val p = Path().apply {
                moveTo(cx, top)
                cubicTo(cx + half * 0.2f, top + (base - top) * 0.25f, cx + half * 1.15f, top + (base - top) * 0.55f, cx + half * 0.75f, base - half * 0.35f)
                cubicTo(cx + half * 0.45f, base, cx - half * 0.45f, base, cx - half * 0.75f, base - half * 0.35f)
                cubicTo(cx - half * 1.15f, top + (base - top) * 0.55f, cx - half * 0.2f, top + (base - top) * 0.25f, cx, top)
                close()
            }
            drawPath(p, Brush.verticalGradient(colors.map { it.copy(alpha = it.alpha * alpha) }, startY = top, endY = base))
        }
        tongue(1f + 0.03f * sin(t), sin(t * 1.3f), listOf(Color(0xFFFF6A1F), Color(0xFFFF9A3C), Color(0xFFB9420E)))
        tongue(0.72f + 0.04f * sin(t * 1.7f + 1f), sin(t * 2f + 0.5f), listOf(Color(0xFFFFB347), Color(0xFFFFC56B), Color(0xFFFF8A3D)))
        tongue(0.42f + 0.03f * sin(t * 2.3f + 2f), sin(t * 2.6f + 1f), listOf(Color(0xFFFFF3C4), Color(0xFFFFE08A), Color(0xFFFFB347)))
        drawCircle(Color.White.copy(alpha = 0.04f), w * 0.48f, Offset(w / 2, h / 2), style = Stroke(1f))
    }
}
