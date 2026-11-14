package ru.bpdiary.domain

import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Y axis: 50..180 mmHg for both charts; values outside are clipped to the border.
private const val AXIS_MIN = 50
private const val AXIS_MAX = 180
private const val AXIS_SPAN = 130f

// Line chart (viewBox 320x200): baseline 190, working height 180, x from 34 to 314, single point at 174.
private const val LINE_BASE = 190f
private const val LINE_HEIGHT = 180f
private const val LINE_X_START = 34f
private const val LINE_X_SPAN = 280f
private const val LINE_X_CENTER = 174f

// Week bars (viewBox 300x120): baseline 112, working height 104, seven columns.
private const val BAR_BASE = 112f
private const val BAR_HEIGHT = 104f
private const val BAR_WIDTH = 300f
private const val BAR_DAYS = 7
private const val HALF = 0.5f

fun yLine(v: Int): Float = LINE_BASE - (v.coerceIn(AXIS_MIN, AXIS_MAX) - AXIS_MIN) / AXIS_SPAN * LINE_HEIGHT

fun xLine(
    i: Int,
    n: Int,
): Float = if (n > 1) LINE_X_START + i * LINE_X_SPAN / (n - 1) else LINE_X_CENTER

fun yBar(v: Int): Float = BAR_BASE - (v.coerceIn(AXIS_MIN, AXIS_MAX) - AXIS_MIN) / AXIS_SPAN * BAR_HEIGHT

/** [daysAgo] is 0 for today and 6 for six days ago. */
fun xBar(daysAgo: Int): Float = ((BAR_DAYS - 1 - daysAgo) + HALF) * BAR_WIDTH / BAR_DAYS

/** One day column. [status] comes from the unrounded daily means (D-19). */
data class DayBar(
    val daysAgo: Int,
    val date: LocalDate,
    val x: Float,
    val yTop: Float,
    val yBottom: Float,
    val status: Status,
)

/** [days] has exactly seven entries, the oldest first and today last; a day without readings has no bar. */
data class WeekBars(
    val days: List<DayBarSlot>,
    /** Mean over all readings of the seven days, rounded half up; null when there are none. */
    val avgSys: Int?,
    val avgDia: Int?,
    val highSysY: Float,
)

data class DayBarSlot(
    val daysAgo: Int,
    val date: LocalDate,
    val bar: DayBar?,
)

fun weekBars(
    readings: List<Reading>,
    th: Thresholds,
    today: LocalDate,
    now: Instant,
): WeekBars {
    // Readings ahead of the clock (the import allows up to 24 h) are not drawn, as on the Charts screen.
    val byDay = readings.filter { !it.takenAt.isAfter(now) }.groupBy { localDay(it) }
    val slots =
        (BAR_DAYS - 1 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dayReadings = byDay[date].orEmpty()
            val bar =
                if (dayReadings.isEmpty()) {
                    null
                } else {
                    val avgSys = dayReadings.map { it.sys }.average()
                    val avgDia = dayReadings.map { it.dia }.average()
                    DayBar(
                        daysAgo = daysAgo,
                        date = date,
                        x = xBar(daysAgo),
                        yTop = yBar(dayReadings.maxOf { it.sys }),
                        yBottom = yBar(dayReadings.minOf { it.dia }),
                        status = classify(avgSys, avgDia, th),
                    )
                }
            DayBarSlot(daysAgo, date, bar)
        }
    val all = slots.flatMap { byDay[it.date].orEmpty() }
    return WeekBars(
        days = slots,
        avgSys = all.takeIf { it.isNotEmpty() }?.let { roundHalfUp(it.map { r -> r.sys }.average()) },
        avgDia = all.takeIf { it.isNotEmpty() }?.let { roundHalfUp(it.map { r -> r.dia }.average()) },
        highSysY = yBar(th.highSys),
    )
}

/** [highStatus] is true when the whole reading is HIGH, also because of DIA (D-20). */
data class LinePoint(
    val index: Int,
    val reading: Reading,
    val x: Float,
    val ySys: Float,
    val yDia: Float,
    val highStatus: Boolean,
)

data class LineChartModel(
    val points: List<LinePoint>,
    /** True when any SYS or DIA is outside 50..180 and was drawn at the border. */
    val clipped: Boolean,
    val highSysY: Float,
    val highDiaY: Float,
)

fun lineChart(
    readings: List<Reading>,
    th: Thresholds,
    period: Int,
    now: Instant,
    zone: ZoneId,
): LineChartModel {
    val pts = readingsInPeriod(readings, period, now, zone)
    val outside = AXIS_MIN..AXIS_MAX
    return LineChartModel(
        points =
            pts.mapIndexed { i, r ->
                LinePoint(
                    i,
                    r,
                    xLine(i, pts.size),
                    yLine(r.sys),
                    yLine(r.dia),
                    classify(r.sys, r.dia, th) == Status.HIGH,
                )
            },
        clipped = pts.any { it.sys !in outside || it.dia !in outside },
        highSysY = yLine(th.highSys),
        highDiaY = yLine(th.highDia),
    )
}
