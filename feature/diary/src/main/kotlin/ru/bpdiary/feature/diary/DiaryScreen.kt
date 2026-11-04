package ru.bpdiary.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import ru.bpdiary.app.feature.diary.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpPrimaryButton
import ru.bpdiary.core.ui.BpSecondaryButton
import ru.bpdiary.core.ui.FilterChipRow

/** Diary (handoff screen 3). Stateless: the filter comes from [state], the rows from [items]. */
@Composable
fun DiaryScreen(
    state: DiaryUiState,
    items: LazyPagingItems<DiaryItem>,
    onEvent: (DiaryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = rememberDiaryTexts()
    val padding =
        WindowInsets.safeDrawing
            .add(WindowInsets(left = 20.dp, top = 8.dp, right = 20.dp, bottom = 24.dp))
            .asPaddingValues()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(BpTheme.colors.bg),
        contentPadding = padding,
    ) {
        item(key = "title", contentType = "title") {
            TitleBlock(onEvent)
        }
        item(key = "filters", contentType = "filters") {
            val filters = DiaryFilter.entries
            FilterChipRow(
                items = filters.map { texts.filter(it) },
                selected = setOf(state.filter.ordinal),
                multiSelect = false,
                onToggle = { onEvent(DiaryEvent.FilterSelected(filters[it])) },
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        val loaded = items.loadState.refresh is LoadState.NotLoading
        if (loaded && items.itemCount == 0 && state.total != null) {
            item(key = "empty", contentType = "empty") {
                if (state.total == 0) DiaryEmpty(onEvent) else FilterEmpty()
            }
        }
        rows(state, items, texts, onEvent)
    }
}

/** The title over «Импорт» and «Экспорт»: side by side, one under the other when a large font needs the room. */
@Composable
private fun TitleBlock(onEvent: (DiaryEvent) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.diary_title),
            style = BpTheme.typography.screenTitle,
            color = BpTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BpSecondaryButton(
                text = stringResource(R.string.diary_import),
                onClick = { onEvent(DiaryEvent.ImportClicked) },
                modifier = Modifier.weight(1f),
                icon = BpIcon.Download,
            )
            BpSecondaryButton(
                text = stringResource(R.string.diary_export),
                onClick = { onEvent(DiaryEvent.ExportClicked) },
                modifier = Modifier.weight(1f),
                icon = BpIcon.Upload,
            )
        }
    }
}

private fun LazyListScope.rows(
    state: DiaryUiState,
    items: LazyPagingItems<DiaryItem>,
    texts: DiaryTexts,
    onEvent: (DiaryEvent) -> Unit,
) {
    items(
        count = items.itemCount,
        key = items.itemKey { it.key },
        contentType = items.itemContentType { if (it is DiaryItem.Header) "header" else "row" },
    ) { index ->
        when (val item = items[index]) {
            is DiaryItem.Header -> {
                DayHeader(texts.header(item.day, state.today))
            }

            is DiaryItem.Row -> {
                // A day's rows form one card: round the top of its first row and the bottom of its last.
                val first = index == 0 || items.peek(index - 1) !is DiaryItem.Row
                val last = index == items.itemCount - 1 || items.peek(index + 1) !is DiaryItem.Row
                DiaryRowItem(item.row, texts, first, last, onEvent, Modifier.animateItem())
            }

            null -> {
                Unit
            }
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text = text,
        style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
        color = BpTheme.colors.mut,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun FilterEmpty() {
    Text(
        text = stringResource(R.string.diary_filter_empty),
        style = BpTheme.typography.body,
        color = BpTheme.colors.mut,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
    )
}

@Composable
private fun DiaryEmpty(onEvent: (DiaryEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.diary_empty),
            style = BpTheme.typography.body,
            color = BpTheme.colors.mut,
            textAlign = TextAlign.Center,
        )
        BpPrimaryButton(
            text = stringResource(R.string.diary_add_reading),
            onClick = { onEvent(DiaryEvent.AddClicked) },
            icon = BpIcon.Add,
        )
    }
}
