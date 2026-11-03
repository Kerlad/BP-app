package ru.bpdiary.feature.charts

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import ru.bpdiary.app.feature.charts.R
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.ui.BpDateFormatter
import ru.bpdiary.domain.Avg
import ru.bpdiary.domain.PeriodStats
import ru.bpdiary.domain.dayLabel
import ru.bpdiary.domain.localDay
import java.time.LocalDate
import ru.bpdiary.app.core.ui.R as UiR

/** One cell of the 2×3 statistics grid: label, big value, caption. */
data class StatCell(
    val label: String,
    val value: String,
    val caption: String,
)

/** Russian texts of the charts screen from resources (docs/05 §5.4). */
class ChartsTexts(
    private val resources: Resources,
    private val dates: BpDateFormatter = BpDateFormatter(resources),
) {
    fun period(period: Period): String =
        resources.getString(if (period == Period.DAYS_7) R.string.charts_period_7 else R.string.charts_period_30)

    /** «14.10». */
    fun fromDate(date: LocalDate): String =
        resources.getString(R.string.charts_from_date, date.dayOfMonth, date.monthValue)

    /** Среднее, Пульс, Утром, Вечером, Максимум, Минимум — in this order. */
    fun cells(
        stats: PeriodStats,
        today: LocalDate,
    ): List<StatCell> =
        listOf(
            StatCell(
                resources.getString(R.string.charts_stat_average),
                reading(stats.avgSys, stats.avgDia),
                resources.getString(UiR.string.unit_mmhg),
            ),
            partOfDay(R.string.charts_stat_morning, stats.morning),
            partOfDay(R.string.charts_stat_evening, stats.evening),
            StatCell(
                resources.getString(R.string.charts_stat_pulse),
                stats.avgPulse?.toString() ?: noValue(),
                resources.getString(R.string.charts_unit_pulse_avg),
            ),
            extreme(R.string.charts_stat_max, stats.max, today),
            extreme(R.string.charts_stat_min, stats.min, today),
        )

    /** «54%». */
    fun percent(value: Int): String = resources.getString(R.string.charts_percent, value)

    /** «Повышенное: 6», «Норма: 7», «Пониженное: 0». */
    fun count(
        status: Status,
        n: Int,
    ): String =
        resources.getString(
            when (status) {
                Status.HIGH -> R.string.charts_count_high
                Status.NORMAL -> R.string.charts_count_normal
                Status.LOW -> R.string.charts_count_low
            },
            n,
        )

    /** «График за 7 дней, среднее 133 на 85, максимум 154 на 94 15 октября» (docs/04 §4.8). */
    fun description(
        period: Period,
        stats: PeriodStats,
        today: LocalDate,
    ): String =
        resources.getString(
            R.string.charts_description,
            period(period),
            stats.avgSys,
            stats.avgDia,
            stats.max.sys,
            stats.max.dia,
            dates.day(dayLabel(localDay(stats.max), today)),
        )

    private fun partOfDay(
        label: Int,
        avg: Avg?,
    ) = StatCell(
        resources.getString(label),
        avg?.let { reading(it.sys, it.dia) } ?: noValue(),
        resources.getQuantityString(R.plurals.measurements_count, avg?.count ?: 0, avg?.count ?: 0),
    )

    private fun extreme(
        label: Int,
        reading: Reading,
        today: LocalDate,
    ) = StatCell(
        resources.getString(label),
        reading(reading.sys, reading.dia),
        dates.day(dayLabel(localDay(reading), today)),
    )

    private fun reading(
        sys: Int,
        dia: Int,
    ) = resources.getString(R.string.charts_reading, sys, dia)

    private fun noValue() = resources.getString(R.string.charts_no_value)
}

@Composable
fun rememberChartsTexts(): ChartsTexts {
    val resources = LocalResources.current
    return remember(resources) { ChartsTexts(resources) }
}
