package ru.bpdiary.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
 * The alarm fired: show the notification, then plan the next one (docs/07 §7.3). A snooze shows the
 * reminder even if it was switched off meanwhile; a regular alarm only for a still enabled reminder.
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
        if (intent.action != ACTION_FIRE) return
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val pending = goAsync()
        scope.launch {
            try {
                handle(id, snooze)
            } finally {
                pending.finish()
            }
        }
    }

    internal suspend fun handle(
        id: Long,
        snooze: Boolean,
    ) {
        reminders.getById(id)?.takeIf { snooze || it.enabled }?.let(notifier::show)
        scheduler.rescheduleAll()
    }

    companion object {
        const val ACTION_FIRE = "ru.bpdiary.app.action.REMINDER_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_SNOOZE = "snooze"

        /** Short work under `goAsync()`; the process may be killed afterwards, which is fine. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
