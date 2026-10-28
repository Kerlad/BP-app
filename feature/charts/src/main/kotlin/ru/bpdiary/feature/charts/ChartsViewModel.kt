package ru.bpdiary.feature.charts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.ThresholdsRepository
import ru.bpdiary.domain.computeStats
import ru.bpdiary.domain.lineChart
import java.time.ZonedDateTime
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L
private const val KEY_PERIOD = "charts_period"
private const val MIN_READINGS = 2

/** The longest period plus a day of margin: readings keep their own offset, so the query is wider. */
private const val WINDOW_DAYS = 31L

/**
 * Charts (docs/05 §5.4). Up to ~60 readings, so the chart and the statistics are computed in Kotlin on
 * `Dispatchers.Default` (docs/03 §3.4). "Now" is kept until [refresh] (`ON_RESUME`, date or zone change).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        readings: ReadingRepository,
        thresholds: ThresholdsRepository,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val period = savedStateHandle.getStateFlow(KEY_PERIOD, Period.DAYS_7)
        private val now = MutableStateFlow(currentMoment())

        private val window: Flow<List<Reading>> =
            now
                .map { it.toLocalDate() to it.zone }
                .distinctUntilChanged()
                .flatMapLatest { (today, zone) ->
                    val from = today.minusDays(WINDOW_DAYS).atStartOfDay(zone).toInstant()
                    val to = today.plusDays(2).atStartOfDay(zone).toInstant()
                    readings.observeRange(from, to)
                }

        val state: StateFlow<ChartsUiState> =
            combine(period, now, window, thresholds.observe(), ::chartsState)
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChartsUiState())

        fun onEvent(event: ChartsEvent) {
            when (event) {
                is ChartsEvent.PeriodSelected -> savedStateHandle[KEY_PERIOD] = event.period
            }
        }

        fun refresh() {
            now.value = currentMoment()
        }

        private fun currentMoment(): ZonedDateTime = time.now().atZone(time.zone())
    }

/** Pure assembly; the screenshot tests build their states with it from demo data. */
internal fun chartsState(
    period: Period,
    now: ZonedDateTime,
    readings: List<Reading>,
    th: Thresholds,
): ChartsUiState {
    val today = now.toLocalDate()
    val chart = lineChart(readings, th, period.days, now.toInstant(), now.zone)
    val enough = chart.points.size >= MIN_READINGS
    return ChartsUiState(
        period = period,
        loading = false,
        today = today,
        from = today.minusDays(period.days - 1L),
        chart = chart.takeIf { enough },
        stats = if (enough) computeStats(readings, th, period.days, now.toInstant(), now.zone) else null,
    )
}
