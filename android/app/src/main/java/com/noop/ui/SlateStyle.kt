package com.noop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
// Fonts, type scale, colour tokens and the small shared pieces of the Slate layout. The layout was
// matched to four reference screenshots the fork owner supplied (Home, Health, Health monitor, Recovery
// detail); its fonts and colours now come from the chosen Saħħa look (SahhaLook.kt).

object SlateFonts {
    // Fork-only (Saħħa): each look has a display face and a text face (OFL; see app/fonts-licenses).
    // Sage: Shippori Mincho (display) + Zen Kaku Gothic New (text). Conqueror: Chakra Petch + Inter.
    private val sageHead = FontFamily(
        Font(R.font.sage_head_semibold, FontWeight.SemiBold),
        Font(R.font.sage_head_extrabold, FontWeight.ExtraBold),
        Font(R.font.sage_head_extrabold, FontWeight.Bold),
    )
    private val sageText = FontFamily(
        Font(R.font.sage_text_regular, FontWeight.Normal),
        Font(R.font.sage_text_medium, FontWeight.Medium),
        Font(R.font.sage_text_medium, FontWeight.SemiBold),
        Font(R.font.sage_text_bold, FontWeight.Bold),
    )
    private val conqHead = FontFamily(
        Font(R.font.conq_head_semibold, FontWeight.SemiBold),
        Font(R.font.conq_head_bold, FontWeight.Bold),
    )
    private val conqText = FontFamily(
        Font(R.font.conq_text_regular, FontWeight.Normal),
        Font(R.font.conq_text_medium, FontWeight.Medium),
        Font(R.font.conq_text_semibold, FontWeight.SemiBold),
        Font(R.font.conq_text_semibold, FontWeight.Bold),
    )

    /** Display face: titles, section heads, numerals. */
    val head: FontFamily get() = if (SahhaLook.sage) sageHead else conqHead

    /** Text face: body copy, captions, chips. */
    val text: FontFamily get() = if (SahhaLook.sage) sageText else conqText

    /** Numerals use the display face in both looks. */
    val num: FontFamily get() = head
}

object SlateType {
    private val text get() = SlateFonts.text
    private val head get() = SlateFonts.head

    /** Centred screen titles: "HEALTH MONITOR", "TUE, 27 OCT". */
    val screenTitle: TextStyle get() = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.6.sp)

    /** Section headers: "FITNESS AGE", "HEART RATE", "ACTIVITIES". */
    val sectionCaps: TextStyle get() = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.4.sp)

    /** Tile and row titles: "RESPIRATORY RATE", "HEART RATE VARIABILITY". */
    val cardCaps: TextStyle get() = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.4.sp)

    /** Dial labels and small caps: "RECOVERY ›", "BPM", "AGE IMPACT". */
    val labelCaps: TextStyle get() = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.6.sp)

    val heading: TextStyle get() = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    val sectionTitle: TextStyle get() = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    val cardTitle: TextStyle get() = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    val body: TextStyle get() = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySmall: TextStyle get() = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val value: TextStyle get() = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    val unit: TextStyle get() = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    val chip: TextStyle get() = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    val tab: TextStyle get() = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

    /** Big numerals: dial values, "94", "28.9". */
    fun num(size: TextUnit): TextStyle =
        TextStyle(fontFamily = SlateFonts.num, fontWeight = FontWeight.Bold, fontSize = size, letterSpacing = (-0.3).sp)
}

/** Slate-layout colour tokens, one set per Saħħa look (dark only). Chrome only; data colours (the
 *  recovery ramp) still come from [Palette]. */
object SlateUi {
    /** Each token resolves per look (a state read, so the Slate screens recompose when the look changes). */
    private fun pick(sage: Color, conqueror: Color): Color = if (SahhaLook.sage) sage else conqueror

