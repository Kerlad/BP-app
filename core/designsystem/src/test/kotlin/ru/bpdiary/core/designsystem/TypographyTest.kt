package ru.bpdiary.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.bpdiary.core.model.Direction
import java.io.File

class TypographyTest {
    private val sage = bpTypography(Direction.SAGE)

    private fun BpTypography.all(): Map<String, TextStyle> =
        mapOf(
            "screenTitle" to screenTitle,
            "greeting" to greeting,
            "panelTitle" to panelTitle,
            "cardTitle" to cardTitle,
            "button" to button,
            "body" to body,
            "secondary" to secondary,
            "caption" to caption,
            "micro" to micro,
            "unit" to unit,
            "key" to key,
            "reminderTitle" to reminderTitle,
            "chip" to chip,
            "navActive" to navActive,
            "navInactive" to navInactive,
            "slash" to slash,
            "numHero" to numHero,
            "numEntry" to numEntry,
            "numReminder" to numReminder,
            "numStat" to numStat,
            "numRow" to numRow,
        )

    private fun assertStyle(
        name: String,
        style: TextStyle,
        size: Int,
        weight: Int,
    ) {
        assertEquals("$name size", size.toFloat(), style.fontSize.value, 0f)
        assertEquals("$name weight", weight, style.fontWeight?.weight)
    }

    @Test
    fun `sage styles match docs 4_3`() {
        assertStyle("screenTitle", sage.screenTitle, 28, 700)
        assertStyle("greeting", sage.greeting, 24, 700)
        assertStyle("panelTitle", sage.panelTitle, 20, 700)
        assertStyle("cardTitle", sage.cardTitle, 16, 700)
        assertStyle("button", sage.button, 18, 700)
        assertStyle("body", sage.body, 15, 400)
        assertStyle("secondary", sage.secondary, 14, 400)
        assertStyle("caption", sage.caption, 13, 400)
        assertStyle("micro", sage.micro, 12, 400)
        assertStyle("unit", sage.unit, 14, 500)
        assertStyle("key", sage.key, 24, 600)
        assertStyle("reminderTitle", sage.reminderTitle, 15, 600)
        assertStyle("chip", sage.chip, 13, 700)
        assertStyle("navActive", sage.navActive, 12, 700)
        assertStyle("navInactive", sage.navInactive, 12, 500)
        assertStyle("slash", sage.slash, 40, 400)
        assertStyle("numHero", sage.numHero, 62, 700)
        assertStyle("numEntry", sage.numEntry, 36, 700)
        assertStyle("numReminder", sage.numReminder, 32, 700)
        assertStyle("numStat", sage.numStat, 26, 700)
        assertStyle("numRow", sage.numRow, 22, 700)
    }

    @Test
    fun `every text size is in sp so the system font scale applies`() {
        for (direction in Direction.entries) {
            for ((name, style) in bpTypography(direction).all()) {
                assertEquals("$direction $name", TextUnitType.Sp, style.fontSize.type)
            }
        }
    }

    @Test
    fun `hero number is tightened by 0_03 em`() {
        assertEquals(TextUnitType.Em, sage.numHero.letterSpacing.type)
        assertEquals(-0.03f, sage.numHero.letterSpacing.value, 0.0001f)
    }

    @Test
    fun `digits use the number weight of the direction`() {
        assertEquals(FontWeight.W800, numberWeight(Direction.PEACH))
        assertEquals(FontWeight.W700, numberWeight(Direction.SAGE))
        assertEquals(FontWeight.W700, numberWeight(Direction.PLUM))
        val peach = bpTypography(Direction.PEACH)
        listOf(peach.numHero, peach.numEntry, peach.numReminder, peach.numStat, peach.numRow)
            .forEach { assertEquals(FontWeight.W800, it.fontWeight) }
    }

    @Test
    fun `peach and plum fall back to Golos Text`() {
        assertEquals(GolosText, fontFamilyFor(Direction.PEACH))
        assertEquals(GolosText, fontFamilyFor(Direction.PLUM))
        assertEquals(GolosText, fontFamilyFor(Direction.SAGE))
    }

    @Test
    fun `material typography maps the closest styles`() {
        val material = sage.toMaterial()
        assertEquals(sage.body, material.bodyLarge)
        assertEquals(sage.screenTitle, material.titleLarge)
    }

    @Test
    fun `icon enum and drawable files match one to one`() {
        val files =
            File("src/main/res/drawable")
                .listFiles { f -> f.name.startsWith("ic_") && f.extension == "xml" }
                .orEmpty()
                .map { it.nameWithoutExtension }
                .toSet()
        val fromEnum =
            BpIcon.entries
                .map {
                    "ic_" +
                        it.name
                            .replace(
                                Regex("([a-z])([A-Z])"),
                                "$1_$2",
                            ).lowercase()
                }.toSet()
        assertEquals(23, BpIcon.entries.size)
        assertEquals(files, fromEnum)
        assertTrue(
            BpIcon.entries
                .map { it.res }
                .toSet()
                .size == BpIcon.entries.size,
        )
    }

    @Test
    fun `icon vectors carry no tint so colours come from the caller`() {
        File("src/main/res/drawable").listFiles { f -> f.name.startsWith("ic_") }.orEmpty().forEach {
            assertTrue("${it.name} must not hardcode a tint", !it.readText().contains("android:tint"))
        }
    }
}
