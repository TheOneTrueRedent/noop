package com.noop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R
import com.noop.sahha.SahhaForm
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// MARK: - Saħħa visuals (fork-only)
//
// The pieces that make the two looks more than palettes: each look's backdrop, the Today orb (Sage: a
// glowing core inside a gold ensō with rays and a flame-shaped aura; Conqueror: a hex reticle that fills
// with the score), the form ladder, Conqueror's segmented readout bars, the pills, the daily principle
// card and the body-age band card. In a Kiai surge (Effort past the top of today's band) the orb, rays and
// ladder turn red (Conqueror only). In Sage the orb takes the colours of the day's form, a ladder that
// climbs like ki: green-gold, red-blue, violet-silver, then silver with purple, red, blue and green
// surging around it. Sage has no surge state. Motion stops under reduced motion or the battery saver
// ([rememberPoseStill]).

private const val SAHHA_DASH = "–"

/**
 * The orb's three colour stops (highlight, main, deep): red in a surge, else a tint a chest unlocked, else
 * (Sage) the colour of the day's [form], else the look's own.
 */
internal fun sahhaOrbStops(surge: Boolean, form: SahhaForm.Form? = null): Triple<Color, Color, Color> = when {
    surge -> Triple(Color(0xFFFFB3A8), SlateUi.surge, Color(0xFF8E1A14))
    else -> orbTintStops(QuestPrefs.tint) ?: sahhaKiStops(form) ?: sahhaLookStops()
}

/**
 * One rung of Sage's ki ladder: the orb's core stops (highlight, main, deep), the [aura] colours its rays
 * and flame carry, and whether the aura [shifting]ly cycles round the orb (the top form only).
 */
internal data class SahhaKi(val high: Color, val main: Color, val deep: Color, val aura: List<Color>, val shifting: Boolean = false)

private val KI_GOLD = Color(0xFFFFC83A)
private val KI_GREEN = Color(0xFF8FE83A)
private val KI_RED = Color(0xFFFF3B3B)
private val KI_BLUE = Color(0xFF3AA2FF)
private val KI_VIOLET = Color(0xFFB45CFF)
private val KI_SILVER = Color(0xFFD8DEE8)
private val KI_SURGE_GREEN = Color(0xFF4BE37A)

/** Sage's ki ladder, low to high. Null outside Sage or without a form. */
internal fun sahhaKi(form: SahhaForm.Form?): SahhaKi? {
    if (!SahhaLook.sage) return null
    return when (form ?: return null) {
        SahhaForm.Form.SEIZA -> SahhaKi(Color(0xFFF6FFC4), Color(0xFFC6F03A), Color(0xFF4F7A0C), listOf(KI_GOLD, KI_GREEN))
        SahhaForm.Form.SHU -> SahhaKi(Color(0xFFFFD2CC), KI_RED, Color(0xFF0C3E9C), listOf(KI_RED, KI_BLUE))
        SahhaForm.Form.HA -> SahhaKi(Color(0xFFF6EEFF), KI_VIOLET, Color(0xFF4C1786), listOf(KI_VIOLET, KI_SILVER))
        SahhaForm.Form.MUSHIN -> SahhaKi(Color(0xFFFFFFFF), KI_SILVER, Color(0xFF5E6A80),
            listOf(KI_VIOLET, KI_RED, KI_BLUE, KI_SURGE_GREEN), shifting = true)
    }
}

/** [sahhaKi] as plain orb stops. */
internal fun sahhaKiStops(form: SahhaForm.Form?): Triple<Color, Color, Color>? =
    sahhaKi(form)?.let { Triple(it.high, it.main, it.deep) }

/** The look's own orb stops: jade (Sage) or violet (Conqueror). */
internal fun sahhaLookStops(): Triple<Color, Color, Color> =
    if (SahhaLook.sage) Triple(Color(0xFFB8FFE4), Color(0xFF3BF0B0), Color(0xFF0E6B4C))
    else Triple(Color(0xFFDCD0FF), Color(0xFF9B7BFF), Color(0xFF3A2287))

