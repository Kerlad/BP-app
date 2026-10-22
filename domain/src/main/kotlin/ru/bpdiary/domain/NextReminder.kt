package ru.bpdiary.domain

import ru.bpdiary.core.model.Reminder
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

private const val DAYS_IN_WEEK = 7

/**
 * The nearest firing strictly after [now], keeping the wall-clock time in the zone of [now].
 * A wall-clock time that does not exist on a DST change day is shifted forward by the zone rules.
 * Returns null when [Reminder.days] is empty.
 */
fun Reminder.nextTriggerAfter(now: ZonedDateTime): ZonedDateTime? {
    if (days.isEmpty()) return null
    val today = now.toLocalDate()
    // Today plus the next seven days covers every weekday, including the same weekday next week.
    return (0..DAYS_IN_WEEK)
        .asSequence()
        .map { today.plusDays(it.toLong()) }
        .filter { it.dayOfWeek in days }
        .map { it.atTime(time).atZone(now.zone) }
        .firstOrNull { it.isAfter(now) }
}

data class NextReminder(
    val reminder: Reminder,
    val at: ZonedDateTime,
)

/** The enabled reminder that fires first; ties go to the earlier one in [reminders]. */
fun nextReminder(
    reminders: List<Reminder>,
    now: ZonedDateTime,
): NextReminder? =
    reminders
        .filter { it.enabled }
        .mapNotNull { r -> r.nextTriggerAfter(now)?.let { NextReminder(r, it) } }
        .minByOrNull { it.at }

/** Presentation turns this into text: "14:00 · label", "Завтра, 08:00 · label", "Пн, 08:00 · label". */
sealed interface NextReminderLabel {
    data class Today(
        val time: LocalTime,
        val label: String,
    ) : NextReminderLabel

    data class Tomorrow(
        val time: LocalTime,
        val label: String,
    ) : NextReminderLabel

    data class Weekday(
        val dow: DayOfWeek,
        val time: LocalTime,
        val label: String,
    ) : NextReminderLabel
}

fun nextReminderLabel(
    next: NextReminder,
    now: ZonedDateTime,
): NextReminderLabel {
    val day = next.at.toLocalDate()
    val today = now.toLocalDate()
    val time = next.at.toLocalTime()
    val label = next.reminder.label
    return when (day) {
        today -> NextReminderLabel.Today(time, label)
        today.plusDays(1) -> NextReminderLabel.Tomorrow(time, label)
        else -> NextReminderLabel.Weekday(day.dayOfWeek, time, label)
    }
}
