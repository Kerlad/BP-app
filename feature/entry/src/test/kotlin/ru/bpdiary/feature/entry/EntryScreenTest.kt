package ru.bpdiary.feature.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.Field
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.ui.assertAccessible
import ru.bpdiary.domain.DayLabel
import ru.bpdiary.domain.Draft
import ru.bpdiary.domain.Key
import ru.bpdiary.domain.Reason
import ru.bpdiary.domain.ValidationError
import java.time.LocalTime

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class EntryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val empty =
        EntryUiState(loading = false, arm = Arm.LEFT, dayLabel = DayLabel.Today, time = LocalTime.of(9, 12))

    private val filled =
        empty.copy(
            draft = Draft(sys = "128", dia = "82", pulse = "71", field = Field.PULSE),
            tags = setOf(Tag.GOOD, Tag.AFTER_MEDICINE),
            comment = "После утренней прогулки",
            liveStatus = LiveStatus.Classified(Status.NORMAL, Hint.Within),
            canSave = true,
        )

    private val invalid =
        empty.copy(
            draft = Draft(sys = "80", dia = "90", pulse = "70", field = Field.PULSE),
            liveStatus = LiveStatus.Classified(Status.HIGH, Hint.Above(135, 85)),
            error = ValidationError(Field.SYS, Reason.SYS_NOT_GREATER),
            canSave = true,
        )

    private val edit =
        filled.copy(
            isEdit = true,
            draft = Draft(sys = "152", dia = "96", pulse = "88"),
            arm = Arm.RIGHT,
            dayLabel = DayLabel.Yesterday,
            time = LocalTime.of(21, 40),
            comment =
                "Голова болела с утра, принял таблетку в восемь, " +
                    "к вечеру стало легче, но всё ещё шумит в ушах при наклоне",
            liveStatus = LiveStatus.Classified(Status.HIGH, Hint.Above(135, 85)),
        )

    private fun show(
        state: EntryUiState,
        direction: Direction = Direction.SAGE,
        dark: Boolean = false,
        fontScale: Float = 1f,
        onEvent: (EntryEvent) -> Unit = {},
    ) {
        rule.setContent {
            Scaled(fontScale) {
                BpTheme(direction, dark) { EntryScreen(state, onEvent) }
            }
        }
    }

    private fun snapshot(
        name: String,
        state: EntryUiState,
        direction: Direction = Direction.SAGE,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        show(state, direction, dark, fontScale)
        rule.assertAccessible()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun emptySageLight() = snapshot("entry_empty_sage_light", empty)

    @Test
    fun emptySageDark() = snapshot("entry_empty_sage_dark", empty, dark = true)

    @Test
    fun filledSageLight() = snapshot("entry_filled_sage_light", filled)

    @Test
    fun filledSageDark() = snapshot("entry_filled_sage_dark", filled, dark = true)

    @Test
    fun filledPeachLight() = snapshot("entry_filled_peach_light", filled, Direction.PEACH)

    @Test
    fun filledPlumDark() = snapshot("entry_filled_plum_dark", filled, Direction.PLUM, dark = true)

    @Test
    fun filledPeachDark() = snapshot("entry_filled_peach_dark", filled, Direction.PEACH, dark = true)

    @Test
    fun filledPlumLight() = snapshot("entry_filled_plum_light", filled, Direction.PLUM)

    @Test
    fun filledSageLightFontScale15() = snapshot("entry_filled_sage_light_fs15", filled, fontScale = 1.5f)

    @Test
    fun errorSageLight() = snapshot("entry_error_sage_light", invalid)

    @Test
    fun errorSageDark() = snapshot("entry_error_sage_dark", invalid, dark = true)

    @Test
    fun editSageLight() = snapshot("entry_edit_sage_light", edit)

    @Test
    fun editSageDark() = snapshot("entry_edit_sage_dark", edit, dark = true)

    @Test
    fun filledSageLightFontScale2() = snapshot("entry_filled_sage_light_fs2", filled, fontScale = 2f)

    @Test
    fun errorSageDarkFontScale2() = snapshot("entry_error_sage_dark_fs2", invalid, dark = true, fontScale = 2f)

    @Test
    fun `texts of the screen`() {
        show(invalid)
        rule.onNodeWithText("Новое измерение").assertExists()
        rule.onNodeWithText("Сегодня, 09:12").assertExists()
        rule.onNodeWithText("Верхнее должно быть больше нижнего").assertExists()
        rule.onNodeWithText("выше 135/85").assertExists()
        rule.onNodeWithText("Свой комментарий").assertExists()
    }

    @Test
    fun `empty state asks for values and save stays clickable to highlight the gap`() {
        show(empty)
        rule.onNodeWithText("Введите значения").assertExists()
        rule.onNodeWithText("Сохранить").assertHasClickAction()
        rule.onNodeWithText("Удалить").assertDoesNotExist()
    }

    @Test
    fun `edit mode shows its title, time, delete and the counter from 100 characters`() {
        show(edit)
        rule.onNodeWithText("Измерение").assertExists()
        rule.onNodeWithText("Вчера, 21:40").assertExists()
        rule.onNodeWithText("Удалить").assertExists()
        rule.onNodeWithText("${edit.comment.length}/120").assertExists()
    }

    @Test
    fun `controls send their events`() {
        val events = mutableListOf<EntryEvent>()
        show(filled) { events += it }
        rule.onNodeWithText("Нижнее").performClick()
        rule.onNodeWithText("Правая").performClick()
        rule.onNodeWithText("Слабость").performScrollTo().performClick()
        rule.onNodeWithContentDescription("цифра 5").performClick()
        rule.onNodeWithContentDescription("сохранить").performClick()
        rule.onNodeWithText("Сохранить").performClick()
        rule.onNodeWithContentDescription("Закрыть").performClick()
        assertEquals(
            listOf(
                EntryEvent.FieldTapped(Field.DIA),
                EntryEvent.ArmSelected(Arm.RIGHT),
                EntryEvent.TagToggled(Tag.WEAKNESS),
                EntryEvent.KeyPressed(Key.Digit('5')),
                EntryEvent.KeyPressed(Key.Next),
                EntryEvent.SaveClicked,
                EntryEvent.CloseClicked,
            ),
            events,
        )
    }

    @Test
    fun `discard dialog offers to leave or continue`() {
        val events = mutableListOf<EntryEvent>()
        show(filled.copy(dialog = EntryDialog.Discard)) { events += it }
        rule.onNodeWithText("Не сохранять измерение?").assertExists()
        rule.onNodeWithText("Продолжить ввод").performClick()
        rule.onNodeWithText("Не сохранять").performClick()
        assertEquals(listOf(EntryEvent.DialogDismissed, EntryEvent.DiscardConfirmed), events)
    }

    /** Landscape or split screen: 360 dp tall. Fields, keypad and Save must all be reachable (review A-05). */
    @Test
    @Config(qualifiers = "w640dp-h360dp-xxhdpi")
    fun `on a low screen the fields, keypad and save scroll into view`() {
        show(filled)
        rule.onNodeWithText("Верхнее").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("7").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Сохранить").performScrollTo().assertIsDisplayed()
        rule.assertAccessible()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-xxhdpi")
    fun lowScreenSageLight() = snapshot("entry_low_screen_sage_light", filled)
}

@Composable
private fun Scaled(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
}
