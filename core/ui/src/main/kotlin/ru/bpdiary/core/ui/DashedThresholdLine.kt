package ru.bpdiary.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

private const val DASH_ON = 4f
private const val DASH_OFF = 5f
private const val LINE_ALPHA = 0.7f
private val LineWidth = 1.5.dp

/**
 * Draws the threshold dashes (colour `hi`, alpha .7): [y] is in chart units, [scale] converts them to pixels.
 * Dash lengths scale with the chart, the stroke width stays in dp.
 */
fun DrawScope.drawThresholdLine(
    y: Float,
    color: Color,
    scale: Float = 1f,
) {
    drawLine(
        color = color.copy(alpha = LINE_ALPHA),
        start = Offset(0f, y * scale),
        end = Offset(size.width, y * scale),
        strokeWidth = LineWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON * scale, DASH_OFF * scale)),
    )
}

/**
 * Stand-alone variant. The chart is [chartWidth] units wide (300 for the week card, 320 for the line chart);
 * the line is stretched to the real width with one scale factor, as in docs/06 §6.5.
 */
@Composable
fun DashedThresholdLine(
    y: Float,
    color: Color,
    chartWidth: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) { drawThresholdLine(y, color, scale = size.width / chartWidth) }
}
