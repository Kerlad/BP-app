package ru.bpdiary.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Field
import ru.bpdiary.core.model.Reading
import ru.bpdiary.domain.fake.FakeReadingRepository
import java.time.Instant
import java.time.ZoneOffset

class SaveReadingUseCaseTest {
    private val time = FixedTimeProvider.default()
    private val repo = FakeReadingRepository(time)
    private val useCase = SaveReadingUseCase(repo, time)

    private fun reading(
        sys: Int = 128,
        dia: Int = 82,
        pulse: Int? = 71,
        takenAt: Instant = time.now(),
    ) = Reading(
        takenAt = takenAt,
        zoneOffset = ZoneOffset.ofHours(3),
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = Arm.LEFT,
    )

    private fun errorOf(
        r: Reading,
        requirePulse: Boolean = true,
    ): ValidationError? = ValidationRules.validate(r, time.now(), requirePulse)

    @ParameterizedTest(name = "sys {0} -> {1}")
    @CsvSource("49,SYS_RANGE", "50,", "300,", "301,SYS_RANGE")
    fun `sys bounds`(
        sys: Int,
        reason: Reason?,
    ) {
        // dia 30 keeps the sys > dia rule out of the way.
        assertEquals(reason?.let { ValidationError(Field.SYS, it) }, errorOf(reading(sys = sys, dia = 30)))
    }

    @ParameterizedTest(name = "dia {0} -> {1}")
    @CsvSource("29,DIA_RANGE", "30,", "200,", "201,DIA_RANGE")
    fun `dia bounds`(
        dia: Int,
        reason: Reason?,
    ) {
        assertEquals(reason?.let { ValidationError(Field.DIA, it) }, errorOf(reading(sys = 300, dia = dia)))
    }

    @ParameterizedTest(name = "pulse {0} -> {1}")
    @CsvSource("24,PULSE_RANGE", "25,", "250,", "251,PULSE_RANGE")
    fun `pulse bounds`(
        pulse: Int,
        reason: Reason?,
    ) {
        assertEquals(reason?.let { ValidationError(Field.PULSE, it) }, errorOf(reading(pulse = pulse)))
    }

    @Test
    fun `sys equal to dia is invalid`() {
        assertEquals(ValidationError(Field.SYS, Reason.SYS_NOT_GREATER), errorOf(reading(sys = 80, dia = 80)))
    }

    @Test
    fun `sys one above dia is valid`() {
        assertEquals(null, errorOf(reading(sys = 81, dia = 80)))
    }

    @Test
    fun `sys below dia is invalid`() {
        assertEquals(ValidationError(Field.SYS, Reason.SYS_NOT_GREATER), errorOf(reading(sys = 70, dia = 90)))
    }

    @Test
    fun `future time is invalid and now is valid`() {
        assertEquals(null, errorOf(reading(takenAt = time.now())))
        assertEquals(
            ValidationError(null, Reason.FUTURE_TIME),
            errorOf(reading(takenAt = time.now().plusSeconds(1))),
        )
    }

    @Test
    fun `missing pulse is rejected for manual entry and allowed for import`() {
        assertEquals(ValidationError(Field.PULSE, Reason.PULSE_MISSING), errorOf(reading(pulse = null)))
        assertEquals(null, errorOf(reading(pulse = null), requirePulse = false))
    }

    @Test
    fun `valid reading is saved and id returned`() =
        runTest {
            val result = useCase(reading())
            assertEquals(SaveResult.Success(1L), result)
            assertEquals(reading().copy(id = 1L), repo.getById(1L))
        }

    @Test
    fun `invalid reading never reaches the repository`() =
        runTest {
            val result = useCase(reading(sys = 49))
            assertEquals(SaveResult.Invalid(ValidationError(Field.SYS, Reason.SYS_RANGE)), result)
            assertEquals(0, repo.storedCount)
        }
}
