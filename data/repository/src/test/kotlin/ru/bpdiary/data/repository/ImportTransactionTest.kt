package ru.bpdiary.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.data.database.BpDatabase
import ru.bpdiary.data.database.ReadingEntity
import java.time.Instant
import java.time.ZoneOffset

/** The import is all or nothing (docs/13 §13.6). */
@RunWith(RobolectricTestRunner::class)
class ImportTransactionTest {
    private lateinit var db: BpDatabase
    private lateinit var readings: ReadingRepositoryImpl
    private lateinit var importer: ReadingImporterImpl

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BpDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        readings = ReadingRepositoryImpl(db.readingDao(), db.readingMaintenanceDao(), FixedTimeProvider.default())
        importer = ReadingImporterImpl(db.readingMaintenanceDao(), FixedTimeProvider.default())
    }

    @After
    fun tearDown() = db.close()

    private fun reading(minute: Long) =
        Reading(
            takenAt = Instant.parse("2026-10-01T06:00:00Z").plusSeconds(minute * 60),
            zoneOffset = ZoneOffset.ofHours(3),
            sys = 120,
            dia = 80,
            pulse = null,
            arm = Arm.LEFT,
        )

    private fun entity(
        id: Long,
        minute: Long,
    ) = ReadingEntity(
        id = id,
        takenAt = reading(minute).takenAt.toEpochMilli(),
        tzOffsetSeconds = 10800,
        sys = 120,
        dia = 80,
        pulse = null,
        arm = "LEFT",
        tags = "[]",
        comment = "",
        profileId = 1,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `a failure on row 500 of 1000 leaves nothing new`() =
        runTest {
            importer.importAll(listOf(reading(0)), replaceExisting = false)
            // The same primary key twice makes the insert fail halfway.
            val rows =
                (1L..1000L).map { entity(id = if (it == 500L) 100 else it + 1000, minute = it) } +
                    entity(id = 100, minute = 2000)
            assertThrows(SQLiteConstraintException::class.java) {
                kotlinx.coroutines.runBlocking { db.readingMaintenanceDao().importAll(rows, replaceExisting = true) }
            }
            assertEquals(1, db.readingMaintenanceDao().countIncludingDeleted())
        }

    @Test
    fun `add keeps the old rows, replace removes them with the soft-deleted ones`() =
        runTest {
            val id = readings.save(reading(0))
            readings.softDelete(id)
            importer.importAll(listOf(reading(1), reading(2)), replaceExisting = false)
            assertEquals(3, db.readingMaintenanceDao().countIncludingDeleted())

            importer.importAll(listOf(reading(3)), replaceExisting = true)
            assertEquals(1, db.readingMaintenanceDao().countIncludingDeleted())
        }
}
