package ru.bpdiary.domain

import kotlinx.coroutines.flow.Flow
import ru.bpdiary.core.model.Reading
import java.time.Instant

/** Paging for the diary is added in Step 6. */
interface ReadingRepository {
    fun observeLast(): Flow<Reading?>

    /** Readings with `from <= takenAt <= to`, oldest first. Soft-deleted ones are hidden. */
    fun observeRange(
        from: Instant,
        to: Instant,
    ): Flow<List<Reading>>

    fun observeById(id: Long): Flow<Reading?>

    fun observeCount(): Flow<Int>

    suspend fun getById(id: Long): Reading?

    /** Inserts when `reading.id == 0`, otherwise updates. Returns the row id. */
    suspend fun save(reading: Reading): Long

    /** Hides the reading; it stays restorable until [purgeExpired] removes it. */
    suspend fun softDelete(id: Long)

    suspend fun restore(id: Long)

    /** Physically removes readings soft-deleted more than 24 hours ago (D-17). */
    suspend fun purgeExpired()

    /** "Delete all data" and import "Replace all": removes everything, soft-deleted rows included. */
    suspend fun deleteAll()
}
