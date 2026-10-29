package ru.bpdiary.app.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.bpdiary.app.MainActivity

/**
 * E2E scenario 2 of docs/10 §10.5: `tune` → preset 140/90 → back → the chip of the last reading changes its status,
 * and the diary rows are recoloured. The reading is 138/86: raised under the default 135/85, in range under 140/90.
 */
@RunWith(AndroidJUnit4::class)
class ChangeLevelsTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aClinicPresetRecoloursTheLastReadingAndTheDiary() {
        rule.passOnboarding()
        rule.recordReading(sys = "138", dia = "86", pulse = "71")
        rule.waitForDescription("Статус: повышенное")

        rule.tapDescription("Уровни давления")
        rule.tapText("140/90")
        rule.tapDescription("Назад")

        rule.waitForDescription("Статус: в норме")

        // The diary writes the status in the note of the row, and it is recomputed with the new levels.
        rule.tapText("Дневник")
        // The filter chips carry the same words, so the note is recognised by its separator.
        rule.waitForTextContaining("В норме · ")
        assertFalse(rule.hasTextContaining("Повышенное · "))
    }
}
