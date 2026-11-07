package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SlateLookTest {
    private val before = LookState(
        appearance = AppearanceMode.SYSTEM,
        ringGauges = false,
        dayCycleBackground = true,
        cardOpacityPercent = 85,
        hiddenSections = listOf(TodaySection.JOURNAL, TodaySection.SYNTHESIS),
    )

    @Test
    fun appliedSetsTheBundleAndKeepsExistingHiddenSections() {
        val after = SlateLook.applied(before)
        assertEquals(AppearanceMode.DARK, after.appearance)
        assertEquals(true, after.ringGauges)
        assertEquals(false, after.dayCycleBackground)
        assertEquals(100, after.cardOpacityPercent)
        assertEquals(
            listOf(TodaySection.JOURNAL, TodaySection.SYNTHESIS, TodaySection.LIVE_SESSION, TodaySection.YOUR_CARDS),
            after.hiddenSections,
        )
    }

    @Test
    fun snapshotRoundTripsSoOffRestoresExactly() {
        assertEquals(before, LookState.fromJson(before.toJson()))
        assertNull(LookState.fromJson(null))
        assertNull(LookState.fromJson("{not json"))
    }

    @Test
    fun slateOnlyReplacesTheDarkTokens() {
        assertSame(LightTokens, SlateLook.tokensFor(dark = false, slate = true))
        assertSame(DarkTokens, SlateLook.tokensFor(dark = true, slate = false))
        assertSame(SlateTokens, SlateLook.tokensFor(dark = true, slate = true))
        // Data colour worlds are untouched: only surfaces/text move.
        assertEquals(DarkTokens.recovery100, SlateTokens.recovery100)
        assertEquals(DarkTokens.chargeColor, SlateTokens.chargeColor)
        assertEquals(DarkTokens.sleepDeep, SlateTokens.sleepDeep)
    }
}
