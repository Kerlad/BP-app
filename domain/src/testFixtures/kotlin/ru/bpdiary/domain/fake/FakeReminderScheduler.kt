package ru.bpdiary.domain.fake

import ru.bpdiary.domain.ReminderPermissions
import ru.bpdiary.domain.ReminderScheduler

/** Counts calls; a test asserts that a change was followed by [rescheduleAll]. */
class FakeReminderScheduler : ReminderScheduler {
    var rescheduleCount = 0
        private set
    val snoozed = mutableListOf<Long>()
    var cancelled = false
        private set

    override suspend fun rescheduleAll() {
        rescheduleCount++
    }

    override fun snooze(reminderId: Long) {
        snoozed += reminderId
    }

    override fun cancelAll() {
        cancelled = true
    }
}

class FakeReminderPermissions(
    var notifications: Boolean = true,
    var exactAlarms: Boolean = true,
) : ReminderPermissions {
    override fun notificationsAllowed(): Boolean = notifications

    override fun exactAlarmsAllowed(): Boolean = exactAlarms
}
