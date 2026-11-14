package ru.bpdiary.feature.transfer

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.domain.csv.ColumnRole
import ru.bpdiary.domain.csv.CsvEncoding
import ru.bpdiary.domain.csv.CsvInspection
import ru.bpdiary.domain.csv.CsvLimits
import ru.bpdiary.domain.csv.CsvOptions
import ru.bpdiary.domain.csv.ImportReport
import java.io.IOException
import java.time.ZoneOffset
import javax.inject.Inject

enum class ImportStep { PICK, PREVIEW, STRATEGY, RUNNING, RESULT }

enum class FileProblem { UNREADABLE, TOO_LARGE, TOO_MANY_ROWS }

data class ImportUiState(
    val step: ImportStep = ImportStep.PICK,
    val busy: Boolean = false,
    val fileName: String = "",
    val fileProblem: FileProblem? = null,
    val options: CsvOptions? = null,
    val inspection: CsvInspection? = null,
    val editingFormat: Boolean = false,
    val showingErrors: Boolean = false,
    val confirmingReplace: Boolean = false,
    /** Null while the share of the file is not known yet. */
    val progress: Float? = null,
    val report: ImportReport? = null,
    val failed: Boolean = false,
    val reportSaved: Boolean = false,
    val reportFailed: Boolean = false,
    /** «+03:00»: the note about local time in the preview. */
    val zoneOffset: String = "",
)

sealed interface ImportEvent {
    data object PickClicked : ImportEvent

    data class FileChosen(
        val uri: String,
    ) : ImportPreviewEvent

    data object EditFormatClicked : ImportEvent

    data class EncodingSelected(
        val encoding: CsvEncoding,
    ) : ImportPreviewEvent

    data class DelimiterSelected(
        val delimiter: Char,
    ) : ImportPreviewEvent

    data class ColumnSelected(
        val index: Int,
        val role: ColumnRole,
    ) : ImportPreviewEvent

    data class DayOrderSelected(
        val dayFirst: Boolean,
    ) : ImportPreviewEvent

    data class ArmFromCommentChanged(
        val enabled: Boolean,
    ) : ImportPreviewEvent

    data object ShowErrorsClicked : ImportEvent

    data object NextClicked : ImportEvent

    data object AddClicked : ImportEvent

    data object ReplaceClicked : ImportEvent

    data object ReplaceConfirmed : ImportEvent

    data object DialogDismissed : ImportEvent

    data object SaveReportClicked : ImportEvent

    data class ReportTargetChosen(
        val uri: String,
    ) : ImportEvent

    data object OpenDiaryClicked : ImportEvent

    data object BackClicked : ImportEvent
}

/** What changes the way the file is read; [ImportPreview] answers these. */
sealed interface ImportPreviewEvent : ImportEvent

sealed interface ImportEffect {
    data object Close : ImportEffect
}

/** The wizard of docs/13 §13.4: file → preview and columns → strategy → result. */
@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        private val workspace: ImportWorkspace,
        resources: Resources,
        time: TimeProvider,
    ) : ViewModel() {
        private val texts = ImportTexts(resources)
        private val mutable =
            MutableStateFlow(
                ImportUiState(
                    zoneOffset =
                        time
                            .zone()
                            .rules
                            .getOffset(time.now())
                            .label(),
                ),
            )
        val state: StateFlow<ImportUiState> = mutable.asStateFlow()

        private val effectChannel = Channel<ImportEffect>(Channel.BUFFERED)
        val effects: Flow<ImportEffect> = effectChannel.receiveAsFlow()

        private val preview = ImportPreview(workspace, viewModelScope, mutable)

        fun onEvent(event: ImportEvent) {
            when (event) {
                is ImportPreviewEvent -> preview.onEvent(event)
                ImportEvent.EditFormatClicked -> mutable.update { it.copy(editingFormat = !it.editingFormat) }
                ImportEvent.ShowErrorsClicked -> mutable.update { it.copy(showingErrors = !it.showingErrors) }
                ImportEvent.NextClicked -> mutable.update { it.copy(step = ImportStep.STRATEGY) }
                ImportEvent.AddClicked -> start(replaceAll = false)
                ImportEvent.ReplaceClicked -> mutable.update { it.copy(confirmingReplace = true) }
                ImportEvent.ReplaceConfirmed -> start(replaceAll = true)
                ImportEvent.DialogDismissed -> mutable.update { it.copy(confirmingReplace = false) }
                is ImportEvent.ReportTargetChosen -> saveReport(event.uri)
                ImportEvent.BackClicked -> back()
                ImportEvent.OpenDiaryClicked -> close()
                ImportEvent.PickClicked, ImportEvent.SaveReportClicked -> Unit
            }
        }

        private fun start(replaceAll: Boolean) {
            val current = mutable.value
            val options = current.options ?: return
            mutable.update {
                it.copy(step = ImportStep.RUNNING, confirmingReplace = false, showingErrors = false, progress = null)
            }
            viewModelScope.launch {
                workspace.run(options, current.inspection?.rows ?: 0, replaceAll).collect { progress ->
                    when (progress) {
                        is ImportProgress.Running -> {
                            mutable.update { it.copy(progress = progress.fraction) }
                        }

                        is ImportProgress.Done -> {
                            // The worker keeps the bad rows in a file: its output is too small for them.
                            val errors = workspace.errors().take(CsvLimits.MAX_ERRORS_SHOWN)
                            val report = progress.report.copy(errors = errors)
                            mutable.update { it.copy(step = ImportStep.RESULT, report = report) }
                        }

                        ImportProgress.Failed -> {
                            mutable.update { it.copy(step = ImportStep.RESULT, failed = true) }
                        }
                    }
                }
            }
        }

        private fun saveReport(uri: String) {
            val report = mutable.value.report ?: return
            viewModelScope.launch {
                try {
                    workspace.saveText(uri, texts.reportFile(report, workspace.errors()))
                    mutable.update { it.copy(reportSaved = true, reportFailed = false) }
                } catch (_: IOException) {
                    mutable.update { it.copy(reportSaved = false, reportFailed = true) }
                }
            }
        }

        private fun back() {
            when (mutable.value.step) {
                ImportStep.PICK, ImportStep.RESULT -> close()
                ImportStep.PREVIEW -> restart()
                ImportStep.STRATEGY -> mutable.update { it.copy(step = ImportStep.PREVIEW) }
                ImportStep.RUNNING -> Unit
            }
        }

        private fun restart() {
            preview.cancel()
            workspace.clear()
            mutable.update { ImportUiState(zoneOffset = it.zoneOffset) }
        }

        private fun close() {
            effectChannel.trySend(ImportEffect.Close)
        }

        override fun onCleared() {
            if (mutable.value.step != ImportStep.RUNNING) workspace.clear()
        }
    }

private fun ZoneOffset.label(): String = if (totalSeconds == 0) "+00:00" else id
