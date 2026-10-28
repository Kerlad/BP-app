package ru.bpdiary.feature.entry

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

/** `readingId == null`: new reading; otherwise edit of that reading (D-10). */
@Serializable
data class EntryRoute(
    val readingId: Long? = null,
)

fun NavGraphBuilder.entryScreen(
    onSaved: (EntryEffect.Saved) -> Unit,
    onDeleted: (readingId: Long) -> Unit,
    onClose: () -> Unit,
) {
    composable<EntryRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<EntryRoute>()
        EntryDestination(route.readingId, onSaved, onDeleted, onClose)
    }
}

@Composable
private fun EntryDestination(
    readingId: Long?,
    onSaved: (EntryEffect.Saved) -> Unit,
    onDeleted: (readingId: Long) -> Unit,
    onClose: () -> Unit,
) {
    val viewModel =
        hiltViewModel<EntryViewModel, EntryViewModel.Factory>(creationCallback = { it.create(readingId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val saved by rememberUpdatedState(onSaved)
    val deleted by rememberUpdatedState(onDeleted)
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is EntryEffect.Saved -> saved(effect)
                    is EntryEffect.Deleted -> deleted(effect.readingId)
                    EntryEffect.Closed -> close()
                }
            }
        }
    }
    EntryScreen(state = state, onEvent = viewModel::onEvent)
}
