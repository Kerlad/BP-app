package ru.bpdiary.feature.settings

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
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.DISCLAIMER_VERSION
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeReminderRepository
import ru.bpdiary.domain.fake.FakeReminderScheduler
import ru.bpdiary.domain.fake.FakeSettingsRepository
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val readings = FakeReadingRepository(FixedTimeProvider.default())
    private val reminders = FakeReminderRepository()
    private val settings = FakeSettingsRepository()
    private val scheduler = FakeReminderScheduler()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): SettingsViewModel {
        val vm = SettingsViewModel(readings, reminders, settings, scheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private suspend fun addReading() =
        readings.save(
            Reading(
                takenAt = Instant.parse("2026-10-20T04:00:00Z"),
                zoneOffset = ZoneOffset.ofHours(3),
                sys = 128,
                dia = 82,
                pulse = 71,
                arm = Arm.LEFT,
            ),
        )

    @Test
    fun `the current theme is shown and a choice is stored`() =
        runTest {
            val vm = viewModel()
            assertEquals(ThemeMode.SYSTEM, vm.state.value.themeMode)
            vm.onEvent(SettingsEvent.ThemeSelected(ThemeMode.DARK))
            assertEquals(ThemeMode.DARK, settings.themeMode.first())
            assertEquals(ThemeMode.DARK, vm.state.value.themeMode)
            vm.onEvent(SettingsEvent.ThemeSelected(ThemeMode.LIGHT))
            assertEquals(ThemeMode.LIGHT, settings.themeMode.first())
        }

    @Test
    fun `no dialog at first, the button opens the first one`() =
        runTest {
            val vm = viewModel()
            assertEquals(DeleteDialog.None, vm.state.value.dialog)
            vm.onEvent(SettingsEvent.DeleteClicked)
            assertEquals(DeleteDialog.First, vm.state.value.dialog)
        }

    @Test
    fun `the first confirmation only leads to the second dialog and deletes nothing`() =
        runTest {
            addReading()
            val vm = viewModel()
            vm.onEvent(SettingsEvent.DeleteClicked)
            vm.onEvent(SettingsEvent.DeleteConfirmed)
            assertEquals(DeleteDialog.Second, vm.state.value.dialog)
            assertEquals(1, readings.storedCount)
            assertFalse(scheduler.cancelled)
        }

    @Test
    fun `cancelling in either dialog keeps everything`() =
        runTest {
            addReading()
            val vm = viewModel()
            vm.onEvent(SettingsEvent.DeleteClicked)
            vm.onEvent(SettingsEvent.DialogDismissed)
            assertEquals(DeleteDialog.None, vm.state.value.dialog)

            vm.onEvent(SettingsEvent.DeleteClicked)
            vm.onEvent(SettingsEvent.DeleteConfirmed)
            vm.onEvent(SettingsEvent.DialogDismissed)
            assertEquals(DeleteDialog.None, vm.state.value.dialog)
            assertEquals(1, readings.storedCount)
        }

    @Test
    fun `the second confirmation deletes everything and sends the app back to the start`() =
        runTest {
            addReading()
            readings.softDelete(addReading())
            settings.setOnboardingDone(true)
            settings.setDisclaimerAck(DISCLAIMER_VERSION)
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(SettingsEvent.DeleteClicked)
                vm.onEvent(SettingsEvent.DeleteConfirmed)
                vm.onEvent(SettingsEvent.DeleteConfirmed)
                assertEquals(SettingsEffect.DataDeleted, awaitItem())
            }
            assertEquals(0, readings.storedCount)
            assertFalse(settings.onboardingDone.first())
            assertEquals(0, settings.disclaimerAck.first())
            assertTrue(scheduler.cancelled)
            assertEquals(DeleteDialog.None, vm.state.value.dialog)
        }

    @Test
    fun `the second confirmation without the first does nothing`() =
        runTest {
            addReading()
            val vm = viewModel()
            vm.onEvent(SettingsEvent.DeleteConfirmed)
            assertEquals(1, readings.storedCount)
        }
}
