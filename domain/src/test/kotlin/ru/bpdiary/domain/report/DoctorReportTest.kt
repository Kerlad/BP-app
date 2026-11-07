package ru.bpdiary.domain.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.computeStats
import ru.bpdiary.domain.lineChart
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class DoctorReportTest {
    private val time = FixedTimeProvider.default()
    private val th = Thresholds()
    private val moscow = ZoneOffset.ofHours(3)
    private val today = time.now().atZone(time.zone()).toLocalDate()

    private fun reading(
        minutesAgo: Long,
        sys: Int = 120,
        dia: Int = 80,
        pulse: Int? = 70,
    ) = Reading(
        takenAt = time.now().minusSeconds(minutesAgo * 60),
        zoneOffset = moscow,
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = Arm.LEFT,
    )

    private fun report(readings: List<Reading>) =
        buildDoctorReport(readings, th, today.minusDays(29)..today, time.now(), time.zone())

    private fun many(count: Int) = (1..count).map { reading(minutesAgo = it.toLong()) }

    private fun pages(count: Int) = report(many(count)).pages.map { it.rows.size }

    // --- pages ----------------------------------------------------------------------------------

    @Test
    fun `no readings is one page with an empty table, no chart and no statistics`() {
        val r = report(emptyList())
        assertEquals(1, r.pageCount)
        assertEquals(emptyList<ReportRow>(), r.pages.single().rows)
        assertNull(r.chart)
        assertEquals(emptyList<StatRow>(), r.stats)
        assertEquals(0, r.count)
    }

    @Test
    fun `the first page holds 25 rows and each later page 50`() {
        assertEquals(listOf(1), pages(1))
        assertEquals(listOf(25), pages(25))
        assertEquals(listOf(25, 1), pages(26))
        assertEquals(listOf(25, 25), pages(50))
        assertEquals(listOf(25, 26), pages(51))
        assertEquals(listOf(25, 50), pages(75))
        assertEquals(listOf(25, 50, 1), pages(76))
        assertEquals(listOf(25, 50, 50, 50, 25), pages(200))
    }

    @Test
    fun `no page has more than 50 rows and numbers run 1 to total`() {
        val r = report(many(1000))
        assertTrue(r.pages.all { it.rows.size <= NEXT_PAGE_ROWS })
        assertEquals((1..r.pageCount).toList(), r.pages.map { it.number })
        assertTrue(r.pages.all { it.total == r.pageCount })
        assertEquals(1000, r.pages.sumOf { it.rows.size })
        assertTrue(r.pages.first().isFirst)
    }

    // --- rows -----------------------------------------------------------------------------------

    @Test
    fun `rows run from the oldest to the newest at the offset of the reading`() {
        val newest = reading(minutesAgo = 1)
        val oldest = reading(minutesAgo = 60 * 24 * 3)
        val rows = report(listOf(newest, oldest)).pages.single().rows
        val first = oldest.takenAt.atOffset(moscow)
        val last = newest.takenAt.atOffset(moscow)
        assertEquals(listOf(first.toLocalDate(), last.toLocalDate()), rows.map { it.day })
        assertEquals(LocalTime.of(first.hour, first.minute), rows.first().time)
        assertEquals(LocalTime.of(last.hour, last.minute), rows.last().time)
    }

    @Test
    fun `a row carries the status of the thresholds, tags in the order of the Entry screen and the comment`() {
        val r =
            reading(minutesAgo = 5, sys = 150, dia = 95, pulse = null)
                .copy(tags = setOf(Tag.AFTER_EXERCISE, Tag.HEADACHE), comment = "после прогулки")
        val row =
            report(listOf(r))
                .pages
                .single()
                .rows
                .single()
        assertEquals(Status.HIGH, row.status)
        assertEquals(listOf(Tag.HEADACHE, Tag.AFTER_EXERCISE), row.tags)
        assertEquals("после прогулки", row.comment)
        assertNull(row.pulse)
        assertEquals(Arm.LEFT, row.arm)
    }

    @Test
    fun `a reading from the future is not in the report`() {
        val future = reading(minutesAgo = -10)
        assertEquals(1, report(listOf(reading(5), future)).count)
    }

    // --- statistics and chart --------------------------------------------------------------------

    @Test
    fun `the statistics are those of the Charts screen`() {
        val list = listOf(reading(60 * 30, 130, 85), reading(60 * 5, 110, 70, pulse = 60), reading(1, 150, 95))
        val expected = computeStats(list, th, 30, time.now(), time.zone())!!
        val rows = report(list).stats.associateBy { it.label }

        assertEquals(StatValue.Pressure(expected.avgSys, expected.avgDia), rows.getValue(StatLabel.AVERAGE).value)
        assertEquals(expected.count, rows.getValue(StatLabel.AVERAGE).count)
        assertEquals(StatValue.Beats(expected.avgPulse!!), rows.getValue(StatLabel.PULSE).value)
        assertEquals(StatValue.Pressure(150, 95), rows.getValue(StatLabel.MAX).value)
        assertEquals(StatValue.Pressure(110, 70), rows.getValue(StatLabel.MIN).value)
        assertEquals(
            StatValue.Share(expected.inRangePercent, expected.countHigh, expected.countNormal, expected.countLow),
            rows.getValue(StatLabel.IN_RANGE).value,
        )
    }

    @Test
    fun `a part of the day without readings has no line, and no pulse means no pulse line`() {
        // Every reading of the table is at one time of day, so one of the two lines is missing.
        val labels = report(many(3).map { it.copy(pulse = null) }).stats.map { it.label }
        assertTrue(StatLabel.PULSE !in labels)
        assertTrue((StatLabel.MORNING in labels) != (StatLabel.EVENING in labels))
    }

    @Test
    fun `the chart has a point for every reading`() {
        val list = many(40)
        val chart = report(list).chart
        assertNotNull(chart)
        assertEquals(40, chart!!.points.size)
        assertEquals(lineChart(list, th, 30, time.now(), time.zone()).points.size, chart.points.size)
    }

    @Test
    fun `a period that starts before the thirty days still includes every reading`() {
        val old = reading(minutesAgo = 60L * 24 * 400)
        val period = LocalDate.of(2025, 9, 15)..today
        val r = buildDoctorReport(listOf(old, reading(1)), th, period, time.now(), time.zone())
        assertEquals(2, r.count)
        assertEquals(2, r.chart!!.points.size)
        assertEquals(2, r.stats.first { it.label == StatLabel.AVERAGE }.count)
    }

    @Test
    fun `the header facts are kept`() {
        val r = report(many(3))
        assertEquals(today.minusDays(29), r.from)
        assertEquals(today, r.to)
        assertEquals(today, r.generatedOn)
        assertEquals(th, r.thresholds)
    }
}
