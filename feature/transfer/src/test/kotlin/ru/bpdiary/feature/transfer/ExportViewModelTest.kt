package ru.bpdiary.feature.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeThresholdsRepository
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class ExportViewModelTest {
    private val time = FixedTimeProvider.default()
    private val files = FakeExportFiles()
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val today = LocalDate.of(2026, 10, 20)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun daysAgo(days: Long) =
        Reading(
            takenAt = time.now().minusSeconds(days * 86_400),
            zoneOffset = ZoneOffset.ofHours(3),
            sys = 120,
            dia = 80,
            pulse = 70,
            arm = Arm.LEFT,
        )

    private fun viewModel(vararg readings: Reading) =
        ExportViewModel(
            FakeReadingRepository(time, readings.toList()),
            FakeThresholdsRepository(),
            files,
            resources,
            time,
        )

    @Test
    fun `it opens on 30 days ending today`() {
        val state = viewModel(daysAgo(0), daysAgo(29), daysAgo(30)).state.value
        assertEquals(ExportPeriod.DAYS_30, state.period)
        assertEquals(today.minusDays(29), state.from)
        assertEquals(today, state.to)
        assertEquals(2, state.count)
    }

    @Test
    fun `each period counts its own readings`() {
        val vm = viewModel(daysAgo(0), daysAgo(6), daysAgo(7), daysAgo(89), daysAgo(90))
        vm.onEvent(ExportEvent.PeriodSelected(ExportPeriod.DAYS_7))
        assertEquals(2, vm.state.value.count)
        vm.onEvent(ExportEvent.PeriodSelected(ExportPeriod.DAYS_90))
        assertEquals(4, vm.state.value.count)
        vm.onEvent(ExportEvent.PeriodSelected(ExportPeriod.ALL))
        assertEquals(5, vm.state.value.count)
    }

    @Test
    fun `all the time starts at the first reading`() {
        val vm = viewModel(daysAgo(400), daysAgo(1))
        vm.onEvent(ExportEvent.PeriodSelected(ExportPeriod.ALL))
        assertEquals(today.minusDays(400), vm.state.value.from)
        assertEquals("davlenie_2025-09-15_2026-10-20.csv", vm.state.value.fileName)
    }

    @Test
    fun `an empty diary has nothing to count`() {
        val vm = viewModel()
        vm.onEvent(ExportEvent.PeriodSelected(ExportPeriod.ALL))
        assertEquals(0, vm.state.value.count)
    }

    @Test
    fun `a chosen range replaces the period, a dismissed one leaves it`() {
        val vm = viewModel(daysAgo(10), daysAgo(40))
        vm.onEvent(ExportEvent.PeriodSelected(ExportPeriod.CUSTOM))
        assertTrue(vm.state.value.choosingRange)
        assertEquals(ExportPeriod.DAYS_30, vm.state.value.period)

        vm.onEvent(ExportEvent.RangeDismissed)
        assertFalse(vm.state.value.choosingRange)
        assertEquals(ExportPeriod.DAYS_30, vm.state.value.period)

        vm.onEvent(ExportEvent.PeriodSelected(ExportPeriod.CUSTOM))
        vm.onEvent(ExportEvent.RangeChosen(today.minusDays(41), today.minusDays(39)))
        assertEquals(ExportPeriod.CUSTOM, vm.state.value.period)
        assertEquals(1, vm.state.value.count)
        assertFalse(vm.state.value.choosingRange)
    }

    @Test
    fun `save writes the period to the chosen place`() {
        val vm = viewModel(daysAgo(1), daysAgo(40))
        vm.onEvent(ExportEvent.SaveTargetChosen("content://target"))

        val text = files.saved.getValue("content://target")
        assertTrue(text, text.startsWith("﻿# Дневник давления — экспорт 20.10.2026\r\n"))
        assertTrue(text, text.contains("# Период: 21.09.2026 — 20.10.2026, измерений: 1\r\n"))
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `a failed save says so and a new period clears the message`() {
        files.failure = IOException()
        val vm = viewModel(daysAgo(1))
        vm.onEvent(ExportEvent.SaveTargetChosen("content://target"))
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.saved)

        vm.onEvent(ExportEvent.PeriodSelected(ExportPeriod.DAYS_7))
        assertFalse(vm.state.value.failed)
    }

    @Test
    fun `share hands over a temporary file with the Latin name`() =
        runTest {
            val vm = viewModel(daysAgo(1))
            vm.effects.test {
                vm.onEvent(ExportEvent.ShareClicked)
                val name = "davlenie_2026-09-21_2026-10-20.csv"
                assertEquals(ExportEffect.Share("content://$name"), awaitItem())
                assertTrue(files.shared.containsKey(name))
            }
        }

    @Test
    fun `a failed share says so`() {
        files.failure = IOException()
        val vm = viewModel(daysAgo(1))
        vm.onEvent(ExportEvent.ShareClicked)
        assertTrue(vm.state.value.failed)
    }

    @Test
    fun `what an earlier share left behind is removed at the start`() {
        viewModel()
        assertEquals(1, files.cleared)
    }
}
