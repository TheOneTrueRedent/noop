package com.noop.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.noop.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * Fork-only (Saħħa): the photo card. The day's Rest, Charge and Effort (plus resting HR, HRV and steps
 * when known) drawn over a photo the user takes or picks, as a 1080×1350 PNG to share. The camera is the
 * system camera app, opened only when the user taps Take photo, so the app needs no camera permission;
 * the photo stays in the app's cache and is only shared when the user taps Share. With no photo the card
 * is drawn on the Slate background.
 */
data class SlatePhotoStats(
    val date: String,
    val rest: Int?,
    val charge: Int?,
    val effort: String?,
    val effortFraction: Double,
    val restingHr: Int?,
    val hrv: Int?,
    val steps: Int?,
)

/** The text the renderer draws, resolved by the caller so the canvas code stays locale-free. */
private data class PhotoCardLabels(
    val brand: String, val edition: String, val rest: String, val charge: String, val effort: String,
    val footer: String,
)

private const val CARD_W = 1080
private const val CARD_H = 1350
private const val PHOTO_MAX_SIDE = 2160

/** Decode [uri] no larger than about [PHOTO_MAX_SIDE] on its long side, upright per its EXIF tag. */
private fun decodePhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_SIDE) sample *= 2
    val bmp = cr.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val degrees = cr.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    if (degrees == 0f) bmp else
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
}.getOrNull()

private fun renderPhotoCard(context: Context, photo: Bitmap?, s: SlatePhotoStats, l: PhotoCardLabels): Bitmap {
    val out = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val textBold = ResourcesCompat.getFont(context, R.font.slate_text_bold) ?: Typeface.DEFAULT_BOLD
    val textSemi = ResourcesCompat.getFont(context, R.font.slate_text_semibold) ?: Typeface.DEFAULT
    val numBold = ResourcesCompat.getFont(context, R.font.slate_num_bold) ?: Typeface.DEFAULT_BOLD
    val w = CARD_W.toFloat()
    val h = CARD_H.toFloat()

    if (photo != null) {
        // Centre-crop the photo to the card.
        val scale = maxOf(w / photo.width, h / photo.height)
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate((w - photo.width * scale) / 2f, (h - photo.height * scale) / 2f)
        }
        c.drawBitmap(photo, m, Paint(Paint.FILTER_BITMAP_FLAG))
    } else {
        c.drawRect(0f, 0f, w, h, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(SlateUi.bgTop.toArgb(), SlateUi.bgMid.toArgb(), SlateUi.bgBottom.toArgb()),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        })
    }
    // Scrims so white text reads on any photo: a light one at the top, a deep one under the dials.
    val black = android.graphics.Color.BLACK
    c.drawRect(0f, 0f, w, 300f, Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, 300f, (0x99 shl 24) or (black and 0xFFFFFF), 0, Shader.TileMode.CLAMP)
    })
    c.drawRect(0f, h * 0.48f, w, h, Paint().apply {
        shader = LinearGradient(0f, h * 0.48f, 0f, h, 0, (0xE6 shl 24) or (black and 0xFFFFFF), Shader.TileMode.CLAMP)
    })

    val white = SlateUi.textPrimary.toArgb()
    val secondary = SlateUi.textSecondary.toArgb()
    val pad = 72f
    // Wordmark and edition, top left; date, top right.
    val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = textBold; textSize = 60f; color = white; letterSpacing = 0.12f }
    c.drawText(l.brand.uppercase(), pad, pad + 52f, brand)
    val edition = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = textBold; textSize = 24f; color = SlateUi.brandRed.toArgb(); letterSpacing = 0.24f
    }
    c.drawText(l.edition.uppercase(), pad + 4f, pad + 92f, edition)
    val date = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = textSemi; textSize = 34f; color = white; textAlign = Paint.Align.RIGHT
    }
    c.drawText(s.date, w - pad, pad + 48f, date)

    // The three dials, Rest / Charge / Effort, in the Slate colours.
    val dials = listOf(
        Triple(l.rest, s.rest?.let { slatePercentText(it) }, Pair((s.rest ?: 0) / 100.0, SlateUi.sleepBlue.toArgb())),
        Triple(l.charge, s.charge?.let { slatePercentText(it) }, Pair((s.charge ?: 0) / 100.0, SlateUi.positive.toArgb())),
        Triple(l.effort, s.effort, Pair(s.effortFraction, SlateUi.strainBlue.toArgb())),
    )
    val d = 250f
    val stroke = 22f
    val cy = h - 420f
    val slot = (w - 2 * pad) / 3f
    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; color = (0x40 shl 24) or 0xFFFFFF
    }
    val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = numBold; textSize = 76f; color = white; textAlign = Paint.Align.CENTER }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = textBold; textSize = 28f; color = white; textAlign = Paint.Align.CENTER; letterSpacing = 0.16f
    }
    dials.forEachIndexed { i, (name, text, ring) ->
        val cx = pad + slot * i + slot / 2f
        val r = RectF(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f)
        c.drawOval(r, track)
        val frac = ring.first.coerceIn(0.0, 1.0).toFloat()
        if (text != null && frac > 0f) {
            arc.color = ring.second
            c.drawArc(r, -90f, 360f * frac, false, arc)
        }
        c.drawText(text ?: "—", cx, cy + value.textSize * 0.35f, value)
        c.drawText(name.uppercase(), cx, cy + d / 2f + 64f, label)
    }
    if (l.footer.isNotBlank()) {
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = textSemi; textSize = 32f; color = secondary; textAlign = Paint.Align.CENTER
        }
        c.drawText(l.footer, w / 2f, h - 110f, foot)
    }
    return out
}

