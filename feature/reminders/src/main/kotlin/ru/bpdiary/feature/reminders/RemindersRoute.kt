package ru.bpdiary.feature.reminders

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import ru.bpdiary.core.ui.POST_NOTIFICATIONS_PERMISSION
import ru.bpdiary.core.ui.canRequestNotificationPermission
import ru.bpdiary.core.ui.openExactAlarmSettings
import ru.bpdiary.core.ui.openNotificationSettings

@Serializable
data object RemindersRoute

fun NavGraphBuilder.remindersScreen(
    onAdd: () -> Unit,
    onOpen: (reminderId: Long) -> Unit,
) {
    composable<RemindersRoute> { RemindersDestination(onAdd, onOpen) }
}

@Composable
private fun RemindersDestination(
    onAdd: () -> Unit,
    onOpen: (reminderId: Long) -> Unit,
) {
    val viewModel = hiltViewModel<RemindersViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    // After the second denial the system no longer shows its dialog (Android 13+): the button opens the settings.
    var settingsOnly by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onEvent(RemindersEvent.PermissionsRefreshed)
            if (!granted && activity?.canAskAgain() == false) settingsOnly = true
        }
    // The user may have changed a permission in the system settings meanwhile.
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(RemindersEvent.PermissionsRefreshed)
        onPauseOrDispose {}
    }
    val ask =
        if (settingsOnly || !canRequestNotificationPermission()) {
            NotificationAsk.OPEN_SETTINGS
        } else {
            NotificationAsk.REQUEST
        }
    RemindersScreen(
        state = state,
        notificationAsk = ask,
        onEvent = { event ->
            when (event) {
                RemindersEvent.AddClicked -> {
                    onAdd()
                }

                is RemindersEvent.ItemClicked -> {
                    onOpen(event.id)
                }

                RemindersEvent.NotificationsBannerClicked -> {
                    if (ask == NotificationAsk.REQUEST) {
                        launcher.launch(POST_NOTIFICATIONS_PERMISSION)
                    } else {
                        context.openNotificationSettings()
                    }
                }

                RemindersEvent.ExactAlarmsBannerClicked -> {
                    context.openExactAlarmSettings()
                }

                else -> {
                    viewModel.onEvent(event)
                }
            }
        },
    )
}

/** False once the system will not show its permission dialog any more. Only meaningful after a denial. */
private fun Activity.canAskAgain(): Boolean = shouldShowRequestPermissionRationale(POST_NOTIFICATIONS_PERMISSION)
