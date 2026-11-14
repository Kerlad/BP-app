package ru.bpdiary.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.data.database.BpDatabase
import ru.bpdiary.data.database.DefaultRemindersCallback
import ru.bpdiary.domain.DeleteAllDataUseCase
import ru.bpdiary.domain.EARLIEST_READING_TIME
import ru.bpdiary.domain.LATEST_READING_TIME
import ru.bpdiary.domain.fake.FakeReminderScheduler
import ru.bpdiary.domain.fake.FakeSettingsRepository
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RepositoriesTest {
    private lateinit var db: BpDatabase
    private lateinit var time: ManualTimeProvider
    private lateinit var readings: ReadingRepositoryImpl
    private lateinit var reminders: ReminderRepositoryImpl

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BpDatabase::class.java)
                .addCallback(DefaultRemindersCallback)
                .allowMainThreadQueries()
                .build()
        time = ManualTimeProvider(Instant.parse("2026-10-20T06:12:00Z"), ZoneId.of("Europe/Moscow"))
        readings = ReadingRepositoryImpl(db.readingDao(), db.readingMaintenanceDao(), time)
        reminders = ReminderRepositoryImpl(db, db.reminderDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun reading(
        at: String = "2026-10-20T04:00:00Z",
        sys: Int = 128,
    ) = Reading(
        takenAt = Instant.parse(at),
        zoneOffset = ZoneOffset.ofHours(3),
        sys = sys,
        dia = 82,
        pulse = 71,
        arm = Arm.LEFT,
    )

    // --- readings -------------------------------------------------------------------------------

    @Test
    fun `save inserts and returns the new id`() =
        runTest {
            val id = readings.save(reading())
            assertEquals(reading().copy(id = id), readings.getById(id))
            assertEquals(id, readings.observeLast().first()?.id)
        }

    @Test
    fun `update keeps creation time and moves updated time`() =
        runTest {
            val id = readings.save(reading())
            val created = time.now().toEpochMilli()
            time.advance(Duration.ofMinutes(5))

            assertEquals(id, readings.save(reading(sys = 150).copy(id = id)))

            val stored = db.readingDao().getById(id)!!
            assertEquals(150, stored.sys)
            assertEquals(created, stored.createdAt)
            assertEquals(time.now().toEpochMilli(), stored.updatedAt)
        }

    @Test
    fun `range is inclusive and returns domain objects`() =
        runTest {
            listOf("2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z", "2026-10-03T00:00:01Z").forEach {
                readings.save(reading(at = it))
            }
            val inRange =
                readings.observeRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-03T00:00:00Z"))
            assertEquals(
                listOf("2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z").map(Instant::parse),
                inRange.first().map { it.takenAt },
            )
        }

    @Test
    fun `the widest range converts and finds a reading from before 1970 (D-03)`() =
        runTest {
            readings.save(reading(at = "1969-07-01T10:00:00Z"))
            readings.save(reading(at = "2026-10-01T00:00:00Z"))
            val all = readings.observeRange(EARLIEST_READING_TIME, LATEST_READING_TIME).first()
            assertEquals(
                listOf("1969-07-01T10:00:00Z", "2026-10-01T00:00:00Z").map(Instant::parse),
                all.map { it.takenAt },
            )
        }

    @Test
    fun `soft delete restore and purge after 24 hours`() =
        runTest {
            val keep = readings.save(reading(at = "2026-10-01T00:00:00Z"))
            val gone = readings.save(reading(at = "2026-10-02T00:00:00Z"))

            readings.softDelete(gone)
            assertNull(readings.getById(gone))
            assertEquals(1, readings.observeCount().first())

            readings.restore(gone)
            assertEquals(2, readings.observeCount().first())

            readings.softDelete(gone)
            time.advance(Duration.ofHours(23))
            readings.purgeExpired()
            assertEquals(2, db.readingMaintenanceDao().countIncludingDeleted())

            time.advance(Duration.ofHours(2))
            readings.purgeExpired()
            assertEquals(1, db.readingMaintenanceDao().countIncludingDeleted())
            assertEquals(keep, readings.getById(keep)?.id)
        }

    @Test
    fun `delete all leaves nothing even soft deleted`() =
        runTest {
            val id = readings.save(reading())
            readings.save(reading())
            readings.softDelete(id)
            readings.deleteAll()
            assertEquals(0, db.readingMaintenanceDao().countIncludingDeleted())
        }

    @Test
    fun `delete all data leaves no reading in the database file and the default reminders`() =
        runTest {
            val soft = readings.save(reading())
            readings.save(reading())
            readings.softDelete(soft)
            reminders.delete(reminders.getAll().first().id)
            val scheduler = FakeReminderScheduler()

            DeleteAllDataUseCase(readings, reminders, FakeSettingsRepository(), scheduler).invoke()

            assertEquals(0, db.readingMaintenanceDao().countIncludingDeleted())
            assertEquals(3, reminders.getAll().size)
            assertEquals(true, scheduler.cancelled)
        }

    // --- reminders ------------------------------------------------------------------------------

    @Test
    fun `fresh database has the three default reminders in time order`() =
        runTest {
            val all = reminders.getAll()
            assertEquals(
                listOf(
                    Triple(LocalTime.of(8, 0), "Утреннее измерение", true),
                    Triple(LocalTime.of(14, 0), "Дневное измерение", false),
                    Triple(LocalTime.of(21, 0), "Вечернее измерение", true),
                ),
                all.map { Triple(it.time, it.label, it.enabled) },
            )
            assertEquals(DayOfWeek.entries.take(5).toSet(), all[1].days)
            assertEquals(listOf("Утреннее измерение", "Вечернее измерение"), reminders.enabled().map { it.label })
        }

    @Test
    fun `reminder save update and delete`() =
        runTest {
            val id =
                reminders.save(
                    Reminder(
                        time = LocalTime.of(10, 30),
                        label = "Extra",
                        days = setOf(DayOfWeek.SUNDAY),
                        enabled = true,
                    ),
                )
            assertEquals(LocalTime.of(10, 30), reminders.getById(id)?.time)

            reminders.save(reminders.getById(id)!!.copy(enabled = false))
            assertEquals(false, reminders.getById(id)?.enabled)

            reminders.delete(id)
            assertNull(reminders.getById(id))
        }

    @Test
    fun `a reminder without days is rejected`() =
        runTest {
            val empty = Reminder(time = LocalTime.of(9, 0), label = "x", days = emptySet(), enabled = true)
            assertThrows(
                IllegalArgumentException::class.java,
            ) { kotlinx.coroutines.runBlocking { reminders.save(empty) } }
        }

    @Test
    fun `reset to defaults replaces everything`() =
        runTest {
            reminders.getAll().forEach { reminders.delete(it.id) }
            reminders.save(
                Reminder(time = LocalTime.of(3, 0), label = "x", days = setOf(DayOfWeek.FRIDAY), enabled = true),
            )

            reminders.resetToDefaults()

            assertEquals(
                listOf("Утреннее измерение", "Дневное измерение", "Вечернее измерение"),
                reminders.getAll().map { it.label },
            )
        }
}
