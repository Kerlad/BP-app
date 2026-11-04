package ru.bpdiary.feature.transfer

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Reading
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.ThresholdsRepository
import ru.bpdiary.domain.csv.writeCsv
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

private const val WEEK_DAYS = 7L
private const val MONTH_DAYS = 30L
private const val QUARTER_DAYS = 90L

enum class ExportPeriod(
    val days: Long?,
) {
    DAYS_7(WEEK_DAYS),
    DAYS_30(MONTH_DAYS),
    DAYS_90(QUARTER_DAYS),
    ALL(null),
    CUSTOM(null),
}

data class ExportUiState(
    val period: ExportPeriod = ExportPeriod.DAYS_30,
    val from: LocalDate = LocalDate.MIN,
    val to: LocalDate = LocalDate.MIN,
    val count: Int? = null,
    val saved: Boolean = false,
    val failed: Boolean = false,
    val choosingRange: Boolean = false,
) {
    val fileName: String get() = exportFileName(from, to)
}

sealed interface ExportEvent {
    data class PeriodSelected(
        val period: ExportPeriod,
    ) : ExportEvent

    data class RangeChosen(
        val from: LocalDate,
        val to: LocalDate,
    ) : ExportEvent

    data object RangeDismissed : ExportEvent

    data object SaveClicked : ExportEvent

    data class SaveTargetChosen(
        val uri: String,
    ) : ExportEvent

    data object ShareClicked : ExportEvent

    data object BackClicked : ExportEvent
}

sealed interface ExportEffect {
    data class Share(
        val uri: String,
    ) : ExportEffect
}

/** The one export format of docs/13 §13.3 for a chosen period: saved where the user says, or shared. */
@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        private val readings: ReadingRepository,
        private val thresholds: ThresholdsRepository,
        private val files: ExportFiles,
        private val resources: Resources,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(ExportUiState())
        val state: StateFlow<ExportUiState> = mutable.asStateFlow()

        private val effectChannel = Channel<ExportEffect>(Channel.BUFFERED)
        val effects: Flow<ExportEffect> = effectChannel.receiveAsFlow()

        private var counting: Job? = null

        init {
            // What a share left behind if the app was killed before the screen closed.
            files.clearShared()
            select(ExportPeriod.DAYS_30)
        }

        /** The app that received a shared file has read it by the time the user leaves this screen. */
        override fun onCleared() {
            files.clearShared()
        }

        fun onEvent(event: ExportEvent) {
            when (event) {
                is ExportEvent.PeriodSelected -> select(event.period)
                is ExportEvent.RangeChosen -> load(ExportPeriod.CUSTOM, event.from, event.to)
                ExportEvent.RangeDismissed -> mutable.update { it.copy(choosingRange = false) }
                is ExportEvent.SaveTargetChosen -> save(event.uri)
                ExportEvent.ShareClicked -> share()
                ExportEvent.SaveClicked, ExportEvent.BackClicked -> Unit
            }
        }

        private fun today(): LocalDate = time.now().atZone(time.zone()).toLocalDate()

        private fun select(period: ExportPeriod) {
            if (period == ExportPeriod.CUSTOM) {
                mutable.update { it.copy(choosingRange = true) }
                return
            }
            val today = today()
            val from = period.days?.let { today.minusDays(it - 1) } ?: today
            load(period, from, today)
        }

        private fun load(
            period: ExportPeriod,
            from: LocalDate,
            to: LocalDate,
        ) {
            counting?.cancel()
            counting =
                viewModelScope.launch {
                    val list = read(period, from, to)
                    // «Всё время» starts at the first reading.
                    val start = if (period == ExportPeriod.ALL) list.firstOrNull()?.localDay() ?: from else from
                    mutable.update {
                        it.copy(
                            period = period,
                            from = start,
                            to = to,
                            count = list.size,
                            saved = false,
                            failed = false,
                            choosingRange = false,
                        )
                    }
                }
        }

        private suspend fun read(
            period: ExportPeriod,
            from: LocalDate,
            to: LocalDate,
        ): List<Reading> {
            val zone = time.zone()
            val start = if (period == ExportPeriod.ALL) Instant.EPOCH else from.atStartOfDay(zone).toInstant()
            val end =
                if (period == ExportPeriod.ALL) {
                    Instant.ofEpochMilli(Long.MAX_VALUE)
                } else {
                    to
                        .plusDays(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .minusMillis(1)
                }
            return readings.observeRange(start, end).first()
        }

        private fun Reading.localDay(): LocalDate = takenAt.atOffset(zoneOffset).toLocalDate()

        private suspend fun writer(): (Appendable) -> Unit {
            val current = mutable.value
            val list = read(current.period, current.from, current.to)
            val th = thresholds.observe().first()
            val texts = csvExportTexts(resources, today(), current.from..current.to, list.size, th)
            return { out -> writeCsv(out, list, th, texts) }
        }

        private fun save(uri: String) {
            viewModelScope.launch {
                val done =
                    try {
                        files.save(uri, writer())
                        true
                    } catch (_: IOException) {
                        false
                    }
                mutable.update { it.copy(saved = done, failed = !done) }
            }
        }

        private fun share() {
            viewModelScope.launch {
                try {
                    val uri = files.forShare(mutable.value.fileName, writer())
                    mutable.update { it.copy(failed = false) }
                    effectChannel.send(ExportEffect.Share(uri))
                } catch (_: IOException) {
                    mutable.update { it.copy(failed = true) }
                }
            }
        }
    }
