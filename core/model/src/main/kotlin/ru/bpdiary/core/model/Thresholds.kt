package ru.bpdiary.core.model

data class Thresholds(
    val highSys: Int = 135,
    val highDia: Int = 85,
    val lowSys: Int = 100,
    val lowDia: Int = 60,
    val pulseLow: Int = 50,
    val pulseHigh: Int = 90,
)

/** Allowed values of the thresholds (FR-5). */
object ThresholdLimits {
    const val HIGH_SYS_MIN = 110
    const val HIGH_SYS_MAX = 200
    const val HIGH_DIA_MIN = 70
    const val HIGH_DIA_MAX = 130
    const val LOW_SYS_MIN = 70
    const val LOW_SYS_MAX = 120
    const val LOW_DIA_MIN = 40
    const val LOW_DIA_MAX = 90
    const val PULSE_MIN = 30
    const val PULSE_MAX = 200

    val HIGH_SYS = HIGH_SYS_MIN..HIGH_SYS_MAX
    val HIGH_DIA = HIGH_DIA_MIN..HIGH_DIA_MAX
    val LOW_SYS = LOW_SYS_MIN..LOW_SYS_MAX
    val LOW_DIA = LOW_DIA_MIN..LOW_DIA_MAX
    val PULSE = PULSE_MIN..PULSE_MAX
}

enum class ThresholdsProblem {
    HIGH_SYS_RANGE,
    HIGH_DIA_RANGE,
    LOW_SYS_RANGE,
    LOW_DIA_RANGE,
    PULSE_LOW_RANGE,
    PULSE_HIGH_RANGE,
    LOW_SYS_NOT_BELOW_HIGH,
    LOW_DIA_NOT_BELOW_HIGH,
    PULSE_NOT_ORDERED,
}

/** The first violated rule, or null when the thresholds are consistent. */
fun Thresholds.problem(): ThresholdsProblem? =
    when {
        highSys !in ThresholdLimits.HIGH_SYS -> ThresholdsProblem.HIGH_SYS_RANGE
        highDia !in ThresholdLimits.HIGH_DIA -> ThresholdsProblem.HIGH_DIA_RANGE
        lowSys !in ThresholdLimits.LOW_SYS -> ThresholdsProblem.LOW_SYS_RANGE
        lowDia !in ThresholdLimits.LOW_DIA -> ThresholdsProblem.LOW_DIA_RANGE
        pulseLow !in ThresholdLimits.PULSE -> ThresholdsProblem.PULSE_LOW_RANGE
        pulseHigh !in ThresholdLimits.PULSE -> ThresholdsProblem.PULSE_HIGH_RANGE
        lowSys >= highSys -> ThresholdsProblem.LOW_SYS_NOT_BELOW_HIGH
        lowDia >= highDia -> ThresholdsProblem.LOW_DIA_NOT_BELOW_HIGH
        pulseLow >= pulseHigh -> ThresholdsProblem.PULSE_NOT_ORDERED
        else -> null
    }
