package ru.bpdiary.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.bpdiary.domain.ReminderNotifier
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.ReminderScheduler
import javax.inject.Inject

/**
 * The alarm fired: show the notification, then plan the next one (docs/07 §7.3); or the user tapped «Отложить».
 * A snooze shows the reminder even if it was switched off meanwhile; a regular alarm only for a still enabled one.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: ReminderRepository

    @Inject lateinit var scheduler: ReminderScheduler

    @Inject lateinit var notifier: ReminderNotifier

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        when (intent.action) {
            ACTION_SNOOZE -> snooze(context, id)
            ACTION_FIRE -> fire(id, intent.getBooleanExtra(EXTRA_SNOOZE, false))
        }
    }

    private fun fire(
        id: Long,
        snooze: Boolean,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                handle(id, snooze)
            } finally {
                pending.finish()
            }
        }
    }

    /** «Отложить на 15 минут» from the notification: own alarm (D-13), the notification goes away. */
    internal fun snooze(
        context: Context,
        id: Long,
    ) {
        scheduler.snooze(id)
        NotificationManagerCompat.from(context).cancel(NotificationReminderNotifier.notificationId(id))
    }

    /**
     * The next alarm is planned first: a failure to show this notification must not end the chain of
     * reminders (review B-01).
     */
    internal suspend fun handle(
        id: Long,
        snooze: Boolean,
    ) {
        runCatching { scheduler.rescheduleAll() }
        runCatching { reminders.getById(id)?.takeIf { snooze || it.enabled }?.let(notifier::show) }
    }

    companion object {
        const val ACTION_FIRE = "ru.bpdiary.app.action.REMINDER_FIRE"
        const val ACTION_SNOOZE = "ru.bpdiary.app.action.REMINDER_SNOOZE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_SNOOZE = "snooze"

        /** Short work under `goAsync()`; the process may be killed afterwards, which is fine. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
