package ru.bpdiary.feature.reminders

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.domain.DaysSummary
import ru.bpdiary.domain.EVERY_DAY
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.ReminderScheduler
import ru.bpdiary.domain.WORKDAYS
import ru.bpdiary.domain.toggled
import java.time.DayOfWeek
import java.time.LocalTime

private const val KEY_FORM = "reminder_form"

/** What the user has typed so far; kept in [SavedStateHandle] so rotation and process death keep it (review C-04). */
@Serializable
private data class SavedForm(
    val secondOfDay: Int,
    val label: String,
    val days: List<Int>,
) {
    fun applyTo(state: ReminderEditUiState) =
        state.copy(
            time = LocalTime.ofSecondOfDay(secondOfDay.toLong()),
            label = label,
            days = days.map(DayOfWeek::of).toSet(),
        )

    companion object {
        fun of(state: ReminderEditUiState) =
            SavedForm(state.time.toSecondOfDay(), state.label, state.days.map { it.value }.sorted())
    }
}

/**
 * New (`reminderId == null`) or existing reminder (docs/05 §5.5). Saving and deleting re-plan the alarm before the
 * screen closes, so the next reminder is right the moment the list shows again.
 */
@HiltViewModel(assistedFactory = ReminderEditViewModel.Factory::class)
class ReminderEditViewModel
    @AssistedInject
    constructor(
        @Assisted private val reminderId: Long?,
        private val savedStateHandle: SavedStateHandle,
        private val reminders: ReminderRepository,
        private val scheduler: ReminderScheduler,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(reminderId: Long?): ReminderEditViewModel
        }

        private val restored: SavedForm? =
            savedStateHandle.get<String>(KEY_FORM)?.let {
                runCatching { Json.decodeFromString<SavedForm>(it) }.getOrNull()
            }

        private val initial = ReminderEditUiState(loaded = reminderId == null)

        /** The form as it was opened: closing with anything else asks first. */
        private var pristine: SavedForm = SavedForm.of(initial)

        private val form =
            MutableStateFlow(if (reminderId == null) restored?.applyTo(initial) ?: initial else initial)
        val state: StateFlow<ReminderEditUiState> = form.asStateFlow()

        private val effectChannel = Channel<ReminderEditEffect>(Channel.BUFFERED)
        val effects: Flow<ReminderEditEffect> = effectChannel.receiveAsFlow()

        private var original: Reminder? = null

        // Set before the work starts and kept on success: the screen closes, so a second tap must do nothing.
        private var busy = false

        init {
            if (reminderId != null) load(reminderId)
        }

        private fun load(id: Long) {
            viewModelScope.launch {
                val reminder = reminders.getById(id)
                if (reminder == null) {
                    // Deleted meanwhile (the notification of an old build, a double tap): nothing left to edit.
                    effectChannel.send(ReminderEditEffect.Closed)
                    return@launch
                }
                original = reminder
                val stored =
                    ReminderEditUiState(
                        loaded = true,
                        isNew = false,
                        time = reminder.time,
                        label = reminder.label,
                        days = reminder.days,
                    )
                pristine = SavedForm.of(stored)
                form.value = restored?.applyTo(stored) ?: stored
            }
        }

        /** Changes the form, keeps it in the saved state and drops the «same time» error: the user acted on it. */
        private fun edit(block: (ReminderEditUiState) -> ReminderEditUiState) {
            form.update { block(it).copy(sameTimeTaken = false) }
            savedStateHandle[KEY_FORM] = Json.encodeToString(SavedForm.of(form.value))
        }

        /** Edits of the form itself: time, name and days. */
        private fun onFormEvent(event: ReminderEditEvent) {
            when (event) {
                is ReminderEditEvent.TimeChanged -> {
                    edit { it.copy(time = event.time) }
                }

                is ReminderEditEvent.LabelChanged -> {
                    edit { it.copy(label = event.label.take(LABEL_MAX_LENGTH)) }
                }

                is ReminderEditEvent.DayToggled -> {
                    edit { it.copy(days = it.days.toggled(event.day)) }
                }

                is ReminderEditEvent.PresetSelected -> {
                    val days =
                        when (event.preset) {
                            DaysSummary.Daily -> EVERY_DAY
                            DaysSummary.Workdays -> WORKDAYS
                            is DaysSummary.Listed -> return
                        }
                    edit { it.copy(days = days) }
                }

                else -> {
                    Unit
                }
            }
        }

        fun onEvent(event: ReminderEditEvent) {
            when (event) {
                is ReminderEditEvent.TimeChanged,
                is ReminderEditEvent.LabelChanged,
                is ReminderEditEvent.DayToggled,
                is ReminderEditEvent.PresetSelected,
                -> {
                    onFormEvent(event)
                }

                is ReminderEditEvent.DoneClicked -> {
                    save(event.defaultLabel)
                }

                ReminderEditEvent.DeleteClicked -> {
                    form.update { it.copy(confirmDelete = true) }
                }

                ReminderEditEvent.DialogDismissed -> {
                    form.update { it.copy(confirmDelete = false, confirmDiscard = false) }
                }

                ReminderEditEvent.DiscardConfirmed -> {
                    form.update { it.copy(confirmDiscard = false) }
                    closeNow()
                }

                ReminderEditEvent.DeleteConfirmed -> {
                    delete()
                }

                ReminderEditEvent.CloseClicked -> {
                    val current = form.value
                    if (current.loaded && SavedForm.of(current) != pristine) {
                        form.update { it.copy(confirmDiscard = true) }
                    } else {
                        closeNow()
                    }
                }
            }
        }

        private fun closeNow() {
            savedStateHandle.remove<String>(KEY_FORM)
            viewModelScope.launch { effectChannel.send(ReminderEditEffect.Closed) }
        }

        /** Two enabled reminders at one minute: only one would ring (review B-06), so the second is refused. */
        private suspend fun clashes(
            state: ReminderEditUiState,
            enabled: Boolean,
        ): Boolean =
            enabled &&
                reminders.getAll().any { other ->
                    other.id != reminderId && other.enabled && other.time == state.time &&
                        other.days.any { it in state.days }
                }

        private fun save(defaultLabel: String) {
            val current = form.value
            if (!current.canSave || busy) return
            busy = true
            viewModelScope.launch {
                val label = current.label.trim().ifEmpty { defaultLabel }
                // An existing reminder keeps its switch; a new one starts on.
                val enabled = original?.enabled ?: true
                @Suppress("TooGenericExceptionCaught") // any storage failure is shown the same way
                try {
                    if (clashes(current, enabled)) {
                        busy = false
                        form.update { it.copy(sameTimeTaken = true) }
                        return@launch
                    }
                    reminders.save(
                        Reminder(
                            id = reminderId ?: 0,
                            time = current.time,
                            label = label,
                            days = current.days,
                            enabled = enabled,
                        ),
                    )
                    scheduler.rescheduleAll()
                    savedStateHandle.remove<String>(KEY_FORM)
                    effectChannel.send(ReminderEditEffect.Closed)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    busy = false
                    effectChannel.send(ReminderEditEffect.SaveFailed)
                }
            }
        }

        private fun delete() {
            val id = reminderId ?: return
            if (busy) return
            busy = true
            form.update { it.copy(confirmDelete = false) }
            viewModelScope.launch {
                @Suppress("TooGenericExceptionCaught") // any storage failure is shown the same way
                try {
                    reminders.delete(id)
                    scheduler.rescheduleAll()
                    effectChannel.send(ReminderEditEffect.Closed)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    busy = false
                    effectChannel.send(ReminderEditEffect.SaveFailed)
                }
            }
        }
    }
