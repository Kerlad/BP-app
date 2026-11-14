package ru.bpdiary.feature.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.ChartScale
import ru.bpdiary.core.ui.drawThresholdLine
import ru.bpdiary.core.ui.shrinkingAspectRatio
import ru.bpdiary.domain.LineChartModel
import ru.bpdiary.domain.LinePoint
import ru.bpdiary.domain.yLine

/** Chart units of the line chart (docs/06 §6.5): the canvas is 320×200 or flatter when the screen has less room. */
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

/** Grid step, in label heights, below which only every other Y label is drawn. */
private const val LABEL_ROOM = 1.1f
private val LabelSize = 10.sp
private val LabelGap = 4.dp
private val SysWidth = 2.5.dp
private val DiaWidth = 2.dp
private val GridWidth = 1.dp
private val MinChartHeight = 96.dp

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
                .shrinkingAspectRatio(CHART_WIDTH / CHART_HEIGHT, MinChartHeight * LocalDensity.current.fontScale)
                .semantics { contentDescription = description },
    ) {
        val scale = ChartScale(size.width / CHART_WIDTH, size.height / CHART_HEIGHT)
        val labels = GridValues.associateWith { measurer.measure("$it", labelStyle) }
        val map = plotMapping(scale, labels.values.maxOf { it.size.width }, labels.getValue(GRID_TOP))
        // Every other label when they would touch (large text, F-02); the grid lines all stay.
        val labelHeight = labels.getValue(GRID_TOP).size.height
        val thin = map.y(yLine(GRID_TOP - GRID_STEP)) - map.y(yLine(GRID_TOP)) < labelHeight * LABEL_ROOM
        GridValues.forEach { v ->
            val y = map.y(yLine(v))
            drawLine(colors.ln, Offset(map.plotStart, y), Offset(map.plotEnd, y), GridWidth.toPx())
            if (thin && (GRID_TOP - v) % (2 * GRID_STEP) != 0) return@forEach
            val label = labels.getValue(v)
            // Right-aligned before the plot once the labels are wider than the prototype's column (F-02).
            val x =
                if (map.widened) {
                    map.plotStart - LabelGap.toPx() - label.size.width
                } else {
                    (if (v >= THREE_DIGITS) LABEL_X_WIDE else LABEL_X_NARROW) * scale.x
                }
            drawText(label, topLeft = Offset(x, y + LABEL_BASELINE_SHIFT * scale.y - label.firstBaseline))
        }
        // Threshold dashes keep their prototype length: x stays scaled, y is passed in pixels.
        val dashScale = ChartScale(scale.x, 1f)
        val dashStart = map.plotStart / scale.x
        val dashEnd = map.plotEnd / scale.x
        drawThresholdLine(map.y(chart.highSysY), colors.hi, dashScale, dashStart, dashEnd)
        drawThresholdLine(map.y(chart.highDiaY), colors.hi, dashScale, dashStart, dashEnd)
        val points = chart.points
        drawSeries(points, map, { it.yDia }, colors.mut.copy(alpha = DIA_ALPHA), DiaWidth)
        drawSeries(points, map, { it.ySys }, colors.ac, SysWidth)
        val radius = dot.toPx() / 2
        points.forEach { p ->
            drawCircle(colors.mut, radius, Offset(map.x(p.x), map.y(p.yDia)))
            drawCircle(colors.ac, radius, Offset(map.x(p.x), map.y(p.ySys)))
        }
        val highRadius = (dot + 1.dp).toPx() / 2
        points.filter { it.highStatus }.forEach { p ->
            drawCircle(colors.hi, highRadius, Offset(map.x(p.x), map.y(p.ySys)))
        }
    }
}

/**
 * Chart units to pixels. With the prototype's label sizes this is a plain scale; with large text (F-02) the plot
 * starts after the widest Y label and the grid is squeezed vertically so that «160» and «60» stay inside the canvas
 * instead of running into the legend above and the dates below.
 */
private class PlotMapping(
    val plotStart: Float,
    val plotEnd: Float,
    val widened: Boolean,
    private val k: Float,
    private val c: Float,
) {
    fun x(u: Float): Float = plotStart + (u - PLOT_START) * (plotEnd - plotStart) / (PLOT_END - PLOT_START)

    fun y(u: Float): Float = k * u + c
}

private fun DrawScope.plotMapping(
    scale: ChartScale,
    labelWidth: Int,
    topLabel: TextLayoutResult,
): PlotMapping {
    val prototypeStart = PLOT_START * scale.x
    val neededStart = LABEL_X_WIDE * scale.x + labelWidth + LabelGap.toPx()
    val plotStart = maxOf(prototypeStart, neededStart)
    // Space a label takes above and below its grid line (its baseline is LABEL_BASELINE_SHIFT under the line).
    val above = topLabel.firstBaseline - LABEL_BASELINE_SHIFT * scale.y
    val below = topLabel.size.height - above
    val top = yLine(GRID_TOP)
    val bottom = yLine(GRID_BOTTOM)
    val topPx = maxOf(top * scale.y, above)
    val bottomPx = minOf(bottom * scale.y, size.height - below)
    val k = (bottomPx - topPx) / (bottom - top)
    return PlotMapping(
        plotStart = plotStart,
        plotEnd = PLOT_END * scale.x,
        widened = plotStart > prototypeStart,
        k = k,
        c = topPx - k * top,
    )
}

private fun DrawScope.drawSeries(
    points: List<LinePoint>,
    map: PlotMapping,
    y: (LinePoint) -> Float,
    color: Color,
    width: Dp,
) {
    val path = Path()
    points.forEachIndexed { i, p ->
        if (i == 0) path.moveTo(map.x(p.x), map.y(y(p))) else path.lineTo(map.x(p.x), map.y(y(p)))
    }
    drawPath(path, color, style = Stroke(width = width.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round))
}
