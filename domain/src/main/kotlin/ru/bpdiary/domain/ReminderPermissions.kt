package ru.bpdiary.domain

/** What the system currently lets the reminders do; the Reminders screen shows a banner for each missing one. */
interface ReminderPermissions {
    /** Notifications can be shown: `POST_NOTIFICATIONS` on Android 13+ and the app switch in the settings. */
    fun notificationsAllowed(): Boolean

    /** Exact alarms (D-12). Without them the reminder arrives within a 10-minute window instead. */
    fun exactAlarmsAllowed(): Boolean
}
