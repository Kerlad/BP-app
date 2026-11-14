package ru.bpdiary.domain.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.model.problem
import ru.bpdiary.domain.ThresholdsRepository
import java.io.IOException

class FakeThresholdsRepository(
    initial: Thresholds = Thresholds(),
) : ThresholdsRepository {
    private val state = MutableStateFlow(initial)

    /** Test switch: writes fail like a full disk. */
    var failWrites = false

    override fun observe(): Flow<Thresholds> = state

    override suspend fun set(thresholds: Thresholds) {
        if (failWrites) throw IOException("disk full")
        require(thresholds.problem() == null) { "Invalid thresholds: ${thresholds.problem()}" }
        state.value = thresholds
    }
}
