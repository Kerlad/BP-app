package ru.bpdiary.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import ru.bpdiary.core.ui.DayChangeEffect

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
    DayChangeEffect(viewModel::refresh)
    HomeScreen(state, onEvent = { event ->
        when (event) {
            HomeEvent.AddClicked -> onAddReading()
            HomeEvent.LevelsClicked -> onOpenLevels()
            HomeEvent.WeekClicked -> onOpenCharts()
            HomeEvent.ReminderClicked -> onOpenReminders()
        }
    })
}
