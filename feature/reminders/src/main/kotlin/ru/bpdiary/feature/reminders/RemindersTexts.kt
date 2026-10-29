package ru.bpdiary.feature.reminders

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import ru.bpdiary.app.feature.reminders.R
import ru.bpdiary.core.ui.BpDateFormatter
import ru.bpdiary.domain.DaysSummary

/** «Ежедневно», «Пн – Пт», «Пн, Ср, Пт» from resources (docs/05 §5.5). */
class RemindersTexts(
    resources: Resources,
    val dates: BpDateFormatter = BpDateFormatter(resources),
) {
    private val daily = resources.getString(R.string.reminders_days_daily)
    private val workdays = resources.getString(R.string.reminders_days_workdays)

    fun days(summary: DaysSummary): String =
        when (summary) {
            DaysSummary.Daily -> daily
            DaysSummary.Workdays -> workdays
            is DaysSummary.Listed -> summary.days.joinToString(", ") { dates.weekdayShort(it) }
        }
}

@Composable
fun rememberRemindersTexts(): RemindersTexts {
    val resources = LocalResources.current
    return remember(resources) { RemindersTexts(resources) }
}
