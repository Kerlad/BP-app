package ru.bpdiary.feature.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.transfer.R
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.BpPrimaryButton
import ru.bpdiary.core.ui.BpSecondaryButton
import ru.bpdiary.domain.csv.ImportReport
import ru.bpdiary.domain.csv.RowError

/** Import (docs/13 §13.4): the steps come from [state]. Stateless. */
@Composable
fun ImportScreen(
    state: ImportUiState,
    onEvent: (ImportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = ImportTexts(LocalResources.current)
    TransferFrame(stringResource(R.string.import_title), { onEvent(ImportEvent.BackClicked) }, modifier) {
        when (state.step) {
            ImportStep.PICK -> PickStep(state, texts, onEvent)
            ImportStep.PREVIEW -> PreviewStep(state, texts, onEvent)
            ImportStep.STRATEGY -> StrategyStep(onEvent)
            ImportStep.RUNNING -> RunningStep(state.progress)
            ImportStep.RESULT -> ResultStep(state, texts, onEvent)
        }
    }
    if (state.confirmingReplace) ReplaceDialog(onEvent)
}

@Composable
private fun PickStep(
    state: ImportUiState,
    texts: ImportTexts,
    onEvent: (ImportEvent) -> Unit,
) {
    Text(
        text = stringResource(R.string.import_pick_hint),
        style = BpTheme.typography.body,
        color = BpTheme.colors.ink,
    )
    state.fileProblem?.let { ErrorText(texts.fileProblem(it)) }
    if (state.busy) {
        CircularProgressIndicator(color = BpTheme.colors.ac)
    } else {
        BpPrimaryButton(stringResource(R.string.import_pick_button), { onEvent(ImportEvent.PickClicked) })
    }
}

@Composable
internal fun ErrorText(text: String) {
    Text(text, style = BpTheme.typography.body, color = BpTheme.colors.hi)
}

@Composable
internal fun ErrorList(
    errors: List<RowError>,
    total: Int,
    texts: ImportTexts,
) {
    val colors = BpTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        errors.forEach { Text(texts.rowError(it), style = BpTheme.typography.secondary, color = colors.ink) }
        if (total > errors.size) {
            Text(
                text = stringResource(R.string.import_more_errors, total - errors.size),
                style = BpTheme.typography.secondary,
                color = colors.mut,
            )
        }
    }
}

@Composable
private fun ColumnScope.StrategyStep(onEvent: (ImportEvent) -> Unit) {
    val colors = BpTheme.colors
    Text(
        text = stringResource(R.string.import_strategy_title),
        style = BpTheme.typography.cardTitle,
        color = colors.ink,
    )
    StrategyCard(
        title = stringResource(R.string.import_add),
        hint = stringResource(R.string.import_add_hint),
        titleColor = colors.ink,
    ) { onEvent(ImportEvent.AddClicked) }
    StrategyCard(
        title = stringResource(R.string.import_replace),
        hint = stringResource(R.string.import_replace_hint),
        titleColor = colors.hi,
    ) { onEvent(ImportEvent.ReplaceClicked) }
}

@Composable
private fun StrategyCard(
    title: String,
    hint: String,
    titleColor: Color,
    onClick: () -> Unit,
) {
    BpCard(padding = 18.dp, onClick = onClick) {
        Text(title, style = BpTheme.typography.cardTitle, color = titleColor)
        Text(
            text = hint,
            style = BpTheme.typography.secondary,
            color = BpTheme.colors.mut,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RunningStep(progress: Float?) {
    val colors = BpTheme.colors
    Text(stringResource(R.string.import_running), style = BpTheme.typography.body, color = colors.ink)
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress },
            color = colors.ac,
            trackColor = colors.acs,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(color = colors.ac, trackColor = colors.acs, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultStep(
    state: ImportUiState,
    texts: ImportTexts,
    onEvent: (ImportEvent) -> Unit,
) {
    val report = state.report
    if (state.failed || report == null) {
        ErrorText(stringResource(R.string.import_error_save))
    } else {
        ReportCard(report, state.showingErrors, texts, onEvent)
        if (state.reportSaved) {
            Text(
                text = stringResource(R.string.import_report_saved),
                style = BpTheme.typography.body,
                color = BpTheme.colors.ok,
            )
        }
        if (state.reportFailed) ErrorText(stringResource(R.string.export_failed))
    }
    BpPrimaryButton(stringResource(R.string.import_open_diary), { onEvent(ImportEvent.OpenDiaryClicked) })
    if (report != null && !state.failed) {
        BpSecondaryButton(stringResource(R.string.import_save_report), { onEvent(ImportEvent.SaveReportClicked) })
    }
}

@Composable
private fun ReportCard(
    report: ImportReport,
    showingErrors: Boolean,
    texts: ImportTexts,
    onEvent: (ImportEvent) -> Unit,
) {
    val colors = BpTheme.colors
    val lines = texts.report(report)
    if (report.imported == 0 && report.duplicates > 0 && report.errorCount == 0) {
        Text(
            text = stringResource(R.string.import_error_all_duplicates),
            style = BpTheme.typography.cardTitle,
            color = colors.ink,
        )
    }
    texts.partial(report)?.let { ErrorText(it) }
    BpCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lines.first(), style = BpTheme.typography.cardTitle, color = colors.ink)
            lines.drop(1).forEach { Text(it, style = BpTheme.typography.secondary, color = colors.mut) }
        }
    }
    if (report.errorCount > 0) {
        TextAction(stringResource(R.string.import_show_errors)) { onEvent(ImportEvent.ShowErrorsClicked) }
        if (showingErrors) ErrorList(report.errors, report.errorCount, texts)
    }
}

@Composable
private fun ReplaceDialog(onEvent: (ImportEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(ImportEvent.DialogDismissed) },
        title = { Text(stringResource(R.string.import_replace_confirm_title)) },
        text = { Text(stringResource(R.string.import_replace_confirm_text)) },
        confirmButton = {
            TextButton(onClick = { onEvent(ImportEvent.ReplaceConfirmed) }) {
                Text(stringResource(R.string.import_replace_confirm), color = BpTheme.colors.hi)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ImportEvent.DialogDismissed) }) {
                Text(stringResource(R.string.import_cancel))
            }
        },
    )
}
