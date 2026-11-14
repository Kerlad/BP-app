package ru.bpdiary.feature.home

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Status
import ru.bpdiary.domain.DayLabel
import ru.bpdiary.domain.GreetingSlot
import ru.bpdiary.domain.NextReminderLabel
import ru.bpdiary.domain.WeekBars
import java.time.LocalDate
import java.time.LocalTime

/** Texts are made by [HomeTexts] from these values, so the state stays free of resources. */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        /** In the current device zone: the date header and «Сег» in the week card. */
        val today: LocalDate,
        val greeting: GreetingSlot,
        /** Null: no readings at all, the welcome card is shown instead. */
        val last: LastReadingUi?,
        /** Null only when there are no readings at all (D-24). */
        val week: WeekBars?,
        /** Null: every reminder is off (D-23). */
        val nextReminder: NextReminderLabel?,
        /** A reminder is on but the system does not allow notifications: it would never ring (ROADMAP B-04). */
        val notificationsBanner: Boolean = false,
    ) : HomeUiState
}

data class LastReadingUi(
    /** Opens the reading for editing, like a Diary row. */
    val id: Long,
    /** «Сегодня», «Вчера» or «18 октября»; the day and [time] are in the offset of the measurement. */
    val dayLabel: DayLabel,
    val time: LocalTime,
    val sys: Int,
    val dia: Int,
    /** Null only for imported readings: then neither the value nor the heart is shown (docs/06 §6.8). */
    val pulse: Int?,
    val pulseOutOfRange: Boolean,
    val status: Status,
    val arm: Arm,
)

sealed interface HomeEvent {
    data object AddClicked : HomeEvent

    data object LevelsClicked : HomeEvent

    data object SettingsClicked : HomeEvent

    data class LastReadingClicked(
        val readingId: Long,
    ) : HomeEvent

    data object WeekClicked : HomeEvent

    data object ReminderClicked : HomeEvent
}
