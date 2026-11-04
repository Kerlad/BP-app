package ru.bpdiary.feature.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.ThresholdsRepository
import ru.bpdiary.domain.classify
import ru.bpdiary.domain.dayHeaderBetween
import ru.bpdiary.domain.localDay
import ru.bpdiary.domain.pulseOutOfRange
import java.time.LocalDate
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L
private const val KEY_FILTER = "diary_filter"

/**
 * Diary (docs/05 §5.3). A filter or threshold change starts a new Pager, because the SQL filter takes the
 * thresholds as parameters (docs/06 §6.7). Day headers are inserted into the paging data, not in the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaryViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val readings: ReadingRepository,
        thresholds: ThresholdsRepository,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val filter = savedStateHandle.getStateFlow(KEY_FILTER, DiaryFilter.ALL)
        private val today = MutableStateFlow(currentDay())

        private val effectChannel = Channel<DiaryEffect>(Channel.BUFFERED)
        val effects: Flow<DiaryEffect> = effectChannel.receiveAsFlow()

        val items: Flow<PagingData<DiaryItem>> =
            combine(filter, thresholds.observe(), ::Pair)
                .flatMapLatest { (f, th) -> readings.paged(f.status, th).map { it.toDiaryItems(th) } }
                .cachedIn(viewModelScope)

        val state: StateFlow<DiaryUiState> =
            combine(filter, today, readings.observeCount()) { f, day, count -> DiaryUiState(f, day, count) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    DiaryUiState(today = today.value),
                )

        /** Delete and the filter; navigation and copying are handled by the screen's destination. */
        fun onEvent(event: DiaryEvent) {
            when (event) {
                is DiaryEvent.FilterSelected -> {
                    savedStateHandle[KEY_FILTER] = event.filter
                }

                is DiaryEvent.DeleteClicked -> {
                    viewModelScope.launch {
                        readings.softDelete(event.id)
                        effectChannel.send(DiaryEffect.Deleted(event.id))
                    }
                }

                is DiaryEvent.RowClicked,
                is DiaryEvent.CopyClicked,
                DiaryEvent.AddClicked,
                DiaryEvent.ExportClicked,
                DiaryEvent.ImportClicked,
                -> {
                    Unit
                }
            }
        }

        /** Called on `ON_RESUME` and on date or zone changes, so «Сегодня» and «Вчера» move at midnight. */
        fun refresh() {
            today.value = currentDay()
        }

        private fun currentDay(): LocalDate = time.now().atZone(time.zone()).toLocalDate()
    }

internal fun PagingData<Reading>.toDiaryItems(th: Thresholds): PagingData<DiaryItem> =
    map { DiaryItem.Row(it.toRow(th), localDay(it)) }
        .insertSeparators { before: DiaryItem.Row?, after: DiaryItem.Row? ->
            dayHeaderBetween(before?.day, after?.day)?.let { DiaryItem.Header(it) }
        }

internal fun Reading.toRow(th: Thresholds) =
    DiaryRow(
        id = id,
        time = takenAt.atOffset(zoneOffset).toLocalTime(),
        sys = sys,
        dia = dia,
        pulse = pulse,
        status = classify(sys, dia, th),
        pulseOutOfRange = pulseOutOfRange(pulse, th),
        arm = arm,
        tags = tags,
        comment = comment,
    )
