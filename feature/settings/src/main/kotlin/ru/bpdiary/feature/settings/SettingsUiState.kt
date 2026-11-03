package ru.bpdiary.feature.settings

import ru.bpdiary.core.model.ThemeMode

/** The two confirmations of «Удалить все данные» (D-26). */
enum class DeleteDialog { None, First, Second }

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dialog: DeleteDialog = DeleteDialog.None,
)

sealed interface SettingsEvent {
    data class ThemeSelected(
        val mode: ThemeMode,
    ) : SettingsEvent

    data object DeleteClicked : SettingsEvent

    /** «Продолжить» in the first dialog, «Удалить навсегда» in the second. */
    data object DeleteConfirmed : SettingsEvent

    data object DialogDismissed : SettingsEvent

    data object RemindersClicked : SettingsEvent

    data object AboutClicked : SettingsEvent

    data object BackClicked : SettingsEvent
}

sealed interface SettingsEffect {
    /** Everything is gone: the app starts again from the onboarding. */
    data object DataDeleted : SettingsEffect
}
