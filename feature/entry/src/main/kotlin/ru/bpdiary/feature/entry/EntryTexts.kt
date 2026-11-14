package ru.bpdiary.feature.entry

import ru.bpdiary.app.feature.entry.R
import ru.bpdiary.domain.Reason

/** Text of a validation error shown under the fields. */
internal fun Reason.textRes(): Int =
    when (this) {
        Reason.SYS_NOT_GREATER -> R.string.entry_error_sys_not_greater
        Reason.SYS_RANGE -> R.string.entry_error_sys_range
        Reason.DIA_RANGE -> R.string.entry_error_dia_range
        Reason.PULSE_RANGE -> R.string.entry_error_pulse_range
        Reason.PULSE_MISSING -> R.string.entry_error_pulse_missing
        Reason.FUTURE_TIME -> R.string.entry_error_future_time
    }
