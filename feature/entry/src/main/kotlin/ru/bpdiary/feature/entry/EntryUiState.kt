package ru.bpdiary.feature.entry

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Field
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.domain.DayLabel
import ru.bpdiary.domain.Draft
import ru.bpdiary.domain.Key
import ru.bpdiary.domain.ValidationError
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
    val liveStatus: LiveStatus = LiveStatus.Empty,
    val error: ValidationError? = null,
    /** Field to highlight after Save on incomplete data. */
    val missing: Field? = null,
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

enum class EntryDialog { Discard, Delete }

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
