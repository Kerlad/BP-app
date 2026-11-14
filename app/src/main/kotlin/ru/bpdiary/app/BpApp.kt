package ru.bpdiary.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpToast
import ru.bpdiary.domain.LaunchGate
import ru.bpdiary.feature.charts.ChartsRoute
import ru.bpdiary.feature.charts.chartsScreen
import ru.bpdiary.feature.diary.diaryScreen
import ru.bpdiary.feature.entry.EntryEffect
import ru.bpdiary.feature.entry.EntryRoute
import ru.bpdiary.feature.entry.entryScreen
import ru.bpdiary.feature.home.HomeRoute
import ru.bpdiary.feature.home.homeScreen
import ru.bpdiary.feature.levels.LevelsRoute
import ru.bpdiary.feature.levels.levelsScreen
import ru.bpdiary.feature.onboarding.DisclaimerRoute
import ru.bpdiary.feature.onboarding.OnboardingRoute
import ru.bpdiary.feature.onboarding.disclaimerScreen
import ru.bpdiary.feature.onboarding.onboardingScreen
import ru.bpdiary.feature.reminders.ReminderEditRoute
import ru.bpdiary.feature.reminders.RemindersRoute
import ru.bpdiary.feature.reminders.reminderEditScreen
import ru.bpdiary.feature.reminders.remindersScreen
import ru.bpdiary.feature.settings.AboutRoute
import ru.bpdiary.feature.settings.SettingsRoute
import ru.bpdiary.feature.settings.aboutScreen
import ru.bpdiary.feature.settings.settingsScreen
import ru.bpdiary.feature.transfer.ExportRoute
import ru.bpdiary.feature.transfer.ImportRoute
import ru.bpdiary.feature.transfer.exportScreen
import ru.bpdiary.feature.transfer.importScreen
import ru.bpdiary.app.core.ui.R as UiR

private const val TOAST_MS = 2_400L
private const val UNDO_MS = 5_000L

/**
 * Navigation and the app-wide messages: toast after saving, undo snackbar after deleting and the
 * critical-value hint (D-06). [openEntryNew] is the reminder notification's request for a new reading.
 */
@Composable
fun BpApp(
    shell: ShellViewModel = hiltViewModel(),
    startGate: LaunchGate = LaunchGate.MAIN,
    openEntryNew: Boolean = false,
    onOpenEntryHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    // Read once: the stored flags change while the onboarding is going on, the start screen must not.
    val startDestination: Any = remember { startGate.destination() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var toast by rememberSaveable { mutableStateOf<Int?>(null) }
    var criticalHint by rememberSaveable { mutableStateOf(false) }
    val deletedText = stringResource(R.string.snackbar_reading_deleted)
    val undoText = stringResource(R.string.snackbar_undo)

    /** «Измерение удалено / Отменить» for 5 s; the reading stays soft-deleted in the database meanwhile. */
    fun offerUndo(readingId: Long) {
        scope.launch {
            val result =
                withTimeoutOrNull(UNDO_MS) {
                    snackbar.showSnackbar(deletedText, undoText, duration = SnackbarDuration.Indefinite)
                }
            if (result == null) snackbar.currentSnackbarData?.dismiss()
            if (result == SnackbarResult.ActionPerformed) shell.undoDelete(readingId)
        }
    }

    LaunchedEffect(openEntryNew) {
        if (openEntryNew) {
            navController.openNewEntry()
            onOpenEntryHandled()
        }
    }

    AppShell(
        navController = navController,
        overlay = {
            val bottom = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
            toast?.let { text ->
                BpToast(stringResource(text), bottom.padding(16.dp))
                LaunchedEffect(text) {
                    delay(TOAST_MS)
                    toast = null
                }
            }
            SnackbarHost(snackbar, bottom)
        },
    ) {
        NavHost(navController = navController, startDestination = startDestination) {
            appGraph(
                navController = navController,
                onReadingDeleted = ::offerUndo,
                onSaveFailed = { toast = UiR.string.save_failed },
                onReadingSaved = { saved ->
                    navController.leaveEntry(saved)
                    toast = if (saved.edited) R.string.toast_changes_saved else R.string.toast_reading_saved
                    criticalHint = saved.critical
                },
            )
        }
    }
    if (criticalHint) {
        AlertDialog(
            onDismissRequest = { criticalHint = false },
            text = { Text(stringResource(R.string.critical_hint_text)) },
            confirmButton = {
                TextButton(onClick = { criticalHint = false }) { Text(stringResource(R.string.critical_hint_ok)) }
            },
        )
    }
}

/** All destinations. The messages after saving or deleting a reading are shown by [BpApp]. */
private fun NavGraphBuilder.appGraph(
    navController: NavHostController,
    onReadingDeleted: (readingId: Long) -> Unit,
    onSaveFailed: () -> Unit,
    onReadingSaved: (EntryEffect.Saved) -> Unit,
) {
    onboardingScreen(onFinished = { navController.navigate(DisclaimerRoute) })
    disclaimerScreen(onAccepted = navController::openAppAfterDisclaimer)
    homeScreen(
        onAddReading = { navController.navigate(EntryRoute()) },
        onOpenLevels = { navController.navigate(LevelsRoute) },
        onOpenCharts = { navController.navigateToTab(AppTab.CHARTS) },
        onOpenReminders = { navController.navigate(RemindersRoute) },
    )
    chartsScreen()
    remindersScreen(
        onClose = { navController.popBackStack() },
        onAdd = { navController.navigate(ReminderEditRoute()) },
        onOpen = { navController.navigate(ReminderEditRoute(it)) },
    )
    reminderEditScreen(onClose = { navController.popBackStack() }, onSaveFailed = onSaveFailed)
    levelsScreen(
        onClose = { navController.popBackStack() },
        onOpenSettings = { navController.navigate(SettingsRoute) },
        onSaveFailed = onSaveFailed,
    )
    settingsScreen(
        onClose = { navController.popBackStack() },
        onOpenReminders = { navController.navigate(RemindersRoute) },
        onOpenAbout = { navController.navigate(AboutRoute) },
        onDataDeleted = navController::restartFromOnboarding,
    )
    aboutScreen(onClose = { navController.popBackStack() })
    diaryScreen(
        onOpenReading = { navController.navigate(EntryRoute(readingId = it)) },
        onAddReading = { navController.navigate(EntryRoute()) },
        onDeleted = onReadingDeleted,
        onSaveFailed = onSaveFailed,
        onOpenExport = { navController.navigate(ExportRoute) },
        onOpenImport = { navController.navigate(ImportRoute) },
    )
    exportScreen(onClose = { navController.popBackStack() })
    importScreen(onClose = { navController.popBackStack() })
    entryScreen(
        onSaved = onReadingSaved,
        onDeleted = { id ->
            navController.popBackStack()
            onReadingDeleted(id)
        },
        onClose = { navController.popBackStack() },
    )
}

private fun LaunchGate.destination(): Any =
    when (this) {
        LaunchGate.ONBOARDING -> OnboardingRoute
        LaunchGate.DISCLAIMER -> DisclaimerRoute
        LaunchGate.MAIN -> HomeRoute
    }

/** A new reading goes to Home, an edit back to where it was opened from (D-10). */
private fun NavHostController.leaveEntry(saved: EntryEffect.Saved) {
    if (saved.edited || !popBackStack(HomeRoute, inclusive = false)) {
        popBackStack()
    }
}
