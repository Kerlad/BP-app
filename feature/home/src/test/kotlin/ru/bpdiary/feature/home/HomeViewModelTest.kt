package ru.bpdiary.feature.home

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.DayLabel
import ru.bpdiary.domain.GreetingSlot
import ru.bpdiary.domain.NextReminderLabel
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeReminderPermissions
import ru.bpdiary.domain.fake.FakeReminderRepository
import ru.bpdiary.domain.fake.FakeThresholdsRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Plain JVM: the ViewModel touches no Android API. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val moscow = ZoneOffset.ofHours(3)
    private val thresholds = FakeThresholdsRepository()
    private val reminders = FakeReminderRepository()
    private val permissions = FakeReminderPermissions()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** [local] is Moscow wall-clock time, e.g. "2026-10-20T07:24". */
    private fun reading(
        local: String,
        sys: Int,
        dia: Int,
        pulse: Int? = 70,
        id: Long = 0,
    ) = Reading(
        id = id,
        takenAt = LocalDateTime.parse(local).toInstant(moscow),
        zoneOffset = moscow,
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = Arm.LEFT,
    )

    private fun TestScope.viewModel(
        readings: List<Reading> = emptyList(),
        time: TimeProvider = FixedTimeProvider.default(),
    ): Pair<HomeViewModel, FakeReadingRepository> {
        val repo = FakeReadingRepository(time, readings.mapIndexed { i, r -> r.copy(id = i + 1L) })
        val vm = HomeViewModel(repo, thresholds, reminders, permissions, time)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm to repo
    }

    private fun HomeViewModel.content() = state.value as HomeUiState.Content

    private fun HomeViewModel.todayBarStatus() =
        content()
            .week!!
            .days
            .last()
            .bar!!
            .status

    private fun HomeUiState.asContent() = this as HomeUiState.Content

    @Test
    fun `threshold change recolors the last reading`() =
        runTest {
            thresholds.set(Thresholds(highSys = 135))
            val (vm, _) = viewModel(listOf(reading("2026-10-20T07:24", sys = 136, dia = 80)))
            vm.state.test {
                assertEquals(Status.HIGH, awaitItem().asContent().last!!.status)
                thresholds.set(Thresholds(highSys = 140))
                assertEquals(Status.NORMAL, awaitItem().asContent().last!!.status)
            }
        }

    @Test
    fun `threshold change recolors the week bars`() =
        runTest {
            val (vm, _) = viewModel(listOf(reading("2026-10-20T07:24", sys = 136, dia = 80)))
            assertEquals(Status.HIGH, vm.todayBarStatus())
            thresholds.set(Thresholds(highSys = 140))
            assertEquals(Status.NORMAL, vm.todayBarStatus())
        }

    @Test
    fun `no readings - welcome card, no week card`() =
        runTest {
            val (vm, _) = viewModel()
            val s = vm.content()
            assertNull(s.last)
            assertNull(s.week)
            assertEquals(LocalDate.of(2026, 10, 20), s.today)
            assertEquals(GreetingSlot.MORNING, s.greeting)
        }

    @Test
    fun `one reading - one bar and its own value as the average`() =
        runTest {
            val (vm, _) = viewModel(listOf(reading("2026-10-20T07:24", sys = 128, dia = 82, pulse = 71)))
            val s = vm.content()
            val last = s.last!!
            assertEquals(DayLabel.Today, last.dayLabel)
            assertEquals(LocalTime.of(7, 24), last.time)
            assertEquals(128 to 82, last.sys to last.dia)
            assertEquals(71, last.pulse)
            assertFalse(last.pulseOutOfRange)
            assertEquals(Status.NORMAL, last.status)
            val week = s.week!!
            assertEquals(1, week.days.count { it.bar != null })
            assertEquals(128 to 82, week.avgSys to week.avgDia)
        }

    @Test
    fun `only older readings - empty week card is still shown (D-24)`() =
        runTest {
            val (vm, _) = viewModel(listOf(reading("2026-10-10T08:00", sys = 128, dia = 82)))
            val s = vm.content()
            assertEquals(DayLabel.Date(LocalDate.of(2026, 10, 10)), s.last!!.dayLabel)
            val week = s.week!!
            assertEquals(7, week.days.size)
            assertTrue(week.days.all { it.bar == null })
            assertNull(week.avgSys)
        }

    @Test
    fun `week average is over all readings, not over the daily means`() =
        runTest {
            val (vm, _) =
                viewModel(
                    listOf(
                        reading("2026-10-19T08:00", sys = 120, dia = 80),
                        reading("2026-10-19T21:00", sys = 130, dia = 80),
                        reading("2026-10-20T08:00", sys = 150, dia = 90),
                        // Eight days ago: outside the week.
                        reading("2026-10-12T08:00", sys = 180, dia = 110),
                    ),
                )
            val week = vm.content().week!!
            assertEquals(133 to 83, week.avgSys to week.avgDia)
            assertEquals(2, week.days.count { it.bar != null })
        }

    @Test
    fun `pulse out of range and missing pulse`() =
        runTest {
            val (vm, repo) = viewModel(listOf(reading("2026-10-20T07:24", sys = 128, dia = 82, pulse = 95)))
            assertTrue(vm.content().last!!.pulseOutOfRange)
            repo.save(reading("2026-10-20T08:00", sys = 128, dia = 82, pulse = null))
            assertNull(vm.content().last!!.pulse)
            assertFalse(vm.content().last!!.pulseOutOfRange)
        }

    @Test
    fun `new and deleted readings update the card`() =
        runTest {
            val (vm, repo) = viewModel(listOf(reading("2026-10-19T21:10", sys = 128, dia = 82)))
            assertEquals(DayLabel.Yesterday, vm.content().last!!.dayLabel)
            val id = repo.save(reading("2026-10-20T09:00", sys = 150, dia = 95))
            assertEquals(150, vm.content().last!!.sys)
            assertEquals(Status.HIGH, vm.content().last!!.status)
            repo.softDelete(id)
            assertEquals(128, vm.content().last!!.sys)
        }

    @Test
    fun `next reminder today, and null when all are off (D-23)`() =
        runTest {
            val (vm, _) = viewModel()
            assertEquals(
                NextReminderLabel.Today(LocalTime.of(21, 0), "Вечернее измерение"),
                vm.content().nextReminder,
            )
            reminders.getAll().forEach { reminders.save(it.copy(enabled = false)) }
            assertNull(vm.content().nextReminder)
        }

    @Test
    fun `refresh after midnight moves today, the greeting and the week`() =
        runTest {
            // 2026-10-20 23:30 Moscow.
            val time = ManualTimeProvider(Instant.parse("2026-10-20T20:30:00Z"), ZoneId.of("Europe/Moscow"))
            val (vm, _) = viewModel(listOf(reading("2026-10-20T07:24", sys = 128, dia = 82)), time)
            assertEquals(GreetingSlot.NIGHT, vm.content().greeting)
            assertEquals(DayLabel.Today, vm.content().last!!.dayLabel)

            time.advance(Duration.ofHours(1))
            // Nothing changes until the screen asks for it.
            assertEquals(LocalDate.of(2026, 10, 20), vm.content().today)
            vm.refresh()

            val s = vm.content()
            assertEquals(LocalDate.of(2026, 10, 21), s.today)
            assertEquals(DayLabel.Yesterday, s.last!!.dayLabel)
            val week = s.week!!
            assertEquals(LocalDate.of(2026, 10, 21), week.days.last().date)
            assertNull(week.days.last().bar)
            assertNotNull(week.days[week.days.size - 2].bar)
        }

    @Test
    fun `refresh after a zone change uses the new zone`() =
        runTest {
            // 2026-10-20 09:12 in Moscow is 13:12 in Novosibirsk.
            val time = ManualTimeProvider(Instant.parse("2026-10-20T06:12:00Z"), ZoneId.of("Europe/Moscow"))
            val (vm, _) = viewModel(time = time)
            assertEquals(GreetingSlot.MORNING, vm.content().greeting)
            time.moveTo(ZoneId.of("Asia/Novosibirsk"))
            vm.refresh()
            assertEquals(GreetingSlot.DAY, vm.content().greeting)
            assertEquals(NextReminderLabel.Today(LocalTime.of(21, 0), "Вечернее измерение"), vm.content().nextReminder)
        }

    @Test
    fun `banner when notifications are denied and a reminder is on`() =
        runTest {
            permissions.notifications = false
            val (vm, _) = viewModel()
            assertTrue(vm.content().notificationsBanner)
        }

    @Test
    fun `no banner when every reminder is off`() =
        runTest {
            permissions.notifications = false
            val (vm, _) = viewModel()
            reminders.getAll().forEach { reminders.save(it.copy(enabled = false)) }
            assertFalse(vm.content().notificationsBanner)
        }

    @Test
    fun `no banner when notifications are allowed`() =
        runTest {
            val (vm, _) = viewModel()
            assertFalse(vm.content().notificationsBanner)
        }

    @Test
    fun `refresh reads the permission again`() =
        runTest {
            permissions.notifications = false
            val (vm, _) = viewModel()
            assertTrue(vm.content().notificationsBanner)
            permissions.notifications = true
            assertTrue(vm.content().notificationsBanner)
            vm.refresh()
            assertFalse(vm.content().notificationsBanner)
        }
}
