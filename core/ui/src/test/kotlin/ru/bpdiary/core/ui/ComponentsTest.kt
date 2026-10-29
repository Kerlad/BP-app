package ru.bpdiary.core.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.app.core.ui.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.Status
import ru.bpdiary.domain.Key

@RunWith(RobolectricTestRunner::class)
class ComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun themed(content: @Composable () -> Unit) =
        rule.setContent { BpTheme(Direction.SAGE, dark = false, content = content) }

    @Test
    fun `a disabled primary button takes no clicks, an enabled one does`() {
        var clicks = 0
        themed {
            Column {
                BpPrimaryButton("Выключена", onClick = { clicks += 10 }, enabled = false)
                BpPrimaryButton("Включена", onClick = { clicks += 1 })
            }
        }
        rule.onNodeWithText("Выключена").assertIsNotEnabled().performClick()
        rule.onNodeWithText("Включена").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `status chip is announced as a status and spelled out`() {
        themed { StatusChip(Status.HIGH) }
        rule.onNodeWithContentDescription("Статус: повышенное").assertExists()
    }

    @Test
    fun `every status has its own announcement`() {
        themed {
            StatusChip(Status.NORMAL)
            StatusChip(Status.LOW)
        }
        rule.onNodeWithContentDescription("Статус: в норме").assertExists()
        rule.onNodeWithContentDescription("Статус: пониженное").assertExists()
    }

    @Test
    fun `big reading reads as one sentence`() {
        themed { BigReading(sys = 128, dia = 82, size = BigReadingSize.Hero) }
        rule.onNodeWithContentDescription("128 на 82 миллиметра ртутного столба").assertExists()
    }

    @Test
    fun `unit uses non breaking spaces`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("мм рт. ст.", context.getString(R.string.unit_mmhg))
    }

    @Test
    fun `numpad keys carry the documented descriptions and send the right keys`() {
        val pressed = mutableListOf<Key>()
        themed { NumPad(onKey = { pressed += it }, action = NumPadAction.Next) }

        rule.onNodeWithContentDescription("цифра 7").performClick()
        rule.onNodeWithContentDescription("цифра 0").performClick()
        rule.onNodeWithContentDescription("стереть").performClick()
        rule.onNodeWithContentDescription("далее").performClick()

        assertEquals(listOf(Key.Digit('7'), Key.Digit('0'), Key.Del, Key.Next), pressed)
    }

    @Test
    fun `numpad action key says save on the pulse field`() {
        themed { NumPad(onKey = {}, action = NumPadAction.Save) }
        rule.onNodeWithContentDescription("сохранить").assertExists()
        rule.onNodeWithContentDescription("далее").assertDoesNotExist()
    }

    @Test
    fun `numpad has all ten digits`() {
        themed { NumPad(onKey = {}, action = NumPadAction.Next) }
        for (d in 0..9) rule.onNodeWithContentDescription("цифра $d").assertExists()
    }

    @Test
    fun `stepper disables a button instead of showing an error`() {
        var down = 0
        var up = 0
        themed {
            Stepper(
                display = "≥135",
                onDecrement = { down++ },
                onIncrement = { up++ },
                decrementDescription = "меньше",
                incrementDescription = "больше",
                canIncrement = false,
            )
        }
        rule.onNodeWithContentDescription("больше").assertIsNotEnabled()
        rule.onNodeWithContentDescription("меньше").performClick()
        rule.onNodeWithContentDescription("больше").performClick()
        assertEquals(1, down)
        assertEquals(0, up)
    }

    @Test
    fun `stepper buttons have a 48 dp touch target`() {
        themed {
            Stepper("1", {}, {}, decrementDescription = "меньше", incrementDescription = "больше")
        }
        rule.onNodeWithContentDescription("меньше").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("больше").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `switch toggles and has a 48 dp touch target`() {
        var checked = false
        themed { BpSwitch(checked = checked, onCheckedChange = { checked = it }) }
        rule
            .onNode(isToggleable())
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(true, checked)
    }

    @Test
    fun `segmented toggle reports the tapped segment and marks the selected one`() {
        var selected = 0
        var tapped = -1
        themed {
            SegmentedToggle(listOf("Левая", "Правая"), selectedIndex = selected, onSelect = { tapped = it })
        }
        rule.onNodeWithText("Левая").assertIsSelected()
        rule.onNodeWithText("Правая").assertIsNotSelected().performClick()
        assertEquals(1, tapped)
        assertEquals(0, selected)
    }

    @Test
    fun `filter chips report taps and keep a 48 dp touch height`() {
        val toggled = mutableListOf<Int>()
        themed {
            FilterChipRow(
                items = listOf("Хорошо", "Слабость"),
                selected = setOf(1),
                multiSelect = true,
                onToggle = { toggled += it },
            )
        }
        rule.onNodeWithText("Хорошо").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Слабость").assertIsSelected()
        assertEquals(listOf(0), toggled)
    }

    @Test
    fun `bottom navigation selects an item`() {
        var tapped = -1
        themed {
            BpBottomNav(
                items =
                    listOf(
                        BpNavItem(BpIcon.Home, "Главная"),
                        BpNavItem(BpIcon.MenuBook, "Дневник"),
                    ),
                selectedIndex = 0,
                onSelect = { tapped = it },
            )
        }
        rule.onNodeWithText("Главная").assertIsSelected()
        rule.onNodeWithText("Дневник").performClick()
        assertEquals(1, tapped)
    }

    @Test
    fun `top bar button has a description and works`() {
        var closed = false
        themed {
            BpTopBar(
                title = "Заголовок",
                navigationIcon = BpIcon.Close,
                navigationDescription = "закрыть",
                onNavigate = { closed = true },
            )
        }
        rule.onNodeWithContentDescription("закрыть").assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(true, closed)
    }

    @Test
    fun `clickable card reports clicks`() {
        var clicks = 0
        themed { BpCard(onClick = { clicks++ }) { Text("карточка") } }
        rule.onNodeWithText("карточка").performClick()
        assertEquals(1, clicks)
    }
}
