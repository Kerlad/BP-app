package ru.bpdiary.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

/**
 * [onDataDeleted]: «Удалить все данные» is done, the app starts again from the onboarding.
 */
fun NavGraphBuilder.settingsScreen(
    onClose: () -> Unit,
    onOpenAbout: () -> Unit,
    onDataDeleted: () -> Unit,
) {
    composable<SettingsRoute> { SettingsDestination(onClose, onOpenAbout, onDataDeleted) }
}

fun NavGraphBuilder.aboutScreen(onClose: () -> Unit) {
    composable<AboutRoute> { AboutDestination(onClose) }
}

@Composable
private fun SettingsDestination(
    onClose: () -> Unit,
    onOpenAbout: () -> Unit,
    onDataDeleted: () -> Unit,
) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val deleted by rememberUpdatedState(onDataDeleted)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SettingsEffect.DataDeleted -> deleted()
                }
            }
        }
    }
    SettingsScreen(state, onEvent = { event ->
        when (event) {
            SettingsEvent.BackClicked -> onClose()
            SettingsEvent.AboutClicked -> onOpenAbout()
            else -> viewModel.onEvent(event)
        }
    })
}

@Composable
private fun AboutDestination(onClose: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) { context.versionName() }
    AboutScreen(version = version, onBack = onClose)
}

private fun android.content.Context.versionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
