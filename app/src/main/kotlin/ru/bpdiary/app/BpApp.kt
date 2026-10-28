package ru.bpdiary.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpToast
import ru.bpdiary.feature.entry.EntryEffect
import ru.bpdiary.feature.entry.EntryRoute
import ru.bpdiary.feature.entry.entryScreen

private const val TOAST_MS = 2_400L
private const val UNDO_MS = 5_000L

/** Temporary start destination; Step 5 replaces it with the real Home screen. */
@Serializable
data object HomeRoute

/**
 * Navigation and the app-wide messages: toast after saving, undo snackbar after deleting and the
 * critical-value hint (D-06). The full shell with the bottom navigation comes in Step 10.1.
 */
@Composable
fun BpApp(shell: ShellViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var toast by rememberSaveable { mutableStateOf<Int?>(null) }
    var criticalHint by rememberSaveable { mutableStateOf(false) }
    val deletedText = stringResource(R.string.snackbar_reading_deleted)
    val undoText = stringResource(R.string.snackbar_undo)

    Box(Modifier.fillMaxSize().background(BpTheme.colors.bg)) {
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> { HomePlaceholder { navController.navigate(EntryRoute()) } }
            entryScreen(
                onSaved = { saved ->
                    navController.leaveEntry(saved)
                    toast = if (saved.edited) R.string.toast_changes_saved else R.string.toast_reading_saved
                    criticalHint = saved.critical
                },
                onDeleted = { id ->
                    navController.popBackStack()
                    scope.launch {
                        val result =
                            withTimeoutOrNull(UNDO_MS) {
                                snackbar.showSnackbar(deletedText, undoText, duration = SnackbarDuration.Indefinite)
                            }
                        if (result == null) snackbar.currentSnackbarData?.dismiss()
                        if (result == SnackbarResult.ActionPerformed) shell.undoDelete(id)
                    }
                },
                onClose = { navController.popBackStack() },
            )
        }
        val bottom = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
        toast?.let { text ->
            BpToast(stringResource(text), bottom.padding(16.dp))
            LaunchedEffect(text) {
                delay(TOAST_MS)
                toast = null
            }
        }
        SnackbarHost(snackbar, bottom)
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

/** A new reading goes to Home, an edit back to where it was opened from (D-10). */
private fun NavHostController.leaveEntry(saved: EntryEffect.Saved) {
    if (saved.edited || !popBackStack(HomeRoute, inclusive = false)) {
        popBackStack()
    }
}

@Composable
private fun HomePlaceholder(onAdd: () -> Unit) {
    val colors = BpTheme.colors
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 62.dp)
                    .clip(RoundedCornerShape(BpTheme.shapes.card))
                    .background(colors.ac)
                    .clickable(role = Role.Button, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.home_add_reading), style = BpTheme.typography.button, color = colors.aci)
        }
    }
}
