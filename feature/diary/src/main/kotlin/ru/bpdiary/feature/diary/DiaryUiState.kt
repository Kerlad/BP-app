package ru.bpdiary.feature.diary

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import java.time.LocalDate
import java.time.LocalTime

enum class DiaryFilter(
    val status: Status?,
) {
    ALL(null),
    HIGH(Status.HIGH),
    NORMAL(Status.NORMAL),
    LOW(Status.LOW),
}

/** The rows themselves come separately as `PagingData<DiaryItem>` (docs/05 §5.3). */
data class DiaryUiState(
    val filter: DiaryFilter = DiaryFilter.ALL,
    /** In the current device zone; headers compare the day of each reading with it. */
    val today: LocalDate = LocalDate.MIN,
    /** All live readings regardless of the filter; null until counted. 0 shows «Дневник пуст». */
    val total: Int? = null,
)

sealed interface DiaryItem {
    val key: String

    data class Header(
        val day: LocalDate,
    ) : DiaryItem {
        override val key: String get() = "day-$day"
    }

    data class Row(
        val row: DiaryRow,
        /** Local day of the measurement, in its own offset. */
        val day: LocalDate,
    ) : DiaryItem {
        override val key: String get() = "reading-${row.id}"
    }
}

/** Texts (time, note) are made by [DiaryTexts]. */
data class DiaryRow(
    val id: Long,
    /** In the offset of the measurement. */
    val time: LocalTime,
    val sys: Int,
    val dia: Int,
    val pulse: Int?,
    val status: Status,
    val pulseOutOfRange: Boolean,
    val arm: Arm,
    val tags: Set<Tag>,
    val comment: String,
)

sealed interface DiaryEvent {
    data class FilterSelected(
        val filter: DiaryFilter,
    ) : DiaryEvent

    /** Tap or «Изменить»: edit the reading (D-10). */
    data class RowClicked(
        val id: Long,
    ) : DiaryEvent

    /** Swipe or «Удалить»: soft delete with undo. */
    data class DeleteClicked(
        val id: Long,
    ) : DiaryEvent

    data class CopyClicked(
        val row: DiaryRow,
    ) : DiaryEvent

    data object AddClicked : DiaryEvent
}

sealed interface DiaryEffect {
    data class Deleted(
        val id: Long,
    ) : DiaryEffect
}
