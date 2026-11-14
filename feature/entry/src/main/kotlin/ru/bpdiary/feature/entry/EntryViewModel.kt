package ru.bpdiary.feature.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Field
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.Draft
import ru.bpdiary.domain.DraftResult
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.Reason
import ru.bpdiary.domain.SaveReadingUseCase
import ru.bpdiary.domain.SaveResult
import ru.bpdiary.domain.SettingsRepository
import ru.bpdiary.domain.ThresholdsRepository
import ru.bpdiary.domain.ValidationError
import ru.bpdiary.domain.classify
import ru.bpdiary.domain.dayLabel
import ru.bpdiary.domain.isCritical
import ru.bpdiary.domain.reduce
import ru.bpdiary.domain.takeWholeChars
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Max comment length (FR-1, docs/05 §5.2). */
const val COMMENT_MAX_LENGTH = 120

/** Live status appears once SYS and DIA both have this many digits. */
private const val LIVE_STATUS_DIGITS = 2
private const val STOP_TIMEOUT_MS = 5_000L
private const val KEY_FORM = "entry_form"

/**
 * New reading (`readingId == null`) or edit of an existing one (D-10).
 * The whole form lives in [SavedStateHandle], so it survives rotation and process death.
 */
@HiltViewModel(assistedFactory = EntryViewModel.Factory::class)
class EntryViewModel
    @AssistedInject
    constructor(
        @Assisted private val readingId: Long?,
        private val savedStateHandle: SavedStateHandle,
        private val readings: ReadingRepository,
        thresholdsRepository: ThresholdsRepository,
        private val settings: SettingsRepository,
        private val time: TimeProvider,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(readingId: Long?): EntryViewModel
        }

        private val saveReading = SaveReadingUseCase(readings, time)
        private val form = MutableStateFlow<Form?>(null)
        private val transient = MutableStateFlow(Transient())
        private var original: Reading? = null
        private var busy = false

        private val effectChannel = Channel<EntryEffect>(Channel.BUFFERED)
        val effects: Flow<EntryEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<EntryUiState> =
            combine(form, thresholdsRepository.observe(), transient) { f, th, t ->
                f?.toUiState(th, t) ?: EntryUiState(isEdit = readingId != null)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EntryUiState())

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val restored = savedStateHandle.get<String>(KEY_FORM)?.let { Json.decodeFromString<Form>(it) }
            if (readingId != null) {
                val reading = readings.getById(readingId)
                if (reading == null) {
                    effectChannel.send(EntryEffect.Closed)
                    return
                }
                original = reading
                setForm(restored ?: Form.of(reading))
            } else {
                setForm(restored ?: newForm())
            }
        }

        private suspend fun newForm(): Form {
            val now = time.now()
            return Form(
                arm = settings.lastArm.first(),
                takenAtMillis = now.toEpochMilli(),
                offsetSeconds =
                    time
                        .zone()
                        .rules
                        .getOffset(now)
                        .totalSeconds,
            )
        }

        private fun setForm(value: Form) {
            form.value = value
            savedStateHandle[KEY_FORM] = Json.encodeToString(value)
        }

        private fun updateForm(block: (Form) -> Form) {
            val current = form.value ?: return
            setForm(block(current))
        }

        fun onEvent(event: EntryEvent) {
            when (event) {
                is EntryEvent.KeyPressed -> {
                    onKey(event)
                }

                is EntryEvent.FieldTapped -> {
                    updateForm { it.withDraft(it.draft().focus(event.field)) }
                    transient.update { it.copy(missing = null) }
                }

                is EntryEvent.ArmSelected -> {
                    updateForm { it.copy(arm = event.arm) }
                }

                is EntryEvent.TagToggled -> {
                    updateForm { f ->
                        f.copy(
                            tags =
                                if (event.tag in
                                    f.tags
                                ) {
                                    f.tags - event.tag
                                } else {
                                    f.tags + event.tag
                                },
                        )
                    }
                }

                is EntryEvent.CommentChanged -> {
                    updateForm { it.copy(comment = event.text.takeWholeChars(COMMENT_MAX_LENGTH)) }
                }

                EntryEvent.SaveClicked -> {
                    save()
                }

                EntryEvent.CloseClicked -> {
                    close()
                }

                EntryEvent.DiscardConfirmed -> {
                    transient.update { it.copy(dialog = null) }
                    effectChannel.trySend(EntryEffect.Closed)
                }

                EntryEvent.DeleteClicked -> {
                    if (original != null) transient.update { it.copy(dialog = EntryDialog.Delete) }
                }

                EntryEvent.DeleteConfirmed -> {
                    delete()
                }

                EntryEvent.DialogDismissed, is EntryEvent.Picker -> {
                    val (t, f) = pick(event, transient.value, form.value, time)
                    f?.let(::setForm)
                    transient.value = t
                }
            }
        }

        private fun onKey(event: EntryEvent.KeyPressed) {
            val current = form.value ?: return
            when (val result = reduce(current.draft(), event.key)) {
                is DraftResult.Updated -> {
                    setForm(current.withDraft(result.draft))
                    transient.update { it.copy(error = null, missing = null) }
                }

                DraftResult.Ignored -> {
                    Unit
                }

                DraftResult.Save -> {
                    save()
                }
            }
        }

        private fun close() {
            val current = form.value
            val dirty =
                when {
                    current == null -> false
                    original != null -> current.content() != Form.of(original!!).content()
                    else -> current.hasInput()
                }
            if (dirty) {
                transient.update {
                    it.copy(
                        dialog = EntryDialog.Discard,
                    )
                }
            } else {
                effectChannel.trySend(EntryEffect.Closed)
            }
        }

        private fun save() {
            val current = form.value ?: return
            if (busy || !current.complete(pulseRequired)) {
                // Incomplete data: highlight the first empty required field and move focus to it.
                val empty = current.firstEmpty(pulseRequired)
                if (!busy && empty != null) {
                    setForm(current.withDraft(current.draft().focus(empty)))
                    transient.update { it.copy(missing = empty, error = null) }
                }
                return
            }
            busy = true
            transient.update { it.copy(saveFailed = false) }
            viewModelScope.launch {
                val reading = current.toReading(original)
                val result =
                    attempt(saveFailed) { saveReading(reading, requirePulse = pulseRequired) } ?: return@launch
                when (result) {
                    is SaveResult.Success -> {
                        // The reading is stored; a failed write of the last arm is not worth an error.
                        runCatching { settings.setLastArm(current.arm) }
                        savedStateHandle.remove<String>(KEY_FORM)
                        effectChannel.send(
                            EntryEffect.Saved(
                                readingId = result.id,
                                edited = original != null,
                                critical = isCritical(reading.sys, reading.dia),
                            ),
                        )
                    }

                    is SaveResult.Invalid -> {
                        transient.update { it.copy(error = result.error) }
                        busy = false
                    }
                }
            }
        }

        private fun delete() {
            val id = original?.id ?: return
            transient.update { it.copy(dialog = null) }
            if (busy) return
            busy = true
            transient.update { it.copy(saveFailed = false) }
            viewModelScope.launch {
                attempt(saveFailed) { readings.softDelete(id) } ?: return@launch
                savedStateHandle.remove<String>(KEY_FORM)
                effectChannel.send(EntryEffect.Deleted(id))
            }
        }

        /** The storage refused a write: show «Не удалось сохранить…» and unlock Save. */
        private val saveFailed: () -> Unit = {
            transient.update { it.copy(saveFailed = true) }
            busy = false
        }

        /** Manual entry needs a pulse; an imported reading edited without one may stay without (D-07). */
        private val pulseRequired: Boolean get() = original?.let { it.pulse != null } ?: true

        private fun Form.toUiState(
            th: Thresholds,
            t: Transient,
        ): EntryUiState {
            val takenAt = Instant.ofEpochMilli(takenAtMillis)
            val offset = ZoneOffset.ofTotalSeconds(offsetSeconds)
            // Same rule as localDay(): the day in the offset of the measurement.
            val day = takenAt.atOffset(offset).toLocalDate()
            val today = time.now().atZone(time.zone()).toLocalDate()
            return EntryUiState(
                isEdit = readingId != null,
                loading = false,
                draft = draft(),
                arm = arm,
                tags = tags,
                comment = comment,
                dayLabel = dayLabel(day, today),
                time = takenAt.atOffset(offset).toLocalTime(),
                date = day,
                today = today,
                liveStatus = liveStatus(th),
                error = t.error,
                missing = t.missing,
                saveFailed = t.saveFailed,
                canSave = complete(pulseRequired),
                dialog = t.dialog,
            )
        }
    }

