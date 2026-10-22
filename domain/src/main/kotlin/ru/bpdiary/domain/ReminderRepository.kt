package ru.bpdiary.domain

import kotlinx.coroutines.flow.Flow
import ru.bpdiary.core.model.Reminder

interface ReminderRepository {
    /** Sorted by time of day. */
    fun observeAll(): Flow<List<Reminder>>

    suspend fun getAll(): List<Reminder>

    suspend fun enabled(): List<Reminder>

    suspend fun getById(id: Long): Reminder?

    /** Inserts when `reminder.id == 0`, otherwise updates. Returns the row id. */
    suspend fun save(reminder: Reminder): Long

    suspend fun delete(id: Long)

    /** Removes all reminders and creates the three defaults again ("Delete all data"). */
    suspend fun resetToDefaults()
}
