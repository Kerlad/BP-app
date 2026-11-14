package ru.bpdiary.feature.reminders

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
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/** `reminderId == null`: a new reminder; otherwise the edit of that reminder. */
@Serializable
data class ReminderEditRoute(
    val reminderId: Long? = null,
)

fun NavGraphBuilder.reminderEditScreen(
    onClose: () -> Unit,
    onSaveFailed: () -> Unit,
) {
    composable<ReminderEditRoute> { backStackEntry ->
        ReminderEditDestination(backStackEntry.toRoute<ReminderEditRoute>().reminderId, onClose, onSaveFailed)
    }
}

@Composable
private fun ReminderEditDestination(
    reminderId: Long?,
    onClose: () -> Unit,
    onSaveFailed: () -> Unit,
) {
    val viewModel =
        hiltViewModel<ReminderEditViewModel, ReminderEditViewModel.Factory>(
            creationCallback = { it.create(reminderId) },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val close by rememberUpdatedState(onClose)
    val failed by rememberUpdatedState(onSaveFailed)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    ReminderEditEffect.Closed -> close()
                    ReminderEditEffect.SaveFailed -> failed()
                }
            }
        }
    }
    ReminderEditScreen(state = state, onEvent = viewModel::onEvent)
}
