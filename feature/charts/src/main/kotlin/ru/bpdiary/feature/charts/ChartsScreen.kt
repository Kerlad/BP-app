package ru.bpdiary.feature.charts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.charts.R
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.FixedScreenColumn
import ru.bpdiary.core.ui.SegmentedToggle
import ru.bpdiary.core.ui.flexibleHeight

private const val FADE_MS = 150
private const val PLOT_END_GAP = 2f
private val DotWeek = 8.dp
private val DotMonth = 4.dp

/** Above this font scale three statistics do not fit a row. */
private const val THREE_COLUMNS_MAX_FONT_SCALE = 1.15f
private const val COLUMNS = 3
private const val COLUMNS_LARGE_FONT = 2

/** Charts (handoff screen 4). Stateless: everything comes from [state]. */
@Composable
fun ChartsScreen(
    state: ChartsUiState,
    onEvent: (ChartsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = rememberChartsTexts()
    FixedScreenColumn(
        gap = 14.dp,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        modifier =
            modifier
                .fillMaxSize()
                .background(BpTheme.colors.bg)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Header(state.period, texts, onEvent)
        if (!state.loading) {
            // Switching 7/30 fades instead of blinking (docs/05 §5.4).
            AnimatedContent(
                targetState = state,
                contentKey = { it.period },
                transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
                label = "period",
                modifier = Modifier.flexibleHeight(),
            ) { shown ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { PeriodContent(shown, texts) }
            }
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
private fun ColumnScope.PeriodContent(
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
    BpCard(modifier = Modifier.weight(1f, fill = false), padding = 0.dp) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Legend()
            LineChart(
                chart = chart,
                dot = if (state.period == Period.DAYS_7) DotWeek else DotMonth,
                description = texts.description(state.period, stats, state.today),
                modifier = Modifier.weight(1f, fill = false),
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
        LegendItem(stringResource(R.string.charts_legend_dia)) { LegendDot(colors.mut, square = true) }
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
private fun LegendDot(
    color: Color,
    square: Boolean = false,
) {
    // The same shapes as the chart dots: round for SYS, square for DIA (review C-07).
    Box(Modifier.size(10.dp).clip(if (square) RectangleShape else CircleShape).background(color))
}

/** «14.10» under the start of the plot, «Сегодня» at the right (prototype padding 0 2 0 30 in chart units). */
@Composable
private fun AxisLabels(from: String) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(PLOT_START))
        Row(
            modifier = Modifier.weight(CHART_WIDTH - PLOT_START - PLOT_END_GAP),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(from, style = BpTheme.typography.micro, color = BpTheme.colors.mut)
            Text(stringResource(R.string.charts_today), style = BpTheme.typography.micro, color = BpTheme.colors.mut)
        }
        Spacer(Modifier.weight(PLOT_END_GAP))
    }
}

@Composable
private fun StatsGrid(cells: List<StatCell>) {
    val columns = if (LocalDensity.current.fontScale > THREE_COLUMNS_MAX_FONT_SCALE) COLUMNS_LARGE_FONT else COLUMNS
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(columns).forEach { row ->
            // The cards of a row are equally high when a caption takes two lines.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell -> StatCellCard(cell, Modifier.weight(1f).fillMaxHeight()) }
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp).semantics(true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(cell.label, style = BpTheme.typography.caption, color = colors.mut)
            Text(cell.value, style = BpTheme.typography.numRow, color = colors.ink, maxLines = 1)
            Text(cell.caption, style = BpTheme.typography.micro, color = colors.mut)
        }
    }
}
