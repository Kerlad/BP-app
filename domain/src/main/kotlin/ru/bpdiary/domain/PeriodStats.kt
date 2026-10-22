package ru.bpdiary.domain

import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds
import java.time.Instant
import java.time.ZoneId

private const val NOON_HOUR = 12
private const val PERCENT = 100

/** Mean of the readings that fall into a part of the day. */
data class Avg(
    val sys: Int,
    val dia: Int,
    val count: Int,
)

data class PeriodStats(
    val count: Int,
    val avgSys: Int,
    val avgDia: Int,
    /** Null when none of the readings has a pulse. */
    val avgPulse: Int?,
    /** Local hour of the measurement < 12. */
    val morning: Avg?,
    /** Local hour of the measurement >= 12. */
    val evening: Avg?,
    val max: Reading,
    val min: Reading,
    val countHigh: Int,
    val countNormal: Int,
    val countLow: Int,
    val inRangePercent: Int,
)

/** Rounds half up, like JS `Math.round` (not half to even). */
private fun roundHalfUp(value: Double): Int = Math.round(value).toInt()

private fun Collection<Int>.mean(): Int = roundHalfUp(sum().toDouble() / size)

private fun List<Reading>.avg(): Avg? = if (isEmpty()) null else Avg(map { it.sys }.mean(), map { it.dia }.mean(), size)

private fun Reading.localHour(): Int = takenAt.atOffset(zoneOffset).hour

/**
 * Statistics for the last [period] local days, today included, up to [now]. Null when there are no readings.
 * Max and min are taken by SYS; on a tie the later reading wins (D-21, a deliberate change from the prototype).
 */
fun computeStats(
    readings: List<Reading>,
    th: Thresholds,
    period: Int,
    now: Instant,
    zone: ZoneId,
): PeriodStats? {
    val from =
        now
            .atZone(zone)
            .toLocalDate()
            .minusDays(period - 1L)
            .atStartOfDay(zone)
            .toInstant()
    val pts = readings.filter { !it.takenAt.isBefore(from) && !it.takenAt.isAfter(now) }.sortedBy { it.takenAt }
    if (pts.isEmpty()) return null

    val byStatus = pts.groupingBy { classify(it.sys, it.dia, th) }.eachCount()
    val countNormal = byStatus[Status.NORMAL] ?: 0
    return PeriodStats(
        count = pts.size,
        avgSys = pts.map { it.sys }.mean(),
        avgDia = pts.map { it.dia }.mean(),
        avgPulse = pts.mapNotNull { it.pulse }.takeIf { it.isNotEmpty() }?.mean(),
        morning = pts.filter { it.localHour() < NOON_HOUR }.avg(),
        evening = pts.filter { it.localHour() >= NOON_HOUR }.avg(),
        max = pts.reduce { a, p -> if (p.sys >= a.sys) p else a },
        min = pts.reduce { a, p -> if (p.sys <= a.sys) p else a },
        countHigh = byStatus[Status.HIGH] ?: 0,
        countNormal = countNormal,
        countLow = byStatus[Status.LOW] ?: 0,
        inRangePercent = roundHalfUp(countNormal.toDouble() / pts.size * PERCENT),
    )
}
