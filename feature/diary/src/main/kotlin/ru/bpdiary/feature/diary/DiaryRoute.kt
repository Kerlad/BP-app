package ru.bpdiary.feature.diary

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.bpdiary.core.ui.DayChangeEffect

@Serializable
data object DiaryRoute

/** [onDeleted] shows the app-wide «Измерение удалено / Отменить» snackbar, [onSaveFailed] the storage error. */
@Suppress("LongParameterList") // navigation wiring, like the @Composable destination it calls
fun NavGraphBuilder.diaryScreen(
    onOpenReading: (readingId: Long) -> Unit,
    onAddReading: () -> Unit,
    onDeleted: (readingId: Long) -> Unit,
    onSaveFailed: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenImport: () -> Unit,
) {
    composable<DiaryRoute> {
        DiaryDestination(onOpenReading, onAddReading, onDeleted, onSaveFailed, onOpenExport, onOpenImport)
    }
}

@Composable
private fun DiaryDestination(
    onOpenReading: (readingId: Long) -> Unit,
    onAddReading: () -> Unit,
    onDeleted: (readingId: Long) -> Unit,
    onSaveFailed: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val viewModel = hiltViewModel<DiaryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val texts = rememberDiaryTexts()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val deleted by rememberUpdatedState(onDeleted)
    val failed by rememberUpdatedState(onSaveFailed)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is DiaryEffect.Deleted -> deleted(effect.id)
                    DiaryEffect.SaveFailed -> failed()
                }
            }
        }
    }
    DayChangeEffect(viewModel::refresh)
    DiaryScreen(state, items, onEvent = { event ->
        when (event) {
            is DiaryEvent.RowClicked -> {
                onOpenReading(event.id)
            }

            DiaryEvent.AddClicked -> {
                onAddReading()
            }

            DiaryEvent.ExportClicked -> {
                onOpenExport()
            }

            DiaryEvent.ImportClicked -> {
                onOpenImport()
            }

            is DiaryEvent.CopyClicked -> {
                val text = texts.copy(event.row)
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text))) }
            }

            else -> {
                viewModel.onEvent(event)
            }
        }
    })
}
