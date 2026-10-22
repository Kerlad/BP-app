package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds

class ClassifyTest {
    private val th = Thresholds()

    @ParameterizedTest(name = "{0}/{1} -> {2}")
    @CsvSource(
        "120,90,HIGH",
        "136,70,HIGH",
        "135,70,HIGH",
        "120,85,HIGH",
        "135,85,HIGH",
        "134,84,NORMAL",
        "134,61,NORMAL",
        "101,61,NORMAL",
        "100,70,LOW",
        "101,60,LOW",
        "100,60,LOW",
        "145,55,HIGH",
        "99,99,HIGH",
    )
    fun `classify ints`(
        sys: Int,
        dia: Int,
        expected: Status,
    ) {
        assertEquals(expected, classify(sys, dia, th))
    }

    @ParameterizedTest(name = "{0}/{1} -> {2}")
    @CsvSource(
        "134.5,80.0,NORMAL",
        "135.0,80.0,HIGH",
        "120.0,84.9,NORMAL",
        "120.0,85.0,HIGH",
        "100.0,70.0,LOW",
        "100.1,60.1,NORMAL",
        "150.0,50.0,HIGH",
    )
    fun `classify doubles without rounding`(
        sys: Double,
        dia: Double,
        expected: Status,
    ) {
        assertEquals(expected, classify(sys, dia, th))
    }

    @Test
    fun `thresholds are respected`() {
        val custom = Thresholds(highSys = 140, highDia = 90)
        assertEquals(Status.NORMAL, classify(138, 88, custom))
        assertEquals(Status.HIGH, classify(140, 80, custom))
    }

    @Test
    fun `pulse out of range`() {
        assertFalse(pulseOutOfRange(null, th))
        assertTrue(pulseOutOfRange(49, th))
        assertFalse(pulseOutOfRange(50, th))
        assertFalse(pulseOutOfRange(90, th))
        assertTrue(pulseOutOfRange(91, th))
    }
}
