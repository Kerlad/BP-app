package ru.bpdiary.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The 15 design tokens, in the order of the design handoff README so that the colour lists carry over one to one:
 * `bg, sf, sf2, ink, mut, ac, aci, acs, hi, hiS, ok, okS, lo, loS, ln`.
 */
@Immutable
data class BpColors(
    /** Screen background. */
    val bg: Color,
    /** Cards, surface. */
    val sf: Color,
    /** Navigation bar, steppers. */
    val sf2: Color,
    /** Primary text. */
    val ink: Color,
    /** Secondary text. */
    val mut: Color,
    /** Accent. */
    val ac: Color,
    /** Text on the accent. */
    val aci: Color,
    /** Soft accent: pills, background of the active navigation item. */
    val acs: Color,
    /** High status and its soft background. */
    val hi: Color,
    val hiS: Color,
    /** Normal status and its soft background. */
    val ok: Color,
    val okS: Color,
    /** Low status and its soft background. */
    val lo: Color,
    val loS: Color,
    /** Lines, outlines, chart grid. */
    val ln: Color,
) {
    /** The tokens in handoff order. */
    internal fun tokens(): List<Color> = listOf(bg, sf, sf2, ink, mut, ac, aci, acs, hi, hiS, ok, okS, lo, loS, ln)
}
