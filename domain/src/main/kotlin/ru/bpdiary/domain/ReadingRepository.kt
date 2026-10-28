package ru.bpdiary.domain

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds
import java.time.Instant

interface ReadingRepository {
    fun observeLast(): Flow<Reading?>

    /** Readings with `from <= takenAt <= to`, oldest first. Soft-deleted ones are hidden. */
    fun observeRange(
        from: Instant,
        to: Instant,
    ): Flow<List<Reading>>

    /**
     * Diary pages, newest first. [status] null means every reading; otherwise the filter runs in SQL
     * with [th] (docs/03 §3.3), so a threshold change needs a new call (docs/06 §6.7).
     */
    fun paged(
        status: Status?,
        th: Thresholds,
    ): Flow<PagingData<Reading>>

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
