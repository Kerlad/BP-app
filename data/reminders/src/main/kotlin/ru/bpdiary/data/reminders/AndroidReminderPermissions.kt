package ru.bpdiary.data.reminders

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.bpdiary.domain.ReminderPermissions
import javax.inject.Inject

/** `areNotificationsEnabled` is false both without `POST_NOTIFICATIONS` (Android 13+) and with the app switched off. */
class AndroidReminderPermissions
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val alarms: AlarmManager,
    ) : ReminderPermissions {
        override fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

        override fun exactAlarmsAllowed(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    }
