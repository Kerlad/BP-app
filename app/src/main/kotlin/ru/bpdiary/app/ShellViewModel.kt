package ru.bpdiary.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.SettingsRepository
import javax.inject.Inject

/** What the first frame needs: the theme and the font boost (docs/02 §2.5). */
data class Appearance(
    val themeMode: ThemeMode,
    val fontScaleBoost: Float,
)

/** App-wide actions that outlive a single screen, such as undoing a delete from the snackbar. */
@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        private val readings: ReadingRepository,
        settings: SettingsRepository,
    ) : ViewModel() {
        /** Null until the settings have been read: the splash screen stays up meanwhile, so the theme never flashes. */
        val appearance: StateFlow<Appearance?> =
            combine(settings.themeMode, settings.fontScaleBoost, ::Appearance)
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        fun undoDelete(readingId: Long) {
            viewModelScope.launch { readings.restore(readingId) }
        }
    }