/** The canvas behind every Slate screen in the current look. */
@Composable
internal fun SlateBackdrop(content: @Composable BoxScope.() -> Unit) {
    val sage = SahhaLook.sage
    val top = SlateUi.bgTop
    val mid = SlateUi.bgMid
    val bottom = SlateUi.bgBottom
    val grid = SlateUi.gridLine
    Box(Modifier.fillMaxSize().background(bottom)) {
        Canvas(Modifier.fillMaxSize()) {
            if (sage) {
                drawRect(Brush.radialGradient(
                    0f to top, 0.46f to mid, 1f to bottom,
                    center = Offset(size.width / 2f, size.height * 0.26f), radius = size.width * 1.2f,
                ))
            } else {
                drawRect(Brush.verticalGradient(0f to top, 0.35f to mid, 1f to bottom))
                // A faint scanner grid over the top half.
                val step = 26.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height * 0.5f), 1f); x += step }
                var y = 0f
                while (y < size.height * 0.5f) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
            }
        }
        content()
    }
}

/** One readout the orb can show: a caption, a value and how full its ring is. */
internal data class SahhaOrbFace(val caption: String, val value: String?, val fraction: Double, val onOpen: () -> Unit)

/**
 * The Today orb. A tap opens the shown score's detail; a long press steps to the next face (Charge →
 * Rest → Effort). [surge] turns it red.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SahhaTodayOrb(
    faces: List<SahhaOrbFace>, surge: Boolean, modifier: Modifier = Modifier, diameter: Dp = 270.dp,
    form: SahhaForm.Form? = null,
) {
    var index by remember { mutableStateOf(0) }
    val face = faces[index.mod(faces.size)]
    val still = rememberPoseStill()
    val breathe = if (still) 0.5f else rememberInfiniteTransition(label = "sahha_orb").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (surge) 900 else 4_200, easing = LinearEasing), RepeatMode.Reverse),
        label = "sahha_orb_breathe",
    ).value
    var started by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) { started = true }
    val target = face.fraction.coerceIn(0.0, 1.0).toFloat()
    val shown = if (still) target else animateFloatAsState(
        if (started) target else 0f, tween(900, easing = FastOutSlowInEasing), label = "sahha_orb_fill",
    ).value
    val (high, main, deep) = sahhaOrbStops(surge, form)
    val sage = SahhaLook.sage
    // The aura follows the form unless a chest tint was chosen; then the tint's own colour carries it.
    val ki = sahhaKi(form)?.takeIf { orbTintStops(QuestPrefs.tint) == null }
    val phase = if (still) 0f else rememberInfiniteTransition(label = "sahha_ki").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "sahha_ki_phase",
    ).value
    val accent = if (surge) SlateUi.surge else SlateUi.accent
    val track = SlateUi.track
    val textColor = SlateUi.textPrimary
    Box(
        modifier.size(diameter).clip(CircleShape)
            .combinedClickable(onClick = face.onOpen, onLongClick = { index++ }),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (sage) drawSageOrb(shown, breathe, phase, high, main, deep, accent, ki?.aura.orEmpty(), ki?.shifting == true)
            else drawConquerorOrb(shown, breathe, high, main, deep, accent, track)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(face.value ?: SAHHA_DASH, style = SlateType.num(if (sage) 66.sp else 70.sp), color = textColor)
            Text(face.caption.uppercase(), style = SlateType.labelCaps.copy(letterSpacing = 2.6.sp),
                color = textColor.copy(alpha = 0.85f))
        }
    }
}

private fun DrawScope.drawSageOrb(
    fill: Float, breathe: Float, phase: Float, high: Color, main: Color, deep: Color, ring: Color,
    aura: List<Color>, shifting: Boolean,
) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension / 2f
    val n = aura.size
    // The aura colour at position [i], drifting along the list as [phase] turns when the form shifts.
    fun auraAt(i: Int): Color {
        if (n == 0) return main
        if (!shifting) return aura[i.mod(n)]
        val t = phase * n + i
        val k = kotlin.math.floor(t).toInt()
        return lerp(aura[k.mod(n)], aura[(k + 1).mod(n)], t - k)
    }
    // Rays fanning up and out from the orb, alternating through the aura's colours.
    val rays = listOf(-90f to 1f, -125f to 0.85f, -55f to 0.85f, -160f to 0.7f, -20f to 0.7f, -105f to 0.9f, -75f to 0.9f)
    rays.forEachIndexed { i, (deg, len) ->
        val a = Math.toRadians(deg.toDouble()).toFloat()
        drawLine(auraAt(i).copy(alpha = 0.26f + 0.2f * breathe), c,
            Offset(c.x + cos(a) * outer * 1.25f * len, c.y + sin(a) * outer * 1.25f * len),
            strokeWidth = 2.5f * density, cap = StrokeCap.Round)
    }
    // A flame-shaped aura rising behind the orb: the form's colours blended from base to tip.
    val r = outer * 0.6f
    val flame = Path().apply {
        moveTo(c.x, c.y - outer * 1.05f)
        cubicTo(c.x + r * 0.55f, c.y - outer * 0.55f, c.x + r * 1.45f, c.y - r * 0.2f, c.x + r * 1.05f, c.y + r * 0.95f)
        cubicTo(c.x + r * 0.6f, c.y + r * 1.45f, c.x - r * 0.6f, c.y + r * 1.45f, c.x - r * 1.05f, c.y + r * 0.95f)
        cubicTo(c.x - r * 1.45f, c.y - r * 0.2f, c.x - r * 0.55f, c.y - outer * 0.55f, c.x, c.y - outer * 1.05f)
        close()
    }
    val flameAlpha = 0.22f + 0.12f * breathe
    if (n >= 2 && !shifting) {
        drawPath(flame, Brush.verticalGradient(
            0f to aura[1].copy(alpha = flameAlpha), 1f to aura[0].copy(alpha = flameAlpha),
            startY = c.y - outer * 1.05f, endY = c.y + r * 1.4f,
        ))
    } else {
        drawPath(flame, main.copy(alpha = flameAlpha))
    }
    // The top form: surges of each colour circling the silver core, each swelling in turn.
    if (shifting && n > 0) {
        for (i in 0 until n) {
            val ang = (2 * PI * (phase + i.toFloat() / n)).toFloat()
            val swell = 0.5f + 0.5f * sin((2 * PI * (phase * 2 + i.toFloat() / n)).toFloat())
            val at = Offset(c.x + cos(ang) * r * 0.95f, c.y + sin(ang) * r * 0.95f)
            val br = r * (0.85f + 0.35f * swell)
            drawCircle(Brush.radialGradient(0f to aura[i].copy(alpha = 0.35f + 0.35f * swell), 1f to Color.Transparent,
                center = at, radius = br), radius = br, center = at)
        }
    }
    // Glow, then the core lit from the top left.
    drawCircle(Brush.radialGradient(0f to main.copy(alpha = 0.55f + 0.15f * breathe), 1f to Color.Transparent,
        center = c, radius = r * 1.6f), radius = r * 1.6f, center = c)
    drawCircle(Brush.radialGradient(0f to high.copy(alpha = 0.9f), 0.55f to main.copy(alpha = 0.7f), 1f to deep.copy(alpha = 0.4f),
        center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.5f), radius = r, center = c)
    // The ensō: a brush circle open at the top right, the stroke growing with the score. In the top form
    // it carries every colour of the aura.
    val er = outer * 0.8f
    val sweep = 40f + 280f * fill
    drawArc(ring.copy(alpha = 0.18f), startAngle = -50f, sweepAngle = 320f, useCenter = false,
        topLeft = Offset(c.x - er, c.y - er), size = Size(er * 2, er * 2), style = Stroke(width = 6f * density, cap = StrokeCap.Round))
    val enso = Stroke(width = 13f * density, cap = StrokeCap.Round)
    if (shifting && n > 0) {
        val stops = (0..n).map { auraAt(it) }
        drawArc(Brush.sweepGradient(stops, center = c), startAngle = -50f, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(c.x - er, c.y - er), size = Size(er * 2, er * 2), style = enso)
    } else {
        drawArc(ring, startAngle = -50f, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(c.x - er, c.y - er), size = Size(er * 2, er * 2), style = enso)
    }
}

private fun DrawScope.drawConquerorOrb(fill: Float, breathe: Float, high: Color, main: Color, deep: Color, accent: Color, track: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension / 2f * 0.94f
    fun hex(r: Float) = Path().apply {
        for (i in 0 until 6) {
            val a = (-PI / 2 + i * PI / 3).toFloat()
            val p = Offset(c.x + r * cos(a), c.y + r * sin(a))
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawCircle(Brush.radialGradient(0f to main.copy(alpha = 0.30f + 0.12f * breathe), 1f to Color.Transparent,
        center = c, radius = outer), radius = outer, center = c)
    val shell = hex(outer)
    drawPath(shell, Brush.radialGradient(0f to deep.copy(alpha = 0.45f), 1f to Color(0x10FFFFFF), center = c, radius = outer))
    drawPath(shell, track, style = Stroke(width = 1.5f * density))
    val inner = hex(outer * 0.84f)
    drawPath(inner, main.copy(alpha = 0.35f), style = Stroke(width = 2f * density))
    // The score fills the inner hex's perimeter clockwise from the top.
    if (fill > 0f) {
        val pm = PathMeasure().apply { setPath(inner, false) }
        val seg = Path()
        pm.getSegment(0f, pm.length * fill, seg, true)
        drawPath(seg, high, style = Stroke(width = 5f * density, cap = StrokeCap.Square))
    }
    // Crosshair ticks and corner brackets.
    val t = 14.dp.toPx()
    listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f)).forEach { d ->
        val a = Offset(c.x + d.x * (outer + 2f), c.y + d.y * (outer + 2f))
        drawLine(accent, a, Offset(a.x + d.x * t, a.y + d.y * t), strokeWidth = 2f * density)
    }
}

/** The form ladder under the orb: Seiza · Shu · Ha · Mushin, then Kiai (lit only in a surge). */
@Composable
internal fun SahhaFormLadder(form: SahhaForm.Form?, surge: Boolean, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple(SahhaForm.Form.SEIZA, uiString(R.string.sahha_form_seiza), "<34"),
        Triple(SahhaForm.Form.SHU, uiString(R.string.sahha_form_shu), "34–66"),
        Triple(SahhaForm.Form.HA, uiString(R.string.sahha_form_ha), "67–89"),
        Triple(SahhaForm.Form.MUSHIN, uiString(R.string.sahha_form_mushin), "90+"),
    )
    val sage = SahhaLook.sage
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { (f, name, range) ->
            val on = f == form && !surge
            val ki = sahhaKi(f)
            LadderCell(name, range, on, ki?.main ?: SlateUi.energy, sage, Modifier.weight(1f),
                dimColor = ki?.main?.copy(alpha = 0.7f), aura = ki?.aura.orEmpty())
        }
        if (!sage) {
            LadderCell(uiString(R.string.sahha_form_kiai), uiString(R.string.sahha_form_kiai_range), surge, SlateUi.surge, sage,
                Modifier.weight(1f), dimColor = SlateUi.surge.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun LadderCell(
    name: String, range: String, on: Boolean, color: Color, sage: Boolean, modifier: Modifier,
    dimColor: Color? = null, aura: List<Color> = emptyList(),
) {
    val shape = if (sage) RoundedCornerShape(12.dp) else RoundedCornerShape(2.dp)
    // A two-tone or many-tone rung is edged with all of its colours.
    val edge = if (aura.size >= 2) {
        Brush.linearGradient(aura.map { it.copy(alpha = if (on) 1f else 0.35f) })
    } else {
        SolidColor(if (on) color else (dimColor?.copy(alpha = 0.35f) ?: SlateUi.alertBorder))
    }
    Column(
        modifier.clip(shape)
            .background(if (on) color.copy(alpha = 0.16f) else SlateUi.card, shape)
            .border(if (on && aura.size >= 2) 1.5.dp else 1.dp, edge, shape)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = SlateType.value.copy(fontSize = 13.sp), color = if (on) color else (dimColor ?: SlateUi.textPrimary), maxLines = 1)
        Text(range, style = SlateType.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp), color = SlateUi.textSecondary, maxLines = 1)
    }
}

