package ru.bpdiary.domain.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Reading
import ru.bpdiary.domain.ReadingRepository
import java.time.Duration
import java.time.Instant

/** In-memory repository with the same visible behaviour as the Room one: soft delete, purge after 24 h. */
class FakeReadingRepository(
    private val time: TimeProvider,
    initial: List<Reading> = emptyList(),
) : ReadingRepository {
    private data class Row(
        val reading: Reading,
        val deletedAt: Instant? = null,
    )

    private val rows = MutableStateFlow(initial.map { Row(it) })
    private var lastId = initial.maxOfOrNull { it.id } ?: 0L

    /** Live readings, newest first. */
    private val live: Flow<List<Reading>> =
        rows.map { list -> list.filter { it.deletedAt == null }.map { it.reading }.sortedByDescending { it.takenAt } }

    /** Test helper: everything stored, soft-deleted rows included. */
    val storedCount: Int get() = rows.value.size

    override fun observeLast(): Flow<Reading?> = live.map { it.firstOrNull() }

    override fun observeRange(
        from: Instant,
        to: Instant,
    ): Flow<List<Reading>> = live.map { list -> list.filter { it.takenAt in from..to }.sortedBy { it.takenAt } }

    override fun observeById(id: Long): Flow<Reading?> = live.map { list -> list.firstOrNull { it.id == id } }

    override fun observeCount(): Flow<Int> = live.map { it.size }

    override suspend fun getById(id: Long): Reading? =
        rows.value.firstOrNull { it.reading.id == id && it.deletedAt == null }?.reading

    override suspend fun save(reading: Reading): Long {
        val id = if (reading.id == 0L) ++lastId else reading.id
        val saved = reading.copy(id = id)
        rows.value = rows.value.filterNot { it.reading.id == id } + Row(saved)
        return id
    }

    override suspend fun softDelete(id: Long) {
        val now = time.now()
        rows.value = rows.value.map { if (it.reading.id == id) it.copy(deletedAt = now) else it }
    }

    override suspend fun restore(id: Long) {
        rows.value = rows.value.map { if (it.reading.id == id) it.copy(deletedAt = null) else it }
    }

    override suspend fun purgeExpired() {
        val before = time.now().minus(PURGE_AFTER)
        rows.value = rows.value.filterNot { row -> row.deletedAt?.isBefore(before) == true }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    private companion object {
        val PURGE_AFTER: Duration = Duration.ofHours(24)
    }
}
