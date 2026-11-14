package ru.bpdiary.feature.settings

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsSelected
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
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.ui.assertAccessible

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class SettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null

    private fun show(
        state: SettingsUiState,
        dark: Boolean = false,
        onEvent: (SettingsEvent) -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            BpTheme(Direction.SAGE, dark) { SettingsScreen(state, onEvent) }
        }
    }

    private fun snapshot(
        name: String,
        state: SettingsUiState,
        dark: Boolean = false,
    ) {
        show(state, dark)
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.assertAccessible()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun defaultSageLight() = snapshot("settings_default_sage_light", SettingsUiState())

    @Test
    fun darkThemeSageDark() = snapshot("settings_dark_sage_dark", SettingsUiState(ThemeMode.DARK), dark = true)

    @Test
    fun secondDialogSageLight() =
        snapshot("settings_second_dialog_sage_light", SettingsUiState(dialog = DeleteDialog.Second))

    @Test
    fun `the three themes and the two rows, the current theme is selected`() {
        show(SettingsUiState(ThemeMode.LIGHT))
        rule.onNodeWithText("Настройки").assertExists()
        rule.onNodeWithText("Системная").assertExists()
        rule.onNodeWithText("Светлая").assertIsSelected()
        rule.onNodeWithText("Тёмная").assertExists()
        rule.onNodeWithText("О приложении").assertExists()
        rule.onNodeWithText("Удалить все данные").assertExists()
    }

    @Test
    fun `no dialog until the delete row is tapped`() {
        show(SettingsUiState())
        rule.onNodeWithText("Удалить навсегда").assertDoesNotExist()
        rule.onNodeWithText("Удалить все данные?").assertDoesNotExist()
    }

    @Test
    fun `the first dialog asks and offers to go on or cancel`() {
        show(SettingsUiState(dialog = DeleteDialog.First))
        rule.onNodeWithText("Удалить все данные?").assertExists()
        rule.onNodeWithText("Продолжить").assertExists()
        rule.onNodeWithText("Отмена").assertExists()
        rule.onNodeWithText("Удалить навсегда").assertDoesNotExist()
    }

    @Test
    fun `the second dialog has the button «Удалить навсегда»`() {
        show(SettingsUiState(dialog = DeleteDialog.Second))
        rule.onNodeWithText("Удалить навсегда", ignoreCase = false).assertExists()
        rule.onNodeWithText("Продолжить").assertDoesNotExist()
    }

    @Test
    fun `controls send their events`() {
        val events = mutableListOf<SettingsEvent>()
        show(SettingsUiState()) { events += it }
        rule.onNodeWithText("Тёмная").performClick()
        rule.onNodeWithText("Напоминания").performClick()
        rule.onNodeWithText("О приложении").performClick()
        rule.onNodeWithText("Удалить все данные").performClick()
        rule.onNodeWithContentDescription("Назад").performClick()
        assertEquals(
            listOf(
                SettingsEvent.ThemeSelected(ThemeMode.DARK),
                SettingsEvent.RemindersClicked,
                SettingsEvent.AboutClicked,
                SettingsEvent.DeleteClicked,
                SettingsEvent.BackClicked,
            ),
            events,
        )
    }

    @Test
    fun `the dialogs send confirm and dismiss`() {
        val events = mutableListOf<SettingsEvent>()
        show(SettingsUiState(dialog = DeleteDialog.Second)) { events += it }
        rule.onNodeWithText("Отмена").performClick()
        rule.onNodeWithText("Удалить навсегда").performClick()
        assertEquals(listOf(SettingsEvent.DialogDismissed, SettingsEvent.DeleteForeverConfirmed), events)
    }
}
