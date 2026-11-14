package ru.bpdiary.feature.settings

import android.view.View
import androidx.compose.ui.platform.LocalView
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
class LicensesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null

    private fun show(
        dark: Boolean = false,
        onBack: () -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            BpTheme(Direction.SAGE, dark) {
                LicensesScreen(
                    fontLicense = "SIL OPEN FONT LICENSE Version 1.1 (test text)",
                    apacheLicense = "Apache License Version 2.0 (test text)",
                    onBack = onBack,
                )
            }
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
    fun sageLight() = snapshot("licenses_sage_light", dark = false)

    @Test
    fun sageDark() = snapshot("licenses_sage_dark", dark = true)

    @Test
    fun `both headings, the libraries and the texts are shown`() {
        show()
        rule.assertAccessible()
        rule.onNodeWithText("Шрифт Golos Text").assertExists()
        rule.onNodeWithText("Библиотеки").assertExists()
        rule.onNodeWithText("Hilt/Dagger", substring = true).assertExists()
        rule.onNodeWithText("SIL OPEN FONT LICENSE", substring = true).assertExists()
    }

    @Test
    fun `back goes back`() {
        var backs = 0
        show(onBack = { backs++ })
        rule.onNodeWithContentDescription("Назад").performClick()
        assertEquals(1, backs)
    }
}
