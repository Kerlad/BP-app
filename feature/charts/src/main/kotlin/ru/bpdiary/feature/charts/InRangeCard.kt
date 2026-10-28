package ru.bpdiary.feature.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.bpdiary.app.feature.charts.R
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.domain.PeriodStats

private const val PERCENT = 100f
private val PercentSize = 20.sp

/**
 * «В пределах ваших уровней»: percent of normal readings in `ok`, a 12 dp bar of hi/ok/lo segments sized by
 * count with 2 dp gaps (a zero segment is not drawn and leaves no gap), and the three counters.
 */
@Composable
internal fun InRangeCard(
    stats: PeriodStats,
    texts: ChartsTexts,
) {
    val colors = BpTheme.colors
    val title = stringResource(R.string.charts_in_range_title)
    val percent = texts.percent(stats.inRangePercent)
    BpCard(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    title,
                    style = BpTheme.typography.cardTitle,
                    color = colors.ink,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(percent, style = BpTheme.typography.numStat.copy(fontSize = PercentSize), color = colors.ok)
            }
            val segments =
                listOf(stats.countHigh to colors.hi, stats.countNormal to colors.ok, stats.countLow to colors.lo)
                    .filter { it.first > 0 }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clearAndSetSemantics {
                            contentDescription = "$title: $percent"
                            progressBarRangeInfo = ProgressBarRangeInfo(stats.inRangePercent / PERCENT, 0f..1f)
                        },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                segments.forEach { (count, color) -> Segment(count, color) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Counter(texts.count(Status.HIGH, stats.countHigh), stats.countHigh)
                Counter(texts.count(Status.NORMAL, stats.countNormal), stats.countNormal)
                Counter(texts.count(Status.LOW, stats.countLow), stats.countLow)
            }
        }
    }
}

@Composable
private fun RowScope.Segment(
    count: Int,
    color: Color,
) {
    Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
}

/** «Повышенное: 6» with the number in bold `ink`. */
@Composable
private fun Counter(
    line: String,
    value: Int,
) {
    val colors = BpTheme.colors
    val number = "$value"
    val start = line.lastIndexOf(number)
    val text =
        buildAnnotatedString {
            append(line.substring(0, start))
            withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.W700)) { append(number) }
            append(line.substring(start + number.length))
        }
    Text(text, style = BpTheme.typography.caption, color = colors.mut)
}
