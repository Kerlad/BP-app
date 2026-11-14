package ru.bpdiary.feature.reminders

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import ru.bpdiary.domain.DaysSummary
import ru.bpdiary.domain.WORKDAYS
import java.time.DayOfWeek
import java.time.LocalTime

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class ReminderEditScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null

    private val new = ReminderEditUiState(loaded = true)
    private val existing =
        ReminderEditUiState(
            loaded = true,
            isNew = false,
            time = LocalTime.of(14, 0),
            label = "Дневное измерение",
            days = WORKDAYS + DayOfWeek.SATURDAY,
        )
    private val noDays = new.copy(days = emptySet())

    private fun show(
        state: ReminderEditUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
        onEvent: (ReminderEditEvent) -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            Scaled(fontScale) { BpTheme(Direction.SAGE, dark) { ReminderEditScreen(state, onEvent) } }
        }
    }

    private fun snapshot(
        name: String,
        state: ReminderEditUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        show(state, dark, fontScale)
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.assertAccessible()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun newSageLight() = snapshot("reminder_edit_new_sage_light", new)

    @Test
    fun newSageDark() = snapshot("reminder_edit_new_sage_dark", new, dark = true)

    @Test
    fun existingSageDark() = snapshot("reminder_edit_existing_sage_dark", existing, dark = true)

    @Test
    fun noDaysSageLight() = snapshot("reminder_edit_no_days_sage_light", noDays)

    @Test
    fun newSageLightFontScale2() = snapshot("reminder_edit_new_sage_light_fs2", new, fontScale = 2f)

    @Test
    fun `a new reminder has no delete button, an existing one has`() {
        show(new)
        rule.onNodeWithText("Новое напоминание").assertExists()
        rule.onNodeWithText("Удалить напоминание").assertDoesNotExist()
    }

    @Test
    fun `an existing reminder shows its name, days and delete button`() {
        show(existing)
        rule.onNodeWithText("Напоминание").assertExists()
        rule.onNodeWithText("Дневное измерение").assertExists()
        rule.onNodeWithText("Удалить напоминание").performScrollTo().assertExists()
        rule.onNodeWithText("Пн").performScrollTo().assertIsSelected()
        rule.onNodeWithText("Сб").performScrollTo().assertIsSelected()
        rule.onNodeWithText("Вс").performScrollTo().assertIsNotSelected()
    }

    @Test
    fun `done is inactive without days`() {
        val events = mutableListOf<ReminderEditEvent>()
        show(noDays) { events += it }
        rule.onNodeWithText("Готово").assertIsNotEnabled()
        rule.onNodeWithText("Готово").performClick()
        assertEquals(emptyList<ReminderEditEvent>(), events)
    }

    @Test
    fun `presets are highlighted when the days match`() {
        show(new)
        rule.onNodeWithText("Ежедневно").assertIsSelected()
        rule.onNodeWithText("Пн – Пт").assertIsNotSelected()
        rule.onNodeWithText("Готово").assertIsEnabled()
    }

    @Test
    fun `controls send their events`() {
        val events = mutableListOf<ReminderEditEvent>()
        show(existing) { events += it }
        rule.onNodeWithText("Пн – Пт").performScrollTo().performClick()
        rule.onNodeWithText("Вс").performScrollTo().performClick()
        rule.onNodeWithText("Удалить напоминание").performScrollTo().performClick()
        rule.onNodeWithText("Готово").performClick()
        assertEquals(
            listOf(
                ReminderEditEvent.PresetSelected(DaysSummary.Workdays),
                ReminderEditEvent.DayToggled(DayOfWeek.SUNDAY),
                ReminderEditEvent.DeleteClicked,
                ReminderEditEvent.DoneClicked("Измерение давления"),
            ),
            events,
        )
    }

    @Test
    fun `the delete dialog confirms or dismisses`() {
        val events = mutableListOf<ReminderEditEvent>()
        show(existing.copy(confirmDelete = true)) { events += it }
        rule.onNodeWithText("Удалить напоминание?").assertExists()
        rule.onNodeWithText("Отмена").performClick()
        rule.onNodeWithText("Удалить").performClick()
        assertEquals(listOf(ReminderEditEvent.DialogDismissed, ReminderEditEvent.DeleteConfirmed), events)
    }

    @Test
    fun `nothing to edit before an existing reminder is loaded`() {
        show(ReminderEditUiState(loaded = false, isNew = false))
        rule.onNodeWithText("Готово").assertIsNotEnabled()
        rule.onNodeWithText("Ежедневно").assertDoesNotExist()
    }

    @Test
    fun `a clash with another reminder is shown above Done`() {
        show(new.copy(sameTimeTaken = true))
        rule.onNodeWithText("Напоминание на это время уже есть.").assertExists()
    }

    @Test
    fun `closing with changes asks, both answers reach the view model`() {
        val events = mutableListOf<ReminderEditEvent>()
        show(new.copy(confirmDiscard = true)) { events += it }
        rule.onNodeWithText("Не сохранять напоминание?").assertExists()
        rule.onNodeWithText("Продолжить ввод").performClick()
        rule.onNodeWithText("Не сохранять").performClick()
        assertEquals(listOf(ReminderEditEvent.DialogDismissed, ReminderEditEvent.DiscardConfirmed), events)
    }
}
