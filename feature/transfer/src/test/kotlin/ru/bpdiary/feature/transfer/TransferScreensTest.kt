package ru.bpdiary.feature.transfer

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsNotEnabled
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
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.ui.assertAccessible
import ru.bpdiary.domain.csv.ColumnRole
import java.time.LocalDate

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class TransferScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null
    private val events = ArrayList<Any>()
    private val preview =
        ImportUiState(
            step = ImportStep.PREVIEW,
            fileName = "davlenie.csv",
            options = options,
            inspection = inspection,
            zoneOffset = "+03:00",
        )
    private val export =
        ExportUiState(
            from = LocalDate.of(2026, 9, 21),
            to = LocalDate.of(2026, 10, 20),
            count = 27,
        )

    private fun show(
        dark: Boolean = false,
        fontScale: Float = 1f,
        screen: @Composable () -> Unit,
    ) {
        rule.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BpTheme(Direction.SAGE, dark) { screen() }
            }
        }
    }

    private fun showImport(
        state: ImportUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) = show(dark, fontScale) { ImportScreen(state, { events += it }) }

    private fun showExport(
        state: ExportUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) = show(dark, fontScale) { ExportScreen(state, { events += it }) }

    private fun capture(name: String) {
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.assertAccessible()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun exportSageLight() {
        showExport(export)
        capture("export_sage_light")
    }

    @Test
    fun exportSageDark() {
        showExport(export, dark = true)
        capture("export_sage_dark")
    }

    @Test
    fun exportSageLightFontScale2() {
        showExport(export, fontScale = 2f)
        capture("export_sage_light_fs2")
    }

    @Test
    fun exportEmptySageLight() {
        showExport(export.copy(count = 0))
        capture("export_empty_sage_light")
    }

    @Test
    fun importPickSageLight() {
        showImport(ImportUiState())
        capture("import_pick_sage_light")
    }

    @Test
    fun importPickProblemSageDark() {
        showImport(ImportUiState(fileProblem = FileProblem.TOO_LARGE), dark = true)
        capture("import_pick_problem_sage_dark")
    }

    @Test
    fun importPreviewSageLight() {
        showImport(preview)
        capture("import_preview_sage_light")
    }

    @Test
    fun importPreviewSageDark() {
        showImport(preview, dark = true)
        capture("import_preview_sage_dark")
    }

    @Test
    fun importPreviewSageLightFontScale2() {
        showImport(preview, fontScale = 2f)
        capture("import_preview_sage_light_fs2")
    }

    @Test
    fun importPreviewOpenSageLight() {
        val errors = badRows(3)
        showImport(
            preview.copy(
                editingFormat = true,
                showingErrors = true,
                inspection =
                    inspection.copy(
                        ambiguousDayOrder = true,
                        valid = 24,
                        errors = errors,
                        errorCount = errors.size,
                    ),
            ),
        )
        capture("import_preview_open_sage_light")
    }

    @Test
    fun importStrategySageLight() {
        showImport(preview.copy(step = ImportStep.STRATEGY))
        capture("import_strategy_sage_light")
    }

    @Test
    fun importReplaceDialogSageLight() {
        showImport(preview.copy(step = ImportStep.STRATEGY, confirmingReplace = true))
        capture("import_replace_dialog_sage_light")
    }

    @Test
    fun importRunningSageLight() {
        showImport(preview.copy(step = ImportStep.RUNNING, progress = 0.4f))
        capture("import_running_sage_light")
    }

    @Test
    fun importResultSageLight() {
        showImport(preview.copy(step = ImportStep.RESULT, report = report))
        capture("import_result_sage_light")
    }

    @Test
    fun importResultErrorsSageDark() {
        val errors = badRows(10)
        showImport(
            preview.copy(
                step = ImportStep.RESULT,
                showingErrors = true,
                report = report.copy(errorCount = 12, errors = errors),
            ),
            dark = true,
        )
        capture("import_result_errors_sage_dark")
    }

    @Test
    fun importFailedSageLight() {
        showImport(preview.copy(step = ImportStep.RESULT, failed = true))
        capture("import_failed_sage_light")
    }

    @Test
    fun `every period of docs 13 is offered and sends its event`() {
        showExport(export)
        listOf("7 дней", "30 дней", "90 дней", "Всё время", "Период…").forEach {
            rule.onNodeWithText(it).performScrollTo().assertExists()
        }
        rule.onNodeWithText("90 дней").performClick()
        assertEquals(listOf<Any>(ExportEvent.PeriodSelected(ExportPeriod.DAYS_90)), events)
    }

    @Test
    fun `the range and the count of the period are shown`() {
        showExport(export)
        rule.onNodeWithText("21.09.2026 — 20.10.2026").assertExists()
        rule.onNodeWithText("27 измерений").assertExists()
    }

    @Test
    fun `an empty period cannot be saved or shared`() {
        showExport(export.copy(count = 0))
        rule.onNodeWithText("За этот период измерений нет").assertExists()
        rule.onNodeWithText("Сохранить файл").assertIsNotEnabled()
        rule.onNodeWithText("Поделиться").assertIsNotEnabled()
    }

    @Test
    fun `save, share and back send their events`() {
        showExport(export)
        rule.onNodeWithText("Сохранить файл").performClick()
        rule.onNodeWithText("Поделиться").performClick()
        rule.onNodeWithContentDescription("Назад").performClick()
        assertEquals(listOf(ExportEvent.SaveClicked, ExportEvent.ShareClicked, ExportEvent.BackClicked), events)
    }

    @Test
    fun `the result of the save is said in words`() {
        showExport(export.copy(saved = true))
        rule.onNodeWithText("Файл сохранён").assertExists()
    }

    @Test
    fun `the first step offers the file choice with the hint of docs 13`() {
        showImport(ImportUiState())
        rule.onNodeWithText("Подойдёт файл из другого дневника давления или таблицы Excel.").assertExists()
        rule.onNodeWithText("Выбрать файл").performClick()
        assertEquals(listOf<Any>(ImportEvent.PickClicked), events)
    }

    @Test
    fun `the preview shows the file, the columns, the note on time and the summary`() {
        showImport(preview)
        rule.onNodeWithText("davlenie.csv").assertExists()
        rule.onNodeWithText("Кодировка: UTF-8").assertExists()
        rule.onNodeWithText("Разделитель: запятая").assertExists()
        rule.onNodeWithText("Дата и время").assertExists()
        rule.onNodeWithText("суббота, до приёма").assertExists()
        rule.onNodeWithText("Время считается местным (UTC+03:00)").performScrollTo().assertExists()
        rule.onNodeWithText("Распознано 27 строк, ошибок 0").performScrollTo().assertExists()
        rule.onNodeWithText("Далее").performScrollTo().performClick()
        assertEquals(listOf<Any>(ImportEvent.NextClicked), events)
    }

    @Test
    fun `a column can be given another meaning`() {
        showImport(preview)
        rule.onNodeWithText("Комментарий").performScrollTo().performClick()
        rule.onNodeWithText("Не импортировать").performClick()
        assertEquals(listOf<Any>(ImportEvent.ColumnSelected(4, ColumnRole.IGNORE)), events)
    }

    @Test
    fun `the day order is asked only when the file does not tell`() {
        showImport(preview)
        rule.onNodeWithText("День.Месяц").assertDoesNotExist()
    }

    @Test
    fun `the day order appears for an ambiguous file`() {
        showImport(preview.copy(inspection = inspection.copy(ambiguousDayOrder = true)))
        rule.onNodeWithText("Месяц.День").performScrollTo().performClick()
        assertEquals(listOf<Any>(ImportEvent.DayOrderSelected(false)), events)
    }

    @Test
    fun `next is closed while the file cannot be imported`() {
        showImport(preview.copy(inspection = inspection.copy(valid = 0)))
        rule.onNodeWithText("Далее").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `the two strategies send their events`() {
        showImport(preview.copy(step = ImportStep.STRATEGY))
        rule.onNodeWithText("Добавить к имеющимся").performClick()
        rule.onNodeWithText("Заменить все данные").performClick()
        assertEquals(listOf(ImportEvent.AddClicked, ImportEvent.ReplaceClicked), events)
    }

    @Test
    fun `replace is confirmed in a dialog`() {
        showImport(preview.copy(step = ImportStep.STRATEGY, confirmingReplace = true))
        rule.onNodeWithText("Заменить").performClick()
        rule.onNodeWithText("Отмена").performClick()
        assertEquals(listOf(ImportEvent.ReplaceConfirmed, ImportEvent.DialogDismissed), events)
    }

    @Test
    fun `the result has the report and both actions`() {
        showImport(preview.copy(step = ImportStep.RESULT, report = report))
        rule.onNodeWithText("Импортировано 27 измерений").assertExists()
        rule.onNodeWithText("Показать ошибки").assertDoesNotExist()
        rule.onNodeWithText("Сохранить отчёт").performScrollTo().performClick()
        rule.onNodeWithText("Открыть дневник").performScrollTo().performClick()
        assertEquals(listOf(ImportEvent.SaveReportClicked, ImportEvent.OpenDiaryClicked), events)
    }

    @Test
    fun `the bad rows of the result open on request, with a count of the rest`() {
        val errors = badRows(10)
        showImport(
            preview.copy(
                step = ImportStep.RESULT,
                report = report.copy(errorCount = 12, errors = errors),
            ),
        )
        rule.onNodeWithText("Импортировано 27 из 39. 12 строк пропущено — показать?").assertExists()
        rule.onNodeWithText("Строка 1: верхнее значение не больше нижнего").assertDoesNotExist()
        rule.onNodeWithText("Показать ошибки").performScrollTo().performClick()
        assertEquals(listOf<Any>(ImportEvent.ShowErrorsClicked), events)
    }

    @Test
    fun `the list of bad rows ends with the count of the rest`() {
        val errors = badRows(10)
        showImport(
            preview.copy(
                step = ImportStep.RESULT,
                showingErrors = true,
                report = report.copy(errorCount = 12, errors = errors),
            ),
        )
        rule.onNodeWithText("Строка 1: верхнее значение не больше нижнего").performScrollTo().assertExists()
        rule.onNodeWithText("И ещё строк с ошибками: 2").performScrollTo().assertExists()
    }

    @Test
    fun `a file of duplicates only is named so`() {
        showImport(preview.copy(step = ImportStep.RESULT, report = report.copy(imported = 0, duplicates = 27)))
        rule.onNodeWithText("Эти измерения уже есть в дневнике.").assertExists()
    }

    @Test
    fun `a failed import says the data did not change and offers no report`() {
        showImport(preview.copy(step = ImportStep.RESULT, failed = true))
        rule.onNodeWithText("Не удалось сохранить. Данные не изменились.").assertExists()
        rule.onNodeWithText("Сохранить отчёт").assertDoesNotExist()
    }
}
