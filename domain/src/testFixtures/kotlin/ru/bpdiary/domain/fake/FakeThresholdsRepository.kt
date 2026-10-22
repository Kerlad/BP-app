package ru.bpdiary.domain.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.model.problem
import ru.bpdiary.domain.ThresholdsRepository

class FakeThresholdsRepository(
    initial: Thresholds = Thresholds(),
) : ThresholdsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<Thresholds> = state

    override suspend fun set(thresholds: Thresholds) {
        require(thresholds.problem() == null) { "Invalid thresholds: ${thresholds.problem()}" }
        state.value = thresholds
    }
}
