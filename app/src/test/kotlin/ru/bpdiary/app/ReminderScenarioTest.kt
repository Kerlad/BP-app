package ru.bpdiary.app

import android.app.AlarmManager
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Direction
import ru.bpdiary.data.reminders.AlarmReminderScheduler
import ru.bpdiary.data.reminders.ReminderReceiver
import ru.bpdiary.domain.fake.FakeReminderPermissions
import ru.bpdiary.domain.fake.FakeReminderRepository
import ru.bpdiary.feature.reminders.RemindersScreen
import ru.bpdiary.feature.reminders.RemindersViewModel
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * E2E scenario 3 of docs/10 §10.5, on the JVM: the real Reminders screen, its view model and the real alarm
 * scheduler against `ShadowAlarmManager`. Switch the morning reminder off, switch the day one on, and the alarm
 * that is set must follow, then the default ones come back when everything is off or on again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class ReminderScenarioTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val shadow = shadowOf(alarms)
    private val moscow = ZoneId.of("Europe/Moscow")

    /** Tuesday 2026-10-20, 06:12: before the morning reminder; the day one (Mon-Fri) is switched off by default. */
    private val time = ManualTimeProvider(LocalDateTime.parse("2026-10-20T06:12").atZone(moscow).toInstant(), moscow)
    private val reminders = FakeReminderRepository()
    private val scheduler = AlarmReminderScheduler(context, alarms, reminders, time)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ShadowAlarmManager.reset()
    }

    private fun at(local: String): Long =
        LocalDateTime
            .parse(local)
            .atZone(moscow)
            .toInstant()
            .toEpochMilli()

    private fun nextAlarmReminderId(): Long? =
        shadow.peekNextScheduledAlarm()?.let {
            shadowOf(it.operation).savedIntent.getLongExtra(ReminderReceiver.EXTRA_REMINDER_ID, -1)
        }

    private fun showScreen() {
        val viewModel = RemindersViewModel(reminders, scheduler, FakeReminderPermissions())
        rule.setContent {
            val state by viewModel.state.collectAsState()
            BpTheme(Direction.SAGE, dark = false) { RemindersScreen(state = state, onEvent = viewModel::onEvent) }
        }
    }

    @Test
    fun `switching the morning reminder off and the day one on moves the alarm`() {
        kotlinx.coroutines.runBlocking { scheduler.rescheduleAll() }
        assertEquals(
            "the first start plans the morning",
            at("2026-10-20T08:00"),
            shadow.peekNextScheduledAlarm()?.triggerAtMs,
        )
        showScreen()

        rule.onNodeWithContentDescription("Утреннее измерение, 08:00").performClick()
        rule.waitForIdle()
        assertEquals("the evening is next", at("2026-10-20T21:00"), shadow.peekNextScheduledAlarm()?.triggerAtMs)
        assertEquals(3L, nextAlarmReminderId())

        rule.onNodeWithContentDescription("Дневное измерение, 14:00").performClick()
        rule.waitForIdle()
        assertEquals("the day one is next", at("2026-10-20T14:00"), shadow.peekNextScheduledAlarm()?.triggerAtMs)
        assertEquals(2L, nextAlarmReminderId())
        assertEquals("one alarm, not one per reminder", 1, shadow.scheduledAlarms.size)
    }

    @Test
    fun `the alarm is exact and keeps the code of the reminder alarm`() {
        showScreen()
        rule.onNodeWithContentDescription("Дневное измерение, 14:00").performClick()
        rule.waitForIdle()
        val alarm = shadow.peekNextScheduledAlarm()!!
        assertEquals(true, alarm.isAllowWhileIdle)
        assertEquals(AlarmReminderScheduler.REQ_REMINDER, shadowOf(alarm.operation).requestCode)
    }

    @Test
    fun `with every reminder off no alarm is left, with one on again it is back`() {
        kotlinx.coroutines.runBlocking { scheduler.rescheduleAll() }
        showScreen()
        rule.onNodeWithContentDescription("Утреннее измерение, 08:00").performClick()
        rule.onNodeWithContentDescription("Вечернее измерение, 21:00").performClick()
        rule.waitForIdle()
        assertNull(shadow.peekNextScheduledAlarm())

        rule.onNodeWithContentDescription("Вечернее измерение, 21:00").performClick()
        rule.waitForIdle()
        assertEquals(at("2026-10-20T21:00"), shadow.peekNextScheduledAlarm()?.triggerAtMs)
    }

    @Test
    fun `a snooze is not cancelled by switching a reminder`() {
        kotlinx.coroutines.runBlocking {
            scheduler.rescheduleAll()
            scheduler.snooze(reminderId = 1)
        }
        showScreen()
        rule.onNodeWithContentDescription("Дневное измерение, 14:00").performClick()
        rule.waitForIdle()
        val codes = shadow.scheduledAlarms.map { shadowOf(it.operation).requestCode }.sorted()
        assertEquals(listOf(AlarmReminderScheduler.REQ_REMINDER, AlarmReminderScheduler.REQ_SNOOZE), codes)
    }
}
