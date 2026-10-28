package ru.bpdiary.domain

import ru.bpdiary.core.model.Reminder

/** Keeps one alarm for the nearest enabled reminder (docs/07 §7.3). */
interface ReminderScheduler {
    /** Re-plans the nearest reminder. A pending snooze is left alone (D-13). */
    suspend fun rescheduleAll()

    /** «Отложить на 15 минут» for [reminderId], independent of [rescheduleAll]. */
    fun snooze(reminderId: Long)

    /** «Удалить все данные»: the reminder alarm and the snooze. */
    fun cancelAll()
}

/** Shows the reminder notification; the real one is built in Step 9.2. */
fun interface ReminderNotifier {
    fun show(reminder: Reminder)
}
