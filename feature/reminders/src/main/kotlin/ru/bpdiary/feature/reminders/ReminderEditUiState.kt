package ru.bpdiary.feature.reminders

import ru.bpdiary.domain.DaysSummary
import ru.bpdiary.domain.daysSummary
import java.time.DayOfWeek
import java.time.LocalTime

/** A reminder written down in the name field is cut to this many characters; the notification title stays short. */
internal const val LABEL_MAX_LENGTH = 40

private val NEW_REMINDER_TIME: LocalTime = LocalTime.NOON

data class ReminderEditUiState(
    /** False until an existing reminder has been read; the time picker needs its initial value. */
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val time: LocalTime = NEW_REMINDER_TIME,
    val label: String = "",
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val confirmDelete: Boolean = false,
) {
    /** An empty set of days is not allowed: «Готово» stays inactive (docs/05 §5.5). */
    val canSave: Boolean get() = days.isNotEmpty()

    /** The preset the days match, for the chip highlight; null for any other selection. */
    val preset: DaysSummary? get() = daysSummary(days).takeUnless { it is DaysSummary.Listed }
}

sealed interface ReminderEditEvent {
    data class TimeChanged(
        val time: LocalTime,
    ) : ReminderEditEvent

    data class LabelChanged(
        val label: String,
    ) : ReminderEditEvent

    data class DayToggled(
        val day: DayOfWeek,
    ) : ReminderEditEvent

    /** «Ежедневно» or «Пн – Пт». */
    data class PresetSelected(
        val preset: DaysSummary,
    ) : ReminderEditEvent

    /** [defaultLabel] is saved when the name field was left empty. */
    data class DoneClicked(
        val defaultLabel: String,
    ) : ReminderEditEvent

    data object DeleteClicked : ReminderEditEvent

    data object DeleteConfirmed : ReminderEditEvent

    data object DialogDismissed : ReminderEditEvent

    data object CloseClicked : ReminderEditEvent
}

sealed interface ReminderEditEffect {
    data object Closed : ReminderEditEffect

    /** The reminder could not be stored (review A-03); the app shows «Не удалось сохранить…». */
    data object SaveFailed : ReminderEditEffect
}
