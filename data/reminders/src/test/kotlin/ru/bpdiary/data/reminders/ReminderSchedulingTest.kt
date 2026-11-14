package ru.bpdiary.data.reminders

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.domain.ReminderNotifier
import ru.bpdiary.domain.fake.FakeReminderRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ReminderSchedulingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val shadow = shadowOf(alarms)
    private val moscow = ZoneId.of("Europe/Moscow")
    private val reminders = FakeReminderRepository()

    /** 08:00 daily and 21:00 daily on, 14:00 Mon–Fri off (the defaults). */
    private fun scheduler(local: String): AlarmReminderScheduler {
        val time = ManualTimeProvider(LocalDateTime.parse(local).atZone(moscow).toInstant(), moscow)
        return AlarmReminderScheduler(context, alarms, reminders, time)
    }

    private fun at(local: String): Long =
        LocalDateTime
            .parse(local)
            .atZone(moscow)
            .toInstant()
            .toEpochMilli()

    private fun ShadowAlarmManager.ScheduledAlarm.requestCode(): Int = shadowOf(operation).requestCode

    private fun ShadowAlarmManager.ScheduledAlarm.reminderId(): Long =
        shadowOf(operation).savedIntent.getLongExtra(ReminderReceiver.EXTRA_REMINDER_ID, -1)

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `one exact alarm for the nearest enabled reminder`() =
        runTest {
            // Tuesday 2026-10-20.
            listOf(
                "2026-10-20T06:00" to "2026-10-20T08:00",
                "2026-10-20T08:00" to "2026-10-20T21:00",
                "2026-10-20T09:12" to "2026-10-20T21:00",
                "2026-10-20T21:00" to "2026-10-21T08:00",
                "2026-10-20T23:59" to "2026-10-21T08:00",
            ).forEach { (now, expected) ->
                scheduler(now).rescheduleAll()
                val alarm = shadow.scheduledAlarms.single()
                assertEquals("now $now", at(expected), alarm.triggerAtMs)
                assertTrue(alarm.isAllowWhileIdle)
                assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
                assertEquals(AlarmReminderScheduler.REQ_REMINDER, alarm.requestCode())
            }
        }

    @Test
    fun `weekday reminder on Friday evening goes to Monday`() =
        runTest {
            reminders.getAll().forEach { reminders.save(it.copy(enabled = it.label == "Дневное измерение")) }
            // Friday 2026-10-23 22:00 → Monday 2026-10-26 14:00.
            scheduler("2026-10-23T22:00").rescheduleAll()
            assertEquals(at("2026-10-26T14:00"), shadow.scheduledAlarms.single().triggerAtMs)
        }

    @Test
    fun `toggling reminders moves or removes the alarm`() =
        runTest {
            val s = scheduler("2026-10-20T09:12")
            s.rescheduleAll()
            val evening = reminders.getAll().first { it.time == LocalTime.of(21, 0) }
            assertEquals(evening.id, shadow.scheduledAlarms.single().reminderId())

            reminders.save(evening.copy(enabled = false))
            s.rescheduleAll()
            val next = shadow.scheduledAlarms.single()
            assertEquals(at("2026-10-21T08:00"), next.triggerAtMs)

            reminders.getAll().forEach { reminders.save(it.copy(enabled = false)) }
            s.rescheduleAll()
            assertTrue(shadow.scheduledAlarms.isEmpty())
        }

    @Test
    fun `snooze survives rescheduleAll and cancelAll removes both`() =
        runTest {
            val s = scheduler("2026-10-20T08:00")
            s.snooze(reminderId = 1)
            s.rescheduleAll()
            s.rescheduleAll()
            val byCode = shadow.scheduledAlarms.associateBy { it.requestCode() }
            assertEquals(setOf(AlarmReminderScheduler.REQ_REMINDER, AlarmReminderScheduler.REQ_SNOOZE), byCode.keys)
            val snoozed = byCode.getValue(AlarmReminderScheduler.REQ_SNOOZE)
            assertEquals(at("2026-10-20T08:15"), snoozed.triggerAtMs)
            assertTrue(shadowOf(snoozed.operation).savedIntent.getBooleanExtra(ReminderReceiver.EXTRA_SNOOZE, false))

            s.cancelAll()
            assertTrue(shadow.scheduledAlarms.isEmpty())
        }

    @Test
    fun `without exact alarms an inexact alarm that also fires in Doze is used (B-02)`() =
        runTest {
            ShadowAlarmManager.setCanScheduleExactAlarms(false)
            val s = scheduler("2026-10-20T09:12")
            assertFalse(s.canScheduleExact())
            s.rescheduleAll()
            val alarm = shadow.scheduledAlarms.single()
            assertEquals(at("2026-10-20T21:00"), alarm.triggerAtMs)
            assertTrue(alarm.isAllowWhileIdle)
        }

    @Test
    fun `a failing notification does not break the alarm chain (B-01)`() =
        runTest {
            val s = scheduler("2026-10-20T21:00")
            val receiver =
                ReminderReceiver().apply {
                    this.reminders = this@ReminderSchedulingTest.reminders
                    scheduler = s
                    notifier = ReminderNotifier { error("notification failed") }
                }
            receiver.handle(id = 3, snooze = false)
            assertEquals(at("2026-10-21T08:00"), shadow.peekNextScheduledAlarm()?.triggerAtMs)
        }

    @Test
    fun `fired alarm shows an enabled reminder and plans the next one`() =
        runTest {
            val shown = mutableListOf<Reminder>()
            val s = scheduler("2026-10-20T21:00")
            val receiver =
                ReminderReceiver().apply {
                    this.reminders = this@ReminderSchedulingTest.reminders
                    scheduler = s
                    notifier = ReminderNotifier { shown += it }
                }
            receiver.handle(id = 3, snooze = false)
            assertEquals(listOf("Вечернее измерение"), shown.map { it.label })
            assertEquals(at("2026-10-21T08:00"), shadow.scheduledAlarms.single().triggerAtMs)

            // Switched off meanwhile: a regular alarm stays silent, a snooze still shows.
            receiver.handle(id = 2, snooze = false)
            receiver.handle(id = 2, snooze = true)
            assertEquals(listOf("Вечернее измерение", "Дневное измерение"), shown.map { it.label })
            // Deleted reminder: nothing to show.
            receiver.handle(id = 99, snooze = true)
            assertEquals(2, shown.size)
        }

    @Test
    fun `boot and clock changes enqueue a reschedule`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("reminders_reschedule").get()
        assertEquals(1, work.size)

        BootReceiver().onReceive(context, Intent("some.other.ACTION"))
        assertEquals(
            1,
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork("reminders_reschedule")
                .get()
                .size,
        )
    }
}
