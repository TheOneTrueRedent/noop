package com.noop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.R

// MARK: - Slate style (fork-only)
//
// Fonts, type scale, colour tokens and the small shared pieces of the Slate layout, matched to the four
// reference screenshots the fork owner supplied (Home, Health, Health monitor, Recovery detail). Colours
// were sampled from those screenshots. The fonts are open-licence stand-ins (see app/fonts-licenses):
// Barlow for numerals, Figtree for text.

object SlateFonts {
    val text = FontFamily(
        Font(R.font.slate_text_regular, FontWeight.Normal),
        Font(R.font.slate_text_medium, FontWeight.Medium),
        Font(R.font.slate_text_semibold, FontWeight.SemiBold),
        Font(R.font.slate_text_bold, FontWeight.Bold),
    )
    val num = FontFamily(
        Font(R.font.slate_num_medium, FontWeight.Medium),
        Font(R.font.slate_num_semibold, FontWeight.SemiBold),
        Font(R.font.slate_num_bold, FontWeight.Bold),
    )
}

object SlateType {
    private val text = SlateFonts.text

    /** Centred screen titles: "HEALTH MONITOR", "TUE, 27 OCT". */
    val screenTitle = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.6.sp)

    /** Section headers: "FITNESS AGE", "HEART RATE", "ACTIVITIES". */
    val sectionCaps = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.4.sp)

    /** Tile and row titles: "RESPIRATORY RATE", "HEART RATE VARIABILITY". */
    val cardCaps = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.4.sp)

    /** Dial labels and small caps: "RECOVERY ›", "BPM", "AGE IMPACT". */
    val labelCaps = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.6.sp)

    val heading = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    val sectionTitle = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    val cardTitle = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    val body = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySmall = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    val value = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    val unit = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    val chip = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    val tab = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

    /** Big numerals (DIN-style): dial values, "94", "28.9". */
    fun num(size: TextUnit): TextStyle =
        TextStyle(fontFamily = SlateFonts.num, fontWeight = FontWeight.Bold, fontSize = size, letterSpacing = (-0.3).sp)
}

/** Slate-only colour tokens, sampled from the reference screenshots. Chrome only; data colours (the
 *  recovery ramp) still come from [Palette]. */
object SlateUi {
    val bgTop = Color(0xFF273238)
    val bgMid = Color(0xFF141A1F)
    val bgBottom = Color(0xFF101418)
    val card = Color(0xFF2D3337)
    val cardBlue = Color(0xFF252F36)
    val raised = Color(0xFF41474B)
    val track = Color(0xFF394247)
    val gridLine = Color(0xFF222C32)
    val panel = Color(0xFF1B272E)
    val alertCard = Color(0xFF141720)
    val alertBorder = Color(0xFF222735)
    val alertBadge = Color(0xFF2E3443)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFB0B5BA)
    val textTertiary = Color(0xFF7E868C)
    val link = Color(0xFF3299DD)
    val deltaBlue = Color(0xFF62C3F2)
    val sleepBlue = Color(0xFF6D97B1)
    val strainBlue = Color(0xFF008ADF)
    val heartBlue = Color(0xFF3299DD)
    val positive = Color(0xFF00D7A1)
    val positiveBg = Color(0xFF274A46)
    val warning = Color(0xFFF5A623)
    val warningBg = Color(0xFF4B4333)
    val neutralBg = Color(0xFF393F43)
    val neutralText = Color(0xFF9AA0A5)
    val flame = Color(0xFFF26B21)
    val outlookWarm = Color(0xFF8B7B64)
    val outlookCool = Color(0xFF324A56)
    val tabBar = Color(0xFF2B373E)
    val tabInactive = Color(0xFF8A949A)
    val coachCore = Color(0xFF19303A)
    val coachRimA = Color(0xFF6C5CE7)
    val coachRimB = Color(0xFF2E86DE)
    val orbCore = Color(0xFF0D1012)
    val orbBody = Color(0xFF604727)
    val orbRim = Color(0xFFE8C79A)
    val orbSpark = Color(0xFFF6D6A4)

    /** The twelve-step factor bar ramp, worst (left) to best (right), as sampled. */
    val factorRamp: List<Color> = listOf(
        Color(0xFFF59D1D), Color(0xFFE3962A), Color(0xFFCF8E33), Color(0xFFB4813A), Color(0xFF9C753A),
        Color(0xFF7F7A55), Color(0xFF5F8263), Color(0xFF3E8C6B), Color(0xFF1FA37C), Color(0xFF09BD8F),
        Color(0xFF12C995), Color(0xFF629E94),
    )

    val background: Brush = Brush.verticalGradient(
        0f to bgTop, 0.32f to bgMid, 1f to bgBottom,
    )
}

