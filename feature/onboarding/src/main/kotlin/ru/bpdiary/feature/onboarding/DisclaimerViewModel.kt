package ru.bpdiary.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.bpdiary.core.model.DISCLAIMER_VERSION
import ru.bpdiary.domain.SettingsRepository
import javax.inject.Inject

/** The disclaimer with its checkbox (docs/09 §9.6, D-18): without the tick the app does not go on. */
@HiltViewModel
class DisclaimerViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(DisclaimerUiState())
        val state: StateFlow<DisclaimerUiState> = form.asStateFlow()

        private val effectChannel = Channel<DisclaimerEffect>(Channel.BUFFERED)
        val effects: Flow<DisclaimerEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: DisclaimerEvent) {
            when (event) {
                is DisclaimerEvent.CheckedChanged -> form.update { it.copy(checked = event.checked) }
                DisclaimerEvent.ContinueClicked -> accept()
            }
        }

        private fun accept() {
            if (!form.value.checked) return
            viewModelScope.launch {
                settings.setDisclaimerAck(DISCLAIMER_VERSION)
                effectChannel.send(DisclaimerEffect.Accepted)
            }
        }
    }
