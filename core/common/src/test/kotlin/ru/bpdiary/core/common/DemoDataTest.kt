package ru.bpdiary.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.bpdiary.core.model.Arm
import java.time.Instant

class DemoDataTest {
    private val readings = DemoData.readings()

    @Test
    fun `generates 59 readings newest first`() {
        assertEquals(59, readings.size)
        assertEquals(readings.sortedByDescending { it.takenAt }, readings)
        assertEquals((1L..59L).toList().reversed(), readings.map { it.id })
        assertEquals(true, readings.all { it.arm == Arm.LEFT && it.tags.isEmpty() })
    }

    @Test
    fun `first three and last three are pinned`() {
        fun row(i: Int) = readings[i].let { Triple(it.takenAt, it.sys to it.dia, it.pulse) }
        assertEquals(Triple(Instant.parse("2026-10-20T04:26:00Z"), 136 to 88, 74), row(0))
        assertEquals(Triple(Instant.parse("2026-10-19T18:31:00Z"), 143 to 87, 68), row(1))
        assertEquals(Triple(Instant.parse("2026-10-19T04:07:00Z"), 130 to 81, 70), row(2))
        assertEquals(Triple(Instant.parse("2026-09-22T04:10:00Z"), 139 to 85, 64), row(56))
        assertEquals(Triple(Instant.parse("2026-09-21T18:25:00Z"), 136 to 86, 75), row(57))
        assertEquals(Triple(Instant.parse("2026-09-21T04:05:00Z"), 127 to 90, 69), row(58))
    }

    @Test
    fun `all rows equal the output of the prototype makeData`() {
        // demo_data_golden.csv comes from demo_data_oracle.js run under node with TZ=Europe/Moscow.
        val golden =
            javaClass
                .getResource("/demo_data_golden.csv")!!
                .readText()
                .lines()
                .filter { it.isNotBlank() }
        val actual = readings.map { "${it.takenAt},${it.sys},${it.dia},${it.pulse}" }
        assertEquals(golden.map { it.replace(".000Z", "Z") }, actual)
    }

    // --- bulk data for the benchmarks (docs/10 §10.7: a diary of 2 000 readings) ---------------------

    private val bulk = DemoData.bulk(count = 2_000)

    @Test
    fun `bulk makes exactly the requested number, newest first, none in the future`() {
        assertEquals(2_000, bulk.size)
        assertEquals(bulk.sortedByDescending { it.takenAt }, bulk)
        val now = FixedTimeProvider.default().now()
        assertEquals(true, bulk.none { it.takenAt.isAfter(now) })
    }

    @Test
    fun `bulk readings are all different moments, two a day, with plausible values`() {
        assertEquals(2_000, bulk.map { it.takenAt }.toSet().size)
        val perDay = bulk.groupBy { it.takenAt.atZone(FixedTimeProvider.default().zone()).toLocalDate() }
        assertEquals(true, perDay.values.all { it.size <= 2 })
        assertEquals(true, bulk.all { it.sys in 100..170 && it.dia in 60..110 && it.pulse in 50..100 })
        assertEquals(true, bulk.all { it.dia < it.sys })
    }

    @Test
    fun `bulk is the same on every call`() {
        assertEquals(bulk, DemoData.bulk(count = 2_000))
    }

    @Test
    fun `bulk of zero is empty and a small bulk is a prefix of a large one`() {
        assertEquals(emptyList<Any>(), DemoData.bulk(count = 0))
        assertEquals(bulk.take(10), DemoData.bulk(count = 10))
    }
}
