package ru.bpdiary.feature.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import ru.bpdiary.core.ui.DayChangeEffect

@Serializable
data object ChartsRoute

fun NavGraphBuilder.chartsScreen() {
    composable<ChartsRoute> { ChartsDestination() }
}

@Composable
private fun ChartsDestination() {
    val viewModel = hiltViewModel<ChartsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DayChangeEffect(viewModel::refresh)
    ChartsScreen(state, onEvent = viewModel::onEvent)
}
