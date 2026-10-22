package ru.bpdiary.domain

import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Field
import ru.bpdiary.core.model.Reading
import java.time.Instant

enum class Reason { SYS_RANGE, DIA_RANGE, PULSE_RANGE, PULSE_MISSING, SYS_NOT_GREATER, FUTURE_TIME }

/** [field] is null for [Reason.FUTURE_TIME]. */
data class ValidationError(
    val field: Field?,
    val reason: Reason,
)

sealed interface SaveResult {
    data class Success(
        val id: Long,
    ) : SaveResult

    data class Invalid(
        val error: ValidationError,
    ) : SaveResult
}

object ValidationRules {
    const val SYS_MIN = 50
    const val SYS_MAX = 300
    const val DIA_MIN = 30
    const val DIA_MAX = 200
    const val PULSE_MIN = 25
    const val PULSE_MAX = 250

    val SYS_RANGE = SYS_MIN..SYS_MAX
    val DIA_RANGE = DIA_MIN..DIA_MAX
    val PULSE_RANGE = PULSE_MIN..PULSE_MAX

    /** Returns the first violated rule, or null. Manual entry requires a pulse; import does not. */
    fun validate(
        reading: Reading,
        now: Instant,
        requirePulse: Boolean = true,
    ): ValidationError? =
        when {
            reading.sys !in SYS_RANGE -> ValidationError(Field.SYS, Reason.SYS_RANGE)
            reading.dia !in DIA_RANGE -> ValidationError(Field.DIA, Reason.DIA_RANGE)
            reading.pulse == null && requirePulse -> ValidationError(Field.PULSE, Reason.PULSE_MISSING)
            reading.pulse != null && reading.pulse !in PULSE_RANGE -> ValidationError(Field.PULSE, Reason.PULSE_RANGE)
            reading.sys <= reading.dia -> ValidationError(Field.SYS, Reason.SYS_NOT_GREATER)
            reading.takenAt.isAfter(now) -> ValidationError(null, Reason.FUTURE_TIME)
            else -> null
        }
}

class SaveReadingUseCase(
    private val repository: ReadingRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        reading: Reading,
        requirePulse: Boolean = true,
    ): SaveResult {
        val error = ValidationRules.validate(reading, time.now(), requirePulse)
        return if (error != null) SaveResult.Invalid(error) else SaveResult.Success(repository.save(reading))
    }
}
