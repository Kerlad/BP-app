package ru.bpdiary.feature.home

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import ru.bpdiary.app.feature.home.R
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.ui.BpDateFormatter
import ru.bpdiary.domain.DayBarSlot
import ru.bpdiary.domain.GreetingSlot
import ru.bpdiary.domain.NextReminderLabel

/** Russian texts of Home from resources (docs/05 §5.1, docs/06 §6.6). */
class HomeTexts(
    private val resources: Resources,
    val dates: BpDateFormatter = BpDateFormatter(resources),
) {
    fun greeting(slot: GreetingSlot): String =
        resources.getString(
            when (slot) {
                GreetingSlot.MORNING -> R.string.home_greeting_morning
                GreetingSlot.DAY -> R.string.home_greeting_day
                GreetingSlot.EVENING -> R.string.home_greeting_evening
                GreetingSlot.NIGHT -> R.string.home_greeting_night
            },
        )

    /** «Левая рука», «Правая рука». */
    fun arm(arm: Arm): String =
        resources.getString(if (arm == Arm.LEFT) R.string.home_arm_left else R.string.home_arm_right)

    /** «уд/мин · Левая рука». */
    fun pulseCaption(arm: Arm): String = resources.getString(R.string.home_pulse_caption, arm(arm))

    /** «Сег» for today, otherwise «Пн» … «Вс». */
    fun weekDay(slot: DayBarSlot): String =
        if (slot.daysAgo == 0) {
            resources.getString(R.string.home_week_today)
        } else {
            dates.weekdayShort(slot.date.dayOfWeek)
        }

    /** «133/85», the bold part of «среднее 133/85». */
    fun average(
        sys: Int,
        dia: Int,
    ): String = "$sys/$dia"

    fun averageLine(value: String): String = resources.getString(R.string.home_week_average, value)

    /** «14:00 · Дневное измерение», «Завтра, 08:00 · …», «Пн, 08:00 · …»; null → «Напоминания выключены». */
    fun nextReminder(label: NextReminderLabel?): String =
        when (label) {
            null -> {
                resources.getString(R.string.home_reminders_off)
            }

            is NextReminderLabel.Today -> {
                resources.getString(R.string.home_reminder_today, dates.time(label.time), label.label)
            }

            is NextReminderLabel.Tomorrow -> {
                resources.getString(R.string.home_reminder_tomorrow, dates.time(label.time), label.label)
            }

            is NextReminderLabel.Weekday -> {
                resources.getString(
                    R.string.home_reminder_weekday,
                    dates.weekdayShort(label.dow),
                    dates.time(label.time),
                    label.label,
                )
            }
        }
}

@Composable
fun rememberHomeTexts(): HomeTexts {
    val resources = LocalResources.current
    return remember(resources) { HomeTexts(resources) }
}
