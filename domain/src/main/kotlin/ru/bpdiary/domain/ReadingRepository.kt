package ru.bpdiary.domain

import ru.bpdiary.core.model.Reading

/** Grows in Step 2.3; only what the use cases need so far. */
interface ReadingRepository {
    /** Inserts when `reading.id == 0`, otherwise updates. Returns the row id. */
    suspend fun save(reading: Reading): Long
}