@Composable
fun SlatePhotoCardDialog(stats: SlatePhotoStats, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var card by remember { mutableStateOf<Bitmap?>(null) }
    val labels = PhotoCardLabels(
        brand = uiString(R.string.brand_name),
        edition = uiString(R.string.brand_edition),
        rest = uiString(R.string.trends_rest),
        charge = uiString(R.string.trends_charge),
        effort = uiString(R.string.trends_effort),
        footer = listOfNotNull(
            stats.restingHr?.let { uiString(R.string.sahha_photo_rhr, it) },
            stats.hrv?.let { uiString(R.string.sahha_photo_hrv, it) },
            stats.steps?.let { uiString(R.string.sahha_photo_steps, String.format(java.util.Locale.getDefault(), "%,d", it)) },
        ).joinToString("  ·  "),
    )
    LaunchedEffect(photo, stats) {
        card = withContext(Dispatchers.Default) { renderPhotoCard(context, photo, stats, labels) }
    }
    val load: (Uri?) -> Unit = { uri ->
        if (uri != null) scope.launch { photo = withContext(Dispatchers.IO) { decodePhoto(context, uri) } ?: photo }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { load(it) }
    val shotUri = remember {
        val f = File(File(context.cacheDir, "photos").apply { mkdirs() }, "photo-card-shot.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) load(shotUri) }
    val failedShare = uiString(R.string.sahha_photo_share_failed)
    val shareTitle = uiString(R.string.sahha_photo_card)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(SlateUi.bgBottom).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiString(R.string.sahha_photo_card).uppercase(), style = SlateType.screenTitle,
                    color = SlateUi.textPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = uiString(R.string.slate_close), tint = SlateUi.textPrimary)
                }
            }
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                card?.let {
                    Image(it.asImageBitmap(), contentDescription = uiString(R.string.sahha_photo_card),
                        modifier = Modifier.aspectRatio(CARD_W / CARD_H.toFloat()).clip(RoundedCornerShape(18.dp)))
                }
            }
            Text(uiString(R.string.sahha_photo_privacy), style = SlateType.bodySmall, color = SlateUi.textTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SlateActionButton(Icons.Filled.PhotoCamera, uiString(R.string.sahha_photo_take),
                    { runCatching { take.launch(shotUri) } }, Modifier.weight(1f))
                SlateActionButton(Icons.Filled.PhotoLibrary, uiString(R.string.sahha_photo_choose),
                    { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    Modifier.weight(1f))
            }
            SlateActionButton(Icons.Filled.Share, uiString(R.string.sahha_photo_share), {
                val bmp = card ?: return@SlateActionButton
                scope.launch {
                    runCatching {
                        val file = withContext(Dispatchers.IO) {
                            File(File(context.cacheDir, "recaps").apply { mkdirs() }, "sahha-photo-card.png").apply {
                                outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    }.onFailure { Toast.makeText(context, failedShare, Toast.LENGTH_LONG).show() }
                }
            }, Modifier.fillMaxWidth())
        }
    }
}

/** Effort fraction for the photo card ring, clamped like the home dial. */
internal fun slatePhotoEffortFraction(effort: Double?, effortMax: Double): Double =
    if (effort == null || effortMax <= 0.0) 0.0 else min(1.0, effort / effortMax)
