package ru.bpdiary.feature.onboarding

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.ThresholdPreset
import ru.bpdiary.domain.fake.FakeReminderPermissions
import ru.bpdiary.domain.fake.FakeReminderRepository
import ru.bpdiary.domain.fake.FakeReminderScheduler
import ru.bpdiary.domain.fake.FakeSettingsRepository
import ru.bpdiary.domain.fake.FakeThresholdsRepository
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val thresholds = FakeThresholdsRepository()
    private val reminders = FakeReminderRepository()
    private val scheduler = FakeReminderScheduler()
    private val permissions = FakeReminderPermissions()
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): OnboardingViewModel {
        val vm = OnboardingViewModel(thresholds, reminders, scheduler, permissions, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `starts on the first of three steps`() =
        runTest {
            assertEquals(0, viewModel().state.value.step)
            assertEquals(3, ONBOARDING_STEPS)
        }

    @Test
    fun `next and back move between the steps and stop at the first one`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(OnboardingEvent.NextClicked)
            assertEquals(1, vm.state.value.step)
            vm.onEvent(OnboardingEvent.NextClicked)
            assertEquals(2, vm.state.value.step)
            vm.onEvent(OnboardingEvent.BackPressed)
            assertEquals(1, vm.state.value.step)
            vm.onEvent(OnboardingEvent.BackPressed)
            vm.onEvent(OnboardingEvent.BackPressed)
            assertEquals(0, vm.state.value.step)
        }

    @Test
    fun `the preset sets only the high levels and is shown as chosen`() =
        runTest {
            val vm = viewModel()
            val before = thresholds.observe().first()
            vm.onEvent(OnboardingEvent.PresetSelected(ThresholdPreset.CLINIC))
            val after = thresholds.observe().first()
            assertEquals(before.copy(highSys = 140, highDia = 90), after)
            assertEquals(after, vm.state.value.thresholds)
        }

    @Test
    fun `a preset that would break the low levels is ignored`() =
        runTest {
            thresholds.set(Thresholds(lowSys = 120, lowDia = 70, highSys = 135, highDia = 85))
            val vm = viewModel()
            vm.onEvent(OnboardingEvent.PresetSelected(ThresholdPreset.HOME))
            assertEquals(135, thresholds.observe().first().highSys)
        }

    @Test
    fun `the reminders are listed by time with their switches`() =
        runTest {
            val items = viewModel().state.value.reminders
            assertEquals(listOf(8, 14, 21), items.map { it.time.hour })
            assertEquals(listOf(true, false, true), items.map { it.enabled })
        }

    @Test
    fun `a switch saves at once and re-plans the alarm`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(OnboardingEvent.ReminderToggled(id = 2, enabled = true))
            assertTrue(reminders.getById(2)!!.enabled)
            vm.onEvent(OnboardingEvent.ReminderToggled(id = 1, enabled = false))
            assertFalse(reminders.getById(1)!!.enabled)
            assertEquals(2, scheduler.rescheduleCount)
            assertEquals(LocalTime.of(8, 0), reminders.getById(1)!!.time)
        }

    @Test
    fun `permission hints appear only while something is switched on and the permission is missing`() =
        runTest {
            permissions.notifications = false
            permissions.exactAlarms = false
            val vm = viewModel()
            assertTrue(vm.state.value.notificationsMissing)
            assertTrue(vm.state.value.exactAlarmsMissing)

            listOf(1L, 3L).forEach { vm.onEvent(OnboardingEvent.ReminderToggled(it, enabled = false)) }
            assertFalse(vm.state.value.notificationsMissing)
            assertFalse(vm.state.value.exactAlarmsMissing)

            vm.onEvent(OnboardingEvent.ReminderToggled(1, enabled = true))
            assertTrue(vm.state.value.notificationsMissing)
        }

    @Test
    fun `granting a permission removes its hint after the refresh`() =
        runTest {
            permissions.notifications = false
            val vm = viewModel()
            assertTrue(vm.state.value.notificationsMissing)
            permissions.notifications = true
            vm.onEvent(OnboardingEvent.PermissionsRefreshed)
            assertFalse(vm.state.value.notificationsMissing)
        }

    @Test
    fun `a refused permission does not block the way on`() =
        runTest {
            permissions.notifications = false
            permissions.exactAlarms = false
            val vm = viewModel()
            vm.effects.test {
                repeat(ONBOARDING_STEPS) { vm.onEvent(OnboardingEvent.NextClicked) }
                assertEquals(OnboardingEffect.OpenDisclaimer, awaitItem())
            }
        }

    @Test
    fun `finishing marks the onboarding done and opens the disclaimer, not before`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(OnboardingEvent.NextClicked)
            vm.onEvent(OnboardingEvent.NextClicked)
            assertFalse(settings.onboardingDone.first())
            vm.effects.test {
                vm.onEvent(OnboardingEvent.NextClicked)
                assertEquals(OnboardingEffect.OpenDisclaimer, awaitItem())
            }
            assertTrue(settings.onboardingDone.first())
        }

    @Test
    fun `the onboarding does not accept the disclaimer by itself`() =
        runTest {
            val vm = viewModel()
            repeat(ONBOARDING_STEPS) { vm.onEvent(OnboardingEvent.NextClicked) }
            assertEquals(0, settings.disclaimerAck.first())
        }
}
