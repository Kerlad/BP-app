package ru.bpdiary.feature.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.transfer.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.BpPrimaryButton
import ru.bpdiary.core.ui.BpSecondaryButton
import ru.bpdiary.core.ui.BpTopBar
import ru.bpdiary.core.ui.FilterChipRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The frame of the export and the import: a top bar with «Назад» over a scrolling column. */
@Composable
internal fun TransferFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BpTheme.colors.bg)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 10.dp, end = 20.dp, bottom = 24.dp),
    ) {
        BpTopBar(
            title = title,
            navigationIcon = BpIcon.ArrowBack,
            navigationDescription = stringResource(R.string.import_back_description),
            onNavigate = onBack,
        )
        Column(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Export (docs/13 §13.3): the period, then save or share. Stateless. */
@Composable
fun ExportScreen(
    state: ExportUiState,
    onEvent: (ExportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    TransferFrame(stringResource(R.string.export_title), { onEvent(ExportEvent.BackClicked) }, modifier) {
        PeriodCard(state, onEvent)
        val ready = (state.count ?: 0) > 0
        BpPrimaryButton(
            text = stringResource(R.string.export_save),
            onClick = { onEvent(ExportEvent.SaveClicked) },
            enabled = ready,
        )
        BpSecondaryButton(
            text = stringResource(R.string.export_share),
            onClick = { onEvent(ExportEvent.ShareClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = ready,
        )
        val message =
            when {
                state.saved -> stringResource(R.string.export_saved)
                state.failed -> stringResource(R.string.export_failed)
                else -> null
            }
        if (message != null) {
            Text(message, style = BpTheme.typography.body, color = if (state.failed) colors.hi else colors.ok)
        }
    }
    if (state.choosingRange) RangeDialog(onEvent, state.to)
}

@Composable
private fun PeriodCard(
    state: ExportUiState,
    onEvent: (ExportEvent) -> Unit,
) {
    val colors = BpTheme.colors
    val texts = ImportTexts(LocalResources.current)
    BpCard {
        Text(
            text = stringResource(R.string.export_period_title),
            style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
            color = colors.ink,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val periods = ExportPeriod.entries
        val labels =
            listOf(
                stringResource(R.string.export_period_7),
                stringResource(R.string.export_period_30),
                stringResource(R.string.export_period_90),
                stringResource(R.string.export_period_all),
                stringResource(R.string.export_period_custom),
            )
        FilterChipRow(
            items = labels,
            selected = setOf(state.period.ordinal),
            multiSelect = false,
            onToggle = { onEvent(ExportEvent.PeriodSelected(periods[it])) },
        )
        val count = state.count
        if (count != null) {
            Text(
                text = stringResource(R.string.export_range, texts.date(state.from), texts.date(state.to)),
                style = BpTheme.typography.body,
                color = colors.ink,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text =
                    if (count == 0) {
                        stringResource(R.string.export_empty)
                    } else {
                        pluralStringResource(R.plurals.export_count, count, count)
                    },
                style = BpTheme.typography.secondary,
                color = colors.mut,
            )
        }
    }
}

/** Any period up to today; the picker works in UTC days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(
    onEvent: (ExportEvent) -> Unit,
    today: LocalDate,
) {
    val lastMillis =
        today
            .plusDays(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    val picker =
        rememberDateRangePickerState(
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis < lastMillis
                },
        )
    val from = picker.selectedStartDateMillis
    val to = picker.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = { onEvent(ExportEvent.RangeDismissed) },
        confirmButton = {
            TextButton(
                enabled = from != null && to != null,
                onClick = {
                    if (from != null && to != null) onEvent(ExportEvent.RangeChosen(from.toDay(), to.toDay()))
                },
            ) {
                Text(stringResource(R.string.export_range_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ExportEvent.RangeDismissed) }) {
                Text(stringResource(R.string.import_cancel))
            }
        },
    ) {
        DateRangePicker(state = picker, title = null, headline = null, showModeToggle = false)
    }
}

private fun Long.toDay(): LocalDate = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()
