package ru.bpdiary.feature.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.diary.R
import ru.bpdiary.core.designsystem.BpColors
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.ui.BigReading
import ru.bpdiary.core.ui.BigReadingSize

/**
 * One reading in its day card: status stripe 4×38, time, «128/82» with the one-line note, pulse on the right.
 * Tap edits, swipe to the left deletes, long press opens «Изменить / Удалить / Копировать значения».
 * TalkBack gets the same three as custom actions, since swipes and long presses are hard to reach there.
 */
@Composable
internal fun DiaryRowItem(
    row: DiaryRow,
    texts: DiaryTexts,
    first: Boolean,
    last: Boolean,
    onEvent: (DiaryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    val r = BpTheme.shapes.card
    val shape =
        RoundedCornerShape(
            topStart = if (first) r else 0.dp,
            topEnd = if (first) r else 0.dp,
            bottomStart = if (last) r else 0.dp,
            bottomEnd = if (last) r else 0.dp,
        )
    var menu by remember { mutableStateOf(false) }
    val edit = stringResource(R.string.diary_menu_edit)
    val delete = stringResource(R.string.diary_menu_delete)
    val copy = stringResource(R.string.diary_menu_copy)
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        modifier = modifier.clip(shape),
        enableDismissFromStartToEnd = false,
        onDismiss = { onEvent(DiaryEvent.DeleteClicked(row.id)) },
        backgroundContent = { SwipeBackground() },
    ) {
        Box(Modifier.background(colors.sf)) {
            Column {
                RowContent(
                    row = row,
                    texts = texts,
                    modifier =
                        Modifier
                            .combinedClickable(
                                role = Role.Button,
                                onLongClick = { menu = true },
                                onClick = { onEvent(DiaryEvent.RowClicked(row.id)) },
                            ).semantics { customActions = rowActions(row, edit, delete, copy, onEvent) },
                )
                if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ln))
            }
            RowMenu(
                expanded = menu,
                onDismiss = { menu = false },
                items =
                    listOf(
                        edit to DiaryEvent.RowClicked(row.id),
                        delete to DiaryEvent.DeleteClicked(row.id),
                        copy to DiaryEvent.CopyClicked(row),
                    ),
                onEvent = onEvent,
            )
        }
    }
}

private fun rowActions(
    row: DiaryRow,
    edit: String,
    delete: String,
    copy: String,
    onEvent: (DiaryEvent) -> Unit,
) = listOf(
    edit to DiaryEvent.RowClicked(row.id),
    delete to DiaryEvent.DeleteClicked(row.id),
    copy to DiaryEvent.CopyClicked(row),
).map { (label, event) ->
    CustomAccessibilityAction(label) {
        onEvent(event)
        true
    }
}

@Composable
private fun RowContent(
    row: DiaryRow,
    texts: DiaryTexts,
    modifier: Modifier,
) {
    val colors = BpTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.statusColor(row.status)),
        )
        Text(
            text = texts.time(row),
            style = BpTheme.typography.secondary,
            color = colors.mut,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 42.dp),
        )
        Column(Modifier.weight(1f)) {
            BigReading(row.sys, row.dia, BigReadingSize.Row)
            Text(
                text = texts.note(row),
                style = BpTheme.typography.micro,
                color = colors.mut,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        row.pulse?.let { Pulse(it, row.pulseOutOfRange, texts.pulseDescription(it)) }
    }
}

@Composable
private fun Pulse(
    pulse: Int,
    outOfRange: Boolean,
    description: String,
) {
    val colors = BpTheme.colors
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = BpIcon.FavoriteFilled.painter(),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.hi),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "$pulse",
            style = BpTheme.typography.body.copy(fontWeight = FontWeight.W700),
            color = if (outOfRange) colors.hi else colors.ink,
        )
    }
}

@Composable
private fun SwipeBackground() {
    val colors = BpTheme.colors
    Box(
        modifier = Modifier.fillMaxSize().background(colors.hi).padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = stringResource(R.string.diary_swipe_delete),
            style = BpTheme.typography.body.copy(fontWeight = FontWeight.W700),
            color = colors.bg,
        )
    }
}

@Composable
private fun RowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<Pair<String, DiaryEvent>>,
    onEvent: (DiaryEvent) -> Unit,
) {
    val colors = BpTheme.colors
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = colors.sf) {
        items.forEach { (label, event) ->
            DropdownMenuItem(
                text = { Text(label, style = BpTheme.typography.body, color = colors.ink) },
                onClick = {
                    onDismiss()
                    onEvent(event)
                },
            )
        }
    }
}

private fun BpColors.statusColor(status: Status): Color =
    when (status) {
        Status.HIGH -> hi
        Status.NORMAL -> ok
        Status.LOW -> lo
    }
