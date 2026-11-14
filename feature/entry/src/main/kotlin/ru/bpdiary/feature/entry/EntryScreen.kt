package ru.bpdiary.feature.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.bpdiary.app.feature.entry.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Field
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.ui.BpTopBar
import ru.bpdiary.core.ui.FilterChipRow
import ru.bpdiary.core.ui.NumPad
import ru.bpdiary.core.ui.NumPadAction
import ru.bpdiary.core.ui.SegmentedToggle
import ru.bpdiary.core.ui.StatusChip
import ru.bpdiary.core.ui.labelRes
import ru.bpdiary.core.ui.rememberDateFormatter
import ru.bpdiary.domain.Reason
import ru.bpdiary.app.core.ui.R as UiR

/** The comment counter appears from this length on (docs/05 §5.2). */
private const val COMMENT_COUNTER_FROM = 100
private const val DISABLED_ALPHA = 0.4f
private const val FIELD_LINE_HEIGHT = 1.1f
private val MinFieldFont = 18.sp
private val MinLabelFont = 9.sp
internal val Gap = 12.dp

/** Full-screen entry and edit of a reading (handoff screen 2). Stateless: everything comes from [state]. */
@Composable
fun EntryScreen(
    state: EntryUiState,
    onEvent: (EntryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    BackHandler { onEvent(EntryEvent.CloseClicked) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
    ) {
        BpTopBar(
            title = stringResource(if (state.isEdit) R.string.entry_title_edit else R.string.entry_title_new),
            navigationIcon = BpIcon.Close,
            navigationDescription = stringResource(R.string.entry_close_description),
            onNavigate = { onEvent(EntryEvent.CloseClicked) },
            trailing = { if (state.isEdit) DeleteAction { onEvent(EntryEvent.DeleteClicked) } },
        )
        val form: @Composable ColumnScope.() -> Unit = {
            TimeAndArmRow(state, onEvent)
            FieldGrid(state, onEvent)
            state.error?.let { ErrorLine(stringResource(it.reason.textRes())) }
            if (state.saveFailed) ErrorLine(stringResource(UiR.string.save_failed))
            LiveStatusRow(state.liveStatus)
            val tags = Tag.entries
            FilterChipRow(
                items = tags.map { stringResource(it.labelRes()) },
                selected = tags.indices.filter { tags[it] in state.tags }.toSet(),
                multiSelect = true,
                onToggle = { onEvent(EntryEvent.TagToggled(tags[it])) },
            )
            CommentField(state.comment) { onEvent(EntryEvent.CommentChanged(it)) }
        }
        val keys: @Composable () -> Unit = {
            Spacer(Modifier.height(Gap))
            NumPad(
                onKey = { onEvent(EntryEvent.KeyPressed(it)) },
                action = if (state.draft.field == Field.PULSE) NumPadAction.Save else NumPadAction.Next,
            )
            Spacer(Modifier.height(Gap))
            SaveButton(enabled = state.canSave) { onEvent(EntryEvent.SaveClicked) }
        }
        AdaptiveEntryLayout(form, keys, Modifier.weight(1f))
    }
    when (state.dialog) {
        EntryDialog.Discard -> DiscardDialog(onEvent)
        EntryDialog.Delete -> DeleteDialog(onEvent)
        null -> Unit
    }
}

@Composable
private fun DeleteAction(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.entry_delete),
        style = BpTheme.typography.body.copy(fontWeight = FontWeight.W600),
        color = BpTheme.colors.hi,
        maxLines = 1,
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 14.dp),
    )
}

@Composable
private fun TimeAndArmRow(
    state: EntryUiState,
    onEvent: (EntryEvent) -> Unit,
) {
    val colors = BpTheme.colors
    // Flows onto two lines at large font scales instead of clipping the time.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Not clickable in MVP: the date and time picker comes in v1.1 (FR-1.9).
        Box(Modifier.padding(end = 8.dp)) {
            Row(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(colors.sf)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Image(
                    painter = BpIcon.Schedule.painter(),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.mut),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = rememberDateFormatter().dayTime(state.dayLabel, state.time),
                    style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W600),
                    color = colors.ink,
                    maxLines = 1,
                )
            }
        }
        SegmentedToggle(
            options = listOf(stringResource(R.string.entry_arm_left), stringResource(R.string.entry_arm_right)),
            selectedIndex = state.arm.ordinal,
            onSelect = { onEvent(EntryEvent.ArmSelected(Arm.entries[it])) },
        )
    }
}

