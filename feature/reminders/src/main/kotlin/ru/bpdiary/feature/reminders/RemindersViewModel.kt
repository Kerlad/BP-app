package ru.bpdiary.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.bpdiary.domain.ReminderPermissions
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.ReminderScheduler
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Reminders list (docs/05 §5.5). A switch saves at once and re-plans the alarm; opening, adding and the permission
 * buttons need the navigation or the activity, so the destination handles those events.
 */
@HiltViewModel
class RemindersViewModel
    @Inject
    constructor(
        private val reminders: ReminderRepository,
        private val scheduler: ReminderScheduler,
        private val permissions: ReminderPermissions,
    ) : ViewModel() {
        private val granted = MutableStateFlow(readPermissions())

        val state: StateFlow<RemindersUiState> =
            combine(reminders.observeAll(), granted) { list, (notifications, exact) ->
                RemindersUiState(
                    items = list.sortedBy { it.time }.map { it.toItem() },
                    notificationsMissing = !notifications,
                    exactAlarmsMissing = !exact,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RemindersUiState())

        fun onEvent(event: RemindersEvent) {
            when (event) {
                is RemindersEvent.Toggled -> toggle(event.id, event.enabled)
                RemindersEvent.PermissionsRefreshed -> granted.value = readPermissions()
                else -> Unit
            }
        }

        private fun toggle(
            id: Long,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                val reminder = reminders.getById(id) ?: return@launch
                reminders.save(reminder.copy(enabled = enabled))
                scheduler.rescheduleAll()
            }
        }

        private fun readPermissions() = permissions.notificationsAllowed() to permissions.exactAlarmsAllowed()
    }
