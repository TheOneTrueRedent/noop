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
// ladder turn red. In Sage the orb's aura takes the colour of the day's form, a ladder that climbs like
// ki: green-gold, blue, violet, then silver, with red for the surge. Motion stops under reduced motion or
// the battery saver ([rememberPoseStill]).

private const val SAHHA_DASH = "–"

/**
 * The orb's three colour stops (highlight, main, deep): red in a surge, else a tint a chest unlocked, else
 * (Sage) the colour of the day's [form], else the look's own.
 */
internal fun sahhaOrbStops(surge: Boolean, form: SahhaForm.Form? = null): Triple<Color, Color, Color> = when {
    surge -> Triple(Color(0xFFFFB3A8), SlateUi.surge, Color(0xFF8E1A14))
    else -> orbTintStops(QuestPrefs.tint) ?: sahhaKiStops(form) ?: sahhaLookStops()
}

/** Sage's ki ladder: each form's colour stops, low to high. Null outside Sage or without a form. */
internal fun sahhaKiStops(form: SahhaForm.Form?): Triple<Color, Color, Color>? {
    if (!SahhaLook.sage) return null
    return when (form ?: return null) {
        SahhaForm.Form.SEIZA -> Triple(Color(0xFFF2FFB8), Color(0xFFC6F03A), Color(0xFF4F7A0C))
        SahhaForm.Form.SHU -> Triple(Color(0xFFC8F4FF), Color(0xFF38C6FF), Color(0xFF0C4F96))
        SahhaForm.Form.HA -> Triple(Color(0xFFF1CCFF), Color(0xFFB45CFF), Color(0xFF4C1786))
        SahhaForm.Form.MUSHIN -> Triple(Color(0xFFFFFFFF), Color(0xFFD2DAE6), Color(0xFF5E6A80))
    }
}

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
    val accent = if (surge) SlateUi.surge else SlateUi.accent
    val track = SlateUi.track
    val textColor = SlateUi.textPrimary
    Box(
        modifier.size(diameter).clip(CircleShape)
            .combinedClickable(onClick = face.onOpen, onLongClick = { index++ }),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (sage) drawSageOrb(shown, breathe, high, main, deep, accent)
            else drawConquerorOrb(shown, breathe, high, main, deep, accent, track)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(face.value ?: SAHHA_DASH, style = SlateType.num(if (sage) 66.sp else 70.sp), color = textColor)
            Text(face.caption.uppercase(), style = SlateType.labelCaps.copy(letterSpacing = 2.6.sp),
                color = textColor.copy(alpha = 0.85f))
        }
    }
}

private fun DrawScope.drawSageOrb(fill: Float, breathe: Float, high: Color, main: Color, deep: Color, ring: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension / 2f
    // Rays fanning up and out from the orb.
    val rays = listOf(-90f to 1f, -125f to 0.85f, -55f to 0.85f, -160f to 0.7f, -20f to 0.7f, -105f to 0.9f, -75f to 0.9f)
    rays.forEach { (deg, len) ->
        val a = Math.toRadians(deg.toDouble()).toFloat()
        drawLine(main.copy(alpha = 0.22f + 0.18f * breathe), c,
            Offset(c.x + cos(a) * outer * 1.25f * len, c.y + sin(a) * outer * 1.25f * len),
            strokeWidth = 2.5f * density, cap = StrokeCap.Round)
    }
    // A flame-shaped aura rising behind the orb, in the orb's own colour.
    val r = outer * 0.6f
    val flame = Path().apply {
        moveTo(c.x, c.y - outer * 1.05f)
        cubicTo(c.x + r * 0.55f, c.y - outer * 0.55f, c.x + r * 1.45f, c.y - r * 0.2f, c.x + r * 1.05f, c.y + r * 0.95f)
        cubicTo(c.x + r * 0.6f, c.y + r * 1.45f, c.x - r * 0.6f, c.y + r * 1.45f, c.x - r * 1.05f, c.y + r * 0.95f)
        cubicTo(c.x - r * 1.45f, c.y - r * 0.2f, c.x - r * 0.55f, c.y - outer * 0.55f, c.x, c.y - outer * 1.05f)
        close()
    }
    drawPath(flame, main.copy(alpha = 0.2f + 0.12f * breathe))
    // Glow, then the core lit from the top left.
    drawCircle(Brush.radialGradient(0f to main.copy(alpha = 0.55f + 0.15f * breathe), 1f to Color.Transparent,
        center = c, radius = r * 1.6f), radius = r * 1.6f, center = c)
    drawCircle(Brush.radialGradient(0f to high.copy(alpha = 0.9f), 0.55f to main.copy(alpha = 0.7f), 1f to deep.copy(alpha = 0.4f),
        center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.5f), radius = r, center = c)
    // The ensō: a brush circle open at the top right, the stroke growing with the score.
    val er = outer * 0.8f
    val sweep = 40f + 280f * fill
    drawArc(ring.copy(alpha = 0.18f), startAngle = -50f, sweepAngle = 320f, useCenter = false,
        topLeft = Offset(c.x - er, c.y - er), size = Size(er * 2, er * 2), style = Stroke(width = 6f * density, cap = StrokeCap.Round))
    drawArc(ring, startAngle = -50f, sweepAngle = sweep, useCenter = false,
        topLeft = Offset(c.x - er, c.y - er), size = Size(er * 2, er * 2), style = Stroke(width = 13f * density, cap = StrokeCap.Round))
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
            val ki = sahhaKiStops(f)?.second
            LadderCell(name, range, on, ki ?: SlateUi.energy, sage, Modifier.weight(1f), dimColor = ki?.copy(alpha = 0.7f))
        }
        LadderCell(uiString(R.string.sahha_form_kiai), uiString(R.string.sahha_form_kiai_range), surge, SlateUi.surge, sage,
            Modifier.weight(1f), dimColor = SlateUi.surge.copy(alpha = 0.7f))
    }
}

@Composable
private fun LadderCell(name: String, range: String, on: Boolean, color: Color, sage: Boolean, modifier: Modifier, dimColor: Color? = null) {
    val shape = if (sage) RoundedCornerShape(12.dp) else RoundedCornerShape(2.dp)
    Column(
        modifier.clip(shape)
            .background(if (on) color.copy(alpha = 0.16f) else SlateUi.card, shape)
            .border(1.dp, if (on) color else (dimColor?.copy(alpha = 0.35f) ?: SlateUi.alertBorder), shape)
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
