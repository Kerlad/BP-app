package ru.bpdiary.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.domain.DeleteAllDataUseCase
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.ReminderScheduler
import ru.bpdiary.domain.SettingsRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** Theme and «Удалить все данные» behind two dialogs (docs/05 §5.7, D-26). */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        readings: ReadingRepository,
        reminders: ReminderRepository,
        private val settings: SettingsRepository,
        scheduler: ReminderScheduler,
        private val time: TimeProvider,
    ) : ViewModel() {
        private val deleteAll = DeleteAllDataUseCase(readings, reminders, settings, scheduler)
        private val dialog = MutableStateFlow(DeleteDialog.None)

        /** When the second dialog appeared; taps within [SECOND_DIALOG_GUARD] of it are ignored (review A-07). */
        private var secondShownAt: Instant = Instant.MIN

        private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
        val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<SettingsUiState> =
            combine(settings.themeMode, dialog, ::SettingsUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

        fun onEvent(event: SettingsEvent) {
            when (event) {
                is SettingsEvent.ThemeSelected -> viewModelScope.launch { settings.setThemeMode(event.mode) }
                SettingsEvent.DeleteClicked -> dialog.value = DeleteDialog.First
                SettingsEvent.DeleteFirstConfirmed -> showSecond()
                SettingsEvent.DeleteForeverConfirmed -> deleteForever()
                SettingsEvent.DialogDismissed -> dialog.value = DeleteDialog.None
                SettingsEvent.RemindersClicked, SettingsEvent.AboutClicked, SettingsEvent.BackClicked -> Unit
            }
        }

        private fun showSecond() {
            if (dialog.value != DeleteDialog.First) return
            secondShownAt = time.now()
            dialog.value = DeleteDialog.Second
        }

        private fun deleteForever() {
            if (dialog.value != DeleteDialog.Second) return
            if (Duration.between(secondShownAt, time.now()) < SECOND_DIALOG_GUARD) return
            dialog.value = DeleteDialog.None
            viewModelScope.launch {
                deleteAll()
                effectChannel.send(SettingsEffect.DataDeleted)
            }
        }

        private companion object {
            val SECOND_DIALOG_GUARD: Duration = Duration.ofSeconds(1)
        }
    }
