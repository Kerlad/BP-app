package ru.bpdiary.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.DISCLAIMER_VERSION
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.domain.LaunchGate
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeSettingsRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the appearance is read from the settings, theme and font boost together`() =
        runTest {
            settings.setThemeMode(ThemeMode.DARK)
            settings.setFontScaleBoost(1.15f)
            val vm = ShellViewModel(FakeReadingRepository(FixedTimeProvider.default()), settings)
            assertEquals(Appearance(ThemeMode.DARK, 1.15f), vm.appearance.value)
        }

    @Test
    fun `later changes reach the screen`() =
        runTest {
            val vm = ShellViewModel(FakeReadingRepository(FixedTimeProvider.default()), settings)
            assertEquals(Appearance(ThemeMode.SYSTEM, 1f), vm.appearance.value)
            settings.setThemeMode(ThemeMode.LIGHT)
            assertEquals(Appearance(ThemeMode.LIGHT, 1f), vm.appearance.value)
        }

    @Test
    fun `app start purges readings deleted more than 24 hours ago (D-17)`() =
        runTest {
            val time = ManualTimeProvider(Instant.parse("2026-10-20T06:12:00Z"), ZoneId.of("Europe/Moscow"))
            val readings = FakeReadingRepository(time)
            val old = readings.save(reading(time.now()))
            val recent = readings.save(reading(time.now()))
            readings.softDelete(old)
            time.advance(Duration.ofHours(23))
            readings.softDelete(recent)
            time.advance(Duration.ofHours(2))

            ShellViewModel(readings, settings)

            assertEquals(1, readings.storedCount)
        }

    @Test
    fun `a fresh install starts with the onboarding`() =
        runTest {
            val vm = ShellViewModel(FakeReadingRepository(FixedTimeProvider.default()), settings)
            assertEquals(LaunchGate.ONBOARDING, vm.launchGate.value)
        }

    @Test
    fun `the gate follows the onboarding and the accepted disclaimer`() =
        runTest {
            val vm = ShellViewModel(FakeReadingRepository(FixedTimeProvider.default()), settings)
            settings.setOnboardingDone(true)
            assertEquals(LaunchGate.DISCLAIMER, vm.launchGate.value)
            settings.setDisclaimerAck(DISCLAIMER_VERSION)
            assertEquals(LaunchGate.MAIN, vm.launchGate.value)
        }

    private fun reading(at: Instant) =
        Reading(takenAt = at, zoneOffset = ZoneOffset.ofHours(3), sys = 120, dia = 80, pulse = 70, arm = Arm.LEFT)
}
