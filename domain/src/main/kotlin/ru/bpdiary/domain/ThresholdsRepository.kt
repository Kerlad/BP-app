package ru.bpdiary.domain

import kotlinx.coroutines.flow.Flow
import ru.bpdiary.core.model.Thresholds

interface ThresholdsRepository {
    /** Emits only when the thresholds really change. */
    fun observe(): Flow<Thresholds>

    /** @throws IllegalArgumentException when the thresholds are inconsistent (see `Thresholds.problem()`). */
    suspend fun set(thresholds: Thresholds)
}
