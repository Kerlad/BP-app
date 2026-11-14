package ru.bpdiary.feature.transfer

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import ru.bpdiary.domain.report.DoctorReport
import ru.bpdiary.domain.report.buildDoctorReport
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

private const val WEEK_DAYS = 7L
private const val MONTH_DAYS = 30L
private const val QUARTER_DAYS = 90L

/** What is made of the chosen period: the report for the doctor (the default) or the table. */
enum class ExportFormat(
    val extension: String,
    val mime: String,
) {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv"),
}

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
    val format: ExportFormat = ExportFormat.PDF,
    val period: ExportPeriod = ExportPeriod.DAYS_30,
    val from: LocalDate = LocalDate.MIN,
    val to: LocalDate = LocalDate.MIN,
    val count: Int? = null,
    val saved: Boolean = false,
    val failed: Boolean = false,
    val choosingRange: Boolean = false,
    /** True while a file is being made: a second click must not start a second one. */
    val busy: Boolean = false,
    /** The real today (not the end of the chosen period): the latest day the range dialog may offer. */
    val today: LocalDate = LocalDate.MIN,
) {
    val fileName: String get() = exportFileName(from, to, format)
}

sealed interface ExportEvent {
    data class PeriodSelected(
        val period: ExportPeriod,
    ) : ExportEvent

    data class FormatSelected(
        val format: ExportFormat,
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

    data object PrintClicked : ExportEvent

    data object BackClicked : ExportEvent
}

/** The PDF kept in memory for the print dialog: it is read when the system asks, long after the click. */
class PrintJob(
    val name: String,
    val pages: Int,
    val pdf: ByteArray,
)

sealed interface ExportEffect {
    data class Share(
        val uri: String,
        val mime: String,
    ) : ExportEffect

    data class Print(
        val job: PrintJob,
    ) : ExportEffect
}

/** The report or the table of docs/13 §13.3 for a chosen period: saved where the user says, shared, or printed. */
@Suppress("TooManyFunctions")
@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        private val readings: ReadingRepository,
        private val thresholds: ThresholdsRepository,
        private val files: ExportFiles,
        private val resources: Resources,
        private val renderer: ReportRenderer,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(ExportUiState(today = today))
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
                is ExportEvent.FormatSelected -> chooseFormat(event.format)
                is ExportEvent.PeriodSelected -> select(event.period)
                is ExportEvent.RangeChosen -> load(ExportPeriod.CUSTOM, event.from, event.to)
                ExportEvent.RangeDismissed -> mutable.update { it.copy(choosingRange = false) }
                is ExportEvent.SaveTargetChosen -> save(event.uri)
                ExportEvent.ShareClicked -> share()
                ExportEvent.PrintClicked -> print()
                ExportEvent.SaveClicked, ExportEvent.BackClicked -> Unit
            }
        }

        private fun chooseFormat(format: ExportFormat) {
            mutable.update { it.copy(format = format, saved = false, failed = false) }
        }

        private val today: LocalDate get() = time.now().atZone(time.zone()).toLocalDate()

        private fun select(period: ExportPeriod) {
            if (period == ExportPeriod.CUSTOM) {
                mutable.update { it.copy(choosingRange = true) }
                return
            }
            val today = this.today
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
                    val start = if (period == ExportPeriod.ALL) list.firstOrNull()?.let(::localDay) ?: from else from
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

        private suspend fun report(): DoctorReport {
            val current = mutable.value
            val list = read(current.period, current.from, current.to)
            return buildDoctorReport(
                list,
                thresholds.observe().first(),
                current.from..current.to,
                time.now(),
                time.zone(),
            )
        }

        /** The file of the chosen format for the chosen period. */
        private suspend fun content(): ByteArray {
            val current = mutable.value
            return if (current.format == ExportFormat.PDF) {
                renderer.render(report())
            } else {
                val list = read(current.period, current.from, current.to)
                val th = thresholds.observe().first()
                val texts = csvExportTexts(resources, today, current.from..current.to, list.size, th)
                StringBuilder().also { writeCsv(it, list, th, texts) }.toString().toByteArray(Charsets.UTF_8)
            }
        }

        /** Runs one file job at a time: [busy] is set at the start and cleared at the end, whatever happens. */
        private fun exclusive(block: suspend () -> Unit) {
            if (mutable.value.busy) return
            mutable.update { it.copy(busy = true) }
            viewModelScope.launch {
                try {
                    block()
                } finally {
                    mutable.update { it.copy(busy = false) }
                }
            }
        }

        /** False when the file could not be made: storage, a renderer in a bad state, or no memory for the PDF. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun attempt(block: suspend () -> Unit): Boolean =
            try {
                block()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                false
            } catch (_: IllegalStateException) {
                false
            } catch (_: OutOfMemoryError) {
                // A long report as one byte array can exhaust the heap on a weak phone: say so instead of crashing.
                false
            }

        private fun save(uri: String) =
            exclusive {
                val done = attempt { files.save(uri, content()) }
                mutable.update { it.copy(saved = done, failed = !done) }
            }

        private fun share() =
            exclusive {
                val done =
                    attempt {
                        val uri = files.forShare(mutable.value.fileName, content())
                        mutable.update { it.copy(failed = false) }
                        effectChannel.send(ExportEffect.Share(uri, mutable.value.format.mime))
                    }
                if (!done) mutable.update { it.copy(failed = true) }
            }

        private fun print() =
            exclusive {
                val done =
                    attempt {
                        val report = report()
                        val pdf = renderer.render(report)
                        mutable.update { it.copy(failed = false, saved = false) }
                        effectChannel.send(ExportEffect.Print(PrintJob(mutable.value.fileName, report.pageCount, pdf)))
                    }
                if (!done) mutable.update { it.copy(failed = true, saved = false) }
            }
    }

private fun localDay(reading: Reading): LocalDate = reading.takenAt.atOffset(reading.zoneOffset).toLocalDate()
