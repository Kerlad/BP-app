package ru.bpdiary.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(
    onAddReading: () -> Unit,
    onOpenLevels: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenReminders: () -> Unit,
) {
    composable<HomeRoute> {
        HomeDestination(onAddReading, onOpenLevels, onOpenCharts, onOpenReminders)
    }
}

@Composable
private fun HomeDestination(
    onAddReading: () -> Unit,
    onOpenLevels: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenReminders: () -> Unit,
) {
    val viewModel = hiltViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    DateChangeEffect(viewModel::refresh)
    HomeScreen(state, onEvent = { event ->
        when (event) {
            HomeEvent.AddClicked -> onAddReading()
            HomeEvent.LevelsClicked -> onOpenLevels()
            HomeEvent.WeekClicked -> onOpenCharts()
            HomeEvent.ReminderClicked -> onOpenReminders()
        }
    })
}

/** Midnight, a manual clock change or a zone change while the screen is visible (docs/05 §5.1). */
@Composable
private fun DateChangeEffect(onChange: () -> Unit) {
    val context = LocalContext.current
    val change by rememberUpdatedState(onChange)
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = change()
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
}
