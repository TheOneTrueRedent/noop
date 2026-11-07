package com.noop.sahha

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fork-only: [ExactAge] must equal the Home Assistant integration's `vitals.exact_age` to the day. */
class ExactAgeTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun matchesHomeAssistantReference() {
        // vitals.exact_age(date(1987,11,18), date(2026,11,1)) == 38 + 348/365
        assertEquals(38 + 348.0 / 365, ExactAge.years(d("1987-11-18"), d("2026-11-01")), 1e-12)
        assertEquals(39.0, ExactAge.years(d("1987-11-18"), d("2026-11-18")), 1e-12)
        assertEquals(35 + 90.0 / 365, ExactAge.years(d("1991-08-07"), d("2026-11-05")), 1e-12)
    }

    @Test fun leapDayBirthdayFallsOnTheTwentyEighth() {
        // 2027 is common: the birthday is 28 Feb 2027, the next 29 Feb 2028 (366 days on).
        assertEquals(27.0, ExactAge.years(d("2000-02-29"), d("2027-02-28")), 1e-12)
        assertEquals(27 + 1.0 / 366, ExactAge.years(d("2000-02-29"), d("2027-03-01")), 1e-12)
    }

    @Test fun beforeBirthIsZero() {
        assertEquals(0.0, ExactAge.years(d("2026-01-02"), d("2026-01-01")), 0.0)
    }
}