@Composable
private fun FieldGrid(
    state: EntryUiState,
    onEvent: (EntryEvent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field.entries.forEach { field ->
            FieldCard(
                field = field,
                value = state.draft.value(field),
                active = state.draft.field == field,
                invalid = state.error?.field == field || state.missing == field,
                onClick = { onEvent(EntryEvent.FieldTapped(field)) },
            )
        }
    }
}

@Composable
private fun RowScope.FieldCard(
    field: Field,
    value: String,
    active: Boolean,
    invalid: Boolean,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    val shape = RoundedCornerShape(BpTheme.shapes.card)
    val border =
        when {
            invalid -> colors.hi
            active -> colors.ac
            else -> Color.Transparent
        }
    val (label, unit) =
        when (field) {
            Field.SYS -> R.string.entry_field_sys to R.string.entry_field_sys_unit
            Field.DIA -> R.string.entry_field_dia to R.string.entry_field_dia_unit
            Field.PULSE -> R.string.entry_field_pulse to R.string.entry_field_pulse_unit
        }
    Column(
        modifier =
            Modifier
                .weight(1f)
                .clip(shape)
                .background(colors.sf)
                .border(BorderStroke(2.dp, border), shape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { selected = active }
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FittedText(
            text = stringResource(label),
            style = BpTheme.typography.caption.copy(fontWeight = FontWeight.W600, color = colors.mut),
        )
        val numStyle = BpTheme.typography.numEntry
        // Three digits at 36 sp and font scale 2.0 do not fit a third of 360 dp: shrink instead of clipping.
        BasicText(
            text = value.ifEmpty { stringResource(R.string.entry_field_empty) },
            style =
                numStyle.copy(
                    color = if (value.isEmpty()) colors.ln else colors.ink,
                    lineHeight = FIELD_LINE_HEIGHT.em,
                ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = MinFieldFont, maxFontSize = numStyle.fontSize),
        )
        FittedText(text = stringResource(unit), style = BpTheme.typography.micro.copy(color = colors.mut))
    }
}

/** One line that shrinks down to [MinLabelFont] at large font scales instead of being cut mid-word. */
@Composable
private fun FittedText(
    text: String,
    style: TextStyle,
) {
    BasicText(
        text = text,
        style = style,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MinLabelFont, maxFontSize = style.fontSize),
    )
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text = text,
        style = BpTheme.typography.caption.copy(fontWeight = FontWeight.W600),
        color = BpTheme.colors.hi,
        modifier = Modifier.semantics { error(text) },
    )
}

@Composable
private fun LiveStatusRow(status: LiveStatus) {
    val colors = BpTheme.colors
    Row(
        modifier = Modifier.heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (status) {
            LiveStatus.Empty -> {
                Text(
                    text = stringResource(R.string.entry_status_empty),
                    style = BpTheme.typography.chip,
                    color = colors.mut,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(colors.sf)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            is LiveStatus.Classified -> {
                StatusChip(status.status)
                val hint =
                    when (val h = status.hint) {
                        is Hint.Above -> stringResource(R.string.entry_hint_above, h.sys, h.dia)
                        is Hint.Below -> stringResource(R.string.entry_hint_below, h.sys, h.dia)
                        Hint.Within -> stringResource(R.string.entry_hint_within)
                    }
                Text(text = hint, style = BpTheme.typography.caption, color = colors.mut)
            }
        }
    }
}

@Composable
private fun CommentField(
    value: String,
    onChange: (String) -> Unit,
) {
    val colors = BpTheme.colors
    val shape = RoundedCornerShape(BpTheme.shapes.key)
    val hint = stringResource(R.string.entry_comment_hint)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = BpTheme.typography.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ac),
        keyboardOptions =
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        // The hint is drawn inside the field and is not part of its semantics: TalkBack needs the name.
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.entry_comment_hint),
                            style = BpTheme.typography.body,
                            color = colors.mut,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
                if (value.length >= COMMENT_COUNTER_FROM) {
                    Text(
                        text = stringResource(R.string.entry_comment_counter, value.length, COMMENT_MAX_LENGTH),
                        style = BpTheme.typography.micro,
                        color = colors.mut,
                    )
                }
            }
        },
    )
}

@Composable
private fun SaveButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(RoundedCornerShape(BpTheme.shapes.card))
                .background(colors.ac)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.entry_save),
            style = BpTheme.typography.button,
            color = colors.aci,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}
