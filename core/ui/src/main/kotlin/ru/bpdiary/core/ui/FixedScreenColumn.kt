package ru.bpdiary.core.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private object FlexibleChild

/** Above this font scale the flexible block is never squeezed: the whole screen scrolls instead. */
private const val LARGE_FONT_SCALE = 1.3f

/** Marks the child of [FixedScreenColumn] that gets the height the others leave. */
fun Modifier.flexibleHeight(): Modifier = layoutId(FlexibleChild)

/**
 * A main screen that fits the display and does not move: the children keep their height and the one marked
 * [flexibleHeight] gets what is left, down to its minimum intrinsic height. Only when even that does not fit (a small
 * display or a large font) the column scrolls, so that nothing becomes unreachable, and the flexible child takes
 * its full height again: there is no point in squeezing what can be scrolled to. At a font scale above 1.3 the
 * flexible child is not squeezed at all (a squeezed chart overlaps the large text): the screen scrolls.
 */
@Composable
fun FixedScreenColumn(
    gap: Dp,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    BoxWithConstraints(modifier) {
        val room = maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()
        val scroll = rememberScrollState()
        Layout(
            content = content,
            modifier =
                Modifier
                    // Disabled while everything fits: otherwise a swipe still stretches the screen (overscroll).
                    .verticalScroll(scroll, enabled = scroll.canScrollForward || scroll.canScrollBackward)
                    .padding(contentPadding),
        ) { measurables, constraints ->
            val free = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val gapPx = gap.roundToPx()
            val placeables = measurables.map { if (it.layoutId == FlexibleChild) null else it.measure(free) }
            val fixedHeight = placeables.sumOf { it?.height ?: 0 } + gapPx * (measurables.size - 1).coerceAtLeast(0)
            val all =
                placeables.mapIndexed { index, placeable ->
                    placeable ?: measurables[index].let { flexible ->
                        val left = room.roundToPx() - fixedHeight
                        val fits = !largeFont && left >= flexible.minIntrinsicHeight(constraints.maxWidth)
                        // Not squeezed: its natural height, as a finite maximum (a weighted child in a column with an
                        // infinite maximum gets no room and the chart would overlap the text above it).
                        val natural = flexible.maxIntrinsicHeight(constraints.maxWidth)
                        flexible.measure(free.copy(maxHeight = if (fits) left else natural))
                    }
                }
            val height = fixedHeight + all.sumOf { it.height } - placeables.sumOf { it?.height ?: 0 }
            layout(constraints.maxWidth, height) {
                var y = 0
                all.forEach { placeable ->
                    placeable.place(0, y)
                    y += placeable.height + gapPx
                }
            }
        }
    }
}

/**
 * The full width, as high as [ratio] asks for, at least [minHeight], but never more than the parent allows.
 * The way a chart gives way on a [FixedScreenColumn]; give it a weight without fill in its column.
 */
fun Modifier.shrinkingAspectRatio(
    ratio: Float,
    minHeight: Dp = 56.dp,
): Modifier = then(ShrinkingAspectRatio(ratio, minHeight))

private data class ShrinkingAspectRatio(
    val ratio: Float,
    val minHeight: Dp,
) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.maxWidth
        val wanted = (width / ratio).roundToInt()
        // Never taller than the parent allows: a taller child is centred on its slot and overflows it, so the chart
        // ran into the legend above and the dates below at font scale 2 (F-02). [minHeight] still steers the parent
        // through the intrinsic height.
        val height = wanted.coerceAtLeast(minHeight.roundToPx()).coerceAtMost(constraints.maxHeight)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        return layout(width, height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = minHeight.roundToPx()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = if (width == Constraints.Infinity) minHeight.roundToPx() else (width / ratio).roundToInt()
}
