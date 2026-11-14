package ru.bpdiary.feature.levels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.SettingsRepository
import ru.bpdiary.domain.ThresholdsRepository
import ru.bpdiary.domain.stepped
import ru.bpdiary.domain.withPreset
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** Several quick taps on «+» end in one DataStore write (docs/05 §5.6). */
internal const val WRITE_DEBOUNCE_MS = 300L

/**
 * Levels (docs/05 §5.6). The screen shows the edited thresholds at once; DataStore is written 300 ms after the
 * last change, and right away on back so nothing is lost. Steps that would break a rule are ignored here too.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class LevelsViewModel
    @Inject
    constructor(
        private val thresholds: ThresholdsRepository,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val edited = MutableStateFlow<Thresholds?>(null)
        private var written: Thresholds? = null

        private val effectChannel = Channel<LevelsEffect>(Channel.BUFFERED)
        val effects: Flow<LevelsEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<LevelsUiState> =
            combine(edited, settings.themeMode, ::LevelsUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LevelsUiState())

        init {
            viewModelScope.launch {
                val stored = thresholds.observe().first()
                written = stored
                edited.value = stored
                // A failed background write is retried by the next change and reported on Back.
                edited.filterNotNull().debounce(WRITE_DEBOUNCE_MS).collect { tryWrite(it) }
            }
        }

        fun onEvent(event: LevelsEvent) {
            when (event) {
                is LevelsEvent.Step -> {
                    update { it.stepped(event.field, event.delta) }
                }

                is LevelsEvent.PresetSelected -> {
                    update { it.withPreset(event.preset) }
                }

                is LevelsEvent.DarkThemeChanged -> {
                    viewModelScope.launch { settings.setThemeMode(if (event.dark) ThemeMode.DARK else ThemeMode.LIGHT) }
                }

                LevelsEvent.BackClicked -> {
                    viewModelScope.launch {
                        val ok = edited.value?.let { tryWrite(it) } ?: true
                        effectChannel.send(if (ok) LevelsEffect.Closed else LevelsEffect.SaveFailed)
                    }
                }

                LevelsEvent.SettingsClicked -> {
                    Unit
                }
            }
        }

        /** A change that [change] rejects (null) leaves the thresholds as they are. */
        private fun update(change: (Thresholds) -> Thresholds?) {
            val current = edited.value ?: return
            change(current)?.let { edited.value = it }
        }

        /** False when the storage refused the write (review A-03): the screen stays as it is. */
        @Suppress("TooGenericExceptionCaught") // any storage failure is shown the same way
        private suspend fun tryWrite(value: Thresholds): Boolean {
            if (value == written) return true
            return try {
                thresholds.set(value)
                written = value
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }
