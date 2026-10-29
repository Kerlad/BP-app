package ru.bpdiary.feature.reminders

import ru.bpdiary.core.model.Reminder
import ru.bpdiary.domain.DaysSummary
import ru.bpdiary.domain.daysSummary
import java.time.LocalTime

data class ReminderItem(
    val id: Long,
    val time: LocalTime,
    val label: String,
    val days: DaysSummary,
    val enabled: Boolean,
)

internal fun Reminder.toItem() = ReminderItem(id, time, label, daysSummary(days), enabled)

data class RemindersUiState(
    /** Null until the first load, so an empty list is not shown for a moment. Sorted by time (docs/05 §5.5). */
    val items: List<ReminderItem>? = null,
    val notificationsMissing: Boolean = false,
    val exactAlarmsMissing: Boolean = false,
)

sealed interface RemindersEvent {
    data class Toggled(
        val id: Long,
        val enabled: Boolean,
    ) : RemindersEvent

    data class ItemClicked(
        val id: Long,
    ) : RemindersEvent

    data object AddClicked : RemindersEvent

    /** The screen came back to the front: the user may have changed a permission in the system settings. */
    data object PermissionsRefreshed : RemindersEvent

    data object NotificationsBannerClicked : RemindersEvent

    data object ExactAlarmsBannerClicked : RemindersEvent
}
