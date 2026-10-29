package ru.bpdiary.feature.reminders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import ru.bpdiary.app.feature.reminders.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpPrimaryButton
import ru.bpdiary.core.ui.BpTopBar
import ru.bpdiary.core.ui.FilterChipRow
import ru.bpdiary.domain.DaysSummary
import java.time.DayOfWeek
import java.time.LocalTime

private val PRESETS = listOf(DaysSummary.Daily, DaysSummary.Workdays)

/** New or existing reminder: time picker, days, name, «Готово» and, for an existing one, «Удалить напоминание». */
@Composable
fun ReminderEditScreen(
    state: ReminderEditUiState,
    onEvent: (ReminderEditEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    val defaultLabel = stringResource(R.string.reminders_default_label)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 20.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BpTopBar(
                title =
                    stringResource(
                        if (state.isNew) R.string.reminders_edit_title_new else R.string.reminders_edit_title_existing,
                    ),
                navigationIcon = BpIcon.Close,
                navigationDescription = stringResource(R.string.reminders_edit_close_description),
                onNavigate = { onEvent(ReminderEditEvent.CloseClicked) },
            )
            if (state.loaded) {
                TimeSection(state.time) { onEvent(ReminderEditEvent.TimeChanged(it)) }
                DaysSection(state, onEvent)
                LabelField(state.label) { onEvent(ReminderEditEvent.LabelChanged(it)) }
                if (!state.isNew) DeleteButton { onEvent(ReminderEditEvent.DeleteClicked) }
            }
        }
        BpPrimaryButton(
            text = stringResource(R.string.reminders_edit_done),
            onClick = { onEvent(ReminderEditEvent.DoneClicked(defaultLabel)) },
            icon = BpIcon.Check,
            enabled = state.loaded && state.canSave,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
    if (state.confirmDelete) DeleteDialog(onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSection(
    time: LocalTime,
    onChange: (LocalTime) -> Unit,
) {
    // Created once the reminder is read, so the picker starts at its time; later changes only flow outwards.
    val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    LaunchedEffect(picker) {
        // The first value is the initial time that the state already holds.
        snapshotFlow { picker.hour to picker.minute }
            .drop(1)
            .collect { (hour, minute) -> onChange(LocalTime.of(hour, minute)) }
    }
    val colors = BpTheme.colors
    val pickerColors =
        TimePickerDefaults.colors(
            clockDialColor = colors.sf,
            clockDialSelectedContentColor = colors.aci,
            clockDialUnselectedContentColor = colors.ink,
            selectorColor = colors.ac,
            containerColor = colors.bg,
            timeSelectorSelectedContainerColor = colors.acs,
            timeSelectorUnselectedContainerColor = colors.sf,
            timeSelectorSelectedContentColor = colors.ink,
            timeSelectorUnselectedContentColor = colors.ink,
        )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(picker, colors = pickerColors) }
}

@Composable
private fun DaysSection(
    state: ReminderEditUiState,
    onEvent: (ReminderEditEvent) -> Unit,
) {
    val colors = BpTheme.colors
    val texts = rememberRemindersTexts()
    Text(
        text = stringResource(R.string.reminders_edit_days),
        style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
        color = colors.ink,
        modifier = Modifier.semantics { heading() },
    )
    FilterChipRow(
        items = PRESETS.map { texts.days(it) },
        selected = setOfNotNull(PRESETS.indexOfFirst { it == state.preset }.takeIf { it >= 0 }),
        multiSelect = false,
        onToggle = { onEvent(ReminderEditEvent.PresetSelected(PRESETS[it])) },
    )
    // One row of seven equal chips: a wrapped «Вс» would look like a different control.
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            DayChip(
                label = texts.dates.weekdayShort(day),
                selected = day in state.days,
                onClick = { onEvent(ReminderEditEvent.DayToggled(day)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Same look as the feeling tags: selected `acs` with an `ac` outline, otherwise a `ln` outline. */
@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = BpTheme.colors
    Box(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .selectable(selected = selected, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = BpTheme.typography.caption.copy(fontWeight = FontWeight.W600),
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(if (selected) colors.acs else Color.Transparent)
                    .border(BorderStroke(1.5.dp, if (selected) colors.ac else colors.ln), CircleShape)
                    .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun LabelField(
    value: String,
    onChange: (String) -> Unit,
) {
    val colors = BpTheme.colors
    val shape = RoundedCornerShape(BpTheme.shapes.key)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = BpTheme.typography.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ac),
        keyboardOptions =
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(colors.sf)
                        .border(BorderStroke(1.5.dp, colors.ln), shape)
                        .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.reminders_edit_label_hint),
                            style = BpTheme.typography.body,
                            color = colors.mut,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.reminders_edit_delete),
        style = BpTheme.typography.button,
        color = BpTheme.colors.hi,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(BpTheme.shapes.card))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
    )
}

@Composable
private fun DeleteDialog(onEvent: (ReminderEditEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(ReminderEditEvent.DialogDismissed) },
        title = { Text(stringResource(R.string.reminders_delete_title)) },
        confirmButton = {
            TextButton(onClick = { onEvent(ReminderEditEvent.DeleteConfirmed) }) {
                Text(stringResource(R.string.reminders_delete_confirm), color = BpTheme.colors.hi)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ReminderEditEvent.DialogDismissed) }) {
                Text(stringResource(R.string.reminders_delete_cancel))
            }
        },
    )
}
