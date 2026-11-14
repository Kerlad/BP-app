package ru.bpdiary.data.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.ReminderScheduler
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeReminderScheduler
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ReminderWatchdogWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val time = ManualTimeProvider(Instant.parse("2026-10-20T06:12:00Z"), ZoneId.of("Europe/Moscow"))
    private val scheduler = FakeReminderScheduler()

    private fun worker(readings: ReadingRepository): ReminderWatchdogWorker =
        TestListenableWorkerBuilder<ReminderWatchdogWorker>(context)
            .setWorkerFactory(factory(scheduler, readings))
            .build()

    @Test
    fun `the daily run purges expired deletes and re-plans the alarms (D-17)`() =
        runTest {
            val readings = FakeReadingRepository(time)
            val gone = readings.save(reading())
            readings.save(reading())
            readings.softDelete(gone)
            time.advance(Duration.ofHours(25))

            val result = worker(readings).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, readings.storedCount)
            assertEquals(1, scheduler.rescheduleCount)
        }

    @Test
    fun `a failing purge does not stop the alarms`() =
        runTest {
            val broken =
                object : ReadingRepository by FakeReadingRepository(time) {
                    override suspend fun purgeExpired(): Unit = throw java.io.IOException("disk")
                }

            val result = worker(broken).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, scheduler.rescheduleCount)
        }

    private fun reading() =
        Reading(
            takenAt = time.now(),
            zoneOffset = ZoneOffset.ofHours(3),
            sys = 120,
            dia = 80,
            pulse = 70,
            arm = Arm.LEFT,
        )

    private fun factory(
        scheduler: ReminderScheduler,
        readings: ReadingRepository,
    ) = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = ReminderWatchdogWorker(appContext, workerParameters, scheduler, readings)
    }

    @Test
    fun `the watchdog runs every 6 hours (B-03)`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        ReminderWork.start(context)
        val info =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork("reminders_watchdog")
                .get()
                .single()
        assertEquals(Duration.ofHours(6).toMillis(), info.periodicityInfo?.repeatIntervalMillis)
    }
}
