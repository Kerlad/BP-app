package ru.bpdiary.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.ThresholdsRepository
import ru.bpdiary.domain.classify
import ru.bpdiary.domain.dayLabel
import ru.bpdiary.domain.greetingSlot
import ru.bpdiary.domain.localDay
import ru.bpdiary.domain.nextReminder
import ru.bpdiary.domain.nextReminderLabel
import ru.bpdiary.domain.pulseOutOfRange
import ru.bpdiary.domain.weekBars
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** Readings are queried with a day of margin on both sides: their local day uses their own offset. */
private const val WINDOW_DAYS_BACK = 7L
private const val WINDOW_DAYS_AHEAD = 2L

/**
 * Home (docs/05 §5.1). Everything is recomputed when thresholds, readings or reminders change.
 * "Now" is read once and kept until [refresh]: the screen calls it on `ON_RESUME` and on date, time and
 * zone change broadcasts, so the date, the greeting and «Сегодня» follow midnight and zone changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        readings: ReadingRepository,
        thresholds: ThresholdsRepository,
        reminders: ReminderRepository,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val now = MutableStateFlow(currentMoment())

        /** Readings around the last seven days; a new query only when the day or the zone changes. */
        private val week: Flow<WeekWindow> =
            now
                .map { WeekKey(it.toLocalDate(), it.zone) }
                .distinctUntilChanged()
                .flatMapLatest { key ->
                    val from = key.dayStart(-WINDOW_DAYS_BACK)
                    val to = key.dayStart(WINDOW_DAYS_AHEAD)
                    readings.observeRange(from, to).map { WeekWindow(key.today, it) }
                }

        val state: StateFlow<HomeUiState> =
            combine(now, readings.observeLast(), week, thresholds.observe(), reminders.observeAll()) {
                moment,
                last,
                window,
                th,
                list,
                ->
                homeContent(moment, last, window, th, list)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState.Loading)

        fun refresh() {
            now.value = currentMoment()
        }

        private fun currentMoment(): ZonedDateTime = time.now().atZone(time.zone())
    }

private data class WeekKey(
    val today: LocalDate,
    val zone: ZoneId,
) {
    fun dayStart(daysFromToday: Long): Instant = today.plusDays(daysFromToday).atStartOfDay(zone).toInstant()
}

/** [readings] were queried for the seven days ending on [today]. */
internal data class WeekWindow(
    val today: LocalDate,
    val readings: List<Reading>,
)

/** Pure state assembly; also used by the screenshot tests to build states from demo data. */
internal fun homeContent(
    now: ZonedDateTime,
    last: Reading?,
    window: WeekWindow,
    th: Thresholds,
    reminders: List<Reminder>,
): HomeUiState.Content {
    val today = now.toLocalDate()
    return HomeUiState.Content(
        today = today,
        greeting = greetingSlot(now.toLocalTime()),
        last = last?.toUi(today, th),
        week = if (last == null) null else weekBars(window.readings, th, window.today),
        nextReminder = nextReminder(reminders, now)?.let { nextReminderLabel(it, now) },
    )
}

private fun Reading.toUi(
    today: LocalDate,
    th: Thresholds,
) = LastReadingUi(
    dayLabel = dayLabel(localDay(this), today),
    time = takenAt.atOffset(zoneOffset).toLocalTime(),
    sys = sys,
    dia = dia,
    pulse = pulse,
    pulseOutOfRange = pulseOutOfRange(pulse, th),
    status = classify(sys, dia, th),
    arm = arm,
)
