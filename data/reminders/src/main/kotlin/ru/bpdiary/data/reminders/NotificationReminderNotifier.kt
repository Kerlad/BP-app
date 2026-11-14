package ru.bpdiary.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.domain.ReminderNotifier
import javax.inject.Inject
import ru.bpdiary.app.data.reminders.R as RemindersR

/**
 * «Пора измерить давление» under the reminder's name (docs/07 §7.4). Never any values or status: notifications
 * are visible on the lock screen (docs/09). A tap opens the app on a new reading through an explicit intent
 * with `open=entry_new` (D-14); the action snoozes for 15 minutes with its own alarm (D-13).
 */
class NotificationReminderNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ReminderNotifier {
        override fun show(reminder: Reminder) {
            ensureChannel(context)
            if (!canNotify()) return
            val id = notificationId(reminder.id)
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(RemindersR.drawable.ic_notification_reminder)
                    .setColor(ContextCompat.getColor(context, RemindersR.color.reminders_accent))
                    .setContentTitle(reminder.label)
                    .setContentText(context.getString(RemindersR.string.reminders_notification_text))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openEntry(reminder.id))
                    .addAction(
                        0,
                        context.getString(RemindersR.string.reminders_action_snooze),
                        snoozeIntent(reminder.id),
                    ).build()
            // canNotify() checked POST_NOTIFICATIONS just above.
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(id, notification)
        }

        private fun canNotify(): Boolean {
            val granted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /** The launcher activity, explicitly; this module does not know `MainActivity` by name. */
        private fun openEntry(reminderId: Long): PendingIntent? {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            launch.putExtra(EXTRA_OPEN, OPEN_ENTRY_NEW)
            return PendingIntent.getActivity(
                context,
                REQ_OPEN_BASE + reminderId.toInt(),
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun snoozeIntent(reminderId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQ_SNOOZE_ACTION_BASE + reminderId.toInt(),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ReminderReceiver.ACTION_SNOOZE)
                    .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        companion object {
            const val CHANNEL_ID = "reminders"
            const val EXTRA_OPEN = "open"
            const val OPEN_ENTRY_NEW = "entry_new"

            /** Request codes of the notification's own PendingIntents, apart from the alarms (1000, 9000). */
            private const val REQ_OPEN_BASE = 20_000
            private const val REQ_SNOOZE_ACTION_BASE = 30_000
            private const val NOTIFICATION_BASE = 100

            fun notificationId(reminderId: Long): Int = NOTIFICATION_BASE + reminderId.toInt()

            /** HIGH importance, default sound and vibration: a reminder, not an alarm clock. */
            fun ensureChannel(context: Context) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(RemindersR.string.reminders_channel_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { enableVibration(true) }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }
