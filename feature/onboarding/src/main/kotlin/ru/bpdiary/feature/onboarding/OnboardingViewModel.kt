package ru.bpdiary.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.ReminderPermissions
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.ReminderScheduler
import ru.bpdiary.domain.SettingsRepository
import ru.bpdiary.domain.ThresholdsRepository
import ru.bpdiary.domain.withPreset
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The three onboarding steps (docs/05 §5.8). Choices are written at once, like on the Levels and Reminders screens,
 * so leaving half-way loses nothing. The onboarding counts as seen when the last step is left; the disclaimer is
 * accepted separately.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val thresholds: ThresholdsRepository,
        private val reminders: ReminderRepository,
        private val scheduler: ReminderScheduler,
        private val permissions: ReminderPermissions,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val step = MutableStateFlow(0)
        private val granted = MutableStateFlow(readPermissions())

        private val effectChannel = Channel<OnboardingEffect>(Channel.BUFFERED)
        val effects: Flow<OnboardingEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<OnboardingUiState> =
            combine(step, thresholds.observe(), reminders.observeAll(), granted, ::buildState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OnboardingUiState())

        fun onEvent(event: OnboardingEvent) {
            when (event) {
                OnboardingEvent.NextClicked -> next()
                OnboardingEvent.BackPressed -> step.update { (it - 1).coerceAtLeast(0) }
                is OnboardingEvent.PresetSelected -> selectPreset(event)
                is OnboardingEvent.ReminderToggled -> toggle(event.id, event.enabled)
                OnboardingEvent.PermissionsRefreshed -> granted.value = readPermissions()
                OnboardingEvent.NotificationsClicked, OnboardingEvent.ExactAlarmsClicked -> Unit
            }
        }

        private fun next() {
            if (step.value < ONBOARDING_STEPS - 1) {
                step.update { it + 1 }
                return
            }
            viewModelScope.launch {
                settings.setOnboardingDone(true)
                effectChannel.send(OnboardingEffect.OpenDisclaimer)
            }
        }

        private fun selectPreset(event: OnboardingEvent.PresetSelected) {
            viewModelScope.launch {
                // A preset that would break `low < high` with the low levels is ignored, as on the Levels screen.
                thresholds
                    .observe()
                    .first()
                    .withPreset(event.preset)
                    ?.let { thresholds.set(it) }
            }
        }

        private fun toggle(
            id: Long,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                val reminder = reminders.getById(id) ?: return@launch
                reminders.save(reminder.copy(enabled = enabled))
                scheduler.rescheduleAll()
            }
        }

        private fun buildState(
            step: Int,
            levels: Thresholds,
            list: List<Reminder>,
            allowed: Pair<Boolean, Boolean>,
        ): OnboardingUiState {
            val anyOn = list.any { it.enabled }
            return OnboardingUiState(
                step = step,
                thresholds = levels,
                reminders = list.sortedBy { it.time }.map { it.toOnboarding() },
                notificationsMissing = anyOn && !allowed.first,
                exactAlarmsMissing = anyOn && !allowed.second,
            )
        }

        private fun readPermissions() = permissions.notificationsAllowed() to permissions.exactAlarmsAllowed()
    }
