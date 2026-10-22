package ru.bpdiary.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.domain.fake.FakeReadingRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** The fakes stand in for Room in ViewModel tests, so their visible behaviour is pinned here. */
class FakeRepositoriesTest {
    private val clock = ManualTimeProvider(Instant.parse("2026-10-20T06:12:00Z"), ZoneId.of("Europe/Moscow"))
    private val repo = FakeReadingRepository(clock)

    private fun reading(at: String) =
        Reading(
            takenAt = Instant.parse(at),
            zoneOffset = ZoneOffset.UTC,
            sys = 120,
            dia = 80,
            pulse = 70,
            arm = Arm.LEFT,
        )

    @Test
    fun `range is inclusive and ordered oldest first`() =
        runTest {
            listOf("2026-10-01T00:00:00Z", "2026-10-03T00:00:00Z", "2026-10-02T00:00:00Z", "2026-10-04T00:00:01Z")
                .forEach { repo.save(reading(it)) }
            val inRange =
                repo
                    .observeRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-04T00:00:00Z"))
                    .first()
            assertEquals(
                listOf("2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z", "2026-10-03T00:00:00Z").map(Instant::parse),
                inRange.map { it.takenAt },
            )
        }

    @Test
    fun `soft delete hides and restore brings back`() =
        runTest {
            val id = repo.save(reading("2026-10-01T00:00:00Z"))
            repo.softDelete(id)
            assertNull(repo.getById(id))
            assertNull(repo.observeLast().first())
            assertEquals(0, repo.observeCount().first())
            repo.restore(id)
            assertEquals(id, repo.observeLast().first()?.id)
        }

    @Test
    fun `purge removes rows deleted more than 24 hours ago only`() =
        runTest {
            val old = repo.save(reading("2026-10-01T00:00:00Z"))
            val recent = repo.save(reading("2026-10-02T00:00:00Z"))
            repo.softDelete(old)
            clock.advance(Duration.ofHours(25))
            repo.softDelete(recent)
            repo.purgeExpired()
            assertEquals(1, repo.storedCount)
            repo.restore(recent)
            assertEquals(recent, repo.getById(recent)?.id)
        }

    @Test
    fun `save updates an existing row and delete all clears soft deleted rows too`() =
        runTest {
            val id = repo.save(reading("2026-10-01T00:00:00Z"))
            repo.save(reading("2026-10-01T00:00:00Z").copy(id = id, sys = 150))
            assertEquals(150, repo.getById(id)?.sys)
            assertEquals(1, repo.storedCount)
            repo.softDelete(id)
            repo.deleteAll()
            assertEquals(0, repo.storedCount)
        }
}
