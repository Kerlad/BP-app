package ru.bpdiary.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.ReminderScheduler
import ru.bpdiary.domain.nextReminder
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One exact alarm for the nearest enabled reminder, re-planned after every firing (docs/07 §7.1).
 * Without the exact-alarm permission it falls back to a 10-minute window (D-12): late, but it still comes.
 * The snooze has its own request code, so [rescheduleAll] never cancels it (D-13).
 */
@Singleton
class AlarmReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val alarms: AlarmManager,
        private val reminders: ReminderRepository,
        private val time: TimeProvider,
    ) : ReminderScheduler {
        override suspend fun rescheduleAll() {
            cancel(REQ_REMINDER)
            val now = time.now().atZone(time.zone())
            val next = nextReminder(reminders.enabled(), now) ?: return
            schedule(next.at.toInstant().toEpochMilli(), pendingIntent(REQ_REMINDER, next.reminder.id, snooze = false))
        }

        override fun snooze(reminderId: Long) {
            val at = time.now().plus(SNOOZE).toEpochMilli()
            schedule(at, pendingIntent(REQ_SNOOZE, reminderId, snooze = true))
        }

        override fun cancelAll() {
            cancel(REQ_REMINDER)
            cancel(REQ_SNOOZE)
        }

        /** False on API 31+ when the user has not allowed exact alarms; the Reminders screen shows a banner. */
        fun canScheduleExact(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

        private fun schedule(
            at: Long,
            operation: PendingIntent,
        ) {
            if (canScheduleExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarms.setWindow(AlarmManager.RTC_WAKEUP, at, FALLBACK_WINDOW.toMillis(), operation)
            }
        }

        private fun cancel(requestCode: Int) {
            val existing =
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    fireIntent(),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                ) ?: return
            alarms.cancel(existing)
            existing.cancel()
        }

        private fun pendingIntent(
            requestCode: Int,
            reminderId: Long,
            snooze: Boolean,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                fireIntent()
                    .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
                    .putExtra(ReminderReceiver.EXTRA_SNOOZE, snooze),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Explicit, so nobody else can trigger it; extras do not take part in PendingIntent matching. */
        private fun fireIntent() = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE)

        companion object {
            const val REQ_REMINDER = 1000
            const val REQ_SNOOZE = 9000
            val SNOOZE: Duration = Duration.ofMinutes(15)
            val FALLBACK_WINDOW: Duration = Duration.ofMinutes(10)
        }
    }
