package ru.bpdiary.core.designsystem

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.bpdiary.core.model.Direction
import java.io.File

class PalettesTest {
    /** The handoff is the source of truth: the colours are read from its README, not copied into the test. */
    private val handoff: List<String> =
        File("../../design_handoff_bp_diary/README.md").readLines()

    /** The 15 hex values of "- Light:" or "- Dark:" under a "**1x ...**" heading. */
    private fun expected(
        heading: String,
        line: String,
    ): List<Int> {
        val start = handoff.indexOfFirst { it.startsWith("**$heading") }
        require(start >= 0) { "Heading $heading not found in the handoff" }
        val row = handoff.drop(start).first { it.startsWith("- $line:") }
        return Regex(
            "#([0-9A-Fa-f]{6})",
        ).findAll(row).map { (0xFF000000L or it.groupValues[1].toLong(16)).toInt() }.toList()
    }

    private fun actual(colors: BpColors): List<Int> = colors.tokens().map { it.toArgb() }

    @Test
    fun `handoff lists 15 values per palette`() {
        for (heading in listOf("1a", "1b", "1c")) {
            assertEquals(15, expected(heading, "Light").size)
            assertEquals(15, expected(heading, "Dark").size)
        }
    }

    @Test
    fun `peach matches the handoff`() {
        assertEquals(expected("1a", "Light"), actual(Peach.light))
        assertEquals(expected("1a", "Dark"), actual(Peach.dark))
    }

    @Test
    fun `sage matches the handoff`() {
        assertEquals(expected("1b", "Light"), actual(Sage.light))
        assertEquals(expected("1b", "Dark"), actual(Sage.dark))
    }

    @Test
    fun `plum matches the handoff`() {
        assertEquals(expected("1c", "Light"), actual(Plum.light))
        assertEquals(expected("1c", "Dark"), actual(Plum.dark))
    }

    @Test
    fun `direction and mode pick the right palette`() {
        assertEquals(Sage.light, bpColors(Direction.SAGE, dark = false))
        assertEquals(Sage.dark, bpColors(Direction.SAGE, dark = true))
        assertEquals(Peach.light, bpColors(Direction.PEACH, dark = false))
        assertEquals(Plum.dark, bpColors(Direction.PLUM, dark = true))
        assertNotEquals(bpColors(Direction.SAGE, dark = false), bpColors(Direction.SAGE, dark = true))
    }

    @Test
    fun `sage uses one green for accent and normal status`() {
        for (palette in listOf(Sage.light, Sage.dark)) {
            assertEquals(palette.ac, palette.ok)
            assertEquals(palette.acs, palette.okS)
        }
    }

    @Test
    fun `card radius per direction and key radius is 0_6 of it`() {
        assertEquals(28f, bpShapes(Direction.PEACH).card.value, 0f)
        assertEquals(14f, bpShapes(Direction.SAGE).card.value, 0f)
        assertEquals(22f, bpShapes(Direction.PLUM).card.value, 0f)
        for (d in Direction.entries) {
            val shapes = bpShapes(d)
            assertEquals(shapes.card.value * 0.6f, shapes.key.value, 0.0001f)
        }
    }

    @Test
    fun `material scheme takes accent and surfaces from the tokens`() {
        for (dark in listOf(false, true)) {
            val colors = bpColors(Direction.SAGE, dark)
            val scheme = colors.toMaterial(dark)
            assertEquals(colors.ac, scheme.primary)
            assertEquals(colors.aci, scheme.onPrimary)
            assertEquals(colors.bg, scheme.background)
            assertEquals(colors.sf, scheme.surface)
            assertEquals(colors.ink, scheme.onSurface)
            assertEquals(colors.hi, scheme.error)
        }
    }
}
