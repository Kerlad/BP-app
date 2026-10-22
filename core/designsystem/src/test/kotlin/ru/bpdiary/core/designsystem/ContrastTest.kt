package ru.bpdiary.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.bpdiary.core.model.Direction
import java.io.File
import kotlin.math.pow

/**
 * WCAG 2.x contrast of the text/background pairs of every palette (NFR: at least 4.5:1 for text).
 *
 * This test measures and reports; it does not fail on a low ratio, because the colours belong to the designer.
 * The report goes to the console and to `build/reports/contrast.txt`.
 */
private const val MIN_TEXT_RATIO = 4.5

class ContrastTest {
    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <=
            LINEAR_THRESHOLD
        ) {
            v / LINEAR_DIVISOR
        } else {
            ((v + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA_EXPONENT)
        }
    }

    private fun luminance(c: Color): Double =
        LUMA_RED * channel(c.red) + LUMA_GREEN * channel(c.green) + LUMA_BLUE * channel(c.blue)

    private fun ratio(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + FLARE) / (minOf(la, lb) + FLARE)
    }

    private data class Pair(
        val name: String,
        val text: (BpColors) -> Color,
        val background: (BpColors) -> Color,
    )

    private val pairs =
        listOf(
            Pair("ok on okS (status chip)", { it.ok }, { it.okS }),
            Pair("hi on hiS (status chip)", { it.hi }, { it.hiS }),
            Pair("lo on loS (status chip)", { it.lo }, { it.loS }),
            Pair("mut on bg", { it.mut }, { it.bg }),
            Pair("mut on sf", { it.mut }, { it.sf }),
            Pair("ink on bg", { it.ink }, { it.bg }),
            Pair("ink on sf", { it.ink }, { it.sf }),
            Pair("aci on ac (button text)", { it.aci }, { it.ac }),
            Pair("ac on acs (keypad action key)", { it.ac }, { it.acs }),
            Pair("ink on acs (active nav label)", { it.ink }, { it.acs }),
            Pair("mut on sf2 (inactive nav label)", { it.mut }, { it.sf2 }),
            Pair("hi on sf (pulse out of range)", { it.hi }, { it.sf }),
            Pair("bg on ink (toast)", { it.bg }, { it.ink }),
        )

    @Test
    fun `contrast formula matches the WCAG reference values`() {
        assertEquals(21.0, ratio(Color.Black, Color.White), 0.001)
        assertEquals(1.0, ratio(Color.White, Color.White), 0.001)
        // #767676 on white is the well-known 4.54:1 border case.
        assertEquals(4.54, ratio(Color(0xFF767676), Color.White), 0.01)
    }

    private data class Row(
        val palette: String,
        val pair: String,
        val ratio: Double,
    ) {
        val low: Boolean get() = ratio < MIN_TEXT_RATIO
    }

    private fun rows(): List<Row> =
        Direction.entries.flatMap { direction ->
            listOf(false, true).flatMap { dark ->
                val colors = bpColors(direction, dark)
                val palette = "$direction ${if (dark) "dark" else "light"}"
                pairs.map { Row(palette, it.name, ratio(it.text(colors), it.background(colors))) }
            }
        }

    @Test
    fun `report contrast of every pair in every palette`() {
        val rows = rows()
        val lines = mutableListOf("WCAG contrast ratios, minimum for text is 4.5:1")
        for ((palette, group) in rows.groupBy { it.palette }) {
            lines += ""
            lines += palette
            group.forEach {
                val flag = if (it.low) "  <-- below 4.5" else ""
                lines += "  %-34s %5.2f%s".format(it.pair, it.ratio, flag)
            }
        }
        val below = rows.filter { it.low }
        lines += ""
        lines += "Pairs below 4.5:1: ${below.size}"
        below.forEach { lines += "${it.palette}: ${it.pair} = %.2f".format(it.ratio) }
        val report = lines.joinToString(System.lineSeparator())
        println(report)
        File("build/reports").apply { mkdirs() }.resolve("contrast.txt").writeText(report)
    }

    private companion object {
        const val LINEAR_THRESHOLD = 0.04045
        const val LINEAR_DIVISOR = 12.92
        const val GAMMA_OFFSET = 0.055
        const val GAMMA_DIVISOR = 1.055
        const val GAMMA_EXPONENT = 2.4
        const val LUMA_RED = 0.2126
        const val LUMA_GREEN = 0.7152
        const val LUMA_BLUE = 0.0722
        const val FLARE = 0.05
    }
}