/** Conqueror's segmented readout bar: twelve skewed cells filled to [fraction]. */
@Composable
internal fun SahhaReadoutBar(label: String, value: String, fraction: Double, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = SlateType.labelCaps, color = SlateUi.textSecondary, modifier = Modifier.width(72.dp), maxLines = 1)
        val lit = (fraction.coerceIn(0.0, 1.0) * 12).let { kotlin.math.round(it).toInt() }
        val track = SlateUi.track
        Canvas(Modifier.weight(1f).height(14.dp)) {
            val gap = 3.dp.toPx()
            val w = (size.width - gap * 11) / 12f
            val skew = size.height * 0.32f
            for (i in 0 until 12) {
                val x = i * (w + gap)
                val p = Path().apply {
                    moveTo(x + skew, 0f); lineTo(x + w, 0f); lineTo(x + w - skew, size.height); lineTo(x, size.height); close()
                }
                drawPath(p, if (i < lit) color else track)
            }
        }
        Text(value, style = SlateType.value.copy(fontSize = 17.sp), color = SlateUi.textPrimary,
            textAlign = TextAlign.End, modifier = Modifier.width(64.dp), maxLines = 1)
    }
}

/** A pill under the Sage orb: a glossy round badge, a value, a caption and a small ring. */
@Composable
internal fun SahhaPill(icon: ImageVector, badge: Color, value: String, caption: String, fraction: Double?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier.clip(shape).background(SlateUi.card, shape).border(1.dp, SlateUi.alertBorder, shape)
            .clickable(onClick = onClick).padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.radialGradient(listOf(badge, badge.copy(alpha = 0.5f)))),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF0B0B0B), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = SlateType.value.copy(fontSize = 18.sp), color = SlateUi.textPrimary, maxLines = 1)
            Text(caption, style = SlateType.bodySmall, color = SlateUi.textSecondary, maxLines = 1)
        }
        if (fraction != null) SlateRing(fraction.toFloat(), badge, diameter = 24.dp, stroke = 3.dp)
    }
}

