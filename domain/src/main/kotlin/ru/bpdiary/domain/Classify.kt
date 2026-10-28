package ru.bpdiary.domain

import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds

/** HIGH wins over LOW; thresholds are inclusive. */
fun classify(
    sys: Int,
    dia: Int,
    th: Thresholds,
): Status =
    when {
        sys >= th.highSys || dia >= th.highDia -> Status.HIGH
        sys <= th.lowSys || dia <= th.lowDia -> Status.LOW
        else -> Status.NORMAL
    }

/** For daily means: no rounding before comparison (D-19). */
fun classify(
    sys: Double,
    dia: Double,
    th: Thresholds,
): Status =
    when {
        sys >= th.highSys || dia >= th.highDia -> Status.HIGH
        sys <= th.lowSys || dia <= th.lowDia -> Status.LOW
        else -> Status.NORMAL
    }

fun pulseOutOfRange(
    pulse: Int?,
    th: Thresholds,
): Boolean = pulse != null && (pulse < th.pulseLow || pulse > th.pulseHigh)

private const val CRITICAL_SYS = 180
private const val CRITICAL_DIA = 120

/** D-06: high enough to show the neutral "seek help if you feel worse" hint once after saving. */
fun isCritical(
    sys: Int,
    dia: Int,
): Boolean = sys >= CRITICAL_SYS || dia >= CRITICAL_DIA
