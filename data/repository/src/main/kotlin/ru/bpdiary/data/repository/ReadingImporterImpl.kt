package ru.bpdiary.data.repository

import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Reading
import ru.bpdiary.data.database.ReadingMaintenanceDao
import ru.bpdiary.domain.ReadingImporter
import javax.inject.Inject

class ReadingImporterImpl
    @Inject
    constructor(
        private val maintenance: ReadingMaintenanceDao,
        private val time: TimeProvider,
    ) : ReadingImporter {
        override suspend fun importAll(
            readings: List<Reading>,
            replaceExisting: Boolean,
        ) {
            val now = time.now().toEpochMilli()
            maintenance.importAll(readings.map { it.toEntity(createdAt = now, updatedAt = now) }, replaceExisting)
        }
    }
