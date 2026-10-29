package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

class ReminderDaysTest {
    @Test
    fun `all seven days are daily`() {
        assertEquals(DaysSummary.Daily, daysSummary(EVERY_DAY))
    }

    @Test
    fun `Monday to Friday are workdays`() {
        assertEquals(DaysSummary.Workdays, daysSummary(WORKDAYS))
    }

    @Test
    fun `other sets are listed Monday first whatever the input order`() {
        assertEquals(
            DaysSummary.Listed(listOf(MONDAY, WEDNESDAY, FRIDAY)),
            daysSummary(linkedSetOf(FRIDAY, MONDAY, WEDNESDAY)),
        )
        assertEquals(DaysSummary.Listed(listOf(SATURDAY, SUNDAY)), daysSummary(setOf(SUNDAY, SATURDAY)))
    }

    @Test
    fun `workdays plus Saturday are listed, not workdays`() {
        assertEquals(
            DaysSummary.Listed(listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY)),
            daysSummary(WORKDAYS + SATURDAY),
        )
    }

    @Test
    fun `toggled adds a missing day and removes a present one`() {
        assertEquals(setOf(MONDAY, FRIDAY), setOf(MONDAY).toggled(FRIDAY))
        assertEquals(setOf(MONDAY), setOf(MONDAY, FRIDAY).toggled(FRIDAY))
        assertEquals(emptySet<DayOfWeek>(), setOf(MONDAY).toggled(MONDAY))
    }
}
