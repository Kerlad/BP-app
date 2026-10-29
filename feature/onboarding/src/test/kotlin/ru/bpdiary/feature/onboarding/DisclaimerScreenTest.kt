package ru.bpdiary.feature.onboarding

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class DisclaimerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null

    private fun show(
        state: DisclaimerUiState,
        dark: Boolean = false,
        onEvent: (DisclaimerEvent) -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            BpTheme(Direction.SAGE, dark) { DisclaimerScreen(state, onEvent) }
        }
    }

    private fun snapshot(
        name: String,
        state: DisclaimerUiState,
        dark: Boolean = false,
    ) {
        show(state, dark)
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun uncheckedSageLight() = snapshot("disclaimer_unchecked_sage_light", DisclaimerUiState(checked = false))

    @Test
    fun checkedSageDark() = snapshot("disclaimer_checked_sage_dark", DisclaimerUiState(checked = true), dark = true)

    @Test
    fun `the text is the one from the docs, word for word`() {
        show(DisclaimerUiState())
        rule
            .onNodeWithText(
                "Дневник давления помогает вести записи и не является медицинским прибором. " +
                    "Он не ставит диагноз и не заменяет консультацию врача. Все решения о лечении принимает врач.",
            ).assertExists()
    }

    @Test
    fun `the button is inactive until the box is ticked`() {
        show(DisclaimerUiState(checked = false))
        rule.onNodeWithText("Продолжить").assertIsNotEnabled()
        rule.onNodeWithText("Я прочитал(а) и понимаю").assertIsOff()
    }

    @Test
    fun `a ticked box activates the button`() {
        show(DisclaimerUiState(checked = true))
        rule.onNodeWithText("Продолжить").assertIsEnabled()
        rule.onNodeWithText("Я прочитал(а) и понимаю").assertIsOn()
    }

    @Test
    fun `controls send their events`() {
        val events = mutableListOf<DisclaimerEvent>()
        show(DisclaimerUiState(checked = true)) { events += it }
        rule.onNodeWithText("Я прочитал(а) и понимаю").performClick()
        rule.onNodeWithText("Продолжить").performClick()
        assertEquals(listOf(DisclaimerEvent.CheckedChanged(false), DisclaimerEvent.ContinueClicked), events)
    }
}
