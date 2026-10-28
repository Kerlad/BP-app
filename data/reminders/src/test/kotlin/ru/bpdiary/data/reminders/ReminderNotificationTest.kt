package ru.bpdiary.data.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.domain.fake.FakeReminderRepository
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class ReminderNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = NotificationReminderNotifier(context)
    private val morning = Reminder(1, LocalTime.of(8, 0), "Утреннее измерение", DayOfWeek.entries.toSet(), true)
    private val launcher = ComponentName(context.packageName, "ru.bpdiary.app.MainActivity")

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // The library has no activity of its own: register the app's launcher like the merged manifest does.
        val packages = shadowOf(context.packageManager)
        packages.addActivityIfNotPresent(launcher)
        packages.addIntentFilterForActivity(
            launcher,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    private fun shown(): Notification = shadowOf(manager).allNotifications.single()

    @Test
    fun `title is the reminder name, text has no health data`() {
        notifier.show(morning)
        val n = shown()
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertEquals("Утреннее измерение", title)
        assertEquals("Пора измерить давление", text)
        // No values, no status: the notification is visible on the lock screen (docs/09).
        assertFalse((title + text).any { it.isDigit() })
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(Notification.CATEGORY_REMINDER, n.category)
    }

    @Test
    fun `channel reminders with high importance`() {
        notifier.show(morning)
        val channel = manager.getNotificationChannel(NotificationReminderNotifier.CHANNEL_ID)
        assertEquals("Напоминания об измерении", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(NotificationReminderNotifier.CHANNEL_ID, shown().channelId)
    }

    @Test
    fun `tap opens the app explicitly on a new reading (D-14)`() {
        notifier.show(morning)
        val intent = shadowOf(shown().contentIntent).savedIntent
        assertEquals(launcher, intent.component)
        assertEquals(
            NotificationReminderNotifier.OPEN_ENTRY_NEW,
            intent.getStringExtra(NotificationReminderNotifier.EXTRA_OPEN),
        )
    }

    @Test
    fun `snooze action plans its own alarm and removes the notification`() {
        notifier.show(morning)
        val action = shown().actions.single()
        assertEquals("Отложить на 15 минут", action.title.toString())
        val intent = shadowOf(action.actionIntent).savedIntent
        assertEquals(ReminderReceiver.ACTION_SNOOZE, intent.action)

        val alarms = context.getSystemService(AlarmManager::class.java)
        val receiver =
            ReminderReceiver().apply {
                scheduler =
                    AlarmReminderScheduler(context, alarms, FakeReminderRepository(), FixedTimeProvider.default())
            }
        // onReceive() would ask Hilt for injection, which a library test has no app for.
        receiver.snooze(context, intent.getLongExtra(ReminderReceiver.EXTRA_REMINDER_ID, -1))
        val snoozed = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(AlarmReminderScheduler.REQ_SNOOZE, shadowOf(snoozed.operation).requestCode)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `nothing is posted without the notification permission`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.show(morning)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
