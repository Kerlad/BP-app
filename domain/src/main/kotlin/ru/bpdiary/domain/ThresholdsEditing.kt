package ru.bpdiary.domain

import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.model.problem

/** Stepper step on the Levels screen (FR-5). */
const val THRESHOLD_STEP = 5

enum class ThresholdField { HIGH_SYS, HIGH_DIA, LOW_SYS, LOW_DIA, PULSE_LOW, PULSE_HIGH }

fun Thresholds.valueOf(field: ThresholdField): Int =
    when (field) {
        ThresholdField.HIGH_SYS -> highSys
        ThresholdField.HIGH_DIA -> highDia
        ThresholdField.LOW_SYS -> lowSys
        ThresholdField.LOW_DIA -> lowDia
        ThresholdField.PULSE_LOW -> pulseLow
        ThresholdField.PULSE_HIGH -> pulseHigh
    }

/**
 * [field] moved by [delta], or null when the result leaves its range or breaks `low < high`:
 * such a stepper button is disabled instead of showing an error (docs/05 §5.6).
 */
fun Thresholds.stepped(
    field: ThresholdField,
    delta: Int,
): Thresholds? {
    val value = valueOf(field) + delta
    val next =
        when (field) {
            ThresholdField.HIGH_SYS -> copy(highSys = value)
            ThresholdField.HIGH_DIA -> copy(highDia = value)
            ThresholdField.LOW_SYS -> copy(lowSys = value)
            ThresholdField.LOW_DIA -> copy(lowDia = value)
            ThresholdField.PULSE_LOW -> copy(pulseLow = value)
            ThresholdField.PULSE_HIGH -> copy(pulseHigh = value)
        }
    return next.takeIf { it.problem() == null }
}

private const val HOME_SYS = 135
private const val HOME_DIA = 85
private const val CLINIC_SYS = 140
private const val CLINIC_DIA = 90

/** «135/85 — Домашние измерения», «140/90 — Измерения у врача»: they set only the high SYS and DIA. */
enum class ThresholdPreset(
    val highSys: Int,
    val highDia: Int,
) {
    HOME(HOME_SYS, HOME_DIA),
    CLINIC(CLINIC_SYS, CLINIC_DIA),
}

fun Thresholds.matches(preset: ThresholdPreset): Boolean = highSys == preset.highSys && highDia == preset.highDia

/** Null when the preset would break `low < high` with the current low levels. */
fun Thresholds.withPreset(preset: ThresholdPreset): Thresholds? =
    copy(highSys = preset.highSys, highDia = preset.highDia).takeIf { it.problem() == null }
