package ru.bpdiary.feature.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.drawThresholdLine
import ru.bpdiary.domain.LineChartModel
import ru.bpdiary.domain.LinePoint
import ru.bpdiary.domain.yLine

/** Chart units of the line chart (docs/06 §6.5): the canvas keeps the 320×200 proportions. */
internal const val CHART_WIDTH = 320f
private const val CHART_HEIGHT = 200f

/** Plot area between the Y labels and the right edge, as in the prototype. */
internal const val PLOT_START = 30f
private const val PLOT_END = 318f

/** Grid every 20 mmHg from 160 down to 60. */
private const val GRID_TOP = 160
private const val GRID_BOTTOM = 60
private const val GRID_STEP = 20
private val GridValues = (GRID_TOP downTo GRID_BOTTOM step GRID_STEP).toList()
private const val THREE_DIGITS = 100
private const val LABEL_X_WIDE = 2f
private const val LABEL_X_NARROW = 8f

/** Baseline of a Y label below its grid line, in chart units («160» at 41 for the line at 37.7). */
private const val LABEL_BASELINE_SHIFT = 3.3f
private const val DIA_ALPHA = 0.8f
private val LabelSize = 10.sp
private val SysWidth = 2.5.dp
private val DiaWidth = 2.dp
private val GridWidth = 1.dp

/**
 * SYS (`ac`) and DIA (`mut`) lines with dots, grid at 60…160 and the high SYS and DIA dashes.
 * A SYS dot of a HIGH reading is drawn in `hi`, one dp larger (D-20). Values outside 50…180 sit on the border.
 */
@Composable
internal fun LineChart(
    chart: LineChartModel,
    dot: Dp,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = BpTheme.typography.micro.copy(fontSize = LabelSize, color = colors.mut)
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(CHART_WIDTH / CHART_HEIGHT)
                .semantics { contentDescription = description },
    ) {
        val scale = size.width / CHART_WIDTH
        GridValues.forEach { v ->
            val y = yLine(v) * scale
            drawLine(colors.ln, Offset(PLOT_START * scale, y), Offset(PLOT_END * scale, y), GridWidth.toPx())
            val label = measurer.measure("$v", labelStyle)
            val x = if (v >= THREE_DIGITS) LABEL_X_WIDE else LABEL_X_NARROW
            val baseline = (yLine(v) + LABEL_BASELINE_SHIFT) * scale
            drawText(label, topLeft = Offset(x * scale, baseline - label.firstBaseline))
        }
        drawThresholdLine(chart.highSysY, colors.hi, scale, PLOT_START, PLOT_END)
        drawThresholdLine(chart.highDiaY, colors.hi, scale, PLOT_START, PLOT_END)
        val points = chart.points
        drawSeries(points, scale, { it.yDia }, colors.mut.copy(alpha = DIA_ALPHA), DiaWidth)
        drawSeries(points, scale, { it.ySys }, colors.ac, SysWidth)
        val radius = dot.toPx() / 2
        points.forEach { p ->
            drawCircle(colors.mut, radius, Offset(p.x * scale, p.yDia * scale))
            drawCircle(colors.ac, radius, Offset(p.x * scale, p.ySys * scale))
        }
        val highRadius = (dot + 1.dp).toPx() / 2
        points.filter { it.highStatus }.forEach { p ->
            drawCircle(colors.hi, highRadius, Offset(p.x * scale, p.ySys * scale))
        }
    }
}

private fun DrawScope.drawSeries(
    points: List<LinePoint>,
    scale: Float,
    y: (LinePoint) -> Float,
    color: Color,
    width: Dp,
) {
    val path = Path()
    points.forEachIndexed { i, p ->
        if (i == 0) path.moveTo(p.x * scale, y(p) * scale) else path.lineTo(p.x * scale, y(p) * scale)
    }
    drawPath(path, color, style = Stroke(width = width.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round))
}
