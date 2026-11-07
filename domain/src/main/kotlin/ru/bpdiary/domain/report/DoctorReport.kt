package ru.bpdiary.domain.report

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.LineChartModel
import ru.bpdiary.domain.PeriodStats
import ru.bpdiary.domain.classify
import ru.bpdiary.domain.computeStats
import ru.bpdiary.domain.lineChart
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Table rows that fit under the header, the chart and the statistics of the first page (docs/08 §8.2). */
const val FIRST_PAGE_ROWS = 25

/** Table rows of every later page: the limit of docs/08 §8.2 and of the task. */
const val NEXT_PAGE_ROWS = 50

enum class StatLabel { AVERAGE, MORNING, EVENING, PULSE, MAX, MIN, IN_RANGE }

/** What a statistics line says; the words around it come from resources in the renderer. */
sealed interface StatValue {
    data class Pressure(
        val sys: Int,
        val dia: Int,
    ) : StatValue

    data class Beats(
        val pulse: Int,
    ) : StatValue

    /** Share of the readings that are NORMAL, with the counts behind it. */
    data class Share(
        val percent: Int,
        val high: Int,
        val normal: Int,
        val low: Int,
    ) : StatValue
}

/** [count] is the number of readings behind a mean; [day] is the day of a maximum or a minimum. */
data class StatRow(
    val label: StatLabel,
    val value: StatValue,
    val count: Int? = null,
    val day: LocalDate? = null,
)

/** One line of the table, at the offset the reading was taken in. */
data class ReportRow(
    val day: LocalDate,
    val time: LocalTime,
    val sys: Int,
    val dia: Int,
    val pulse: Int?,
    val arm: Arm,
    val status: Status,
    val tags: List<Tag>,
    val comment: String,
)

/** Page 1 carries the header, the chart and the statistics above its rows; later pages only the table. */
data class ReportPage(
    val number: Int,
    val total: Int,
    val rows: List<ReportRow>,
) {
    val isFirst: Boolean get() = number == 1
}

/** Everything the doctor's report shows, without a single word: the renderer adds the texts (docs/08 §8.2). */
data class DoctorReport(
    val from: LocalDate,
    val to: LocalDate,
    val generatedOn: LocalDate,
    val thresholds: Thresholds,
    val count: Int,
    /** Null when there are no readings. */
    val chart: LineChartModel?,
    val stats: List<StatRow>,
    val pages: List<ReportPage>,
) {
    val pageCount: Int get() = pages.size
}

/** Statistics lines in the order of the Charts screen; morning, evening and pulse appear only when they exist. */
internal fun statRows(stats: PeriodStats): List<StatRow> =
    buildList {
        add(StatRow(StatLabel.AVERAGE, StatValue.Pressure(stats.avgSys, stats.avgDia), count = stats.count))
        stats.morning?.let { add(StatRow(StatLabel.MORNING, StatValue.Pressure(it.sys, it.dia), count = it.count)) }
        stats.evening?.let { add(StatRow(StatLabel.EVENING, StatValue.Pressure(it.sys, it.dia), count = it.count)) }
        stats.avgPulse?.let { add(StatRow(StatLabel.PULSE, StatValue.Beats(it))) }
        add(StatRow(StatLabel.MAX, StatValue.Pressure(stats.max.sys, stats.max.dia), day = stats.max.localDay()))
        add(StatRow(StatLabel.MIN, StatValue.Pressure(stats.min.sys, stats.min.dia), day = stats.min.localDay()))
        add(
            StatRow(
                StatLabel.IN_RANGE,
                StatValue.Share(stats.inRangePercent, stats.countHigh, stats.countNormal, stats.countLow),
            ),
        )
    }

private fun Reading.localDay(): LocalDate = takenAt.atOffset(zoneOffset).toLocalDate()

private fun Reading.toRow(th: Thresholds): ReportRow {
    val moment = takenAt.atOffset(zoneOffset)
    return ReportRow(
        day = moment.toLocalDate(),
        time = moment.toLocalTime().withSecond(0).withNano(0),
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = arm,
        status = classify(sys, dia, th),
        tags = Tag.entries.filter { it in tags },
        comment = comment,
    )
}

/** First page [FIRST_PAGE_ROWS], then [NEXT_PAGE_ROWS] each; an empty table is still one page. */
internal fun paginate(rows: List<ReportRow>): List<ReportPage> {
    val chunks = listOf(rows.take(FIRST_PAGE_ROWS)) + rows.drop(FIRST_PAGE_ROWS).chunked(NEXT_PAGE_ROWS)
    return chunks.mapIndexed { i, chunk -> ReportPage(i + 1, chunks.size, chunk) }
}

/**
 * The report of [readings] taken in [period]; the caller has already picked the period.
 * Chart and statistics are the ones of the Charts screen: same functions, only the period is the chosen one.
 */
fun buildDoctorReport(
    readings: List<Reading>,
    th: Thresholds,
    period: ClosedRange<LocalDate>,
    now: Instant,
    zone: ZoneId,
): DoctorReport {
    val list = readings.filter { !it.takenAt.isAfter(now) }.sortedBy { it.takenAt }
    val today = now.atZone(zone).toLocalDate()
    val earliest =
        list
            .firstOrNull()
            ?.takenAt
            ?.atZone(zone)
            ?.toLocalDate()
    val start = if (earliest != null && earliest.isBefore(period.start)) earliest else period.start
    // The period of the Charts functions counts back from today; wide enough that none of the list is cut off.
    val days = (ChronoUnit.DAYS.between(start, today) + 1).coerceAtLeast(1).toInt()
    val stats = computeStats(list, th, days, now, zone)
    return DoctorReport(
        from = period.start,
        to = period.endInclusive,
        generatedOn = today,
        thresholds = th,
        count = list.size,
        chart = if (list.isEmpty()) null else lineChart(list, th, days, now, zone),
        stats = stats?.let(::statRows).orEmpty(),
        pages = paginate(list.map { it.toRow(th) }),
    )
}
