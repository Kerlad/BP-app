package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** D-06: the hint after saving is shown for SYS >= 180 or DIA >= 120, never for low values. */
class CriticalValueTest {
    @ParameterizedTest(name = "{0}/{1} -> {2}")
    @CsvSource(
        "180,90,true",
        "179,119,false",
        "150,120,true",
        "200,130,true",
        "179,90,false",
        "60,40,false",
    )
    fun `critical values`(
        sys: Int,
        dia: Int,
        expected: Boolean,
    ) {
        assertEquals(expected, isCritical(sys, dia))
    }
}
