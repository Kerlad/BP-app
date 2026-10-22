package ru.bpdiary.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.data.database.BpDatabase
import ru.bpdiary.data.database.DefaultReminders
import ru.bpdiary.data.database.ReminderDao
import ru.bpdiary.domain.ReminderRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl
    @Inject
    constructor(
        private val database: BpDatabase,
        private val dao: ReminderDao,
    ) : ReminderRepository {
        override fun observeAll(): Flow<List<Reminder>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override suspend fun getAll(): List<Reminder> = dao.getAll().map { it.toDomain() }

        override suspend fun enabled(): List<Reminder> = dao.getEnabled().map { it.toDomain() }

        override suspend fun getById(id: Long): Reminder? = dao.getById(id)?.toDomain()

        override suspend fun save(reminder: Reminder): Long {
            require(reminder.days.isNotEmpty()) { "A reminder needs at least one day" }
            if (reminder.id == 0L) return dao.insert(reminder.toEntity())
            dao.update(reminder.toEntity())
            return reminder.id
        }

        override suspend fun delete(id: Long) = dao.delete(id)

        override suspend fun resetToDefaults() {
            database.withTransaction {
                dao.deleteAll()
                dao.insertAll(DefaultReminders.ENTITIES)
            }
        }
    }
