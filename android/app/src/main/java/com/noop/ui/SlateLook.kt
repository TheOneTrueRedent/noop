package com.noop.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

// MARK: - Slate look (fork-only)
//
// A one-switch "calm" look: a flat blue-slate dark canvas, solid cards, ring gauges, no day-cycle
// scene, and a trimmed Today (Synthesis, Start-session and Your Cards hidden). It is pure
// orchestration over prefs that already exist plus one extra token set, so no metric, stored value or
// backup key changes. Turning it on snapshots the prefs it touches; turning it off restores them
// exactly, so it is fully switchable. Data colour worlds (recovery / strain / sleep / zones) are the
// DarkTokens ones untouched — only the surfaces and chrome text move.

/** DarkTokens with the Slate reference surfaces (neutral charcoal cards on a near-black canvas, sampled
 *  from the fork owner's screenshots). Only surface / hairline / text / hero tokens differ. */
val SlateTokens: PaletteTokens = DarkTokens.copy(
    surfaceBase = Color(0xFF141A1F),
    surfaceRaised = Color(0xFF2D3337),
    surfaceOverlay = Color(0xFF252F36),
    surfaceInset = Color(0xFF393F43),
    hairline = Color(0xFF353C41),
    hairlineStrong = Color(0xFF41474B),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB0B5BA),
    textTertiary = Color(0xFF7E868C),
    scenicCenter = Color(0xFF1E262B),
    scenicEdge = Color(0xFF141A1F),
    cardFillTop = Color(0xFF2F3539),
    cardFillBottom = Color(0xFF2A3034),
    heroFill = Color(0xFF1E262B),
    heroBorder = Color(0x14FFFFFF),
)

/** The prefs the Slate look sets, captured so switching it off puts every one back. */
data class LookState(
    val appearance: AppearanceMode,
    val ringGauges: Boolean,
    val dayCycleBackground: Boolean,
    val cardOpacityPercent: Int,
    val hiddenSections: List<TodaySection>,
) {
    fun toJson(): String = JSONObject()
        .put("appearance", appearance.storageValue)
        .put("ringGauges", ringGauges)
        .put("dayCycleBackground", dayCycleBackground)
        .put("cardOpacityPercent", cardOpacityPercent)
        .put("hiddenSections", JSONArray(hiddenSections.map { it.raw }))
        .toString()

    companion object {
        fun fromJson(raw: String?): LookState? = runCatching {
            val o = JSONObject(raw ?: return null)
            val hidden = o.getJSONArray("hiddenSections")
            LookState(
                appearance = AppearanceMode.fromStorage(o.getString("appearance")),
                ringGauges = o.getBoolean("ringGauges"),
                dayCycleBackground = o.getBoolean("dayCycleBackground"),
                cardOpacityPercent = o.getInt("cardOpacityPercent"),
                hiddenSections = (0 until hidden.length()).mapNotNull { TodaySection.fromRaw(hidden.getString(it)) },
            )
        }.getOrNull()
    }
}

object SlateLook {
    private const val KEY_ENABLED = "fork.slateLook.enabled"
    private const val KEY_SAVED = "fork.slateLook.saved"

    /** Today sections the look hides. Everything stays reorderable / re-showable in Customise. */
    val trimmedSections: List<TodaySection> =
        listOf(TodaySection.SYNTHESIS, TodaySection.LIVE_SESSION, TodaySection.YOUR_CARDS)

    var enabled by mutableStateOf(false)
        private set

    /** What the look turns [current] into. Pure, so the bundle is pinned by a JVM test. */
    fun applied(current: LookState): LookState = current.copy(
        appearance = AppearanceMode.DARK,
        ringGauges = true,
        dayCycleBackground = false,
        cardOpacityPercent = 100,
        hiddenSections = (current.hiddenSections + trimmedSections).distinct(),
    )

    /** Token set for the current scheme: Slate replaces Dark while the look is on; Light is never touched. */
    fun tokensFor(dark: Boolean, slate: Boolean = enabled): PaletteTokens =
        when {
            !dark -> LightTokens
            slate -> SlateTokens
            else -> DarkTokens
        }

    fun load(ctx: Context) {
        enabled = NoopPrefs.of(ctx).getBoolean(KEY_ENABLED, false)
    }

    private fun read(ctx: Context) = LookState(
        appearance = AppearancePrefs.mode,
        ringGauges = NoopPrefs.todayRingGauges(ctx),
        dayCycleBackground = NoopPrefs.showDayCycleBackground(ctx),
        cardOpacityPercent = NoopPrefs.cardOpacityPercent(ctx),
        hiddenSections = TodayLayoutPrefs.hidden(ctx),
    )

    private fun write(ctx: Context, s: LookState) {
        AppearancePrefs.set(ctx, s.appearance)
        NoopPrefs.setTodayRingGauges(ctx, s.ringGauges)
        NoopPrefs.setShowDayCycleBackground(ctx, s.dayCycleBackground)
        NoopPrefs.setCardOpacityPercent(ctx, s.cardOpacityPercent)
        CardAppearance.opacity = s.cardOpacityPercent / 100f
        TodayLayoutPrefs.setHidden(ctx, s.hiddenSections)
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        if (on == enabled) return
        val prefs = NoopPrefs.of(ctx)
        if (on) {
            val now = read(ctx)
            prefs.edit().putString(KEY_SAVED, now.toJson()).putBoolean(KEY_ENABLED, true).apply()
            write(ctx, applied(now))
        } else {
            LookState.fromJson(prefs.getString(KEY_SAVED, null))?.let { write(ctx, it) }
            prefs.edit().remove(KEY_SAVED).putBoolean(KEY_ENABLED, false).apply()
        }
        enabled = on
    }
}
