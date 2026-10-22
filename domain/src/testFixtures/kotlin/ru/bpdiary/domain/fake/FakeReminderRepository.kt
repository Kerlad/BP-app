package ru.bpdiary.domain.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.domain.ReminderRepository
import java.time.DayOfWeek
import java.time.LocalTime

class FakeReminderRepository(
    initial: List<Reminder> = defaults(),
) : ReminderRepository {
    private val state = MutableStateFlow(initial)
    private var lastId = initial.maxOfOrNull { it.id } ?: 0L

    override fun observeAll(): Flow<List<Reminder>> = state.map(::sorted)

    override suspend fun getAll(): List<Reminder> = sorted(state.value)

    override suspend fun enabled(): List<Reminder> = sorted(state.value).filter { it.enabled }

    override suspend fun getById(id: Long): Reminder? = state.value.firstOrNull { it.id == id }

    override suspend fun save(reminder: Reminder): Long {
        val id = if (reminder.id == 0L) ++lastId else reminder.id
        state.value = state.value.filterNot { it.id == id } + reminder.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun resetToDefaults() {
        lastId = 0
        state.value = emptyList()
        defaults().forEach { save(it) }
    }

    private fun sorted(list: List<Reminder>) = list.sortedWith(compareBy({ it.time }, { it.id }))

    companion object {
        private const val MORNING_HOUR = 8
        private const val DAY_HOUR = 14
        private const val EVENING_HOUR = 21
        private const val WORKDAYS = 5

        /** The three reminders of a fresh database; the texts mirror `DefaultReminders` in `:data:database`. */
        fun defaults(): List<Reminder> =
            listOf(
                Reminder(1, LocalTime.of(MORNING_HOUR, 0), "Утреннее измерение", DayOfWeek.entries.toSet(), true),
                Reminder(
                    2,
                    LocalTime.of(DAY_HOUR, 0),
                    "Дневное измерение",
                    DayOfWeek.entries.take(WORKDAYS).toSet(),
                    false,
                ),
                Reminder(3, LocalTime.of(EVENING_HOUR, 0), "Вечернее измерение", DayOfWeek.entries.toSet(), true),
            )
    }
}
