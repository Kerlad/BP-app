package ru.bpdiary.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.ReminderScheduler
import java.time.Duration

/** Re-plans the reminder alarm; enqueued from [BootReceiver] and at app start. */
@HiltWorker
class RescheduleWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val scheduler: ReminderScheduler,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            scheduler.rescheduleAll()
            return Result.success()
        }
    }

/**
 * Daily "watchdog": puts the alarm back if the system or a vendor battery saver dropped it (docs/07 §7.1),
 * and removes readings deleted more than 24 hours ago (D-17). A failed purge never blocks the alarms.
 */
@HiltWorker
class ReminderWatchdogWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val scheduler: ReminderScheduler,
        private val readings: ReadingRepository,
    ) : CoroutineWorker(context, params) {
        @Suppress("TooGenericExceptionCaught") // any storage failure must leave the alarms intact
        override suspend fun doWork(): Result {
            try {
                readings.purgeExpired()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Retried on the next daily run and at the next app start.
            }
            scheduler.rescheduleAll()
            return Result.success()
        }
    }

/**
 * Reboot, app update, clock or zone change and exact-alarm permission change all drop or skew the alarm.
 * The receiver must return quickly, so it only enqueues [RescheduleWorker] (docs/07 §7.3).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action in ACTIONS) ReminderWork.reschedule(context)
    }

    internal companion object {
        val ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            )
    }
}

object ReminderWork {
    private const val RESCHEDULE = "reminders_reschedule"
    private const val WATCHDOG = "reminders_watchdog"

    /** 6 h, not a day (review B-03): a revoked permission or a dropped alarm is repaired within hours. */
    private const val WATCHDOG_HOURS = 6L
    private val WATCHDOG_PERIOD: Duration = Duration.ofHours(WATCHDOG_HOURS)

    /** One quick re-plan; a newer request replaces a pending one. */
    fun reschedule(context: Context) {
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                RESCHEDULE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RescheduleWorker>().build(),
            )
    }

    /** At app start: the initial schedule after the database is created, and the watchdog (UPDATE: new period). */
    fun start(context: Context) {
        reschedule(context)
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(
                WATCHDOG,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReminderWatchdogWorker>(WATCHDOG_PERIOD).build(),
            )
    }
}
