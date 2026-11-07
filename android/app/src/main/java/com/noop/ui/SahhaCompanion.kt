package com.noop.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.R
import com.noop.push.SelfHostedPushSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// MARK: - The Home Assistant companion: caffeine and Marishi (fork-only, Saħħa)
//
// Both talk to the household's NOOP Push Receiver (v0.5+) at `<push endpoint>/companion`, with the same
// endpoint and bearer token the push already uses: nothing new to configure in the app. GET returns
// today's caffeine from HA's helpers; POST sends a question plus a short summary of the person's own
// numbers to the conversation agent chosen in the receiver's options, and returns its answer. Without push
// configured, or with no agent chosen, each says so and does nothing else.

/** Caffeine as the receiver reports it. */
internal data class SahhaCaffeine(val todayMg: Double?, val drinks: Int?, val lastLocalTime: String?)

internal sealed class MarishiReply {
    data class Answer(val text: String) : MarishiReply()
    object NotConfigured : MarishiReply()
    object NoAgent : MarishiReply()
    object Failed : MarishiReply()
}

internal object SahhaCompanion {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(100, TimeUnit.SECONDS).build()
    }

    private fun target(ctx: Context): Pair<String, String>? {
        val s = SelfHostedPushSettings.from(ctx)
        val endpoint = s.enabledEndpoint()?.url ?: return null
        val token = s.token() ?: return null
        return endpoint.trimEnd('/') + "/companion" to token
    }

    fun configured(ctx: Context): Boolean = runCatching { target(ctx) != null }.getOrDefault(false)

    suspend fun caffeine(ctx: Context): SahhaCaffeine? = withContext(Dispatchers.IO) {
        runCatching {
            val (url, token) = target(ctx) ?: return@runCatching null
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val c = JSONObject(r.body?.string().orEmpty()).optJSONObject("caffeine") ?: return@use null
                val last = c.optString("last").takeIf { it.isNotBlank() && it != "null" }?.let {
                    runCatching { OffsetDateTime.parse(it).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
                }
                SahhaCaffeine(
                    todayMg = if (c.isNull("today_mg")) null else c.optDouble("today_mg"),
                    drinks = if (c.isNull("drinks_today")) null else c.optDouble("drinks_today").roundToInt(),
                    lastLocalTime = last,
                )
            }
        }.getOrNull()
    }

    suspend fun ask(ctx: Context, question: String, context: String): MarishiReply = withContext(Dispatchers.IO) {
        val (url, token) = runCatching { target(ctx) }.getOrNull() ?: return@withContext MarishiReply.NotConfigured
        runCatching {
            val body = JSONObject().put("question", question).put("context", context).toString().toRequestBody(json)
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").post(body).build()
            client.newCall(req).execute().use { r ->
                when {
                    r.code == 409 -> MarishiReply.NoAgent
                    !r.isSuccessful -> MarishiReply.Failed
                    else -> JSONObject(r.body?.string().orEmpty()).optString("answer").takeIf { it.isNotBlank() }
                        ?.let { MarishiReply.Answer(it) } ?: MarishiReply.Failed
                }
            }
        }.getOrDefault(MarishiReply.Failed)
    }
}

/** Home's caffeine line, from Home Assistant. Hidden when push is not set up or HA has no caffeine helper. */
@Composable
internal fun SahhaCaffeineCard() {
    val context = LocalContext.current
    var c by remember { mutableStateOf<SahhaCaffeine?>(null) }
    LaunchedEffect(Unit) { if (SahhaCompanion.configured(context)) c = SahhaCompanion.caffeine(context) }
    val caf = c ?: return
    val mg = caf.todayMg ?: return
    SlateSurface(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiString(R.string.sahha_caffeine).uppercase(), style = SlateType.labelCaps, color = SlateUi.textSecondary)
                Text(
                    listOfNotNull(
                        caf.drinks?.let { uiPlural(R.plurals.sahha_caffeine_drinks, it, it) },
                        caf.lastLocalTime?.let { uiString(R.string.sahha_caffeine_last, it) },
                    ).joinToString(" · "),
                    style = SlateType.bodySmall, color = SlateUi.textSecondary,
                )
            }
            Text(uiString(R.string.sahha_caffeine_mg, mg.roundToInt()), style = SlateType.value, color = SlateUi.textPrimary)
        }
    }
}

const val SAHHA_MARISHI_ROUTE = "sahha_marishi"

private data class MarishiLine(val fromUser: Boolean, val text: String)

/** A short plain-text summary of the person's own last 7 days, sent with each question. */
private fun marishiContext(vm: AppViewModel, ctx: Context): String {
    val days = vm.recentDays.value.takeLast(7)
    val p = ProfileStore.from(ctx)
    val lines = days.map { d ->
        listOfNotNull(
            d.day,
            d.recovery?.let { "charge ${it.roundToInt()}%" },
            d.strain?.let { "effort " + String.format(Locale.US, "%.1f", UnitFormatter.effortValue(it, EffortScale.WHOOP)) + "/21" },
            d.totalSleepMin?.let { "sleep " + hoursMinutes(it) },
            d.restingHr?.let { "resting HR $it" },
            d.avgHrv?.let { "HRV ${it.roundToInt()} ms" },
            d.steps?.let { "steps $it" },
        ).joinToString(", ")
    }
    return "Person: age " + String.format(Locale.US, "%.1f", p.exactAge) + ", " + p.sex + ".\nLast 7 days (newest last):\n" + lines.joinToString("\n")
}

@Composable
fun SahhaMarishiScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chat = remember { mutableStateListOf<MarishiLine>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val notConfigured = uiString(R.string.sahha_marishi_not_configured)
    val noAgent = uiString(R.string.sahha_marishi_no_agent)
    val failed = uiString(R.string.sahha_marishi_failed)
    fun send() {
        val q = input.trim()
        if (q.isEmpty() || busy) return
        chat += MarishiLine(true, q)
        input = ""
        busy = true
        scope.launch {
            val reply = SahhaCompanion.ask(context, q, marishiContext(vm, context))
            chat += MarishiLine(false, when (reply) {
                is MarishiReply.Answer -> reply.text
                MarishiReply.NotConfigured -> notConfigured
                MarishiReply.NoAgent -> noAgent
                MarishiReply.Failed -> failed
            })
            busy = false
        }
    }
    SlateScreen(title = uiString(R.string.sahha_marishi).uppercase(), onBack = onBack) {
        Text(uiString(R.string.sahha_marishi_intro), style = SlateType.body, color = SlateUi.textSecondary)
        chat.forEach { line ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                val shape = RoundedCornerShape(18.dp)
                Text(
                    line.text, style = SlateType.body, color = SlateUi.textPrimary,
                    modifier = Modifier.widthIn(max = 300.dp).clip(shape)
                        .background(if (line.fromUser) SlateUi.energy.copy(alpha = 0.18f) else SlateUi.card, shape)
                        .border(1.dp, SlateUi.alertBorder, shape).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        if (busy) Text(uiString(R.string.sahha_marishi_thinking), style = SlateType.bodySmall, color = SlateUi.textTertiary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(uiString(R.string.sahha_marishi_hint), style = SlateType.body, color = SlateUi.textTertiary) },
                textStyle = SlateType.body.copy(color = SlateUi.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SlateUi.energy, unfocusedBorderColor = SlateUi.alertBorder, cursorColor = SlateUi.energy,
                ),
            )
            IconButton(onClick = { send() }, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = uiString(R.string.sahha_marishi_send),
                    tint = SlateUi.energy, modifier = Modifier.size(26.dp))
            }
        }
    }
}
