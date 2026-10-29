package ru.bpdiary.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.onboarding.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.BpPrimaryButton
import ru.bpdiary.core.ui.BpSwitch
import ru.bpdiary.core.ui.rememberDateFormatter
import ru.bpdiary.domain.ThresholdPreset
import ru.bpdiary.domain.matches
import ru.bpdiary.domain.withPreset

private const val DISABLED_ALPHA = 0.4f
private const val SWITCHED_OFF_ALPHA = 0.5f

/**
 * The three onboarding steps. Stateless; [notificationsOpenSettings] turns the notification button from a request
 * into a way to the app settings once the system no longer shows its dialog.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
    notificationsOpenSettings: Boolean = false,
) {
    BackHandler(enabled = state.step > 0) { onEvent(OnboardingEvent.BackPressed) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BpTheme.colors.bg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
    ) {
        StepDots(state.step, Modifier.padding(top = 16.dp, bottom = 12.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (state.step) {
                0 -> PurposeStep()
                1 -> LevelsStep(state.thresholds, onEvent)
                else -> RemindersStep(state, notificationsOpenSettings, onEvent)
            }
        }
        BpPrimaryButton(
            text = stringResource(R.string.onboarding_next),
            onClick = { onEvent(OnboardingEvent.NextClicked) },
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun StepDots(
    step: Int,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    val description = stringResource(R.string.onboarding_step_description, step + 1, ONBOARDING_STEPS)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(ONBOARDING_STEPS) { index ->
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (index == step) colors.ac else colors.ln))
        }
    }
}

@Composable
private fun StepTitle(
    title: String,
    body: String,
) {
    val colors = BpTheme.colors
    Text(
        text = title,
        style = BpTheme.typography.screenTitle,
        color = colors.ink,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    Text(body, style = BpTheme.typography.body, color = colors.mut)
}

@Composable
private fun PurposeStep() {
    val colors = BpTheme.colors
    Box(
        modifier =
            Modifier
                .padding(top = 24.dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.acs),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = BpIcon.FavoriteFilled.painter(),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.hi),
            modifier = Modifier.size(36.dp),
        )
    }
    StepTitle(stringResource(R.string.onboarding_purpose_title), stringResource(R.string.onboarding_purpose_body))
}

@Composable
private fun LevelsStep(
    thresholds: Thresholds?,
    onEvent: (OnboardingEvent) -> Unit,
) {
    StepTitle(stringResource(R.string.onboarding_levels_title), stringResource(R.string.onboarding_levels_intro))
    Text(
        text = stringResource(R.string.onboarding_levels_later),
        style = BpTheme.typography.secondary,
        color = BpTheme.colors.mut,
    )
    if (thresholds == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThresholdPreset.entries.forEach { preset ->
            PresetCard(
                preset = preset,
                selected = thresholds.matches(preset),
                enabled = thresholds.withPreset(preset) != null,
                onClick = { onEvent(OnboardingEvent.PresetSelected(preset)) },
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: ThresholdPreset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    val shape = RoundedCornerShape(BpTheme.shapes.card)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(shape)
                .background(if (selected) colors.acs else colors.sf)
                .border(BorderStroke(1.5.dp, if (selected) colors.ac else Color.Transparent), shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_preset_value, preset.highSys, preset.highDia),
            style = BpTheme.typography.numRow,
            color = colors.ink,
        )
        Text(
            text =
                stringResource(
                    if (preset ==
                        ThresholdPreset.HOME
                    ) {
                        R.string.onboarding_preset_home
                    } else {
                        R.string.onboarding_preset_clinic
                    },
                ),
            style = BpTheme.typography.secondary,
            color = colors.mut,
        )
    }
}

@Composable
private fun RemindersStep(
    state: OnboardingUiState,
    notificationsOpenSettings: Boolean,
    onEvent: (OnboardingEvent) -> Unit,
) {
    val time = rememberDateFormatter()
    StepTitle(stringResource(R.string.onboarding_reminders_title), stringResource(R.string.onboarding_reminders_body))
    state.reminders.forEach { reminder ->
        ReminderRow(reminder, time.time(reminder.time)) { onEvent(OnboardingEvent.ReminderToggled(reminder.id, it)) }
    }
    if (state.notificationsMissing) {
        val action =
            if (notificationsOpenSettings) {
                R.string.onboarding_open_settings
            } else {
                R.string.onboarding_notifications_allow
            }
        Hint(
            text = stringResource(R.string.onboarding_notifications_hint),
            action = stringResource(action),
            onClick = { onEvent(OnboardingEvent.NotificationsClicked) },
        )
    }
    if (state.exactAlarmsMissing) {
        Hint(
            text = stringResource(R.string.onboarding_exact_hint),
            action = stringResource(R.string.onboarding_exact_action),
            onClick = { onEvent(OnboardingEvent.ExactAlarmsClicked) },
        )
    }
}

@Composable
private fun ReminderRow(
    reminder: OnboardingReminder,
    time: String,
    onChange: (Boolean) -> Unit,
) {
    val colors = BpTheme.colors
    BpCard(padding = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).alpha(if (reminder.enabled) 1f else SWITCHED_OFF_ALPHA)) {
                Text(time, style = BpTheme.typography.numReminder, color = colors.ink)
                Text(reminder.label, style = BpTheme.typography.reminderTitle, color = colors.ink)
            }
            BpSwitch(
                checked = reminder.enabled,
                onCheckedChange = onChange,
                contentDescription = stringResource(R.string.onboarding_switch_description, reminder.label, time),
            )
        }
    }
}

/** A missing permission is explained next to the switches, with a button; it never stops the next step. */
@Composable
private fun Hint(
    text: String,
    action: String,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BpTheme.shapes.card))
                .background(colors.acs)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = BpTheme.typography.body, color = colors.ink)
        Text(
            text = action,
            style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
            color = colors.aci,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(colors.ac)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
