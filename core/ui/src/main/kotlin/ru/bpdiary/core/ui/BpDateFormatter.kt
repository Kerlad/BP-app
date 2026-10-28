package ru.bpdiary.core.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import ru.bpdiary.app.core.ui.R
import ru.bpdiary.domain.DayLabel
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
                resources.getString(R.string.day_month, label.day.dayOfMonth, months[label.day.monthValue - 1])
            }
        }

    /** «Сегодня, 09:12», «18 октября, 21:40» (time chip on the Entry screen). */
    fun dayTime(
        label: DayLabel,
        time: LocalTime,
    ): String = resources.getString(R.string.day_time, day(label), time.format(HOURS_MINUTES))

    private companion object {
        val HOURS_MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

@Composable
fun rememberDateFormatter(): BpDateFormatter {
    val resources = LocalResources.current
    return remember(resources) { BpDateFormatter(resources) }
}
