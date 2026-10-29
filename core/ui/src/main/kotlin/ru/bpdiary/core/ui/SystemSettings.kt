package ru.bpdiary.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast

/** The literal: the constant exists from Android 13 on, and the permission is only requested there. */
const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

/** True where the system shows its own dialog for [POST_NOTIFICATIONS_PERMISSION]. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
fun canRequestNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Notification settings of this app. */
fun Context.openNotificationSettings() {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startSafely(intent)
}

/** The exact-alarm switch of this app (Android 12+, D-12); the details of the app on older versions. */
fun Context.openExactAlarmSettings() {
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
