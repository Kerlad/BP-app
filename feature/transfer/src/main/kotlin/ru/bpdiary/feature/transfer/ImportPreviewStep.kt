package ru.bpdiary.feature.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.transfer.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.BpPrimaryButton
import ru.bpdiary.core.ui.BpSwitch
import ru.bpdiary.core.ui.SegmentedToggle
import ru.bpdiary.domain.csv.ColumnRole
import ru.bpdiary.domain.csv.CsvEncoding
import ru.bpdiary.domain.csv.CsvInspection
import ru.bpdiary.domain.csv.CsvOptions

private val ColumnWidth = 148.dp
private val DELIMITERS = listOf(';', ',', '\t', '|')
private val ENCODINGS = listOf(CsvEncoding.UTF_8, CsvEncoding.WINDOWS_1251, CsvEncoding.UTF_16LE, CsvEncoding.UTF_16BE)

@Composable
internal fun PreviewStep(
    state: ImportUiState,
    texts: ImportTexts,
    onEvent: (ImportEvent) -> Unit,
) {
    val options = state.options ?: return
    val inspection = state.inspection ?: return
    FileCard(state, options, texts, onEvent)
    ColumnsTable(options, inspection, texts) { index, role -> onEvent(ImportEvent.ColumnSelected(index, role)) }
    SwitchRow(stringResource(R.string.import_arm_from_comment), options.armFromComment) {
        onEvent(ImportEvent.ArmFromCommentChanged(it))
    }
    if (inspection.ambiguousDayOrder) DayOrder(options.dayFirst, onEvent)
    Text(
        text = stringResource(R.string.import_local_time, state.zoneOffset),
        style = BpTheme.typography.secondary,
        color = BpTheme.colors.mut,
    )
    Summary(state, inspection, texts, onEvent)
    BpPrimaryButton(
        text = stringResource(R.string.import_next),
        onClick = { onEvent(ImportEvent.NextClicked) },
        icon = BpIcon.ArrowForward,
        enabled = inspection.problem == null && inspection.valid > 0 && !state.busy,
    )
}

/** A link-like action in the type of the screen, level with the text around it. */
@Composable
internal fun TextAction(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Text(
            text = text,
            style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
            color = BpTheme.colors.ac,
        )
    }
}

@Composable
private fun FileCard(
    state: ImportUiState,
    options: CsvOptions,
    texts: ImportTexts,
    onEvent: (ImportEvent) -> Unit,
) {
    val colors = BpTheme.colors
    BpCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.import_file_card),
                style = BpTheme.typography.cardTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            TextAction(stringResource(R.string.import_change)) { onEvent(ImportEvent.EditFormatClicked) }
        }
        if (state.fileName.isNotEmpty()) {
            Text(state.fileName, style = BpTheme.typography.body, color = colors.ink)
        }
        Text(
            text = stringResource(R.string.import_encoding, options.encoding.charsetName),
            style = BpTheme.typography.secondary,
            color = colors.mut,
        )
        Text(
            text = stringResource(R.string.import_delimiter, texts.delimiter(options.delimiter)),
            style = BpTheme.typography.secondary,
            color = colors.mut,
        )
        if (state.editingFormat) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(
                    label = stringResource(R.string.import_encoding_label),
                    current = options.encoding.charsetName,
                    items = ENCODINGS.map { it.charsetName },
                ) { onEvent(ImportEvent.EncodingSelected(ENCODINGS[it])) }
                Choice(
                    label = stringResource(R.string.import_delimiter_label),
                    current = texts.delimiter(options.delimiter),
                    items = DELIMITERS.map { texts.delimiter(it) },
                ) { onEvent(ImportEvent.DelimiterSelected(DELIMITERS[it])) }
            }
        }
    }
}

/** A labelled value that opens a menu. */
@Composable
private fun Choice(
    label: String,
    current: String,
    items: List<String>,
    onSelect: (Int) -> Unit,
) {
    val colors = BpTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.sf2)
                    .clickable(role = Role.DropdownList, onClickLabel = label) { open = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, style = BpTheme.typography.micro, color = colors.mut)
            Text(
                text = current,
                style = BpTheme.typography.secondary,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        open = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

/** The first rows under a selector per column: the machine never decides silently (docs/13 §13.2). */
@Composable
private fun ColumnsTable(
    options: CsvOptions,
    inspection: CsvInspection,
    texts: ImportTexts,
    onSelect: (Int, ColumnRole) -> Unit,
) {
    val colors = BpTheme.colors
    val roles = remember { ColumnRole.entries.toList() }
    BpCard(padding = 12.dp) {
        Text(
            text = stringResource(R.string.import_columns_label),
            style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
            color = colors.ink,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.columns.forEachIndexed { index, role ->
                Column(Modifier.width(ColumnWidth), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Choice(
                        label = inspection.header?.getOrNull(index) ?: "${index + 1}",
                        current = texts.role(role),
                        items = roles.map { texts.role(it) },
                    ) { onSelect(index, roles[it]) }
                    inspection.preview.forEach { row ->
                        Text(
                            text = row.getOrNull(index).orEmpty().ifEmpty { " " },
                            style = BpTheme.typography.secondary,
                            color = if (role == ColumnRole.IGNORE) colors.mut else colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = BpTheme.typography.body, color = BpTheme.colors.ink, modifier = Modifier.weight(1f))
        BpSwitch(checked, onChange, contentDescription = text)
    }
}

@Composable
private fun DayOrder(
    dayFirst: Boolean,
    onEvent: (ImportEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.import_day_order),
            style = BpTheme.typography.secondary,
            color = BpTheme.colors.mut,
        )
        SegmentedToggle(
            options = listOf(stringResource(R.string.import_day_month), stringResource(R.string.import_month_day)),
            selectedIndex = if (dayFirst) 0 else 1,
            onSelect = { onEvent(ImportEvent.DayOrderSelected(it == 0)) },
        )
    }
}

@Composable
private fun Summary(
    state: ImportUiState,
    inspection: CsvInspection,
    texts: ImportTexts,
    onEvent: (ImportEvent) -> Unit,
) {
    val colors = BpTheme.colors
    inspection.problem?.let { ErrorText(texts.problem(it)) }
    Text(
        text = stringResource(R.string.import_summary, inspection.valid, inspection.errorCount),
        style = BpTheme.typography.cardTitle,
        color = colors.ink,
    )
    if (inspection.errorCount > 0) {
        TextAction(stringResource(R.string.import_show_errors)) { onEvent(ImportEvent.ShowErrorsClicked) }
        if (state.showingErrors) ErrorList(inspection.errors, inspection.errorCount, texts)
    }
}