/** The Effort caption against today's recovery-derived band (#43), on the 0–21 axis. */
internal fun sahhaEffortCaption(effort21: Double?, recovery: Double?, toGo: String, onTarget: String, above: String): String? {
    val band = optimalStrainRange(recovery) ?: return null
    val e = effort21 ?: return toGo
    return when {
        e < band.low -> toGo
        e <= band.high -> onTarget
        else -> above
    }
}

/** The day's principle: a short line for the form, or the cost of a Kiai surge. */
@Composable
internal fun SahhaPrincipleCard(form: SahhaForm.Form?, surge: Boolean, effort21: Double?, recovery: Double?) {
    val band = optimalStrainRange(recovery)
    val low = band?.low?.toString() ?: SAHHA_DASH
    val high = band?.high?.toString() ?: SAHHA_DASH
    val (title, body) = when {
        surge -> uiString(R.string.sahha_principle_kiai_title) to
            uiString(R.string.sahha_principle_kiai_body, String.format(Locale.getDefault(), "%.1f", effort21 ?: 0.0), high)
        form == SahhaForm.Form.SEIZA -> uiString(R.string.sahha_principle_seiza_title) to uiString(R.string.sahha_principle_seiza_body)
        form == SahhaForm.Form.SHU -> uiString(R.string.sahha_principle_shu_title) to uiString(R.string.sahha_principle_shu_body, low, high)
        form == SahhaForm.Form.HA -> uiString(R.string.sahha_principle_ha_title) to uiString(R.string.sahha_principle_ha_body, low, high)
        form == SahhaForm.Form.MUSHIN -> uiString(R.string.sahha_principle_mushin_title) to uiString(R.string.sahha_principle_mushin_body)
        else -> return
    }
    val color = if (surge) SlateUi.surge else (sahhaKiStops(form)?.second ?: SlateUi.accent)
    val shape = if (SahhaLook.sage) RoundedCornerShape(18.dp) else RoundedCornerShape(2.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(color.copy(alpha = 0.10f), shape)
            .border(1.dp, color.copy(alpha = 0.5f), shape).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title.uppercase(), style = SlateType.labelCaps.copy(letterSpacing = 2.sp), color = color)
        Text(body, style = SlateType.body, color = SlateUi.textPrimary)
    }
}

