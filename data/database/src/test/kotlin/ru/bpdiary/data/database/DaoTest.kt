package ru.bpdiary.data.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.classify
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: BpDatabase
    private lateinit var readings: ReadingDao
    private lateinit var maintenance: ReadingMaintenanceDao
    private lateinit var reminders: ReminderDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BpDatabase::class.java)
                .addCallback(DefaultRemindersCallback)
                .allowMainThreadQueries()
                .build()
        readings = db.readingDao()
        maintenance = db.readingMaintenanceDao()
        reminders = db.reminderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        takenAt: Long = 1_000,
        sys: Int = 120,
        dia: Int = 80,
        pulse: Int? = 70,
    ) = ReadingEntity(
        takenAt = takenAt,
        tzOffsetSeconds = 10_800,
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = "LEFT",
        tags = "[]",
        comment = "",
        profileId = 1,
        createdAt = takenAt,
        updatedAt = takenAt,
    )

    // --- readings -------------------------------------------------------------------------------

    @Test
    fun `insert and observe last`() =
        runTest {
            assertNull(readings.observeLast().first())
            readings.insert(entity(takenAt = 1_000))
            val id = readings.insert(entity(takenAt = 2_000, sys = 140))
            val last = readings.observeLast().first()
            assertEquals(id, last?.id)
            assertEquals(140, last?.sys)
        }

    @Test
    fun `null pulse round trips`() =
        runTest {
            val id = readings.insert(entity(pulse = null))
            assertNull(readings.getById(id)?.pulse)
        }

    @Test
    fun `update changes the row`() =
        runTest {
            val id = readings.insert(entity())
            readings.update(entity().copy(id = id, sys = 150, updatedAt = 5_000))
            assertEquals(150, readings.getById(id)?.sys)
            assertEquals(5_000L, readings.getById(id)?.updatedAt)
        }

    @Test
    fun `range bounds are inclusive and ordered ascending`() =
        runTest {
            listOf(999L, 1_000L, 1_500L, 2_000L, 2_001L).forEach { readings.insert(entity(takenAt = it)) }
            val inRange = readings.observeRange(1_000, 2_000).first()
            assertEquals(listOf(1_000L, 1_500L, 2_000L), inRange.map { it.takenAt })
        }

    @Test
    fun `soft delete hides and restore brings back`() =
        runTest {
            val id = readings.insert(entity())
            readings.softDelete(id, now = 9_000)
            assertNull(readings.getById(id))
            assertNull(readings.observeById(id).first())
            assertNull(readings.observeLast().first())
            assertEquals(0, readings.observeCount().first())
            assertEquals(emptyList<ReadingEntity>(), readings.observeRange(0, 10_000).first())
            assertEquals(1, maintenance.countIncludingDeleted())

            readings.restore(id)
            assertNotNull(readings.getById(id))
            assertEquals(1, readings.observeCount().first())
        }

    @Test
    fun `purge removes only rows deleted before the cut off`() =
        runTest {
            val old = readings.insert(entity(takenAt = 1))
            val edge = readings.insert(entity(takenAt = 2))
            val fresh = readings.insert(entity(takenAt = 3))
            val alive = readings.insert(entity(takenAt = 4))
            readings.softDelete(old, now = 100)
            readings.softDelete(edge, now = 200)
            readings.softDelete(fresh, now = 300)

            maintenance.purgeDeletedBefore(200)
            assertEquals(3, maintenance.countIncludingDeleted())

            readings.restore(edge)
            readings.restore(fresh)
            assertEquals(
                listOf(edge, fresh, alive).sorted(),
                readings
                    .observeRange(0, 10)
                    .first()
                    .map { it.id }
                    .sorted(),
            )
        }

    @Test
    fun `hard delete all removes soft deleted rows too`() =
        runTest {
            val id = readings.insert(entity())
            readings.insert(entity())
            readings.softDelete(id, now = 1)
            maintenance.hardDeleteAll()
            assertEquals(0, maintenance.countIncludingDeleted())
        }

    @Test
    fun `observeById follows updates`() =
        runTest {
            val id = readings.insert(entity(sys = 120))
            assertEquals(120, readings.observeById(id).first()?.sys)
            readings.update(entity().copy(id = id, sys = 133))
            assertEquals(133, readings.observeById(id).first()?.sys)
        }

    // --- diary paging filter ----------------------------------------------------------------

    private suspend fun filteredIds(
        status: Status?,
        th: Thresholds,
    ): List<Long> {
        val source =
            readings.pagingSource(
                status = status?.name.orEmpty(),
                hs = th.highSys,
                hd = th.highDia,
                ls = th.lowSys,
                ld = th.lowDia,
            )
        val page =
            source.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 1_000, placeholdersEnabled = false),
            ) as PagingSource.LoadResult.Page
        return page.data.map { it.id }
    }

    @Test
    fun `paging filter equals classify on 500 random readings`() =
        runTest {
            val random = Random(42)
            val all =
                (1..500).map { i ->
                    val e = entity(takenAt = i * 1_000L, sys = random.nextInt(70, 191), dia = random.nextInt(40, 121))
                    e.copy(id = readings.insert(e))
                }
            val deleted = all[10].also { readings.softDelete(it.id, now = 1) }
            val alive = all.filter { it.id != deleted.id }

            for (th in listOf(Thresholds(), Thresholds(highSys = 140, highDia = 90, lowSys = 105, lowDia = 65))) {
                assertEquals(
                    alive.sortedByDescending { it.takenAt }.map { it.id },
                    filteredIds(null, th),
                )
                for (status in Status.entries) {
                    val expected =
                        alive
                            .filter { classify(it.sys, it.dia, th) == status }
                            .sortedByDescending { it.takenAt }
                            .map { it.id }
                    assertEquals("$status $th", expected, filteredIds(status, th))
                }
            }
        }

    // --- reminders --------------------------------------------------------------------------

    @Test
    fun `fresh database has the three default reminders`() =
        runTest {
            val list = reminders.getAll()
            assertEquals(
                listOf(
                    Triple(480, "Утреннее измерение", 127) to true,
                    Triple(840, "Дневное измерение", 31) to false,
                    Triple(1260, "Вечернее измерение", 127) to true,
                ),
                list.map { Triple(it.minutesOfDay, it.label, it.daysMask) to it.enabled },
            )
        }

    @Test
    fun `reminders crud and enabled filter`() =
        runTest {
            assertEquals(listOf(480, 1260), reminders.getEnabled().map { it.minutesOfDay })
            val id = reminders.insert(ReminderEntity(minutesOfDay = 600, label = "x", daysMask = 1, enabled = true))
            assertEquals(listOf(480, 600, 840, 1260), reminders.observeAll().first().map { it.minutesOfDay })
            reminders.update(reminders.getById(id)!!.copy(enabled = false))
            assertEquals(false, reminders.getById(id)?.enabled)
            reminders.delete(id)
            assertNull(reminders.getById(id))
            reminders.deleteAll()
            assertEquals(emptyList<ReminderEntity>(), reminders.getAll())
        }
}
