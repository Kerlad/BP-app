package ru.bpdiary.feature.entry

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Field
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.domain.DayLabel
import ru.bpdiary.domain.Draft
import ru.bpdiary.domain.Key
import ru.bpdiary.domain.ValidationError
import java.time.LocalDate
import java.time.LocalTime

data class EntryUiState(
    val isEdit: Boolean = false,
    /** Edit mode until the reading is read from the database. */
    val loading: Boolean = true,
    val draft: Draft = Draft(),
    val arm: Arm = Arm.LEFT,
    val tags: Set<Tag> = emptySet(),
    val comment: String = "",
    val dayLabel: DayLabel = DayLabel.Today,
    val time: LocalTime = LocalTime.MIDNIGHT,
    /** Day of the measurement in its own offset; the date picker opens on it. */
    val date: LocalDate = LocalDate.ofEpochDay(0),
    /** The last day the date picker allows (no future readings, docs/05 §5.2). */
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val liveStatus: LiveStatus = LiveStatus.Empty,
    val error: ValidationError? = null,
    /** Field to highlight after Save on incomplete data. */
    val missing: Field? = null,
    /** The storage refused the last save or delete (review A-03). */
    val saveFailed: Boolean = false,
    val canSave: Boolean = false,
    val dialog: EntryDialog? = null,
)

sealed interface LiveStatus {
    /** Fewer than 2 digits in SYS or DIA: «Введите значения». */
    data object Empty : LiveStatus

    data class Classified(
        val status: Status,
        val hint: Hint,
    ) : LiveStatus
}

sealed interface Hint {
    /** «выше 135/85» */
    data class Above(
        val sys: Int,
        val dia: Int,
    ) : Hint

    /** «ниже 100/60» */
    data class Below(
        val sys: Int,
        val dia: Int,
    ) : Hint

    /** «в пределах ваших уровней» */
    data object Within : Hint
}

enum class EntryDialog { Discard, Delete, PickDate, PickTime }

sealed interface EntryEvent {
    data class KeyPressed(
        val key: Key,
    ) : EntryEvent

    data class FieldTapped(
        val field: Field,
    ) : EntryEvent

    data class ArmSelected(
        val arm: Arm,
    ) : EntryEvent

    data class TagToggled(
        val tag: Tag,
    ) : EntryEvent

    data class CommentChanged(
        val text: String,
    ) : EntryEvent

    data object SaveClicked : EntryEvent

    /** The close button and system back. */
    data object CloseClicked : EntryEvent

    data object DiscardConfirmed : EntryEvent

    data object DeleteClicked : EntryEvent

    data object DeleteConfirmed : EntryEvent

    data object DialogDismissed : EntryEvent

    /** The date and time pickers of the «Сегодня, 09:12» chip (V1.1, docs/05 §5.2). */
    sealed interface Picker : EntryEvent

    /** Opens the date picker, then the time picker. */
    data object TimeClicked : Picker

    data class DateChosen(
        val date: LocalDate,
    ) : Picker

    data class TimeChosen(
        val time: LocalTime,
    ) : Picker
}

sealed interface EntryEffect {
    /** [critical]: show the D-06 hint once for this reading. */
    data class Saved(
        val readingId: Long,
        val edited: Boolean,
        val critical: Boolean,
    ) : EntryEffect

    data class Deleted(
        val readingId: Long,
    ) : EntryEffect

    data object Closed : EntryEffect
}
