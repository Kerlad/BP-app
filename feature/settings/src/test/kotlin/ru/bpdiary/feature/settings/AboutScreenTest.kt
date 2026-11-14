package ru.bpdiary.feature.settings

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.ui.assertAccessible

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class AboutScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null

    private fun show(
        dark: Boolean = false,
        onBack: () -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            BpTheme(Direction.SAGE, dark) { AboutScreen(version = "0.1.0", onBack = onBack) }
        }
    }

    private fun snapshot(
        name: String,
        dark: Boolean,
    ) {
        show(dark)
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.assertAccessible()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun sageLight() = snapshot("about_sage_light", dark = false)

    @Test
    fun sageDark() = snapshot("about_sage_dark", dark = true)

    @Test
    fun `the disclaimer and the urgent care line are the ones from the docs, word for word`() {
        show()
        rule
            .onNodeWithText(
                "Дневник давления помогает вести записи и не является медицинским прибором. " +
                    "Он не ставит диагноз и не заменяет консультацию врача. Все решения о лечении принимает врач.",
            ).assertExists()
        rule.onNodeWithText("При резком ухудшении самочувствия обратитесь за медицинской помощью").assertExists()
    }

    @Test
    fun `the version and the note about a new phone are shown`() {
        show()
        rule.onNodeWithText("Версия 0.1.0").assertExists()
        rule.onNodeWithText("Из облачной копии записи не восстанавливаются.", substring = true).assertExists()
    }

    @Test
    fun `the privacy policy is a link to the public page`() {
        show()
        rule.onNodeWithText("Политика конфиденциальности").assertHasClickAction()
    }

    @Test
    fun `back goes back`() {
        var backs = 0
        show(onBack = { backs++ })
        rule.onNodeWithContentDescription("Назад").performClick()
        assertEquals(1, backs)
    }
}
