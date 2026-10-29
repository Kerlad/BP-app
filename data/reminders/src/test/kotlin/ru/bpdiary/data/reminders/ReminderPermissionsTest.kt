package ru.bpdiary.data.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
class ReminderPermissionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val permissions = AndroidReminderPermissions(context, context.getSystemService(AlarmManager::class.java))

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun `exact alarms follow the system switch`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(permissions.exactAlarmsAllowed())
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(permissions.exactAlarmsAllowed())
    }

    @Test
    fun `notifications follow the system switch`() {
        val shadow = shadowOf(context.getSystemService(NotificationManager::class.java))
        shadow.setNotificationsEnabled(true)
        assertTrue(permissions.notificationsAllowed())
        shadow.setNotificationsEnabled(false)
        assertFalse(permissions.notificationsAllowed())
    }
}
