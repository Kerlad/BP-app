package ru.bpdiary.feature.levels

import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.ThresholdField
import ru.bpdiary.domain.ThresholdPreset

data class LevelsUiState(
    /** Null until read from DataStore; afterwards the edited values, shown before they are written. */
    val thresholds: Thresholds? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

sealed interface LevelsEvent {
    /** [delta] is ±`THRESHOLD_STEP`. */
    data class Step(
        val field: ThresholdField,
        val delta: Int,
    ) : LevelsEvent

    data class PresetSelected(
        val preset: ThresholdPreset,
    ) : LevelsEvent

    data class DarkThemeChanged(
        val dark: Boolean,
    ) : LevelsEvent

    /** The back button and system back: pending changes are written first. */
    data object BackClicked : LevelsEvent

    data object SettingsClicked : LevelsEvent
}

sealed interface LevelsEffect {
    data object Closed : LevelsEffect

    /** The thresholds could not be stored; the app shows «Не удалось сохранить…». */
    data object SaveFailed : LevelsEffect
}
