package ru.bpdiary.feature.charts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.charts.R
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.SegmentedToggle

private const val FADE_MS = 150
private val DotWeek = 8.dp
private val DotMonth = 4.dp

/** Charts (handoff screen 4). Stateless: everything comes from [state]. */
@Composable
fun ChartsScreen(
    state: ChartsUiState,
    onEvent: (ChartsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = rememberChartsTexts()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BpTheme.colors.bg)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(state.period, texts, onEvent)
        if (state.loading) return@Column
        // Switching 7/30 fades instead of blinking (docs/05 §5.4).
        AnimatedContent(
            targetState = state,
            contentKey = { it.period },
            transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
            label = "period",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { PeriodContent(shown, texts) }
        }
    }
}

@Composable
private fun Header(
    period: Period,
    texts: ChartsTexts,
    onEvent: (ChartsEvent) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.charts_title),
            style = BpTheme.typography.screenTitle,
            color = BpTheme.colors.ink,
            modifier = Modifier.padding(end = 8.dp).semantics { heading() },
        )
        val periods = Period.entries
        SegmentedToggle(
            options = periods.map { texts.period(it) },
            selectedIndex = period.ordinal,
            onSelect = { onEvent(ChartsEvent.PeriodSelected(periods[it])) },
        )
    }
}

@Composable
private fun PeriodContent(
    state: ChartsUiState,
    texts: ChartsTexts,
) {
    val chart = state.chart
    val stats = state.stats
    if (chart == null || stats == null) {
        BpCard {
            Text(
                text = stringResource(R.string.charts_not_enough),
                style = BpTheme.typography.body,
                color = BpTheme.colors.mut,
            )
        }
        return
    }
    BpCard(padding = 0.dp) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Legend()
            LineChart(
                chart = chart,
                dot = if (state.period == Period.DAYS_7) DotWeek else DotMonth,
                description = texts.description(state.period, stats, state.today),
            )
            AxisLabels(texts.fromDate(state.from))
            if (chart.clipped) {
                Text(
                    text = stringResource(R.string.charts_clipped),
                    style = BpTheme.typography.micro,
                    color = BpTheme.colors.mut,
                )
            }
        }
    }
    StatsGrid(texts.cells(stats, state.today))
    InRangeCard(stats, texts)
}

@Composable
private fun Legend() {
    val colors = BpTheme.colors
    FlowRow(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(stringResource(R.string.charts_legend_sys)) { LegendDot(colors.ac) }
        LegendItem(stringResource(R.string.charts_legend_dia)) { LegendDot(colors.mut) }
        LegendItem(stringResource(R.string.charts_legend_threshold)) {
            Box(
                Modifier.size(width = 14.dp, height = 2.dp).drawBehind {
                    drawLine(
                        color = colors.hi,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = size.height,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())),
                    )
                },
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    mark: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(label, style = BpTheme.typography.caption, color = BpTheme.colors.mut)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

/** «14.10» under the start of the plot, «Сегодня» at the right (prototype padding 0 2 0 30 in chart units). */
@Composable
private fun AxisLabels(from: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unit = maxWidth / CHART_WIDTH
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = unit * PLOT_START, end = unit * 2),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(from, style = BpTheme.typography.micro, color = BpTheme.colors.mut)
            Text(stringResource(R.string.charts_today), style = BpTheme.typography.micro, color = BpTheme.colors.mut)
        }
    }
}

@Composable
private fun StatsGrid(cells: List<StatCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { cell -> StatCellCard(cell, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatCellCard(
    cell: StatCell,
    modifier: Modifier,
) {
    val colors = BpTheme.colors
    BpCard(modifier = modifier, padding = 0.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).semantics(true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(cell.label, style = BpTheme.typography.caption, color = colors.mut)
            Text(cell.value, style = BpTheme.typography.numStat, color = colors.ink, maxLines = 1)
            Text(cell.caption, style = BpTheme.typography.micro, color = colors.mut)
        }
    }
}
