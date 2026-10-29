package ru.bpdiary.domain

import java.time.DayOfWeek

/** Every day of the week: the «Ежедневно» preset. */
val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

/** Monday to Friday: the «Пн – Пт» preset. */
val WORKDAYS: Set<DayOfWeek> =
    setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

/** How the days of a reminder are named; presentation turns it into «Ежедневно», «Пн – Пт» or «Пн, Ср, Пт». */
sealed interface DaysSummary {
    data object Daily : DaysSummary

    data object Workdays : DaysSummary

    /** Monday first, whatever the order of the input. */
    data class Listed(
        val days: List<DayOfWeek>,
    ) : DaysSummary
}

fun daysSummary(days: Set<DayOfWeek>): DaysSummary =
    when (days) {
        EVERY_DAY -> DaysSummary.Daily
        WORKDAYS -> DaysSummary.Workdays
        else -> DaysSummary.Listed(days.sorted())
    }

/** [day] removed when present, added otherwise. The result may be empty; the caller decides what that means. */
fun Set<DayOfWeek>.toggled(day: DayOfWeek): Set<DayOfWeek> = if (day in this) this - day else this + day
