package ru.bpdiary.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.domain.DaysSummary
import ru.bpdiary.domain.EVERY_DAY
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.ReminderScheduler
import ru.bpdiary.domain.WORKDAYS
import ru.bpdiary.domain.toggled

/**
 * New (`reminderId == null`) or existing reminder (docs/05 §5.5). Saving and deleting re-plan the alarm before the
 * screen closes, so the next reminder is right the moment the list shows again.
 */
@HiltViewModel(assistedFactory = ReminderEditViewModel.Factory::class)
class ReminderEditViewModel
    @AssistedInject
    constructor(
        @Assisted private val reminderId: Long?,
        private val reminders: ReminderRepository,
        private val scheduler: ReminderScheduler,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(reminderId: Long?): ReminderEditViewModel
        }

        private val form = MutableStateFlow(ReminderEditUiState(loaded = reminderId == null))
        val state: StateFlow<ReminderEditUiState> = form.asStateFlow()

        private val effectChannel = Channel<ReminderEditEffect>(Channel.BUFFERED)
        val effects: Flow<ReminderEditEffect> = effectChannel.receiveAsFlow()

        private var original: Reminder? = null

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
                form.value =
                    ReminderEditUiState(
                        loaded = true,
                        isNew = false,
                        time = reminder.time,
                        label = reminder.label,
                        days = reminder.days,
                    )
            }
        }

        fun onEvent(event: ReminderEditEvent) {
            when (event) {
                is ReminderEditEvent.TimeChanged -> {
                    form.update { it.copy(time = event.time) }
                }

                is ReminderEditEvent.LabelChanged -> {
                    form.update { it.copy(label = event.label.take(LABEL_MAX_LENGTH)) }
                }

                is ReminderEditEvent.DayToggled -> {
                    form.update { it.copy(days = it.days.toggled(event.day)) }
                }

                is ReminderEditEvent.PresetSelected -> {
                    val days =
                        when (event.preset) {
                            DaysSummary.Daily -> EVERY_DAY
                            DaysSummary.Workdays -> WORKDAYS
                            is DaysSummary.Listed -> return
                        }
                    form.update { it.copy(days = days) }
                }

                is ReminderEditEvent.DoneClicked -> {
                    save(event.defaultLabel)
                }

                ReminderEditEvent.DeleteClicked -> {
                    form.update { it.copy(confirmDelete = true) }
                }

                ReminderEditEvent.DialogDismissed -> {
                    form.update { it.copy(confirmDelete = false) }
                }

                ReminderEditEvent.DeleteConfirmed -> {
                    delete()
                }

                ReminderEditEvent.CloseClicked -> {
                    viewModelScope.launch { effectChannel.send(ReminderEditEffect.Closed) }
                }
            }
        }

        private fun save(defaultLabel: String) {
            val current = form.value
            if (!current.canSave) return
            viewModelScope.launch {
                val label = current.label.trim().ifEmpty { defaultLabel }
                // An existing reminder keeps its switch; a new one starts on.
                val enabled = original?.enabled ?: true
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
                effectChannel.send(ReminderEditEffect.Closed)
            }
        }

        private fun delete() {
            val id = reminderId ?: return
            form.update { it.copy(confirmDelete = false) }
            viewModelScope.launch {
                reminders.delete(id)
                scheduler.rescheduleAll()
                effectChannel.send(ReminderEditEffect.Closed)
            }
        }
    }