/** Runs a storage call; on failure calls [onFailure] and returns null (review A-03). */
@Suppress("TooGenericExceptionCaught") // any storage failure is shown the same way
private suspend fun <T : Any> attempt(
    onFailure: () -> Unit,
    block: suspend () -> T,
): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        onFailure()
        null
    }

/**
 * Any dialog closed, or the pickers of the time chip: date, then time. The day and time are read in the device
 * zone; a moment in the future is refused with [Reason.FUTURE_TIME] (docs/05 §5.2).
 */
private fun pick(
    event: EntryEvent,
    t: Transient,
    form: Form?,
    time: TimeProvider,
): Pair<Transient, Form?> =
    when (event) {
        EntryEvent.TimeClicked -> {
            t.copy(dialog = EntryDialog.PickDate) to form
        }

        is EntryEvent.DateChosen -> {
            t.copy(dialog = EntryDialog.PickTime, pickedDate = event.date) to form
        }

        is EntryEvent.TimeChosen -> {
            val closed = t.copy(dialog = null, pickedDate = null)
            val day = t.pickedDate
            val at = day?.atTime(event.time)?.atZone(time.zone())?.toInstant()
            when {
                at == null || form == null -> {
                    closed to form
                }

                at.isAfter(time.now()) -> {
                    closed.copy(error = ValidationError(null, Reason.FUTURE_TIME)) to form
                }

                else -> {
                    val offset = time.zone().rules.getOffset(at)
                    closed.copy(error = null) to
                        form.copy(takenAtMillis = at.toEpochMilli(), offsetSeconds = offset.totalSeconds)
                }
            }
        }

        // DialogDismissed: whatever was open closes, a half-done pick is dropped.
        else -> {
            t.copy(dialog = null, pickedDate = null) to form
        }
    }

