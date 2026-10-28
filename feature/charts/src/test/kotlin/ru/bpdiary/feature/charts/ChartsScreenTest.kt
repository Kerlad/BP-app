package ru.bpdiary.feature.charts

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.bpdiary.core.common.DemoData
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.Thresholds
import java.time.LocalDate

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Russian locale as forced by MainActivity: plurals («5 измерений») depend on it.
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class ChartsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null
    private val time = FixedTimeProvider.default()
    private val now = time.now().atZone(time.zone())
    private val demo = DemoData.readings(time)

    private val texts = ChartsTexts(ApplicationProvider.getApplicationContext<Context>().resources)

    private val week = chartsState(Period.DAYS_7, now, demo, Thresholds())
    private val month = chartsState(Period.DAYS_30, now, demo, Thresholds())
    private val notEnough = chartsState(Period.DAYS_7, now, demo.takeLast(1), Thresholds())

    private fun show(
        state: ChartsUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
        onEvent: (ChartsEvent) -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            Scaled(fontScale) {
                BpTheme(Direction.SAGE, dark) { ChartsScreen(state, onEvent) }
            }
        }
    }

    private fun snapshot(
        name: String,
        state: ChartsUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        show(state, dark, fontScale)
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun weekSageLight() = snapshot("charts_7_sage_light", week)

    @Test
    fun weekSageDark() = snapshot("charts_7_sage_dark", week, dark = true)

    @Test
    fun monthSageLight() = snapshot("charts_30_sage_light", month)

    @Test
    fun monthSageDark() = snapshot("charts_30_sage_dark", month, dark = true)

    @Test
    fun notEnoughSageLight() = snapshot("charts_not_enough_sage_light", notEnough)

    @Test
    fun notEnoughSageDark() = snapshot("charts_not_enough_sage_dark", notEnough, dark = true)

    @Test
    fun weekSageLightFontScale2() = snapshot("charts_7_sage_light_fs2", week, fontScale = 2f)

    @Test
    fun monthSageDarkFontScale2() = snapshot("charts_30_sage_dark_fs2", month, dark = true, fontScale = 2f)

    @Test
    fun `week texts`() {
        show(week)
        rule.onNodeWithText("Графики").assertExists()
        rule.onNodeWithText("14.10").assertExists()
        rule.onNodeWithText("Сегодня").assertExists()
        rule.onNodeWithText("Ваш порог").assertExists()
        rule
            .onNodeWithContentDescription("График за 7 дней, среднее 133 на 85, максимум 154 на 94 15 октября")
            .assertExists()
        rule.onNodeWithText("54%").performScrollTo().assertExists()
    }

    @Test
    fun `not enough data hides the statistics`() {
        show(notEnough)
        rule.onNodeWithText("Мало данных для графика. Нужно хотя бы два измерения").assertExists()
        rule.onNodeWithText("Среднее").assertDoesNotExist()
        rule.onNodeWithText("В пределах ваших уровней").assertDoesNotExist()
    }

    @Test
    fun `period toggle sends its event`() {
        val events = mutableListOf<ChartsEvent>()
        show(week) { events += it }
        rule.onNodeWithText("30 дней").performClick()
        assertEquals(listOf(ChartsEvent.PeriodSelected(Period.DAYS_30)), events)
    }

    @Test
    fun `statistics cells (golden 7 days)`() {
        val cells = texts.cells(week.stats!!, LocalDate.of(2026, 10, 20))
        assertEquals(
            listOf(
                StatCell("Среднее", "133/85", "мм рт. ст."),
                StatCell("Пульс", "72", "уд/мин в среднем"),
                StatCell("Утром", "130/83", "7 измерений"),
                StatCell("Вечером", "138/87", "6 измерений"),
                StatCell("Максимум", "154/94", "15 октября, четверг"),
                StatCell("Минимум", "121/79", "14 октября, среда"),
            ),
            cells,
        )
    }

    @Test
    fun `plurals and empty part of the day`() {
        val stats = week.stats!!
        val morning = { n: Int ->
            texts.cells(stats.copy(morning = stats.morning!!.copy(count = n)), now.toLocalDate())[2]
        }
        assertEquals("1 измерение", morning(1).caption)
        assertEquals("3 измерения", morning(3).caption)
        assertEquals("5 измерений", morning(5).caption)
        assertEquals("21 измерение", morning(21).caption)
        val none = texts.cells(stats.copy(morning = null), now.toLocalDate())[2]
        assertEquals("—" to "0 измерений", none.value to none.caption)
        assertEquals("1.11", texts.fromDate(LocalDate.of(2026, 11, 1)))
    }
}

@Composable
private fun Scaled(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
}
