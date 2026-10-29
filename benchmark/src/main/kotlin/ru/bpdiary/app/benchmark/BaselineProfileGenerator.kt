package ru.bpdiary.app.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods of the start path: Home, the Entry screen with its keypad, and the other tabs
 * that open from the bottom navigation. The result is copied into `app/src/main/baseline-prof.txt`,
 * see `docs/benchmarks.md`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun seed() = seedDemoData()

    @Test
    fun generate() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()

            // Home -> Entry: type a reading with the keypad and leave without saving.
            tapText(RECORD)
            listOf("1", "2", "8", "8", "2", "7", "1").forEach { digit ->
                device.findObject(By.desc("цифра $digit"))?.click()
            }
            device.waitForIdle()
            // A typed draft asks before it is thrown away.
            device.pressBack()
            tapText(DISCARD)
            device.wait(Until.hasObject(By.text(RECORD)), TIMEOUT_MS)

            // The other tabs, so their first frame is precompiled too.
            listOf(DIARY, CHARTS, REMINDERS, HOME).forEach { tapText(it) }
        }

    private companion object {
        const val RECORD = "Записать давление"
        const val DISCARD = "Не сохранять"
        const val HOME = "Главная"
        const val DIARY = "Дневник"
        const val CHARTS = "Графики"
        const val REMINDERS = "Напоминания"
        const val TIMEOUT_MS = 5_000L
    }
}
