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
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.DISCLAIMER_VERSION
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeReminderRepository
import ru.bpdiary.domain.fake.FakeReminderScheduler
import ru.bpdiary.domain.fake.FakeSettingsRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val time = ManualTimeProvider(Instant.parse("2026-10-20T06:12:00Z"), ZoneId.of("Europe/Moscow"))
    private val readings = FakeReadingRepository(time)
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
        val vm = SettingsViewModel(readings, reminders, settings, scheduler, time)
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
    fun `the text size is shown and a choice stores the boost`() =
        runTest {
            val vm = viewModel()
            assertEquals(TextSize.NORMAL, vm.state.value.textSize)
            vm.onEvent(SettingsEvent.TextSizeSelected(TextSize.EXTRA_LARGE))
            assertEquals(1.3f, settings.fontScaleBoost.first())
            assertEquals(TextSize.EXTRA_LARGE, vm.state.value.textSize)
            vm.onEvent(SettingsEvent.TextSizeSelected(TextSize.LARGE))
            assertEquals(1.15f, settings.fontScaleBoost.first())
            vm.onEvent(SettingsEvent.TextSizeSelected(TextSize.NORMAL))
            assertEquals(1.0f, settings.fontScaleBoost.first())
            assertEquals(TextSize.NORMAL, vm.state.value.textSize)
        }

    @Test
    fun `a stored boost between the steps shows the closest one`() =
        runTest {
            settings.setFontScaleBoost(1.2f)
            val vm = viewModel()
            assertEquals(TextSize.LARGE, vm.state.value.textSize)
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
            vm.onEvent(SettingsEvent.DeleteFirstConfirmed)
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
            vm.onEvent(SettingsEvent.DeleteFirstConfirmed)
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
                vm.onEvent(SettingsEvent.DeleteFirstConfirmed)
                time.advance(Duration.ofSeconds(2))
                vm.onEvent(SettingsEvent.DeleteForeverConfirmed)
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
            vm.onEvent(SettingsEvent.DeleteForeverConfirmed)
            assertEquals(1, readings.storedCount)
        }

    @Test
    fun `a double tap cannot pass both dialogs (A-07)`() =
        runTest {
            addReading()
            val vm = viewModel()
            vm.onEvent(SettingsEvent.DeleteClicked)
            vm.onEvent(SettingsEvent.DeleteFirstConfirmed)
            vm.onEvent(SettingsEvent.DeleteFirstConfirmed)
            vm.onEvent(SettingsEvent.DeleteForeverConfirmed)
            assertEquals(1, readings.storedCount)
            assertEquals(DeleteDialog.Second, vm.state.value.dialog)

            time.advance(Duration.ofSeconds(2))
            vm.onEvent(SettingsEvent.DeleteForeverConfirmed)
            assertEquals(0, readings.storedCount)
        }
}
