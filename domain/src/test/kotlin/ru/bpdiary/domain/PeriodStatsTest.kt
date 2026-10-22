package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.bpdiary.core.common.DemoData
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Thresholds
import java.time.Instant
import java.time.ZoneOffset

class PeriodStatsTest {
    private val time = FixedTimeProvider.default()
    private val th = Thresholds()
    private val demo = DemoData.readings(time)
    private val moscow = ZoneOffset.ofHours(3)

    private fun reading(
        at: String,
        sys: Int = 120,
        dia: Int = 80,
        pulse: Int? = 70,
        offset: ZoneOffset = moscow,
    ) = Reading(takenAt = Instant.parse(at), zoneOffset = offset, sys = sys, dia = dia, pulse = pulse, arm = Arm.LEFT)

    private fun stats(
        readings: List<Reading>,
        period: Int = 7,
    ) = computeStats(readings, th, period, time.now(), time.zone())

    // --- golden ---------------------------------------------------------------------------------

    @Test
    fun `demo data matches the prototype oracle for 7 and 30 days`() {
        // stats_golden.json comes from stats_oracle.js (prototype logic run under node, TZ=Europe/Moscow).
        val g = flattenJson(javaClass.getResource("/stats_golden.json")!!.readText())
        for ((key, period) in listOf("p7" to 7, "p30" to 30)) {
            val actual = stats(demo, period)!!

            fun int(name: String) = g.getValue("$key.$name").toInt()

            fun avg(name: String) = Avg(int("$name.sys"), int("$name.dia"), int("$name.count"))

            fun point(
                name: String,
                r: Reading,
            ) = assertEquals(
                Triple(Instant.parse(g.getValue("$key.$name.at")), int("$name.sys"), int("$name.dia")),
                Triple(r.takenAt, r.sys, r.dia),
                "$key $name",
            )
            assertEquals(int("count"), actual.count, "$key count")
            assertEquals(int("avgSys"), actual.avgSys, "$key avgSys")
            assertEquals(int("avgDia"), actual.avgDia, "$key avgDia")
            assertEquals(int("avgPulse"), actual.avgPulse, "$key avgPulse")
            assertEquals(avg("morning"), actual.morning, "$key morning")
            assertEquals(avg("evening"), actual.evening, "$key evening")
            point("max", actual.max)
            point("min", actual.min)
            assertEquals(int("countHigh"), actual.countHigh, "$key countHigh")
            assertEquals(int("countNormal"), actual.countNormal, "$key countNormal")
            assertEquals(int("countLow"), actual.countLow, "$key countLow")
            assertEquals(int("inRangePercent"), actual.inRangePercent, "$key inRangePercent")
        }
    }

    // --- edge cases -----------------------------------------------------------------------------

    @Test
    fun `no readings gives null`() {
        assertNull(stats(emptyList()))
    }

    @Test
    fun `one reading`() {
        val r = reading("2026-10-20T04:00:00Z", sys = 140, dia = 90, pulse = 60)
        val s = stats(listOf(r))!!
        assertEquals(1, s.count)
        assertEquals(140, s.avgSys)
        assertEquals(90, s.avgDia)
        assertEquals(60, s.avgPulse)
        assertEquals(Avg(140, 90, 1), s.morning)
        assertNull(s.evening)
        assertEquals(r, s.max)
        assertEquals(r, s.min)
        assertEquals(1, s.countHigh)
        assertEquals(0, s.inRangePercent)
    }

    @Test
    fun `two readings round half up`() {
        // Means 125.5/80.5 -> 126/81 (half up), 70.5 pulse -> 71.
        val s =
            stats(
                listOf(
                    reading("2026-10-19T04:00:00Z", sys = 125, dia = 80, pulse = 70),
                    reading("2026-10-20T04:00:00Z", sys = 126, dia = 81, pulse = 71),
                ),
            )!!
        assertEquals(126, s.avgSys)
        assertEquals(81, s.avgDia)
        assertEquals(71, s.avgPulse)
        assertEquals(100, s.inRangePercent)
    }

    @Test
    fun `no morning readings gives null morning`() {
        val s = stats(listOf(reading("2026-10-20T04:00:00Z").copy(takenAt = Instant.parse("2026-10-19T18:00:00Z"))))!!
        assertNull(s.morning)
        assertEquals(Avg(120, 80, 1), s.evening)
    }

    @Test
    fun `morning boundary is local noon of the measurement`() {
        val s =
            stats(
                listOf(
                    reading("2026-10-19T08:59:00Z"), // 11:59 +03
                    reading("2026-10-19T09:00:00Z"), // 12:00 +03
                ),
            )!!
        assertEquals(1, s.morning?.count)
        assertEquals(1, s.evening?.count)
    }

    @Test
    fun `morning uses the offset the reading was taken in`() {
        // 06:30 UTC is 11:30 at +05 (morning) although it is 09:30 in the device zone, also morning;
        // 07:30 UTC is 12:30 at +05 (evening) but 10:30 in Moscow (morning).
        val s = stats(listOf(reading("2026-10-19T07:30:00Z", offset = ZoneOffset.ofHours(5))))!!
        assertNull(s.morning)
        assertEquals(1, s.evening?.count)
    }

    @Test
    fun `period starts at the beginning of the local day`() {
        // 7 days: from 2026-10-14 00:00 Moscow = 2026-10-13T21:00Z.
        val inside = reading("2026-10-13T21:00:00Z")
        val outside = reading("2026-10-13T20:59:00Z")
        assertEquals(1, stats(listOf(inside, outside))!!.count)
    }

    @Test
    fun `readings after now are ignored`() {
        assertNull(stats(listOf(reading("2026-10-20T06:13:00Z"))))
    }

    @Test
    fun `null pulse is excluded from the pulse average`() {
        val s =
            stats(
                listOf(
                    reading("2026-10-19T04:00:00Z", pulse = null),
                    reading("2026-10-20T04:00:00Z", pulse = 61),
                ),
            )!!
        assertEquals(61, s.avgPulse)
        assertNull(stats(listOf(reading("2026-10-20T04:00:00Z", pulse = null)))!!.avgPulse)
    }

    @Test
    fun `max and min ties pick the latest reading`() {
        val early = reading("2026-10-18T04:00:00Z", sys = 150, dia = 90)
        val late = reading("2026-10-20T04:00:00Z", sys = 150, dia = 95)
        val s = stats(listOf(late, early))!!
        assertEquals(late, s.max)
        assertEquals(late, s.min)
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Flattens the pretty-printed golden JSON ("p7": { "max": { "sys": 1 } }) into "p7.max.sys" -> "1". */
    private fun flattenJson(text: String): Map<String, String> {
        val path = ArrayDeque<String>()
        val out = linkedMapOf<String, String>()
        val entry = Regex("\"([A-Za-z0-9]+)\": (.+?),?")
        for (line in text.lines().map { it.trim() }) {
            val match = entry.matchEntire(line)
            when {
                line.startsWith("}") -> path.removeLastOrNull()
                match == null -> Unit
                match.groupValues[2] == "{" -> path.addLast(match.groupValues[1])
                else -> out[(path + match.groupValues[1]).joinToString(".")] = match.groupValues[2].trim('"')
            }
        }
        return out
    }
}
