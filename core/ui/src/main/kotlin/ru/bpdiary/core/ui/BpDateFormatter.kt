package ru.bpdiary.core.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import ru.bpdiary.app.core.ui.R
import ru.bpdiary.domain.DayLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Turns domain date labels into Russian text from resources (docs/06 §6.3).
 * Month names come from a string array, not from a locale formatter, which would give «окт.».
 */
class BpDateFormatter(
    private val resources: Resources,
) {
    private val months: Array<String> by lazy { resources.getStringArray(R.array.months_genitive) }
    private val weekdays: Array<String> by lazy { resources.getStringArray(R.array.weekdays) }
    private val weekdaysShort: Array<String> by lazy { resources.getStringArray(R.array.weekdays_short) }

    /** «Сегодня», «Вчера» or «18 октября». */
    fun day(label: DayLabel): String =
        when (label) {
            DayLabel.Today -> {
                resources.getString(R.string.day_today)
            }

            DayLabel.Yesterday -> {
                resources.getString(R.string.day_yesterday)
            }

            is DayLabel.Date -> {
                dayMonth(label.day)
            }
        }

    /** Diary day header: «Сегодня», «Вчера» or «18 октября, воскресенье» (weekday in lower case). */
    fun dayHeader(label: DayLabel): String =
        when (label) {
            is DayLabel.Date -> {
                resources.getString(
                    R.string.day_month_weekday,
                    dayMonth(label.day),
                    weekdays[label.day.dayOfWeek.value - 1],
                )
            }

            else -> {
                day(label)
            }
        }

    /** «Сегодня, 09:12», «18 октября, 21:40» (time chip on the Entry screen). */
    fun dayTime(
        label: DayLabel,
        time: LocalTime,
    ): String = resources.getString(R.string.day_time, day(label), time(time))

    /** «09:12». */
    fun time(time: LocalTime): String = time.format(HOURS_MINUTES)

    /** «Вторник, 20 октября» (date header on Home): weekday with a capital letter, nominative. */
    fun weekdayDayMonth(date: LocalDate): String {
        val weekday = weekdays[date.dayOfWeek.value - 1].replaceFirstChar { it.uppercaseChar() }
        return resources.getString(R.string.weekday_day_month, weekday, dayMonth(date))
    }

    /** «Пн» … «Вс». */
    fun weekdayShort(dow: DayOfWeek): String = weekdaysShort[dow.value - 1]

    private fun dayMonth(date: LocalDate): String =
        resources.getString(R.string.day_month, date.dayOfMonth, months[date.monthValue - 1])

    private companion object {
        val HOURS_MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

@Composable
fun rememberDateFormatter(): BpDateFormatter {
    val resources = LocalResources.current
    return remember(resources) { BpDateFormatter(resources) }
}
