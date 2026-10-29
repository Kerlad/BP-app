package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class DayGroupingTest {
    private fun reading(
        at: String,
        offset: ZoneOffset,
    ) = Reading(
        takenAt = Instant.parse(at),
        zoneOffset = offset,
        sys = 120,
        dia = 80,
        pulse = 70,
        arm = Arm.LEFT,
    )

    @Test
    fun `day boundary at 23_59 and 00_00 local`() {
        val plus3 = ZoneOffset.ofHours(3)
        assertEquals(LocalDate.of(2026, 10, 20), localDay(reading("2026-10-20T20:59:59Z", plus3)))
        assertEquals(LocalDate.of(2026, 10, 21), localDay(reading("2026-10-20T21:00:00Z", plus3)))
    }

    @Test
    fun `diary header before the first reading and at each new day only`() {
        val plus3 = ZoneOffset.ofHours(3)
        val evening = localDay(reading("2026-10-20T18:00:00Z", plus3))
        val morning = localDay(reading("2026-10-20T04:00:00Z", plus3))
        val yesterday = localDay(reading("2026-10-19T18:00:00Z", plus3))
        assertEquals(LocalDate.of(2026, 10, 20), dayHeaderBetween(null, evening))
        assertEquals(null, dayHeaderBetween(evening, morning))
        assertEquals(LocalDate.of(2026, 10, 19), dayHeaderBetween(morning, yesterday))
        assertEquals(null, dayHeaderBetween(yesterday, null))
        assertEquals(null, dayHeaderBetween(null, null))
    }

    @Test
    fun `diary header uses the offset of each reading`() {
        // Same instant, different offsets: 23:30 on the 20th at +3 and 00:30 on the 21st at +4.
        val moscow = localDay(reading("2026-10-20T20:30:00Z", ZoneOffset.ofHours(3)))
        val samara = localDay(reading("2026-10-20T20:30:00Z", ZoneOffset.ofHours(4)))
        assertEquals(LocalDate.of(2026, 10, 20), dayHeaderBetween(samara, moscow))
    }

    @Test
    fun `reading keeps the day of its own offset`() {
        // 02:00 UTC on the 21st is still the 20th at UTC-5, whatever zone the device is in now.
        assertEquals(LocalDate.of(2026, 10, 20), localDay(reading("2026-10-21T02:00:00Z", ZoneOffset.ofHours(-5))))
        assertEquals(LocalDate.of(2026, 10, 21), localDay(reading("2026-10-21T02:00:00Z", ZoneOffset.UTC)))
    }

    @Test
    fun `a day label never fails, whatever the day of today`() {
        // The diary shows its first frame before today is known (LocalDate.MIN): minusDays(1) would overflow there.
        val day = LocalDate.of(2026, 10, 18)
        assertEquals(DayLabel.Date(day), dayLabel(day, LocalDate.MIN))
        assertEquals(DayLabel.Date(day), dayLabel(day, LocalDate.MAX))
    }

    @Test
    fun `day labels`() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(DayLabel.Today, dayLabel(today, today))
        assertEquals(DayLabel.Yesterday, dayLabel(today.minusDays(1), today))
        assertEquals(DayLabel.Date(today.minusDays(2)), dayLabel(today.minusDays(2), today))
        assertEquals(DayLabel.Date(today.plusDays(1)), dayLabel(today.plusDays(1), today))
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "00:00,NIGHT",
        "04:59,NIGHT",
        "05:00,MORNING",
        "11:59,MORNING",
        "12:00,DAY",
        "17:59,DAY",
        "18:00,EVENING",
        "22:59,EVENING",
        "23:00,NIGHT",
        "23:59,NIGHT",
    )
    fun `greeting slots`(
        time: String,
        slot: GreetingSlot,
    ) {
        assertEquals(slot, greetingSlot(LocalTime.parse(time)))
    }
}
