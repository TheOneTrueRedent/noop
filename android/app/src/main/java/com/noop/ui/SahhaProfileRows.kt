package com.noop.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.noop.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Fork-only (Saħħa): Settings › Profile › Date of birth. Writes [ProfileStore.dateOfBirthMillis] as local
 * midnight of the chosen day, so [ProfileStore.exactAge] (and Fitness Age) agree with Home Assistant.
 * The whole-years Age stepper above it keeps working and simply re-anchors this date.
 */
@Composable
internal fun SahhaDateOfBirthRow(profile: ProfileStore, onChanged: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val dob = Instant.ofEpochMilli(profile.dateOfBirthMillis).atZone(zone).toLocalDate()
    val shown = dob.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    SettingsFormRow(label = uiString(R.string.sahha_date_of_birth)) {
        Text(
            shown,
            style = NoopType.body,
            color = Palette.accent,
            modifier = Modifier.clickable {
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        profile.dateOfBirthMillis = LocalDate.of(y, m + 1, d).atStartOfDay(zone).toInstant().toEpochMilli()
                        onChanged()
                    },
                    dob.year, dob.monthValue - 1, dob.dayOfMonth,
                ).apply {
                    // The profile clamps age to 13..100; keep the picker inside the same window.
                    val today = LocalDate.now(zone)
                    datePicker.maxDate = today.minusYears(13).atStartOfDay(zone).toInstant().toEpochMilli()
                    datePicker.minDate = today.minusYears(100).atStartOfDay(zone).toInstant().toEpochMilli()
                }.show()
            },
        )
    }
}
