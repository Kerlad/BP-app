package ru.bpdiary.feature.onboarding

import ru.bpdiary.core.model.Reminder
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.ThresholdPreset
import java.time.LocalTime

/** Purpose, levels, reminders (docs/05 §5.8). The disclaimer is a screen of its own after them. */
const val ONBOARDING_STEPS = 3

data class OnboardingReminder(
    val id: Long,
    val time: LocalTime,
    val label: String,
    val enabled: Boolean,
)

internal fun Reminder.toOnboarding() = OnboardingReminder(id, time, label, enabled)

data class OnboardingUiState(
    val step: Int = 0,
    /** Null until read, so no preset is shown as chosen for a moment. */
    val thresholds: Thresholds? = null,
    val reminders: List<OnboardingReminder> = emptyList(),
    /** Only while something is switched on: a refusal never blocks the onboarding, it is just explained. */
    val notificationsMissing: Boolean = false,
    val exactAlarmsMissing: Boolean = false,
) {
    val isLastStep: Boolean get() = step == ONBOARDING_STEPS - 1
}

sealed interface OnboardingEvent {
    data object NextClicked : OnboardingEvent

    data object BackPressed : OnboardingEvent

    data class PresetSelected(
        val preset: ThresholdPreset,
    ) : OnboardingEvent

    data class ReminderToggled(
        val id: Long,
        val enabled: Boolean,
    ) : OnboardingEvent

    /** The screen came back to the front: the user may have changed a permission in the system settings. */
    data object PermissionsRefreshed : OnboardingEvent

    data object NotificationsClicked : OnboardingEvent

    data object ExactAlarmsClicked : OnboardingEvent
}

sealed interface OnboardingEffect {
    data object OpenDisclaimer : OnboardingEffect
}

data class DisclaimerUiState(
    val checked: Boolean = false,
)

sealed interface DisclaimerEvent {
    data class CheckedChanged(
        val checked: Boolean,
    ) : DisclaimerEvent

    data object ContinueClicked : DisclaimerEvent
}

sealed interface DisclaimerEffect {
    data object Accepted : DisclaimerEffect
}
