package ru.bpdiary.feature.settings

import ru.bpdiary.core.model.ThemeMode

/** The two confirmations of «Удалить все данные» (D-26). */
enum class DeleteDialog { None, First, Second }

private const val LARGE_BOOST = 1.15f
private const val EXTRA_LARGE_BOOST = 1.3f

/**
 * The app's own text size, multiplied by the system font scale (docs/04 §4.7 point 5: `font_scale_boost`
 * 1.0 / 1.15 / 1.3).
 */
enum class TextSize(
    val boost: Float,
) {
    NORMAL(1.0f),
    LARGE(LARGE_BOOST),
    EXTRA_LARGE(EXTRA_LARGE_BOOST),
    ;

    companion object {
        /** The stored boost is a float: pick the closest step, so an odd stored value still shows a choice. */
        fun fromBoost(boost: Float): TextSize = entries.minBy { kotlin.math.abs(it.boost - boost) }
    }
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
    val dialog: DeleteDialog = DeleteDialog.None,
)

sealed interface SettingsEvent {
    data class ThemeSelected(
        val mode: ThemeMode,
    ) : SettingsEvent

    data class TextSizeSelected(
        val size: TextSize,
    ) : SettingsEvent

    data object DeleteClicked : SettingsEvent

    /** «Продолжить» in the first dialog. */
    data object DeleteFirstConfirmed : SettingsEvent

    /**
     * «Удалить навсегда» in the second dialog. A separate event, so a double tap on «Продолжить» (the buttons
     * sit in the same place) cannot pass both dialogs (review A-07).
     */
    data object DeleteForeverConfirmed : SettingsEvent

    data object DialogDismissed : SettingsEvent

    data object RemindersClicked : SettingsEvent

    data object AboutClicked : SettingsEvent

    data object BackClicked : SettingsEvent
}

sealed interface SettingsEffect {
    /** Everything is gone: the app starts again from the onboarding. */
    data object DataDeleted : SettingsEffect
}
