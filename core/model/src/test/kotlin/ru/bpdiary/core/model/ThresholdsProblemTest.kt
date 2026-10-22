package ru.bpdiary.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ThresholdsProblemTest {
    @Test
    fun `defaults and both presets are consistent`() {
        assertNull(Thresholds().problem())
        assertNull(Thresholds(highSys = 140, highDia = 90).problem())
    }

    @Test
    fun `range bounds are inclusive`() {
        assertNull(Thresholds(highSys = 110, lowSys = 70).problem())
        assertNull(Thresholds(highSys = 200, highDia = 130, lowSys = 120, lowDia = 90).problem())
        assertNull(Thresholds(pulseLow = 30, pulseHigh = 200).problem())
    }

    @Test
    fun `values out of range are reported`() {
        assertEquals(ThresholdsProblem.HIGH_SYS_RANGE, Thresholds(highSys = 109).problem())
        assertEquals(ThresholdsProblem.HIGH_SYS_RANGE, Thresholds(highSys = 201).problem())
        assertEquals(ThresholdsProblem.HIGH_DIA_RANGE, Thresholds(highDia = 69).problem())
        assertEquals(ThresholdsProblem.LOW_SYS_RANGE, Thresholds(lowSys = 69).problem())
        assertEquals(ThresholdsProblem.LOW_DIA_RANGE, Thresholds(lowDia = 91).problem())
        assertEquals(ThresholdsProblem.PULSE_LOW_RANGE, Thresholds(pulseLow = 29).problem())
        assertEquals(ThresholdsProblem.PULSE_HIGH_RANGE, Thresholds(pulseHigh = 201).problem())
    }

    @Test
    fun `low must stay below high`() {
        assertEquals(ThresholdsProblem.LOW_SYS_NOT_BELOW_HIGH, Thresholds(highSys = 110, lowSys = 110).problem())
        assertEquals(ThresholdsProblem.LOW_DIA_NOT_BELOW_HIGH, Thresholds(highDia = 70, lowDia = 70).problem())
        assertEquals(ThresholdsProblem.PULSE_NOT_ORDERED, Thresholds(pulseLow = 90, pulseHigh = 90).problem())
        assertEquals(ThresholdsProblem.PULSE_NOT_ORDERED, Thresholds(pulseLow = 100, pulseHigh = 80).problem())
    }
}
