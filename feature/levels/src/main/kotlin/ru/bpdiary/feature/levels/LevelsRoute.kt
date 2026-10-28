package ru.bpdiary.feature.levels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LevelsRoute

fun NavGraphBuilder.levelsScreen(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable<LevelsRoute> { LevelsDestination(onClose, onOpenSettings) }
}

@Composable
private fun LevelsDestination(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = hiltViewModel<LevelsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    LevelsEffect.Closed -> close()
                }
            }
        }
    }
    LevelsScreen(state, onEvent = { event ->
        if (event == LevelsEvent.SettingsClicked) onOpenSettings() else viewModel.onEvent(event)
    })
}
