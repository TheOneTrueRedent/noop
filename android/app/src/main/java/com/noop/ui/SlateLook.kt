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

/** Fork-only (Saħħa): DarkTokens with the Conqueror surfaces (violet-black armour), used by the non-Slate
 *  screens (Settings, Sleep, Trends…) while the Conqueror look is on. */
val SlateTokens: PaletteTokens = DarkTokens.copy(
    surfaceBase = Color(0xFF0B0912),
    surfaceRaised = Color(0xFF17131F),
    surfaceOverlay = Color(0xFF1A1525),
    surfaceInset = Color(0xFF241E33),
    hairline = Color(0xFF2C2542),
    hairlineStrong = Color(0xFF3A3156),
    textPrimary = Color(0xFFF2EEFF),
    textSecondary = Color(0xFFA49DBC),
    textTertiary = Color(0xFF857E9F),
    scenicCenter = Color(0xFF1A1233),
    scenicEdge = Color(0xFF0B0912),
    cardFillTop = Color(0xFF1A1525),
    cardFillBottom = Color(0xFF17131F),
    heroFill = Color(0xFF15102A),
    heroBorder = Color(0x1A9B7BFF),
)

/** Fork-only (Saħħa): DarkTokens with the Sage surfaces (deep night-green), for the non-Slate screens
 *  while the Sage look is on. */
val SageTokens: PaletteTokens = DarkTokens.copy(
    surfaceBase = Color(0xFF07110F),
    surfaceRaised = Color(0xFF0F1E1A),
    surfaceOverlay = Color(0xFF10221D),
    surfaceInset = Color(0xFF1A2E28),
    hairline = Color(0xFF214037),
    hairlineStrong = Color(0xFF2C5245),
    textPrimary = Color(0xFFF3F1E6),
    textSecondary = Color(0xFFA9B8B0),
    textTertiary = Color(0xFF7F928A),
    scenicCenter = Color(0xFF0B3A2C),
    scenicEdge = Color(0xFF07110F),
    cardFillTop = Color(0xFF10221D),
    cardFillBottom = Color(0xFF0F1E1A),
    heroFill = Color(0xFF0B2A21),
    heroBorder = Color(0x1A3BF0B0),
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
            slate && SahhaLook.sage -> SageTokens
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
