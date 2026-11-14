package ru.bpdiary.feature.diary

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
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
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.ui.assertAccessible
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class DiaryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null
    private val today = LocalDate.of(2026, 10, 20)
    private val texts = DiaryTexts(ApplicationProvider.getApplicationContext<Context>().resources)

    private val demo = DemoData.readings(FixedTimeProvider.default()).sortedByDescending { it.takenAt }

    private val tagged =
        Reading(
            id = 100,
            takenAt = Instant.parse("2026-10-20T04:24:00Z"),
            zoneOffset = ZoneOffset.ofHours(3),
            sys = 152,
            dia = 96,
            pulse = 95,
            arm = Arm.RIGHT,
            tags = setOf(Tag.AFTER_MEDICINE, Tag.HEADACHE),
            comment = "после зарядки",
        )

    private fun show(
        readings: List<Reading>,
        state: DiaryUiState = DiaryUiState(today = today, total = readings.size),
        dark: Boolean = false,
        fontScale: Float = 1f,
        onEvent: (DiaryEvent) -> Unit = {},
    ) {
        val loaded = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
        val data = PagingData.from(readings, sourceLoadStates = loaded).toDiaryItems(Thresholds())
        rule.setContent {
            view = LocalView.current
            Scaled(fontScale) {
                BpTheme(Direction.SAGE, dark) {
                    DiaryScreen(state, flowOf(data).collectAsLazyPagingItems(), onEvent)
                }
            }
        }
    }

    private fun snapshot(
        name: String,
        readings: List<Reading>,
        state: DiaryUiState = DiaryUiState(today = today, total = readings.size),
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        show(readings, state, dark, fontScale)
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.assertAccessible()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private val withTagged = (listOf(tagged) + demo).sortedByDescending { it.takenAt }

    @Test
    fun `import and export open from the title, also with an empty diary`() {
        val events = ArrayList<DiaryEvent>()
        show(emptyList(), onEvent = { events += it })
        rule.onNodeWithText("Импорт").performClick()
        rule.onNodeWithText("Экспорт").performClick()
        assertEquals(listOf(DiaryEvent.ImportClicked, DiaryEvent.ExportClicked), events)
    }

    private val highOnlyEmpty = DiaryUiState(filter = DiaryFilter.LOW, today = today, total = demo.size)

    @Test
    fun demoSageLight() = snapshot("diary_demo_sage_light", withTagged)

    @Test
    fun demoSageDark() = snapshot("diary_demo_sage_dark", withTagged, dark = true)

    @Test
    fun demoSageLightFontScale2() = snapshot("diary_demo_sage_light_fs2", withTagged, fontScale = 2f)

    @Test
    fun demoSageDarkFontScale2() = snapshot("diary_demo_sage_dark_fs2", withTagged, dark = true, fontScale = 2f)

    @Test
    fun filterEmptySageLight() = snapshot("diary_filter_empty_sage_light", emptyList(), highOnlyEmpty)

    @Test
    fun filterEmptySageDark() = snapshot("diary_filter_empty_sage_dark", emptyList(), highOnlyEmpty, dark = true)

    @Test
    fun emptySageLight() = snapshot("diary_empty_sage_light", emptyList())

    @Test
    fun emptySageDark() = snapshot("diary_empty_sage_dark", emptyList(), dark = true)

    @Test
    fun emptySageLightFontScale2() = snapshot("diary_empty_sage_light_fs2", emptyList(), fontScale = 2f)

    @Test
    fun `headers and the row note`() {
        show(withTagged)
        rule.onNodeWithText("Дневник").assertExists()
        rule.onNodeWithText("Сегодня").assertExists()
        rule.onNodeWithText("Вчера").assertExists()
        rule
            .onNodeWithText("Повышенное · правая рука · Головная боль · После лекарства · после зарядки")
            .assertExists()
        rule.onNodeWithContentDescription("152 на 96 миллиметра ртутного столба").assertExists()
        rule.onNodeWithContentDescription("пульс 95", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `empty states`() {
        show(emptyList(), highOnlyEmpty)
        rule.onNodeWithText("Нет измерений с такой отметкой").assertExists()
        rule.onNodeWithText("Дневник пуст").assertDoesNotExist()
    }

    @Test
    fun `empty diary offers to add a reading`() {
        val events = mutableListOf<DiaryEvent>()
        show(emptyList()) { events += it }
        rule.onNodeWithText("Дневник пуст").assertExists()
        rule.onNodeWithText("Записать давление").performClick()
        assertEquals(listOf(DiaryEvent.AddClicked), events)
    }

    @Test
    fun `tap, filter, long-press menu and swipe send their events`() {
        val events = mutableListOf<DiaryEvent>()
        show(withTagged) { events += it }
        val note = "Повышенное · правая рука · Головная боль · После лекарства · после зарядки"
        rule.onNodeWithText("Норма").performClick()
        rule.onNodeWithText(note).performClick()
        rule.onNodeWithText(note).performTouchInput { longClick() }
        rule.onNodeWithText("Копировать значения").performClick()
        rule.onNodeWithText(note).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val row = tagged.toRow(Thresholds())
        assertEquals(
            listOf(
                DiaryEvent.FilterSelected(DiaryFilter.NORMAL),
                DiaryEvent.RowClicked(100),
                DiaryEvent.CopyClicked(row),
                DiaryEvent.DeleteClicked(100),
            ),
            events,
        )
    }

    @Test
    fun `day headers (docs 06 §6 3)`() {
        assertEquals("Сегодня", texts.header(today, today))
        assertEquals("Вчера", texts.header(today.minusDays(1), today))
        assertEquals("18 октября, воскресенье", texts.header(today.minusDays(2), today))
    }

    @Test
    fun `note leaves out empty parts and keeps the tag order`() {
        val row = tagged.toRow(Thresholds())
        assertEquals(
            "Повышенное · правая рука · Головная боль · После лекарства · после зарядки",
            texts.note(row),
        )
        val plain = row.copy(status = Status.NORMAL, arm = Arm.LEFT, tags = emptySet(), comment = "  ")
        assertEquals("В норме · левая рука", texts.note(plain))
        assertEquals("09:12", texts.time(plain.copy(time = LocalTime.of(9, 12))))
    }

    @Test
    fun `copied values`() {
        val row = tagged.toRow(Thresholds())
        assertEquals("152/96 мм рт. ст., пульс 95 уд/мин", texts.copy(row))
        assertEquals("152/96 мм рт. ст.", texts.copy(row.copy(pulse = null)))
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
