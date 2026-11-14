package ru.bpdiary.feature.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.bpdiary.domain.csv.ColumnRole
import ru.bpdiary.domain.csv.CsvEncoding
import ru.bpdiary.domain.csv.CsvLimit
import ru.bpdiary.domain.csv.CsvLimitException
import ru.bpdiary.domain.csv.CsvOptions
import java.io.IOException

/** The first half of the wizard: reads the chosen file again whenever the user changes how it should be read. */
internal class ImportPreview(
    private val workspace: ImportWorkspace,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ImportUiState>,
) {
    private var inspecting: Job? = null

    fun onEvent(event: ImportPreviewEvent) {
        when (event) {
            is ImportEvent.FileChosen -> choose(event.uri)
            is ImportEvent.EncodingSelected -> redetect(event.encoding, state.value.options?.delimiter)
            is ImportEvent.DelimiterSelected -> redetect(state.value.options?.encoding, event.delimiter)
            is ImportEvent.ColumnSelected -> selectColumn(event.index, event.role)
            is ImportEvent.DayOrderSelected -> changeOptions { it.copy(dayFirst = event.dayFirst) }
            is ImportEvent.ArmFromCommentChanged -> changeOptions { it.copy(armFromComment = event.enabled) }
        }
    }

    fun cancel() {
        inspecting?.cancel()
    }

    private fun choose(uri: String) {
        // While the work runs, the file under it must not change (the worker reads the same cache copy).
        if (state.value.step == ImportStep.RUNNING) return
        state.update { it.copy(busy = true, fileProblem = null) }
        scope.launch {
            when (val result = workspace.pick(uri)) {
                is PickResult.Copied -> {
                    state.update { it.copy(fileName = result.name) }
                    detect(null, null)
                }

                is PickResult.Failed -> {
                    state.update { it.copy(busy = false, fileProblem = result.problem) }
                }
            }
        }
    }

    private fun redetect(
        encoding: CsvEncoding?,
        delimiter: Char?,
    ) {
        state.update { it.copy(busy = true) }
        inspecting?.cancel()
        inspecting = scope.launch { detect(encoding, delimiter) }
    }

    private suspend fun detect(
        encoding: CsvEncoding?,
        delimiter: Char?,
    ) = guarded {
        inspect(workspace.detect(encoding, delimiter))
    }

    private fun selectColumn(
        index: Int,
        role: ColumnRole,
    ) = changeOptions { options ->
        // One column per meaning: the previous holder gives way. Comments may be several and are joined.
        val unique = role != ColumnRole.COMMENT && role != ColumnRole.IGNORE
        val columns =
            options.columns.mapIndexed { i, old ->
                when {
                    i == index -> role
                    unique && old == role -> ColumnRole.IGNORE
                    else -> old
                }
            }
        options.copy(columns = columns)
    }

    private fun changeOptions(change: (CsvOptions) -> CsvOptions) {
        val options = state.value.options ?: return
        val changed = change(options)
        state.update { it.copy(options = changed, busy = true) }
        inspecting?.cancel()
        inspecting = scope.launch { guarded { inspect(changed) } }
    }

    private suspend fun inspect(options: CsvOptions) {
        val inspection = workspace.inspect(options)
        state.update {
            it.copy(
                step = ImportStep.PREVIEW,
                busy = false,
                options = options,
                inspection = inspection,
                fileProblem = null,
            )
        }
    }

    /** A file that breaks a limit or cannot be read sends the user back to the choice of a file. */
    private suspend fun guarded(work: suspend () -> Unit) {
        try {
            work()
        } catch (e: CsvLimitException) {
            if (e.limit == CsvLimit.ROWS) {
                refuse(FileProblem.TOO_MANY_ROWS)
            } else {
                refuse(FileProblem.UNCLOSED_QUOTE, e.line)
            }
        } catch (_: IOException) {
            refuse(FileProblem.UNREADABLE)
        }
    }

    private fun refuse(
        problem: FileProblem,
        line: Int? = null,
    ) {
        workspace.clear()
        state.update { ImportUiState(fileProblem = problem, fileProblemLine = line, zoneOffset = it.zoneOffset) }
    }
}
