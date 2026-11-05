package com.noop.sahha

/**
 * Fork-only (Saħħa): the day's "form", named for the Shu-ha-ri stages of learning a martial art, plus
 * the Kiai surge. Pure: no Android, no clock, no I/O.
 *
 *  - SEIZA (sitting still): Charge below 34. Rest is the training.
 *  - SHU (follow the form): Charge 34–66.
 *  - HA (break the form): Charge 67–89, or 90+ when HRV is not above the person's own norm.
 *  - MUSHIN (no-mind): Charge 90+ AND last night's HRV above the person's norm (the mean of their
 *    recent nights, at least [MIN_BASELINE_NIGHTS] of them). Rare on purpose.
 *
 * The Kiai surge is separate from the form: it is on when the day's Effort has gone past the top of the
 * recovery-based target band (0–21 axis). It is a warning about tomorrow, not a reward.
 */
object SahhaForm {
    enum class Form { SEIZA, SHU, HA, MUSHIN }

    const val MIN_BASELINE_NIGHTS = 5

    fun of(charge: Double?, hrv: Double?, hrvHistory: List<Double>): Form? {
        val c = charge ?: return null
        return when {
            c < 34 -> Form.SEIZA
            c < 67 -> Form.SHU
            c < 90 -> Form.HA
            else -> {
                val norm = hrvHistory.takeIf { it.size >= MIN_BASELINE_NIGHTS }?.average()
                if (hrv != null && norm != null && hrv > norm) Form.MUSHIN else Form.HA
            }
        }
    }

    /** True when [effort21] is past [bandHigh], the top of today's Effort band. */
    fun surging(effort21: Double?, bandHigh: Int?): Boolean =
        effort21 != null && bandHigh != null && effort21 > bandHigh
}
