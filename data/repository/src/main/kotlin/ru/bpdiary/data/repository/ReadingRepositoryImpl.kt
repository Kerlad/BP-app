package ru.bpdiary.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.data.database.ReadingDao
import ru.bpdiary.data.database.ReadingMaintenanceDao
import ru.bpdiary.domain.ReadingRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingRepositoryImpl
    @Inject
    constructor(
        private val dao: ReadingDao,
        private val maintenance: ReadingMaintenanceDao,
        private val time: TimeProvider,
    ) : ReadingRepository {
        override fun observeLast(): Flow<Reading?> = dao.observeLast().map { it?.toDomain() }

        override fun observeRange(
            from: Instant,
            to: Instant,
        ): Flow<List<Reading>> =
            dao.observeRange(from.toEpochMilli(), to.toEpochMilli()).map { list -> list.map { it.toDomain() } }

        override fun paged(
            status: Status?,
            th: Thresholds,
        ): Flow<PagingData<Reading>> =
            Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
                dao.pagingSource(status?.name.orEmpty(), th.highSys, th.highDia, th.lowSys, th.lowDia)
            }.flow.map { page -> page.map { it.toDomain() } }

        override fun observeCount(): Flow<Int> = dao.observeCount()

        override suspend fun getById(id: Long): Reading? = dao.getById(id)?.toDomain()

        override suspend fun save(reading: Reading): Long {
            val now = time.now().toEpochMilli()
            // An update keeps the original creation time; a row that is gone is inserted as new.
            val existing = if (reading.id == 0L) null else dao.getById(reading.id)
            val entity = reading.toEntity(createdAt = existing?.createdAt ?: now, updatedAt = now)
            return if (existing == null) {
                dao.insert(entity)
            } else {
                dao.update(entity)
                reading.id
            }
        }

        override suspend fun softDelete(id: Long) = dao.softDelete(id, time.now().toEpochMilli())

        override suspend fun restore(id: Long) = dao.restore(id)

        override suspend fun purgeExpired() {
            maintenance.purgeDeletedBefore(time.now().minus(PURGE_AFTER).toEpochMilli())
        }

        override suspend fun deleteAll() = maintenance.hardDeleteAll()

        private companion object {
            const val PAGE_SIZE = 50
            val PURGE_AFTER: Duration = Duration.ofHours(24)
        }
    }
