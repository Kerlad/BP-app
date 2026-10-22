package ru.bpdiary.data.datastore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.model.Thresholds

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataStoreTest {
    private lateinit var raw: InMemoryPreferencesStore
    private lateinit var settings: SettingsDataStore

    @Before
    fun setUp() {
        raw = InMemoryPreferencesStore()
        settings = SettingsDataStore(raw)
    }

    private suspend fun assertRejected(thresholds: Thresholds) {
        try {
            settings.setThresholds(thresholds)
            fail("Expected $thresholds to be rejected")
        } catch (expected: IllegalArgumentException) {
            // Rejected as intended.
        }
    }

    @Test
    fun `defaults on a fresh file`() =
        runTest {
            assertEquals(Thresholds(), settings.thresholds.first())
            assertEquals(Arm.LEFT, settings.lastArm.first())
            assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
            assertEquals(Direction.SAGE, settings.direction.first())
            assertEquals(1.0f, settings.fontScaleBoost.first(), 0f)
            assertEquals(false, settings.onboardingDone.first())
            assertEquals(0, settings.disclaimerAck.first())
        }

    @Test
    fun `values round trip`() =
        runTest {
            val th = Thresholds(highSys = 140, highDia = 90, lowSys = 105, lowDia = 65, pulseLow = 55, pulseHigh = 95)
            settings.setThresholds(th)
            settings.setLastArm(Arm.RIGHT)
            settings.setThemeMode(ThemeMode.DARK)
            settings.setDirection(Direction.PLUM)
            settings.setFontScaleBoost(1.15f)
            settings.setOnboardingDone(true)
            settings.setDisclaimerAck(1)

            assertEquals(th, settings.thresholds.first())
            assertEquals(Arm.RIGHT, settings.lastArm.first())
            assertEquals(ThemeMode.DARK, settings.themeMode.first())
            assertEquals(Direction.PLUM, settings.direction.first())
            assertEquals(1.15f, settings.fontScaleBoost.first(), 0f)
            assertEquals(true, settings.onboardingDone.first())
            assertEquals(1, settings.disclaimerAck.first())
        }

    @Test
    fun `invariant violation is rejected and nothing is written`() =
        runTest {
            assertRejected(Thresholds(highSys = 100, lowSys = 100))
            assertRejected(Thresholds(pulseLow = 95, pulseHigh = 90))
            assertRejected(Thresholds(highSys = 300))
            assertEquals(Thresholds(), settings.thresholds.first())
        }

    @Test
    fun `corrupted stored values fall back to defaults`() =
        runTest {
            raw.edit {
                it[stringPreferencesKey("last_arm")] = "UP"
                it[stringPreferencesKey("theme_mode")] = "PINK"
                it[stringPreferencesKey("direction")] = ""
                it[intPreferencesKey("th_high_sys")] = 90
                it[intPreferencesKey("th_low_sys")] = 100
            }
            assertEquals(Arm.LEFT, settings.lastArm.first())
            assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
            assertEquals(Direction.SAGE, settings.direction.first())
            assertEquals(Thresholds(), settings.thresholds.first())
        }

    @Test
    fun `thresholds flow does not repeat on unrelated writes`() =
        runTest(UnconfinedTestDispatcher()) {
            val seen = mutableListOf<Thresholds>()
            val job = launch { settings.thresholds.toList(seen) }

            settings.setThemeMode(ThemeMode.DARK)
            settings.setLastArm(Arm.RIGHT)
            settings.setThresholds(Thresholds(highSys = 140, highDia = 90))
            settings.setThemeMode(ThemeMode.LIGHT)
            job.cancel()

            assertEquals(listOf(Thresholds(), Thresholds(highSys = 140, highDia = 90)), seen)
        }

    @Test
    fun `clear restores the defaults`() =
        runTest {
            settings.setThresholds(Thresholds(highSys = 140, highDia = 90))
            settings.setOnboardingDone(true)
            settings.setDisclaimerAck(1)
            settings.setThemeMode(ThemeMode.DARK)

            settings.clear()

            assertEquals(Thresholds(), settings.thresholds.first())
            assertEquals(false, settings.onboardingDone.first())
            assertEquals(0, settings.disclaimerAck.first())
            assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
        }
}
