package ru.bpdiary.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.bpdiary.app.core.ui.R
import ru.bpdiary.core.designsystem.BpTheme

/** At a system font scale above this the hero number shrinks and the unit moves to its own line (docs/04 §4.7). */
private const val LARGE_FONT_SCALE = 1.3f
private const val HERO_SIZE_AT_LARGE_FONT = 48

/**
 * "128/82" with an optional unit. One merged semantics node reads "128 на 82 миллиметра ртутного столба";
 * the inner texts are hidden from accessibility. The numbers never wrap.
 */
@Composable
fun BigReading(
    sys: Int,
    dia: Int,
    size: BigReadingSize,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.reading_description, sys, dia)
    val semanticsModifier = modifier.clearAndSetSemantics { contentDescription = description }
    when (size) {
        BigReadingSize.Hero -> HeroReading(sys, dia, semanticsModifier)
        BigReadingSize.Entry -> PlainReading(sys, dia, BpTheme.typography.numEntry, semanticsModifier)
        BigReadingSize.Row -> PlainReading(sys, dia, BpTheme.typography.numRow, semanticsModifier)
    }
}

@Composable
private fun PlainReading(
    sys: Int,
    dia: Int,
    style: TextStyle,
    modifier: Modifier,
) {
    Text("$sys/$dia", style = style, color = BpTheme.colors.ink, maxLines = 1, softWrap = false, modifier = modifier)
}

@Composable
private fun HeroReading(
    sys: Int,
    dia: Int,
    modifier: Modifier,
) {
    val colors = BpTheme.colors
    val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val hero = BpTheme.typography.numHero.let { if (large) it.copy(fontSize = HERO_SIZE_AT_LARGE_FONT.sp) else it }
    val gap = with(LocalDensity.current) { NUMBER_GAP.dp.roundToPx() }
    val unitGap = with(LocalDensity.current) { UNIT_GAP.dp.roundToPx() }
    Layout(
        modifier = modifier,
        content = {
            Text("$sys", style = hero, color = colors.ink, maxLines = 1, softWrap = false)
            Text("/", style = BpTheme.typography.slash, color = colors.mut, maxLines = 1, softWrap = false)
            Text("$dia", style = hero, color = colors.ink, maxLines = 1, softWrap = false)
            Text(
                stringResource(R.string.unit_mmhg),
                style = BpTheme.typography.unit,
                color = colors.mut,
                maxLines = 1,
                softWrap = false,
            )
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val (sysP, slashP, diaP) = placeables
        val unitP = placeables.last()
        val baseline = maxOf(sysP[FirstBaseline], diaP[FirstBaseline])
        val numbersWidth = sysP.width + gap + slashP.width + gap + diaP.width
        val numbersHeight = maxOf(baseline - slashP[FirstBaseline] + slashP.height, sysP.height, diaP.height)
        // The unit sits on the baseline after the digits; when it does not fit (or at a large font) it moves below.
        val unitBeside = !large && numbersWidth + gap + unitGap + unitP.width <= constraints.maxWidth
        val width = if (unitBeside) numbersWidth + gap + unitGap + unitP.width else maxOf(numbersWidth, unitP.width)
        val height = if (unitBeside) numbersHeight else numbersHeight + unitP.height
        layout(width.coerceAtMost(constraints.maxWidth).coerceAtLeast(constraints.minWidth), height) {
            sysP.placeRelative(0, baseline - sysP[FirstBaseline])
            val slashX = sysP.width + gap
            slashP.placeRelative(slashX, baseline - slashP[FirstBaseline])
            val diaX = slashX + slashP.width + gap
            diaP.placeRelative(diaX, baseline - diaP[FirstBaseline])
            if (unitBeside) {
                unitP.placeRelative(diaX + diaP.width + gap + unitGap, baseline - unitP[FirstBaseline])
            } else {
                unitP.placeRelative(0, numbersHeight)
            }
        }
    }
}

private const val NUMBER_GAP = 6
private const val UNIT_GAP = 4
