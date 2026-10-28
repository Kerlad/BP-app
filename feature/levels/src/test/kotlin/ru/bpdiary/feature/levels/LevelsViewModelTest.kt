package ru.bpdiary.feature.levels

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.THRESHOLD_STEP
import ru.bpdiary.domain.ThresholdField
import ru.bpdiary.domain.ThresholdPreset
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeReminderRepository
import ru.bpdiary.domain.fake.FakeSettingsRepository
import ru.bpdiary.domain.fake.FakeThresholdsRepository
import ru.bpdiary.feature.home.HomeUiState
import ru.bpdiary.feature.home.HomeViewModel
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class LevelsViewModelTest {
    private val thresholds = FakeThresholdsRepository()
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): LevelsViewModel {
        val vm = LevelsViewModel(thresholds, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun LevelsViewModel.th() = state.value.thresholds!!

    private fun LevelsViewModel.step(
        field: ThresholdField,
        times: Int,
        delta: Int = THRESHOLD_STEP,
    ) = repeat(times) { onEvent(LevelsEvent.Step(field, delta)) }

    @Test
    fun `screen updates at once, DataStore 300 ms after the last tap`() =
        runTest {
            val vm = viewModel()
            vm.step(ThresholdField.HIGH_SYS, times = 3)
            assertEquals(150, vm.th().highSys)
            assertEquals(135, thresholds.observe().first().highSys)

            advanceTimeBy(WRITE_DEBOUNCE_MS - 1)
            assertEquals(135, thresholds.observe().first().highSys)
            advanceTimeBy(2)
            assertEquals(150, thresholds.observe().first().highSys)
        }

    @Test
    fun `back writes pending changes before closing`() =
        runTest {
            val vm = viewModel()
            vm.step(ThresholdField.PULSE_HIGH, times = 1)
            vm.effects.test {
                vm.onEvent(LevelsEvent.BackClicked)
                assertEquals(LevelsEffect.Closed, awaitItem())
            }
            assertEquals(95, thresholds.observe().first().pulseHigh)
        }

    @Test
    fun `steps that would break a rule are ignored`() =
        runTest {
            val vm = viewModel()
            // lowSys 100 → 120 is the top of its range.
            vm.step(ThresholdField.LOW_SYS, times = 10)
            assertEquals(120, vm.th().lowSys)
            // highSys cannot go down to lowSys: 135 → 125 stops above 120.
            vm.step(ThresholdField.HIGH_SYS, times = 10, delta = -THRESHOLD_STEP)
            assertEquals(125, vm.th().highSys)
            // pulse 50..90: low cannot reach high.
            vm.step(ThresholdField.PULSE_LOW, times = 20)
            assertEquals(85, vm.th().pulseLow)
        }

    @Test
    fun `presets change only the high levels`() =
        runTest {
            thresholds.set(Thresholds(lowSys = 95, pulseHigh = 100))
            val vm = viewModel()
            vm.onEvent(LevelsEvent.PresetSelected(ThresholdPreset.CLINIC))
            assertEquals(Thresholds(highSys = 140, highDia = 90, lowSys = 95, pulseHigh = 100), vm.th())
            vm.onEvent(LevelsEvent.PresetSelected(ThresholdPreset.HOME))
            assertEquals(135 to 85, vm.th().highSys to vm.th().highDia)
        }

    @Test
    fun `a preset that breaks low below high is ignored`() =
        runTest {
            thresholds.set(Thresholds(highSys = 150, highDia = 95, lowDia = 85))
            val vm = viewModel()
            vm.onEvent(LevelsEvent.PresetSelected(ThresholdPreset.HOME))
            assertEquals(150 to 95, vm.th().highSys to vm.th().highDia)
        }

    @Test
    fun `dark theme switch sets an explicit mode`() =
        runTest {
            val vm = viewModel()
            assertEquals(ThemeMode.SYSTEM, vm.state.value.themeMode)
            vm.onEvent(LevelsEvent.DarkThemeChanged(true))
            assertEquals(ThemeMode.DARK, settings.themeMode.first())
            vm.onEvent(LevelsEvent.DarkThemeChanged(false))
            assertEquals(ThemeMode.LIGHT, vm.state.value.themeMode)
        }

    @Test
    fun `E2E - preset 140 over 90 turns the last reading on Home from high to normal`() =
        runTest {
            val time = FixedTimeProvider.default()
            val reading =
                Reading(
                    id = 1,
                    takenAt = Instant.parse("2026-10-20T04:24:00Z"),
                    zoneOffset = ZoneOffset.ofHours(3),
                    sys = 137,
                    dia = 86,
                    pulse = 70,
                    arm = Arm.LEFT,
                )
            val home =
                HomeViewModel(FakeReadingRepository(time, listOf(reading)), thresholds, FakeReminderRepository(), time)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { home.state.collect {} }
            val status = { (home.state.value as HomeUiState.Content).last!!.status }
            assertEquals(Status.HIGH, status())

            viewModel().onEvent(LevelsEvent.PresetSelected(ThresholdPreset.CLINIC))
            advanceTimeBy(WRITE_DEBOUNCE_MS + 1)
            assertEquals(Status.NORMAL, status())
        }
}
