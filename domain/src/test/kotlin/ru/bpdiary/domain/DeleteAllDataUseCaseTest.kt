package ru.bpdiary.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.DISCLAIMER_VERSION
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeReminderRepository
import ru.bpdiary.domain.fake.FakeReminderScheduler
import ru.bpdiary.domain.fake.FakeSettingsRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class DeleteAllDataUseCaseTest {
    private val time = FixedTimeProvider.default()
    private val readings = FakeReadingRepository(time)
    private val reminders = FakeReminderRepository()
    private val settings = FakeSettingsRepository()
    private val scheduler = FakeReminderScheduler()
    private val deleteAll = DeleteAllDataUseCase(readings, reminders, settings, scheduler)

    private fun reading() =
        Reading(
            takenAt = Instant.parse("2026-10-20T04:00:00Z"),
            zoneOffset = ZoneOffset.ofHours(3),
            sys = 128,
            dia = 82,
            pulse = 71,
            arm = Arm.LEFT,
        )

    @Test
    fun `no reading is left, soft deleted ones included`() =
        runTest {
            readings.save(reading())
            readings.softDelete(readings.save(reading()))
            deleteAll()
            assertEquals(0, readings.storedCount)
        }

    @Test
    fun `the three default reminders come back whatever was changed`() =
        runTest {
            reminders.delete(2)
            reminders.save(
                Reminder(time = LocalTime.of(3, 0), label = "Свое", days = setOf(DayOfWeek.FRIDAY), enabled = true),
            )
            deleteAll()
            assertEquals(
                listOf("Утреннее измерение", "Дневное измерение", "Вечернее измерение"),
                reminders.getAll().map { it.label },
            )
        }

    @Test
    fun `settings go back to the first launch, so the onboarding comes again`() =
        runTest {
            settings.setThemeMode(ThemeMode.DARK)
            settings.setOnboardingDone(true)
            settings.setDisclaimerAck(DISCLAIMER_VERSION)
            deleteAll()
            assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
            assertFalse(settings.onboardingDone.first())
            assertEquals(0, settings.disclaimerAck.first())
            assertEquals(
                LaunchGate.ONBOARDING,
                launchGate(settings.onboardingDone.first(), settings.disclaimerAck.first()),
            )
        }

    @Test
    fun `the alarms are cancelled first and planned again for the default reminders`() =
        runTest {
            deleteAll()
            assertTrue(scheduler.cancelled)
            assertEquals(1, scheduler.rescheduleCount)
        }
}
