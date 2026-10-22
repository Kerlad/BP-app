package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.bpdiary.core.common.DemoData
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ChartGeometryTest {
    private val time = FixedTimeProvider.default()
    private val th = Thresholds()
    private val demo = DemoData.readings(time)
    private val today = LocalDate.of(2026, 10, 20)
    private val delta = 0.002f

    private fun reading(
        at: String,
        sys: Int = 120,
        dia: Int = 80,
    ) = Reading(
        takenAt = Instant.parse(at),
        zoneOffset = ZoneOffset.ofHours(3),
        sys = sys,
        dia = dia,
        pulse = 70,
        arm = Arm.LEFT,
    )

    private fun golden(prefix: String): List<List<String>> =
        javaClass
            .getResource("/geometry_golden.txt")!!
            .readText()
            .lines()
            .filter { it.startsWith("$prefix,") }
            .map { it.split(",") }

    // --- formulas -------------------------------------------------------------------------------

    @Test
    fun `yLine axis ends and clipping`() {
        assertEquals(190f, yLine(50), delta)
        assertEquals(10f, yLine(180), delta)
        assertEquals(190f - 85f / 130f * 180f, yLine(135), delta)
        assertEquals(yLine(180), yLine(200))
        assertEquals(yLine(50), yLine(30))
    }

    @Test
    fun `xLine single point is centered and points are evenly spaced`() {
        assertEquals(174f, xLine(0, 1))
        assertEquals(34f, xLine(0, 14))
        assertEquals(314f, xLine(13, 14), delta)
        val step = 280f / 13
        for (i in 1 until 14) assertEquals(step, xLine(i, 14) - xLine(i - 1, 14), delta)
    }

    @Test
    fun `yBar axis ends and clipping`() {
        assertEquals(112f, yBar(50), delta)
        assertEquals(8f, yBar(180), delta)
        assertEquals(yBar(180), yBar(250))
        assertEquals(yBar(50), yBar(40))
    }

    @Test
    fun `xBar columns`() {
        assertEquals(6.5f * 300f / 7f, xBar(0), delta)
        assertEquals(0.5f * 300f / 7f, xBar(6), delta)
        for (d in 1..6) assertEquals(300f / 7f, xBar(d - 1) - xBar(d), delta)
    }

    // --- week bars on demo data ---------------------------------------------------------------

    @Test
    fun `week bars match the prototype`() {
        val week = weekBars(demo, th, today)
        assertEquals(7, week.days.size)
        assertEquals((6 downTo 0).toList(), week.days.map { it.daysAgo })
        assertEquals(today, week.days.last().date)

        val bars = golden("bar")
        assertEquals(bars.size, week.days.count { it.bar != null })
        for (row in bars) {
            val bar = week.days.first { it.daysAgo == row[1].toInt() }.bar!!
            assertEquals(row[2].toFloat(), bar.x, delta, "x of ${row[1]}")
            assertEquals(row[3].toFloat(), bar.yTop, delta, "yTop of ${row[1]}")
            assertEquals(row[4].toFloat(), bar.yBottom, delta, "yBottom of ${row[1]}")
            val expected = mapOf("hi" to Status.HIGH, "ok" to Status.NORMAL, "lo" to Status.LOW).getValue(row[5])
            assertEquals(expected, bar.status, "status of ${row[1]}")
        }
        val summary = golden("week").single()
        assertEquals(summary[1].toInt(), week.avgSys)
        assertEquals(summary[2].toInt(), week.avgDia)
        assertEquals(summary[3].toFloat(), week.highSysY, delta)
    }

    @Test
    fun `missing days have no bar and an empty week has no averages`() {
        val week = weekBars(listOf(reading("2026-10-18T04:00:00Z")), th, today)
        assertEquals(1, week.days.count { it.bar != null })
        assertNull(week.days.first { it.daysAgo == 0 }.bar)
        val empty = weekBars(emptyList(), th, today)
        assertTrue(empty.days.all { it.bar == null })
        assertNull(empty.avgSys)
        assertNull(empty.avgDia)
    }

    @Test
    fun `bar status uses unrounded daily means`() {
        // Means 134.5/80: NORMAL. Rounded to 135 it would be HIGH.
        val day =
            listOf(
                reading("2026-10-20T04:00:00Z", sys = 134, dia = 80),
                reading("2026-10-20T05:00:00Z", sys = 135, dia = 80),
            )
        assertEquals(
            Status.NORMAL,
            weekBars(day, th, today)
                .days
                .last()
                .bar!!
                .status,
        )
    }

    @Test
    fun `bar spans max sys to min dia`() {
        val day =
            listOf(
                reading("2026-10-20T04:00:00Z", sys = 150, dia = 95),
                reading("2026-10-20T05:00:00Z", sys = 110, dia = 70),
            )
        val bar = weekBars(day, th, today).days.last().bar!!
        assertEquals(yBar(150), bar.yTop)
        assertEquals(yBar(70), bar.yBottom)
    }

    @Test
    fun `readings older than seven days are not drawn`() {
        val week = weekBars(listOf(reading("2026-10-13T04:00:00Z")), th, today)
        assertTrue(week.days.all { it.bar == null })
    }

    // --- line chart on demo data --------------------------------------------------------------

    @Test
    fun `line chart matches the prototype for 7 and 30 days`() {
        for (period in listOf(7, 30)) {
            val chart = lineChart(demo, th, period, time.now(), time.zone())
            val rows = golden("line$period")
            assertEquals(rows.size, chart.points.size)
            rows.forEachIndexed { i, row ->
                val p = chart.points[i]
                assertEquals(row[1].toInt(), p.index)
                assertEquals(row[2].toFloat(), p.x, delta, "x $period/$i")
                assertEquals(row[3].toFloat(), p.ySys, delta, "ySys $period/$i")
                assertEquals(row[4].toFloat(), p.yDia, delta, "yDia $period/$i")
                assertEquals(row[5].toBoolean(), p.highStatus, "high $period/$i")
            }
            val meta = golden("meta$period").single()
            assertEquals(meta[1].toInt(), chart.points.size)
            assertEquals(meta[2].toBoolean(), chart.clipped)
            assertEquals(meta[3].toFloat(), chart.highSysY, delta)
            assertEquals(meta[4].toFloat(), chart.highDiaY, delta)
        }
    }

    @Test
    fun `points are ordered by time and a dia-driven high status is marked`() {
        val list =
            listOf(
                reading("2026-10-20T04:00:00Z", sys = 120, dia = 90),
                reading("2026-10-19T04:00:00Z", sys = 120, dia = 80),
            )
        val chart = lineChart(list, th, 7, time.now(), time.zone())
        assertEquals(listOf(false, true), chart.points.map { it.highStatus })
        assertTrue(chart.points[0].reading.takenAt < chart.points[1].reading.takenAt)
    }

    @Test
    fun `clipping flag is set for values outside 50 to 180 on either line`() {
        fun clipped(
            sys: Int,
            dia: Int,
        ) = lineChart(listOf(reading("2026-10-20T04:00:00Z", sys, dia)), th, 7, time.now(), time.zone()).clipped
        assertFalse(clipped(180, 50))
        assertTrue(clipped(181, 80))
        assertTrue(clipped(120, 49))
    }

    @Test
    fun `single point is centered and empty period gives no points`() {
        val one = lineChart(listOf(reading("2026-10-20T04:00:00Z")), th, 7, time.now(), time.zone())
        assertEquals(174f, one.points.single().x)
        assertTrue(lineChart(emptyList(), th, 7, time.now(), time.zone()).points.isEmpty())
    }
}
