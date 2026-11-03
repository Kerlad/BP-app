package ru.bpdiary.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.bpdiary.app.feature.home.R
import ru.bpdiary.core.designsystem.BpColors
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.ChartScale
import ru.bpdiary.core.ui.drawThresholdLine
import ru.bpdiary.core.ui.shrinkingAspectRatio
import ru.bpdiary.domain.WeekBars

/** Chart units of the week bars (docs/06 §6.5): the canvas is 300×120 or flatter when Home has less room. */
private const val CHART_WIDTH = 300f
private const val CHART_HEIGHT = 120f
private const val NORMAL_ALPHA = 0.85f
private val BarWidth = 16.dp
private val MinDayFont = 9.sp

/** «7 дней»: one bar per day from the highest SYS to the lowest DIA, coloured by the day's mean status. */
@Composable
internal fun WeekCard(
    week: WeekBars,
    texts: HomeTexts,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    BpCard(modifier = modifier, padding = 0.dp, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.home_week_title),
                    style = BpTheme.typography.cardTitle,
                    color = colors.ink,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Average(week, texts)
            }
            val avgSys = week.avgSys
            val avgDia = week.avgDia
            val description =
                if (avgSys != null && avgDia != null) {
                    stringResource(R.string.home_week_chart_description, avgSys, avgDia)
                } else {
                    null
                }
            Canvas(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .shrinkingAspectRatio(CHART_WIDTH / CHART_HEIGHT)
                        .semantics { if (description != null) contentDescription = description },
            ) {
                val scale = ChartScale(size.width / CHART_WIDTH, size.height / CHART_HEIGHT)
                drawThresholdLine(week.highSysY, colors.hi, scale)
                week.days.mapNotNull { it.bar }.forEach { bar ->
                    drawLine(
                        color = colors.barColor(bar.status),
                        start = Offset(bar.x * scale.x, bar.yTop * scale.y),
                        end = Offset(bar.x * scale.x, bar.yBottom * scale.y),
                        strokeWidth = BarWidth.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            DayLabels(week, texts)
        }
    }
}

@Composable
private fun DayLabels(
    week: WeekBars,
    texts: HomeTexts,
) {
    val style = BpTheme.typography.micro.copy(color = BpTheme.colors.mut, textAlign = TextAlign.Center)
    Row(Modifier.fillMaxWidth()) {
        week.days.forEach { slot ->
            // Shrinks at large font scales instead of cutting «Сег» to «Се».
            BasicText(
                text = texts.weekDay(slot),
                style = style,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = MinDayFont, maxFontSize = style.fontSize),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Average(
    week: WeekBars,
    texts: HomeTexts,
) {
    val colors = BpTheme.colors
    val style = BpTheme.typography.secondary
    val sys = week.avgSys
    val dia = week.avgDia
    if (sys == null || dia == null) {
        Text(stringResource(R.string.home_week_empty), style = style, color = colors.mut)
        return
    }
    val value = texts.average(sys, dia)
    val line = texts.averageLine(value)
    val start = line.indexOf(value)
    val text =
        buildAnnotatedString {
            append(line.substring(0, start))
            withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.W700)) { append(value) }
            append(line.substring(start + value.length))
        }
    Text(text, style = style, color = colors.mut, maxLines = 1)
}

private fun BpColors.barColor(status: Status) =
    when (status) {
        Status.NORMAL -> ac.copy(alpha = NORMAL_ALPHA)
        Status.HIGH -> hi
        Status.LOW -> lo
    }