/** Date skeletons for [slateDateFormat]: "27 Oct" and "Tue, 27 Oct" in the device locale's own order. */
internal const val SLATE_SKELETON_DAY = "dMMM"
internal const val SLATE_SKELETON_WEEKDAY = "EEEdMMM"

/** A whole percentage in the device locale ("55%"). */
internal fun slatePercentText(value: Int): String = String.format(java.util.Locale.getDefault(), "%d%%", value)

/** A date formatter for a skeleton ("dMMM", "EEEdMMM") in the device locale's own order and punctuation. */
internal fun slateDateFormat(skeleton: String): java.time.format.DateTimeFormatter {
    val locale = java.util.Locale.getDefault()
    return java.time.format.DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale,
    )
}

/** Extra bottom space so the last card clears the Slate bar (taller than the stock bar). */
internal val SLATE_BAR_CLEARANCE: Dp = 104.dp

/** A plain Slate card: neutral surface, large radius, generous padding. */
@Composable
internal fun SlateSurface(
    modifier: Modifier = Modifier,
    color: Color = SlateUi.card,
    radius: Dp = 22.dp,
    padding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * The Slate ring: a full track, then an arc from 12 o'clock clockwise with round caps. Animates in once
 * unless motion is reduced or the battery saver is on ([rememberPoseStill]).
 */
@Composable
internal fun SlateRing(
    fraction: Float,
    color: Color,
    diameter: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    track: Color = SlateUi.track,
) {
    val target = fraction.coerceIn(0f, 1f)
    val still = rememberPoseStill()
    var started by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) { started = true }
    val shown = if (still) target else animateFloatAsState(
        targetValue = if (started) target else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "slate_ring",
    ).value
    Canvas(modifier.size(diameter)) {
        val w = stroke.toPx()
        val d = size.minDimension - w
        val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = tl, size = Size(d, d),
            style = Stroke(width = w))
        if (shown > 0f) {
            drawArc(color, startAngle = -90f, sweepAngle = 360f * shown, useCenter = false, topLeft = tl,
                size = Size(d, d), style = Stroke(width = w, cap = StrokeCap.Round))
        }
    }
}

internal enum class SlateChipKind { POSITIVE, WARNING, NEUTRAL, CALIBRATING }

/** The small status chip under a value: "✓ In your range", "⚠ Off your baseline", "● No reading". */
@Composable
internal fun SlateChip(kind: SlateChipKind, text: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        SlateChipKind.POSITIVE -> SlateUi.positiveBg to SlateUi.positive
        SlateChipKind.WARNING -> SlateUi.warningBg to SlateUi.warning
        SlateChipKind.NEUTRAL, SlateChipKind.CALIBRATING -> SlateUi.neutralBg to SlateUi.neutralText
    }
    val icon: ImageVector = when (kind) {
        SlateChipKind.POSITIVE -> Icons.Filled.Check
        SlateChipKind.WARNING -> Icons.Outlined.WarningAmber
        SlateChipKind.NEUTRAL -> Icons.Filled.Circle
        SlateChipKind.CALIBRATING -> Icons.Filled.HourglassEmpty
    }
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg,
            modifier = Modifier.size(if (kind == SlateChipKind.NEUTRAL) 9.dp else 15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = SlateType.chip, color = fg, maxLines = 1)
    }
}

/** A Slate screen: gradient canvas, centred caps title, optional back and trailing action, scrolling body. */
@Composable
internal fun SlateScreen(
    title: String,
    onBack: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
    horizontalPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(SlateUi.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = statusTop + 6.dp, bottom = SLATE_BAR_CLEARANCE),
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
                if (onBack != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = uiString(R.string.slate_back),
                        tint = SlateUi.textPrimary,
                        modifier = Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape)
                            .clickable(onClick = onBack).padding(6.dp),
                    )
                }
                Text(title, style = SlateType.screenTitle, color = SlateUi.textPrimary,
                    modifier = Modifier.align(Alignment.Center))
                if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}