/** The body-age card: the number and where it sits in its ±5-year band. */
@Composable
internal fun SahhaBodyAgeBandCard(fitnessAge: Double, age: Double) {
    val band = com.noop.analytics.FitnessAgeEngine.displayBandYears
    val lo = fitnessAge - band
    val hi = fitnessAge + band
    val start = minOf(lo, age)
    val end = maxOf(hi, age)
    fun pos(v: Double) = ((v - start) / (end - start)).coerceIn(0.0, 1.0).toFloat()
    val fmt = { v: Double -> String.format(Locale.getDefault(), "%.1f", v) }
    val energy = SlateUi.energy
    val accent = SlateUi.accent
    val track = SlateUi.track
    val dot = SlateUi.textPrimary
    val ground = SlateUi.bgBottom
    SlateSurface {
        Text(uiString(R.string.sahha_band_title).uppercase(), style = SlateType.labelCaps, color = SlateUi.textSecondary)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val y = size.height / 2f
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(energy, Offset(pos(lo) * size.width, y), Offset(pos(hi) * size.width, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            val ax = pos(age) * size.width
            drawLine(accent, Offset(ax, y - 12.dp.toPx()), Offset(ax, y + 12.dp.toPx()), strokeWidth = 2.dp.toPx())
            val fx = pos(fitnessAge) * size.width
            drawCircle(dot, 8.dp.toPx(), Offset(fx, y))
            drawCircle(ground, 8.dp.toPx(), Offset(fx, y), style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(lo), style = SlateType.bodySmall, color = SlateUi.textTertiary)
            Text(uiString(R.string.sahha_band_you_are, String.format(Locale.getDefault(), "%.2f", age)), style = SlateType.bodySmall,
                color = accent, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(fmt(hi), style = SlateType.bodySmall, color = SlateUi.textTertiary)
        }
    }
}

/** Conqueror's body-age frame: two nested hexes and a soft violet glow. */
@Composable
internal fun SahhaHexFrame(modifier: Modifier, lit: Boolean) {
    val main = SlateUi.energy
    val track = SlateUi.track
    val a = if (lit) 1f else 0.4f
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension / 2f * 0.92f
        fun hex(r: Float) = Path().apply {
            for (i in 0 until 6) {
                val ang = (-PI / 2 + i * PI / 3).toFloat()
                val p = Offset(c.x + r * cos(ang), c.y + r * sin(ang))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawCircle(Brush.radialGradient(0f to main.copy(alpha = 0.28f * a), 1f to Color.Transparent, center = c, radius = outer),
            radius = outer, center = c)
        drawPath(hex(outer), main.copy(alpha = 0.9f * a), style = Stroke(width = 3f * density))
        drawPath(hex(outer * 0.86f), track, style = Stroke(width = 1.5f * density))
    }
}

/** Sleep minutes as "7h 11m". */
internal fun sahhaSleepText(minutes: Double?): String? = minutes?.let { hoursMinutes(it) }
