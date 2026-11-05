package com.noop.sahha

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Fork-only (Saħħa): age in years to the day, the same definition the Home Assistant integration uses
 * (`vitals.exact_age`): whole years plus the share of the current birthday-year already elapsed. A
 * 29 February birthday falls on 28 February in common years (java.time `plusYears` does the same).
 * 18 Nov 1987 on 1 Nov 2026 → 38 + 348/365 = 38.953….
 */
object ExactAge {
    fun years(birth: LocalDate, today: LocalDate): Double {
        if (today.isBefore(birth)) return 0.0
        val whole = ChronoUnit.YEARS.between(birth, today)
        val last = birth.plusYears(whole)
        val next = birth.plusYears(whole + 1)
        return whole + ChronoUnit.DAYS.between(last, today).toDouble() / ChronoUnit.DAYS.between(last, next)
    }
}
