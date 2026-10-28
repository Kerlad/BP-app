package ru.bpdiary.feature.charts

import ru.bpdiary.domain.LineChartModel
import ru.bpdiary.domain.PeriodStats
import java.time.LocalDate

private const val WEEK = 7
private const val MONTH = 30

enum class Period(
    val days: Int,
) {
    DAYS_7(WEEK),
    DAYS_30(MONTH),
}

/** Texts are made by [ChartsTexts]. [chart] and [stats] are null with fewer than two readings. */
data class ChartsUiState(
    val period: Period = Period.DAYS_7,
    val loading: Boolean = true,
    /** In the current device zone. */
    val today: LocalDate = LocalDate.MIN,
    /** First day of the period: the «14.10» label under the chart. */
    val from: LocalDate = LocalDate.MIN,
    val chart: LineChartModel? = null,
    val stats: PeriodStats? = null,
)

sealed interface ChartsEvent {
    data class PeriodSelected(
        val period: Period,
    ) : ChartsEvent
}
