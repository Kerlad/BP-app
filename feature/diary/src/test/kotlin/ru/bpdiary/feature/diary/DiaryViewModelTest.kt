package ru.bpdiary.feature.diary

import androidx.lifecycle.SavedStateHandle
import androidx.paging.testing.asSnapshot
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeThresholdsRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DiaryViewModelTest {
    private val moscow = ZoneOffset.ofHours(3)
    private val thresholds = FakeThresholdsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** [local] is Moscow wall-clock time. */
    private fun reading(
        id: Long,
        local: String,
        sys: Int = 128,
        dia: Int = 82,
    ) = Reading(
        id = id,
        takenAt = LocalDateTime.parse(local).toInstant(moscow),
        zoneOffset = moscow,
        sys = sys,
        dia = dia,
        pulse = 71,
        arm = Arm.LEFT,
    )

    private val sample =
        listOf(
            reading(1, "2026-10-20T07:24", sys = 150, dia = 95),
            reading(2, "2026-10-19T21:10"),
            reading(3, "2026-10-19T07:30", sys = 95, dia = 58),
            reading(4, "2026-10-18T08:00"),
        )

    private fun TestScope.viewModel(
        readings: List<Reading> = sample,
        handle: SavedStateHandle = SavedStateHandle(),
        time: TimeProvider = FixedTimeProvider.default(),
    ): Pair<DiaryViewModel, FakeReadingRepository> {
        val repo = FakeReadingRepository(time, readings)
        val vm = DiaryViewModel(handle, repo, thresholds, time)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm to repo
    }

    /** Headers as dates, rows as ids, in display order. */
    private fun List<DiaryItem>.shape(): List<Any> =
        map {
            when (it) {
                is DiaryItem.Header -> it.day
                is DiaryItem.Row -> it.row.id
            }
        }

    private fun day(d: Int): LocalDate = LocalDate.of(2026, 10, d)

    @Test
    fun `the first state already knows today, before anything is collected`() =
        runTest {
            val time = FixedTimeProvider.default()
            val vm = DiaryViewModel(SavedStateHandle(), FakeReadingRepository(time, sample), thresholds, time)
            // Not subscribed yet: this is what the first frame of the screen sees.
            assertEquals(time.now().atZone(time.zone()).toLocalDate(), vm.state.value.today)
        }

    @Test
    fun `rows are grouped under a header per local day, newest first`() =
        runTest {
            val (vm, _) = viewModel()
            assertEquals(listOf(day(20), 1L, day(19), 2L, 3L, day(18), 4L), vm.items.asSnapshot().shape())
        }

    @Test
    fun `row carries status, time and pulse range from the thresholds`() =
        runTest {
            val (vm, _) = viewModel()
            val rows =
                vm.items
                    .asSnapshot()
                    .filterIsInstance<DiaryItem.Row>()
                    .map { it.row }
            assertEquals(listOf(Status.HIGH, Status.NORMAL, Status.LOW, Status.NORMAL), rows.map { it.status })
            assertEquals(LocalTime.of(7, 24), rows.first().time)
        }

    @Test
    fun `filter keeps only matching rows and their headers`() =
        runTest {
            val (vm, _) = viewModel()
            vm.onEvent(DiaryEvent.FilterSelected(DiaryFilter.NORMAL))
            assertEquals(DiaryFilter.NORMAL, vm.state.value.filter)
            assertEquals(listOf(day(19), 2L, day(18), 4L), vm.items.asSnapshot().shape())
        }

    @Test
    fun `filter survives process death`() =
        runTest {
            val handle = SavedStateHandle()
            viewModel(handle = handle).first.onEvent(DiaryEvent.FilterSelected(DiaryFilter.LOW))
            val (restored, _) = viewModel(handle = handle)
            assertEquals(DiaryFilter.LOW, restored.state.value.filter)
            assertEquals(listOf(day(19), 3L), restored.items.asSnapshot().shape())
        }

    @Test
    fun `threshold change starts a new pager with the new statuses`() =
        runTest {
            val (vm, _) = viewModel()
            vm.onEvent(DiaryEvent.FilterSelected(DiaryFilter.HIGH))
            assertEquals(listOf(day(20), 1L), vm.items.asSnapshot().shape())
            thresholds.set(Thresholds(highSys = 125, highDia = 80))
            assertEquals(listOf(day(20), 1L, day(19), 2L, day(18), 4L), vm.items.asSnapshot().shape())
        }

    @Test
    fun `deleting the last reading of a day removes its header`() =
        runTest {
            val (vm, repo) = viewModel()
            vm.effects.test {
                vm.onEvent(DiaryEvent.DeleteClicked(4))
                assertEquals(DiaryEffect.Deleted(4), awaitItem())
            }
            assertEquals(listOf(day(20), 1L, day(19), 2L, 3L), vm.items.asSnapshot().shape())
            assertEquals(3, vm.state.value.total)

            repo.restore(4)
            assertEquals(listOf(day(20), 1L, day(19), 2L, 3L, day(18), 4L), vm.items.asSnapshot().shape())
        }

    @Test
    fun `deleting one of two readings keeps the header`() =
        runTest {
            val (vm, _) = viewModel()
            vm.onEvent(DiaryEvent.DeleteClicked(2))
            assertEquals(listOf(day(20), 1L, day(19), 3L, day(18), 4L), vm.items.asSnapshot().shape())
        }

    @Test
    fun `empty diary has a total of zero`() =
        runTest {
            val (vm, _) = viewModel(readings = emptyList())
            assertEquals(0, vm.state.value.total)
            assertEquals(emptyList<Any>(), vm.items.asSnapshot().shape())
        }

    @Test
    fun `refresh after midnight moves today`() =
        runTest {
            // 2026-10-20 23:30 Moscow.
            val time = ManualTimeProvider(Instant.parse("2026-10-20T20:30:00Z"), ZoneId.of("Europe/Moscow"))
            val (vm, _) = viewModel(time = time)
            assertEquals(day(20), vm.state.value.today)
            time.advance(Duration.ofHours(1))
            vm.refresh()
            assertEquals(day(21), vm.state.value.today)
        }
}
