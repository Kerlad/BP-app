package ru.bpdiary.feature.onboarding

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import ru.bpdiary.core.ui.POST_NOTIFICATIONS_PERMISSION
import ru.bpdiary.core.ui.canRequestNotificationPermission
import ru.bpdiary.core.ui.openExactAlarmSettings
import ru.bpdiary.core.ui.openNotificationSettings

@Serializable
data object OnboardingRoute

@Serializable
data object DisclaimerRoute

/** [onFinished]: the last step was left, the disclaimer comes next. */
fun NavGraphBuilder.onboardingScreen(onFinished: () -> Unit) {
    composable<OnboardingRoute> { OnboardingDestination(onFinished) }
}

/** [onAccepted]: the current disclaimer version is stored, the app can open. */
fun NavGraphBuilder.disclaimerScreen(onAccepted: () -> Unit) {
    composable<DisclaimerRoute> { DisclaimerDestination(onAccepted) }
}

@Composable
private fun OnboardingDestination(onFinished: () -> Unit) {
    val viewModel = hiltViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val activity = LocalActivity.current
    val finished by rememberUpdatedState(onFinished)
    // After the second denial the system no longer shows its dialog (Android 13+): the button opens the settings.
    var settingsOnly by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onEvent(OnboardingEvent.PermissionsRefreshed)
            val canAskAgain = activity?.shouldShowRequestPermissionRationale(POST_NOTIFICATIONS_PERMISSION) == true
            if (!granted && !canAskAgain) settingsOnly = true
        }
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(OnboardingEvent.PermissionsRefreshed)
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    OnboardingEffect.OpenDisclaimer -> finished()
                }
            }
        }
    }
    val openSettings = settingsOnly || !canRequestNotificationPermission()
    OnboardingScreen(
        state = state,
        notificationsOpenSettings = openSettings,
        onEvent = { event ->
            when (event) {
                OnboardingEvent.NotificationsClicked -> {
                    if (openSettings) {
                        context.openNotificationSettings()
                    } else {
                        launcher.launch(
                            POST_NOTIFICATIONS_PERMISSION,
                        )
                    }
                }

                OnboardingEvent.ExactAlarmsClicked -> {
                    context.openExactAlarmSettings()
                }

                else -> {
                    viewModel.onEvent(event)
                }
            }
        },
    )
}

@Composable
private fun DisclaimerDestination(onAccepted: () -> Unit) {
    val viewModel = hiltViewModel<DisclaimerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val accepted by rememberUpdatedState(onAccepted)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    DisclaimerEffect.Accepted -> accepted()
                }
            }
        }
    }
    DisclaimerScreen(state = state, onEvent = viewModel::onEvent)
}
