package ru.bpdiary.feature.charts

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.core.common.DemoData
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.common.ManualTimeProvider
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeThresholdsRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Expected numbers come from `domain/src/test/resources/stats_golden.json` (independent JS run of the prototype). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChartsViewModelTest {
    private val thresholds = FakeThresholdsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        readings: List<Reading> = DemoData.readings(),
        handle: SavedStateHandle = SavedStateHandle(),
        time: TimeProvider = FixedTimeProvider.default(),
    ): ChartsViewModel {
        val vm = ChartsViewModel(handle, FakeReadingRepository(time, readings), thresholds, time)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    /** The state is built on Dispatchers.Default, so wait for it instead of reading `value`. */
    private suspend fun ChartsViewModel.loaded(match: (ChartsUiState) -> Boolean = { true }) =
        state.first { !it.loading && match(it) }

    @Test
    fun `7 days match the golden statistics`() =
        runTest {
            val s = viewModel().loaded()
            assertEquals(Period.DAYS_7, s.period)
            assertEquals(LocalDate.of(2026, 10, 14), s.from)
            val stats = s.stats!!
            assertEquals(13, stats.count)
            assertEquals(133 to 85, stats.avgSys to stats.avgDia)
            assertEquals(72, stats.avgPulse)
            assertEquals(Triple(130, 83, 7), stats.morning!!.let { Triple(it.sys, it.dia, it.count) })
            assertEquals(Triple(138, 87, 6), stats.evening!!.let { Triple(it.sys, it.dia, it.count) })
            assertEquals(Instant.parse("2026-10-15T18:27:00Z"), stats.max.takenAt)
            assertEquals(Instant.parse("2026-10-14T04:26:00Z"), stats.min.takenAt)
            assertEquals(Triple(6, 7, 0), Triple(stats.countHigh, stats.countNormal, stats.countLow))
            assertEquals(54, stats.inRangePercent)
            assertEquals(13, s.chart!!.points.size)
            assertFalse(s.chart!!.clipped)
        }

    @Test
    fun `30 days match the golden statistics`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(ChartsEvent.PeriodSelected(Period.DAYS_30))
            val s = vm.loaded { it.period == Period.DAYS_30 }
            assertEquals(LocalDate.of(2026, 9, 21), s.from)
            val stats = s.stats!!
            assertEquals(59, stats.count)
            assertEquals(134 to 86, stats.avgSys to stats.avgDia)
            assertEquals(71, stats.avgPulse)
            assertEquals(30 to 29, stats.morning!!.count to stats.evening!!.count)
            assertEquals(Instant.parse("2026-09-23T04:17:00Z"), stats.min.takenAt)
            assertEquals(Triple(41, 18, 0), Triple(stats.countHigh, stats.countNormal, stats.countLow))
            assertEquals(31, stats.inRangePercent)
        }

    @Test
    fun `fewer than two readings - no chart and no statistics`() =
        runTest {
            val one = DemoData.readings().maxBy { it.takenAt }
            val s = viewModel(readings = listOf(one)).loaded()
            assertNull(s.chart)
            assertNull(s.stats)
            assertNull(viewModel(readings = emptyList()).loaded().chart)
        }

    @Test
    fun `threshold change recounts statuses`() =
        runTest {
            val vm = viewModel()
            val before = vm.loaded()
            assertEquals(6, before.stats!!.countHigh)
            thresholds.set(Thresholds(highSys = 140, highDia = 90))
            val after = vm.loaded { it.stats!!.countHigh != 6 }
            val stats = after.stats!!
            assertEquals(stats.count, stats.countHigh + stats.countNormal + stats.countLow)
            // A higher threshold sits higher on the chart, so its y is smaller.
            assertTrue(after.chart!!.highSysY < before.chart!!.highSysY)
        }

    @Test
    fun `period survives process death`() =
        runTest {
            val handle = SavedStateHandle()
            viewModel(handle = handle).onEvent(ChartsEvent.PeriodSelected(Period.DAYS_30))
            assertEquals(Period.DAYS_30, viewModel(handle = handle).loaded().period)
        }

    @Test
    fun `values outside the axis are marked as clipped`() =
        runTest {
            val two = DemoData.readings().sortedByDescending { it.takenAt }.take(2)
            val s = viewModel(readings = listOf(two[0].copy(sys = 210), two[1])).loaded()
            assertTrue(s.chart!!.clipped)
            assertEquals(210, s.stats!!.max.sys)
        }

    @Test
    fun `refresh after midnight moves the period`() =
        runTest {
            // 2026-10-20 23:30 Moscow.
            val time = ManualTimeProvider(Instant.parse("2026-10-20T20:30:00Z"), ZoneId.of("Europe/Moscow"))
            val vm = viewModel(time = time)
            assertEquals(LocalDate.of(2026, 10, 14), vm.loaded().from)
            time.advance(Duration.ofHours(1))
            vm.refresh()
            val s = vm.loaded { it.today == LocalDate.of(2026, 10, 21) }
            assertEquals(LocalDate.of(2026, 10, 15), s.from)
            assertNotNull(s.stats)
        }
}
