package ru.bpdiary.feature.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.domain.csv.ColumnRole
import ru.bpdiary.domain.csv.CsvEncoding
import ru.bpdiary.domain.csv.CsvLimit
import ru.bpdiary.domain.csv.CsvLimitException
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class ImportViewModelTest {
    private val workspace = FakeImportWorkspace()
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ImportViewModel(workspace, resources, FixedTimeProvider.default())

    private fun ImportViewModel.atPreview() = apply { onEvent(ImportEvent.FileChosen("content://file")) }

    private fun ImportViewModel.atStrategy() = atPreview().apply { onEvent(ImportEvent.NextClicked) }

    @Test
    fun `the note about local time shows the offset of the zone`() {
        assertEquals("+03:00", viewModel().state.value.zoneOffset)
    }

    @Test
    fun `a chosen file comes to the preview with its name and what was found`() {
        val state = viewModel().atPreview().state.value
        assertEquals(ImportStep.PREVIEW, state.step)
        assertEquals("davlenie.csv", state.fileName)
        assertEquals(options, state.options)
        assertEquals(inspection, state.inspection)
        assertFalse(state.busy)
    }

    @Test
    fun `a file that cannot be copied stays at the choice with the reason`() {
        workspace.pickResult = PickResult.Failed(FileProblem.TOO_LARGE)
        val state = viewModel().atPreview().state.value
        assertEquals(ImportStep.PICK, state.step)
        assertEquals(FileProblem.TOO_LARGE, state.fileProblem)
        assertFalse(state.busy)
    }

    @Test
    fun `a file over the limit of rows is refused and removed`() {
        workspace.detectFailure = CsvLimitException(CsvLimit.ROWS)
        val state = viewModel().atPreview().state.value
        assertEquals(ImportStep.PICK, state.step)
        assertEquals(FileProblem.TOO_MANY_ROWS, state.fileProblem)
        assertEquals(1, workspace.cleared)
    }

    @Test
    fun `a file that stops being readable is refused`() {
        workspace.detectFailure = IOException()
        assertEquals(
            FileProblem.UNREADABLE,
            viewModel()
                .atPreview()
                .state.value.fileProblem,
        )
    }

    @Test
    fun `a meaning belongs to one column, the previous holder gives way`() {
        val vm = viewModel().atPreview()
        vm.onEvent(ImportEvent.ColumnSelected(4, ColumnRole.PULSE))
        assertEquals(
            listOf(ColumnRole.DATE_TIME, ColumnRole.SYS, ColumnRole.DIA, ColumnRole.IGNORE, ColumnRole.PULSE),
            vm.state.value.options
                ?.columns,
        )
        assertEquals(vm.state.value.options, workspace.inspected.last())
    }

    @Test
    fun `comments may be several`() {
        val vm = viewModel().atPreview()
        vm.onEvent(ImportEvent.ColumnSelected(3, ColumnRole.COMMENT))
        assertEquals(
            2,
            vm.state.value.options
                ?.columns
                ?.count { it == ColumnRole.COMMENT },
        )
    }

    @Test
    fun `the encoding and the delimiter chosen by hand read the file again`() {
        val vm = viewModel().atPreview()
        vm.onEvent(ImportEvent.EncodingSelected(CsvEncoding.WINDOWS_1251))
        vm.onEvent(ImportEvent.DelimiterSelected(';'))
        assertEquals(
            CsvEncoding.WINDOWS_1251,
            vm.state.value.options
                ?.encoding,
        )
        assertEquals(
            ';',
            vm.state.value.options
                ?.delimiter,
        )
    }

    @Test
    fun `the day order and the arm switch change the options`() {
        val vm = viewModel().atPreview()
        vm.onEvent(ImportEvent.DayOrderSelected(false))
        vm.onEvent(ImportEvent.ArmFromCommentChanged(false))
        assertEquals(
            false,
            vm.state.value.options
                ?.dayFirst,
        )
        assertEquals(
            false,
            vm.state.value.options
                ?.armFromComment,
        )
    }

    @Test
    fun `add starts at once and ends in the report`() {
        val vm = viewModel().atStrategy()
        vm.onEvent(ImportEvent.AddClicked)
        assertEquals(listOf(false), workspace.runs)
        assertEquals(ImportStep.RESULT, vm.state.value.step)
        assertEquals(report, vm.state.value.report)
    }

    @Test
    fun `replace waits for the confirmation`() {
        val vm = viewModel().atStrategy()
        vm.onEvent(ImportEvent.ReplaceClicked)
        assertTrue(vm.state.value.confirmingReplace)
        assertTrue(workspace.runs.isEmpty())

        vm.onEvent(ImportEvent.DialogDismissed)
        assertFalse(vm.state.value.confirmingReplace)
        assertEquals(ImportStep.STRATEGY, vm.state.value.step)
        assertTrue(workspace.runs.isEmpty())

        vm.onEvent(ImportEvent.ReplaceClicked)
        vm.onEvent(ImportEvent.ReplaceConfirmed)
        assertEquals(listOf(true), workspace.runs)
        assertFalse(vm.state.value.confirmingReplace)
    }

    @Test
    fun `the progress of the work reaches the screen`() =
        runTest {
            val progress = MutableSharedFlow<ImportProgress>()
            workspace.progress = progress
            val vm = viewModel().atStrategy()
            vm.onEvent(ImportEvent.AddClicked)
            assertEquals(ImportStep.RUNNING, vm.state.value.step)
            assertNull(vm.state.value.progress)

            progress.emit(ImportProgress.Running(0.5f))
            assertEquals(0.5f, vm.state.value.progress)
        }

    @Test
    fun `a failed write says so`() {
        workspace.progress = flowOf(ImportProgress.Failed)
        val vm = viewModel().atStrategy()
        vm.onEvent(ImportEvent.AddClicked)
        assertEquals(ImportStep.RESULT, vm.state.value.step)
        assertTrue(vm.state.value.failed)
    }

    @Test
    fun `the report brings the first ten bad rows, the errors stay folded until asked for`() {
        workspace.progress = flowOf(ImportProgress.Done(report.copy(errorCount = 12)))
        workspace.badRows = badRows(12)
        val vm = viewModel().atStrategy()
        vm.onEvent(ImportEvent.ShowErrorsClicked)
        vm.onEvent(ImportEvent.AddClicked)
        assertEquals(
            badRows(10),
            vm.state.value.report
                ?.errors,
        )
        assertFalse(vm.state.value.showingErrors)
    }

    @Test
    fun `the saved report holds every bad row`() {
        workspace.progress = flowOf(ImportProgress.Done(report.copy(errorCount = 12)))
        workspace.badRows = badRows(12)
        val vm = viewModel().atStrategy()
        vm.onEvent(ImportEvent.AddClicked)
        vm.onEvent(ImportEvent.ReportTargetChosen("content://report"))

        val text = workspace.saved.getValue("content://report")
        assertTrue(text, text.startsWith("Импортировано 27 измерений\r\n"))
        assertTrue(text, text.contains("Строка 12: верхнее значение не больше нижнего\r\n"))
        assertTrue(vm.state.value.reportSaved)
    }

    @Test
    fun `a report that cannot be saved does not claim it was`() {
        workspace.saveFailure = IOException()
        val vm = viewModel().atStrategy()
        vm.onEvent(ImportEvent.AddClicked)
        vm.onEvent(ImportEvent.ReportTargetChosen("content://report"))
        assertFalse(vm.state.value.reportSaved)
    }

    @Test
    fun `back goes through the steps and removes the copy of the file`() =
        runTest {
            val vm = viewModel().atStrategy()
            vm.effects.test {
                vm.onEvent(ImportEvent.BackClicked)
                assertEquals(ImportStep.PREVIEW, vm.state.value.step)
                assertEquals(0, workspace.cleared)

                vm.onEvent(ImportEvent.BackClicked)
                assertEquals(ImportStep.PICK, vm.state.value.step)
                assertEquals(1, workspace.cleared)
                assertNull(vm.state.value.options)

                vm.onEvent(ImportEvent.BackClicked)
                assertEquals(ImportEffect.Close, awaitItem())
            }
        }

    @Test
    fun `back does nothing while the import runs`() =
        runTest {
            workspace.progress = MutableSharedFlow()
            val vm = viewModel().atStrategy()
            vm.onEvent(ImportEvent.AddClicked)
            vm.effects.test {
                vm.onEvent(ImportEvent.BackClicked)
                expectNoEvents()
            }
            assertEquals(ImportStep.RUNNING, vm.state.value.step)
        }

    @Test
    fun `open the diary closes the wizard`() =
        runTest {
            val vm = viewModel().atStrategy()
            vm.onEvent(ImportEvent.AddClicked)
            vm.effects.test {
                vm.onEvent(ImportEvent.OpenDiaryClicked)
                assertEquals(ImportEffect.Close, awaitItem())
            }
        }
}
