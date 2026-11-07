package com.noop.sahha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SahhaFormTest {
    private val norm = List(7) { 40.0 }

    @Test fun formsFollowChargeBands() {
        assertNull(SahhaForm.of(null, 50.0, norm))
        assertEquals(SahhaForm.Form.SEIZA, SahhaForm.of(33.9, 50.0, norm))
        assertEquals(SahhaForm.Form.SHU, SahhaForm.of(34.0, 50.0, norm))
        assertEquals(SahhaForm.Form.SHU, SahhaForm.of(66.9, 50.0, norm))
        assertEquals(SahhaForm.Form.HA, SahhaForm.of(67.0, 50.0, norm))
        assertEquals(SahhaForm.Form.HA, SahhaForm.of(89.9, 50.0, norm))
    }

    @Test fun mushinNeedsHrvAboveAnEstablishedNorm() {
        assertEquals(SahhaForm.Form.MUSHIN, SahhaForm.of(92.0, 41.0, norm))
        assertEquals(SahhaForm.Form.HA, SahhaForm.of(92.0, 40.0, norm))
        assertEquals(SahhaForm.Form.HA, SahhaForm.of(92.0, null, norm))
        assertEquals(SahhaForm.Form.HA, SahhaForm.of(92.0, 60.0, List(4) { 40.0 }))
    }

    @Test fun surgeIsPastTheTopOfTheBand() {
        assertTrue(SahhaForm.surging(14.1, 14))
        assertFalse(SahhaForm.surging(14.0, 14))
        assertFalse(SahhaForm.surging(null, 14))
        assertFalse(SahhaForm.surging(20.0, null))
    }
}
