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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.bpdiary.domain.DeleteAllDataUseCase
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.ReminderScheduler
import ru.bpdiary.domain.SettingsRepository
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
    ) : ViewModel() {
        private val deleteAll = DeleteAllDataUseCase(readings, reminders, settings, scheduler)
        private val dialog = MutableStateFlow(DeleteDialog.None)

        private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
        val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<SettingsUiState> =
            combine(settings.themeMode, dialog, ::SettingsUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

        fun onEvent(event: SettingsEvent) {
            when (event) {
                is SettingsEvent.ThemeSelected -> viewModelScope.launch { settings.setThemeMode(event.mode) }
                SettingsEvent.DeleteClicked -> dialog.value = DeleteDialog.First
                SettingsEvent.DeleteConfirmed -> confirm()
                SettingsEvent.DialogDismissed -> dialog.value = DeleteDialog.None
                SettingsEvent.AboutClicked, SettingsEvent.BackClicked -> Unit
            }
        }

        private fun confirm() {
            when (dialog.value) {
                DeleteDialog.None -> {
                    Unit
                }

                DeleteDialog.First -> {
                    dialog.update { DeleteDialog.Second }
                }

                DeleteDialog.Second -> {
                    dialog.value = DeleteDialog.None
                    viewModelScope.launch {
                        deleteAll()
                        effectChannel.send(SettingsEffect.DataDeleted)
                    }
                }
            }
        }
    }
