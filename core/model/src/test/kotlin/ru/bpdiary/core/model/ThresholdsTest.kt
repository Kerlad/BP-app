package ru.bpdiary.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThresholdsTest {
    @Test
    fun `defaults match the prototype`() {
        val th = Thresholds()
        assertEquals(135, th.highSys)
        assertEquals(85, th.highDia)
        assertEquals(100, th.lowSys)
        assertEquals(60, th.lowDia)
        assertEquals(50, th.pulseLow)
        assertEquals(90, th.pulseHigh)
    }

    @Test
    fun `tags keep the documented order`() {
        assertEquals(
            listOf("GOOD", "HEADACHE", "DIZZINESS", "WEAKNESS", "AFTER_MEDICINE", "AFTER_EXERCISE"),
            Tag.entries.map { it.name },
        )
    }
}
