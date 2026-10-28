package ru.bpdiary.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.bpdiary.domain.ReadingRepository
import javax.inject.Inject

/** App-wide actions that outlive a single screen, such as undoing a delete from the snackbar. */
@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        private val readings: ReadingRepository,
    ) : ViewModel() {
        fun undoDelete(readingId: Long) {
            viewModelScope.launch { readings.restore(readingId) }
        }
    }
