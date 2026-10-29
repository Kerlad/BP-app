package ru.bpdiary.feature.reminders

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The literal: the constant exists from Android 13 on, and the permission is only requested there. */
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

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
        if (settingsOnly || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
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
                        launcher.launch(POST_NOTIFICATIONS)
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
private fun Activity.canAskAgain(): Boolean = shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)

private fun Context.openNotificationSettings() {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startSafely(intent)
}

/** The banner only shows when exact alarms are denied, which exists on Android 12+ (D-12). */
private fun Context.openExactAlarmSettings() {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", packageName, null))
        } else {
            details
        }
    if (!startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
        startSafely(details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun Context.startSafely(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
