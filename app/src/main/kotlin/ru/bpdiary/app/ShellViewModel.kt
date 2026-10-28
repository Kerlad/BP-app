package ru.bpdiary.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.SettingsRepository
import javax.inject.Inject

/** App-wide actions that outlive a single screen, such as undoing a delete from the snackbar. */
@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        private val readings: ReadingRepository,
        settings: SettingsRepository,
    ) : ViewModel() {
        /** Light or dark theme chosen on the Levels screen; the full theme selector comes in Step 10.3. */
        val themeMode: StateFlow<ThemeMode> =
            settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

        fun undoDelete(readingId: Long) {
            viewModelScope.launch { readings.restore(readingId) }
        }
    }