/** UI-only state that is not worth restoring after process death. */
private data class Transient(
    val error: ValidationError? = null,
    val dialog: EntryDialog? = null,
    /** Empty required field the user tried to skip with Save. */
    val missing: Field? = null,
    val saveFailed: Boolean = false,
    /** Chosen in the date picker, waiting for the time picker. */
    val pickedDate: LocalDate? = null,
)

@Serializable
internal data class Form(
    val sys: String = "",
    val dia: String = "",
    val pulse: String = "",
    val field: Field = Field.SYS,
    val replace: Boolean = false,
    val arm: Arm = Arm.LEFT,
    val tags: Set<Tag> = emptySet(),
    val comment: String = "",
    val takenAtMillis: Long,
    val offsetSeconds: Int,
) {
    fun draft() = Draft(sys = sys, dia = dia, pulse = pulse, field = field, replace = replace)

    fun withDraft(d: Draft) = copy(sys = d.sys, dia = d.dia, pulse = d.pulse, field = d.field, replace = d.replace)

    /** Everything the user can change except the focused field. */
    fun content() = copy(field = Field.SYS, replace = false)

    fun firstEmpty(pulseRequired: Boolean): Field? =
        when {
            sys.isEmpty() -> Field.SYS
            dia.isEmpty() -> Field.DIA
            pulse.isEmpty() && pulseRequired -> Field.PULSE
            else -> null
        }

    fun hasInput() =
        sys.isNotEmpty() || dia.isNotEmpty() || pulse.isNotEmpty() || tags.isNotEmpty() || comment.isNotEmpty()

    fun complete(pulseRequired: Boolean) =
        sys.isNotEmpty() && dia.isNotEmpty() && (pulse.isNotEmpty() || !pulseRequired)

    /** Edits keep the profile; the time is the one on the chip, changed or not (V1.1). */
    fun toReading(original: Reading?): Reading {
        val base =
            original ?: Reading(
                takenAt = Instant.ofEpochMilli(takenAtMillis),
                zoneOffset = ZoneOffset.ofTotalSeconds(offsetSeconds),
                sys = 0,
                dia = 0,
                pulse = null,
                arm = arm,
            )
        return base.copy(
            takenAt = Instant.ofEpochMilli(takenAtMillis),
            zoneOffset = ZoneOffset.ofTotalSeconds(offsetSeconds),
            sys = sys.toInt(),
            dia = dia.toInt(),
            pulse = pulse.toIntOrNull(),
            arm = arm,
            tags = tags,
            comment = comment,
        )
    }

    fun liveStatus(th: Thresholds): LiveStatus {
        if (sys.length < LIVE_STATUS_DIGITS || dia.length < LIVE_STATUS_DIGITS) return LiveStatus.Empty
        val status = classify(sys.toInt(), dia.toInt(), th)
        val hint =
            when (status) {
                Status.HIGH -> Hint.Above(th.highSys, th.highDia)
                Status.LOW -> Hint.Below(th.lowSys, th.lowDia)
                Status.NORMAL -> Hint.Within
            }
        return LiveStatus.Classified(status, hint)
    }

    companion object {
        fun of(reading: Reading) =
            Form(
                // Opening an edit behaves like tapping the focused field: the first digit replaces it.
                replace = true,
                sys = reading.sys.toString(),
                dia = reading.dia.toString(),
                pulse = reading.pulse?.toString().orEmpty(),
                arm = reading.arm,
                tags = reading.tags,
                comment = reading.comment,
                takenAtMillis = reading.takenAt.toEpochMilli(),
                offsetSeconds = reading.zoneOffset.totalSeconds,
            )
    }
}
