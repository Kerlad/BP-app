package ru.bpdiary.domain

import ru.bpdiary.core.model.Reading
import java.time.LocalDate
import java.time.LocalTime

/** The day of the measurement in the zone it was taken in, not in the current device zone. */
fun localDay(reading: Reading): LocalDate = reading.takenAt.atOffset(reading.zoneOffset).toLocalDate()

/**
 * Day header to put between two neighbouring diary rows, newest first (Paging `insertSeparators`).
 * Days come from [localDay]. Returns [afterDay] when it starts a new day; nothing after the last row.
 */
fun dayHeaderBetween(
    beforeDay: LocalDate?,
    afterDay: LocalDate?,
): LocalDate? = afterDay?.takeIf { beforeDay != it }

sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data class Date(
        val day: LocalDate,
    ) : DayLabel
}

/** [today] must be computed in the current device zone (`TimeProvider.zone()`). */
fun dayLabel(
    day: LocalDate,
    today: LocalDate,
): DayLabel =
    when {
        day == today -> DayLabel.Today

        // `day + 1`, not `today - 1`: the latter overflows when a screen still holds the placeholder `LocalDate.MIN`.
        day.plusDays(1) == today -> DayLabel.Yesterday

        else -> DayLabel.Date(day)
    }

enum class GreetingSlot { MORNING, DAY, EVENING, NIGHT }

private const val MORNING_FROM = 5
private const val DAY_FROM = 12
private const val EVENING_FROM = 18
private const val NIGHT_FROM = 23

/** MORNING 05:00-11:59, DAY 12:00-17:59, EVENING 18:00-22:59, NIGHT 23:00-04:59. */
fun greetingSlot(time: LocalTime): GreetingSlot =
    when (time.hour) {
        in MORNING_FROM until DAY_FROM -> GreetingSlot.MORNING
        in DAY_FROM until EVENING_FROM -> GreetingSlot.DAY
        in EVENING_FROM until NIGHT_FROM -> GreetingSlot.EVENING
        else -> GreetingSlot.NIGHT
    }
