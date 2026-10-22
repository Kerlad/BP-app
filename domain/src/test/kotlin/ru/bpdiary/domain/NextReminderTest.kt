package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import ru.bpdiary.core.model.Reminder
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.stream.Stream

class NextReminderTest {
    private val moscow = ZoneId.of("Europe/Moscow")
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun reminder(
        time: String = "08:00",
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        enabled: Boolean = true,
        label: String = "r",
        id: Long = 0,
    ) = Reminder(id = id, time = LocalTime.parse(time), label = label, days = days, enabled = enabled)

    private fun at(
        date: String,
        time: String,
        zone: ZoneId = moscow,
    ): ZonedDateTime = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(zone)

    /** Independent oracle: walk minute by minute until the weekday and the time match. */
    private fun bruteForce(
        r: Reminder,
        now: ZonedDateTime,
    ): ZonedDateTime {
        var t = now.withSecond(0).withNano(0).plusMinutes(1)
        while (!(t.dayOfWeek in r.days && t.toLocalTime() == r.time)) t = t.plusMinutes(1)
        return t
    }

    @ParameterizedTest(name = "{0} {1} mask {2}")
    @MethodSource("grid")
    fun `matches brute force`(
        dow: DayOfWeek,
        time: String,
        maskName: String,
        days: Set<DayOfWeek>,
    ) {
        // 2026-10-19 is a Monday.
        val date = LocalDate.of(2026, 10, 19).plusDays((dow.value - 1).toLong())
        val now = at(date.toString(), time)
        val r = reminder(days = days)
        assertEquals(bruteForce(r, now), r.nextTriggerAfter(now), maskName)
    }

    @Test
    fun `strictly after now`() {
        val r = reminder("08:00")
        assertEquals(at("2026-10-21", "08:00"), r.nextTriggerAfter(at("2026-10-20", "08:00")))
        assertEquals(at("2026-10-20", "08:00"), r.nextTriggerAfter(at("2026-10-20", "07:59")))
    }

    @Test
    fun `single day reminder on its own day after the time fires next week`() {
        val r = reminder("08:00", setOf(DayOfWeek.TUESDAY))
        assertEquals(at("2026-10-27", "08:00"), r.nextTriggerAfter(at("2026-10-20", "08:00")))
    }

    @Test
    fun `friday evening to monday morning`() {
        val r = reminder("08:00", weekdays)
        assertEquals(at("2026-10-26", "08:00"), r.nextTriggerAfter(at("2026-10-23", "22:00")))
    }

    @Test
    fun `empty days has no trigger`() {
        assertNull(reminder(days = emptySet()).nextTriggerAfter(at("2026-10-20", "09:12")))
    }

    @Test
    fun `dst spring forward keeps wall clock time`() {
        // Europe/Berlin switches 02:00 -> 03:00 on Sunday 2026-03-29.
        val now = at("2026-03-28", "09:00", berlin)
        val next = reminder("08:00").nextTriggerAfter(now)
        assertEquals(at("2026-03-29", "08:00", berlin), next)
        assertEquals(22L * 60, Duration.between(now, next).toMinutes())
    }

    @Test
    fun `dst gap time moves forward`() {
        val next = reminder("02:30").nextTriggerAfter(at("2026-03-28", "09:00", berlin))
        assertEquals(at("2026-03-29", "03:30", berlin), next)
    }

    @Test
    fun `dst fall back keeps wall clock time`() {
        // Europe/Berlin switches 03:00 -> 02:00 on Sunday 2026-10-25.
        val next = reminder("08:00").nextTriggerAfter(at("2026-10-24", "09:00", berlin))
        assertEquals(at("2026-10-25", "08:00", berlin), next)
    }

    @Test
    fun `nextReminder picks the earliest enabled one`() {
        val now = at("2026-10-20", "09:12")
        val list =
            listOf(
                reminder("08:00", label = "morning", id = 1),
                reminder("14:00", enabled = false, label = "off", id = 2),
                reminder("21:00", label = "evening", id = 3),
            )
        val next = nextReminder(list, now)!!
        assertEquals(3L, next.reminder.id)
        assertEquals(at("2026-10-20", "21:00"), next.at)
    }

    @Test
    fun `nextReminder is null without enabled reminders`() {
        val now = at("2026-10-20", "09:12")
        assertNull(nextReminder(emptyList(), now))
        assertNull(nextReminder(listOf(reminder(enabled = false)), now))
    }

    @Test
    fun `labels today and tomorrow`() {
        val now = at("2026-10-20", "09:12")
        val day = reminder("14:00", label = "day")
        assertEquals(
            NextReminderLabel.Today(LocalTime.of(14, 0), "day"),
            nextReminderLabel(nextReminder(listOf(day), now)!!, now),
        )
        val morning = reminder("08:00", label = "morning")
        assertEquals(
            NextReminderLabel.Tomorrow(LocalTime.of(8, 0), "morning"),
            nextReminderLabel(nextReminder(listOf(morning), now)!!, now),
        )
    }

    @Test
    fun `saturday evening with only a weekday reminder shows monday`() {
        val now = at("2026-10-24", "20:00")
        val next = nextReminder(listOf(reminder("08:00", weekdays, label = "morning")), now)!!
        assertEquals(
            NextReminderLabel.Weekday(DayOfWeek.MONDAY, LocalTime.of(8, 0), "morning"),
            nextReminderLabel(next, now),
        )
    }

    companion object {
        private val weekdays =
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            )

        @JvmStatic
        fun grid(): Stream<Arguments> {
            val masks =
                mapOf(
                    "daily" to DayOfWeek.entries.toSet(),
                    "mon-fri" to weekdays,
                    "single-wed" to setOf(DayOfWeek.WEDNESDAY),
                )
            return DayOfWeek.entries
                .flatMap { dow ->
                    listOf("00:00", "07:59", "08:00", "23:59").flatMap { time ->
                        masks.map { (name, days) -> Arguments.of(dow, time, name, days) }
                    }
                }.stream()
        }
    }
}
