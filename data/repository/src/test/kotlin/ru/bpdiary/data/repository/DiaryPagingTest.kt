package ru.bpdiary.data.repository

import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.data.database.BpDatabase
import ru.bpdiary.domain.classify
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Diary paging against Room: the status filter runs in SQL and must agree with `classify()` (Step 6). */
@RunWith(RobolectricTestRunner::class)
class DiaryPagingTest {
    private lateinit var db: BpDatabase
    private lateinit var readings: ReadingRepositoryImpl
    private val start = Instant.parse("2026-10-20T06:00:00Z")

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BpDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        readings = ReadingRepositoryImpl(db.readingDao(), db.readingMaintenanceDao(), FixedTimeProvider.default())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 2 000 readings, one every 6 hours back from [start], spread over all three statuses. */
    private suspend fun insertMany(count: Int = COUNT): List<Reading> =
        (0 until count).map { i ->
            val reading =
                Reading(
                    takenAt = start.minus(Duration.ofHours(6L * i)),
                    zoneOffset = ZoneOffset.ofHours(3),
                    sys = 90 + (i * 37) % 80,
                    dia = 50 + (i * 23) % 40,
                    pulse = 60 + i % 30,
                    arm = Arm.LEFT,
                )
            reading.copy(id = readings.save(reading))
        }

    @Test
    fun `all 2000 readings page in, newest first`() =
        runTest {
            val stored = insertMany()
            val loaded = readings.paged(null, Thresholds()).asSnapshot { appendScrollWhile { true } }
            assertEquals(COUNT, loaded.size)
            assertEquals(stored.map { it.id }, loaded.map { it.id })
        }

    @Test
    fun `status filter in SQL matches classify for every status and threshold set`() =
        runTest {
            val stored = insertMany()
            listOf(Thresholds(), Thresholds(highSys = 140, highDia = 90)).forEach { th ->
                Status.entries.forEach { status ->
                    val expected = stored.filter { classify(it.sys, it.dia, th) == status }.map { it.id }
                    val loaded = readings.paged(status, th).asSnapshot { appendScrollWhile { true } }
                    assertTrue("no $status readings with $th", expected.isNotEmpty())
                    assertEquals("$status with $th", expected, loaded.map { it.id })
                }
            }
        }

    @Test
    fun `soft deleted readings are not paged`() =
        runTest {
            val stored = insertMany(count = 10)
            readings.softDelete(stored[3].id)
            val loaded = readings.paged(null, Thresholds()).asSnapshot()
            assertEquals(stored.map { it.id } - stored[3].id, loaded.map { it.id })
        }

    private companion object {
        const val COUNT = 2_000
    }
}
