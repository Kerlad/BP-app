package ru.bpdiary.app.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.bpdiary.app.MainActivity

/**
 * E2E scenario 1 of docs/10 §10.5: «Записать давление» → `1 2 8` `8 2` `7 1` → «Сохранить»: Home shows 128/82
 * and the toast.
 */
@RunWith(AndroidJUnit4::class)
class RecordReadingTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun anEnteredReadingShowsOnHomeWithAToast() {
        rule.passOnboarding()

        // 128 + 82 + 71: the field moves on by itself after three digits and after two digits that start with 8.
        rule.recordReading(sys = "128", dia = "82", pulse = "71")

        rule.waitForText("Измерение сохранено")
        rule.waitForDescription("128 на 82 миллиметра ртутного столба")
    }
}
