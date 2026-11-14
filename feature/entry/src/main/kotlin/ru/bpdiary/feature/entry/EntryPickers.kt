package ru.bpdiary.feature.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.bpdiary.app.feature.entry.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Day of the measurement (V1.1, docs/05 §5.2); days after [today] cannot be chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickDialog(
    date: LocalDate,
    today: LocalDate,
    onEvent: (EntryEvent) -> Unit,
) {
    // The picker works with UTC midnights.
    val limit = today.plusDays(1).utcMillis()
    val picker =
        rememberDatePickerState(
            initialSelectedDateMillis = date.utcMillis(),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis < limit

                    override fun isSelectableYear(year: Int): Boolean = year <= today.year
                },
        )
    val chosen = picker.selectedDateMillis
    DatePickerDialog(
        onDismissRequest = { onEvent(EntryEvent.DialogDismissed) },
        confirmButton = {
            TextButton(
                enabled = chosen != null,
                onClick = { if (chosen != null) onEvent(EntryEvent.DateChosen(chosen.toUtcDay())) },
            ) {
                Text(stringResource(R.string.entry_pick_done))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(EntryEvent.DialogDismissed) }) {
                Text(stringResource(R.string.entry_pick_cancel))
            }
        },
    ) {
        DatePicker(state = picker, showModeToggle = false)
    }
}

/** Time of the measurement, typed with digits (easier than a dial for the 60+ audience). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickDialog(
    time: LocalTime,
    onEvent: (EntryEvent) -> Unit,
) {
    val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = { onEvent(EntryEvent.DialogDismissed) },
        confirmButton = {
            TextButton(onClick = { onEvent(EntryEvent.TimeChosen(LocalTime.of(picker.hour, picker.minute))) }) {
                Text(stringResource(R.string.entry_pick_done))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(EntryEvent.DialogDismissed) }) {
                Text(stringResource(R.string.entry_pick_cancel))
            }
        },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeInput(picker) }
        },
    )
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDay(): LocalDate = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()
