package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.bpdiary.core.model.Thresholds

class ThresholdsEditingTest {
    private val default = Thresholds()

    @Test
    fun `step by 5 inside the range`() {
        assertEquals(140, default.stepped(ThresholdField.HIGH_SYS, THRESHOLD_STEP)?.highSys)
        assertEquals(55, default.stepped(ThresholdField.LOW_DIA, -THRESHOLD_STEP)?.lowDia)
        assertEquals(95, default.stepped(ThresholdField.PULSE_HIGH, THRESHOLD_STEP)?.pulseHigh)
    }

    @Test
    fun `range limits stop the stepper`() {
        assertNull(default.copy(highSys = 200).stepped(ThresholdField.HIGH_SYS, THRESHOLD_STEP))
        assertNull(default.copy(highDia = 70, lowDia = 60).stepped(ThresholdField.HIGH_DIA, -THRESHOLD_STEP))
        assertNull(default.copy(lowSys = 70).stepped(ThresholdField.LOW_SYS, -THRESHOLD_STEP))
        assertNull(default.copy(pulseLow = 30).stepped(ThresholdField.PULSE_LOW, -THRESHOLD_STEP))
        assertNull(default.copy(pulseHigh = 200).stepped(ThresholdField.PULSE_HIGH, THRESHOLD_STEP))
    }

    @Test
    fun `low must stay below high`() {
        val close = Thresholds(highSys = 120, highDia = 85, lowSys = 115, lowDia = 80, pulseLow = 85, pulseHigh = 90)
        assertNull(close.stepped(ThresholdField.LOW_SYS, THRESHOLD_STEP))
        assertNull(close.stepped(ThresholdField.HIGH_SYS, -THRESHOLD_STEP))
        assertNull(close.stepped(ThresholdField.LOW_DIA, THRESHOLD_STEP))
        assertNull(close.stepped(ThresholdField.HIGH_DIA, -THRESHOLD_STEP))
        assertNull(close.stepped(ThresholdField.PULSE_LOW, THRESHOLD_STEP))
        assertNull(close.stepped(ThresholdField.PULSE_HIGH, -THRESHOLD_STEP))
    }

    @Test
    fun `presets change only the high levels and are active when both match`() {
        val clinic = default.withPreset(ThresholdPreset.CLINIC)!!
        assertEquals(default.copy(highSys = 140, highDia = 90), clinic)
        assertTrue(clinic.matches(ThresholdPreset.CLINIC))
        assertFalse(clinic.matches(ThresholdPreset.HOME))
        assertFalse(default.copy(highSys = 140).matches(ThresholdPreset.CLINIC))
        assertTrue(default.matches(ThresholdPreset.HOME))
    }

    @Test
    fun `a preset that would break low below high is not applied`() {
        val highLow = Thresholds(highSys = 150, highDia = 95, lowDia = 85)
        assertNull(highLow.withPreset(ThresholdPreset.HOME))
        assertEquals(90, highLow.withPreset(ThresholdPreset.CLINIC)?.highDia)
    }
}