    val bgTop: Color get() = pick(Color(0xFF0B3A2C), Color(0xFF1A1233))
    val bgMid: Color get() = pick(Color(0xFF07110F), Color(0xFF0B0912))
    val bgBottom: Color get() = pick(Color(0xFF040807), Color(0xFF07060C))
    val card: Color get() = pick(Color(0xFF0F1E1A), Color(0xFF17131F))
    val cardBlue: Color get() = pick(Color(0xFF10221D), Color(0xFF1A1525))
    val raised: Color get() = pick(Color(0xFF1A2E28), Color(0xFF241E33))
    val track: Color get() = pick(Color(0xFF1E332C), Color(0xFF2A2340))
    val gridLine: Color get() = pick(Color(0xFF13231E), Color(0xFF1C1729))
    val panel: Color get() = pick(Color(0xFF0B1714), Color(0xFF120F1A))
    val alertCard: Color get() = pick(Color(0xFF0F1E1A), Color(0xFF17131F))
    val alertBorder: Color get() = pick(Color(0xFF214037), Color(0xFF2C2542))
    val alertBadge: Color get() = pick(Color(0xFF1A2E28), Color(0xFF241E33))
    val textPrimary: Color get() = pick(Color(0xFFF3F1E6), Color(0xFFF2EEFF))
    val textSecondary: Color get() = pick(Color(0xFFA9B8B0), Color(0xFFA49DBC))
    val textTertiary: Color get() = pick(Color(0xFF7F928A), Color(0xFF857E9F))
    val link: Color get() = pick(Color(0xFF3BF0B0), Color(0xFFB09AFF))
    val deltaBlue: Color get() = pick(Color(0xFF3BF0B0), Color(0xFFB9A2FF))
    val sleepBlue: Color get() = pick(Color(0xFF9FB4FF), Color(0xFF8F8DF2))
    val strainBlue: Color get() = pick(Color(0xFFFFC940), Color(0xFF9B7BFF))
    val heartBlue: Color get() = pick(Color(0xFF3BF0B0), Color(0xFFF0566B))
    val positive: Color get() = pick(Color(0xFF3BF0B0), Color(0xFF4FE3B0))
    val positiveBg: Color get() = pick(Color(0xFF133A2E), Color(0xFF1D3330))
    val warning: Color get() = pick(Color(0xFFFFC940), Color(0xFFFFB44A))
    val warningBg: Color get() = pick(Color(0xFF3A2F12), Color(0xFF3A2A15))
    val neutralBg: Color get() = pick(Color(0xFF1A2E28), Color(0xFF241E33))
    val neutralText: Color get() = pick(Color(0xFFA9B8B0), Color(0xFFA49DBC))
    val flame: Color get() = pick(Color(0xFFFF9F45), Color(0xFFFF9A4A))

    /** The look's main energy colour: jade (Sage) or violet (Conqueror). */
    val energy: Color get() = pick(Color(0xFF3BF0B0), Color(0xFF9B7BFF))

    /** The look's second accent: gold (Sage) or crimson (Conqueror). */
    val accent: Color get() = pick(Color(0xFFFFC940), Color(0xFFF0566B))

    /** The Kiai surge red, the same in both looks. */
    val surge = Color(0xFFFF4A3D)

    /** Fork-only: the Saħħa brand red (launcher ring, photo card). */
    val brandRed = Color(0xFFE5484D)
    val outlookWarm = Color(0xFF8B7B64)
    val outlookCool = Color(0xFF324A56)
    val tabBar: Color get() = pick(Color(0xFF0A1613), Color(0xFF17131F))
    val tabInactive: Color get() = pick(Color(0xFF8FA39A), Color(0xFF8A83A3))
    val coachCore: Color get() = pick(Color(0xFF0E2A22), Color(0xFF1C1530))
    val coachRimA: Color get() = pick(Color(0xFF3BF0B0), Color(0xFF9B7BFF))
    val coachRimB: Color get() = pick(Color(0xFFFFC940), Color(0xFFF0566B))
    val orbCore: Color get() = pick(Color(0xFF06120D), Color(0xFF0B0912))
    val orbBody: Color get() = pick(Color(0xFF1C6B47), Color(0xFF3C2A7A))
    val orbRim: Color get() = pick(Color(0xFFFFC940), Color(0xFFB9A2FF))
    val orbSpark: Color get() = pick(Color(0xFFFFE7A0), Color(0xFFE3D9FF))
    val orbMid: Color get() = pick(Color(0xFF0A2318), Color(0xFF1A1230))
    val orbEdge: Color get() = pick(Color(0xFF2FBF7E), Color(0xFF7B5CF0))

/** The twelve-step factor bar ramp, worst (left) to best (right), as sampled. */
    val factorRamp: List<Color> = listOf(
        Color(0xFFF59D1D), Color(0xFFE3962A), Color(0xFFCF8E33), Color(0xFFB4813A), Color(0xFF9C753A),
        Color(0xFF7F7A55), Color(0xFF5F8263), Color(0xFF3E8C6B), Color(0xFF1FA37C), Color(0xFF09BD8F),
        Color(0xFF12C995), Color(0xFF629E94),
    )

    val background: Brush get() = Brush.verticalGradient(
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
            // Fork-only (Saħħa): cards carry a hairline edge in the look's colours.
            .clip(shape).background(color, shape).border(1.dp, SlateUi.alertBorder, shape)
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
    SlateBackdrop {
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
