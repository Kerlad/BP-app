package ru.bpdiary.feature.reminders

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.domain.DaysSummary
import ru.bpdiary.domain.EVERY_DAY
import ru.bpdiary.domain.WORKDAYS
import ru.bpdiary.domain.fake.FakeReminderRepository
import ru.bpdiary.domain.fake.FakeReminderScheduler
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderEditViewModelTest {
    private val repository = FakeReminderRepository()
    private val scheduler = FakeReminderScheduler()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(id: Long? = null) = ReminderEditViewModel(id, repository, scheduler)

    @Test
    fun `a new reminder starts at noon, every day, ready to edit`() {
        val state = viewModel().state.value
        assertTrue(state.loaded)
        assertTrue(state.isNew)
        assertEquals(LocalTime.NOON, state.time)
        assertEquals(EVERY_DAY, state.days)
        assertEquals(DaysSummary.Daily, state.preset)
        assertTrue(state.canSave)
    }

    @Test
    fun `done saves a new reminder as enabled, re-plans the alarm and closes`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(ReminderEditEvent.TimeChanged(LocalTime.of(9, 30)))
                vm.onEvent(ReminderEditEvent.LabelChanged("  Перед работой  "))
                vm.onEvent(ReminderEditEvent.PresetSelected(DaysSummary.Workdays))
                vm.onEvent(ReminderEditEvent.DoneClicked(defaultLabel = "Измерение"))
                assertEquals(ReminderEditEffect.Closed, awaitItem())
            }
            val saved = repository.getAll().single { it.id > 3 }
            assertEquals(Reminder(saved.id, LocalTime.of(9, 30), "Перед работой", WORKDAYS, enabled = true), saved)
            assertEquals(1, scheduler.rescheduleCount)
        }

    @Test
    fun `an empty name is saved as the default name`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(ReminderEditEvent.LabelChanged("   "))
            vm.onEvent(ReminderEditEvent.DoneClicked(defaultLabel = "Измерение"))
            assertEquals("Измерение", repository.getAll().single { it.id > 3 }.label)
        }

    @Test
    fun `the name is cut to the limit`() {
        val vm = viewModel()
        vm.onEvent(ReminderEditEvent.LabelChanged("а".repeat(LABEL_MAX_LENGTH + 10)))
        assertEquals(LABEL_MAX_LENGTH, vm.state.value.label.length)
    }

    @Test
    fun `without days done is inactive and saves nothing`() =
        runTest {
            val vm = viewModel()
            EVERY_DAY.forEach { vm.onEvent(ReminderEditEvent.DayToggled(it)) }
            assertTrue(
                vm.state.value.days
                    .isEmpty(),
            )
            assertFalse(vm.state.value.canSave)
            vm.onEvent(ReminderEditEvent.DoneClicked("Измерение"))
            assertEquals(3, repository.getAll().size)
            assertEquals(0, scheduler.rescheduleCount)
        }

    @Test
    fun `weekday chips and presets change the days, the preset highlight follows`() {
        val vm = viewModel()
        vm.onEvent(ReminderEditEvent.PresetSelected(DaysSummary.Workdays))
        assertEquals(WORKDAYS, vm.state.value.days)
        assertEquals(DaysSummary.Workdays, vm.state.value.preset)
        vm.onEvent(ReminderEditEvent.DayToggled(DayOfWeek.SATURDAY))
        assertNull(vm.state.value.preset)
        vm.onEvent(ReminderEditEvent.DayToggled(DayOfWeek.FRIDAY))
        assertEquals(WORKDAYS - DayOfWeek.FRIDAY + DayOfWeek.SATURDAY, vm.state.value.days)
        vm.onEvent(ReminderEditEvent.PresetSelected(DaysSummary.Daily))
        assertEquals(EVERY_DAY, vm.state.value.days)
    }

    @Test
    fun `an existing reminder is loaded and keeps its switch when saved`() =
        runTest {
            val vm = viewModel(id = 2)
            val state = vm.state.value
            assertTrue(state.loaded)
            assertFalse(state.isNew)
            assertEquals(LocalTime.of(14, 0), state.time)
            assertEquals("Дневное измерение", state.label)
            assertEquals(WORKDAYS, state.days)

            vm.onEvent(ReminderEditEvent.TimeChanged(LocalTime.of(13, 15)))
            vm.onEvent(ReminderEditEvent.DoneClicked("Измерение"))
            val saved = repository.getById(2)!!
            assertEquals(LocalTime.of(13, 15), saved.time)
            assertFalse("a disabled reminder stays disabled", saved.enabled)
            assertEquals(3, repository.getAll().size)
            assertEquals(1, scheduler.rescheduleCount)
        }

    @Test
    fun `delete asks first, then removes, re-plans and closes`() =
        runTest {
            val vm = viewModel(id = 1)
            vm.onEvent(ReminderEditEvent.DeleteClicked)
            assertTrue(vm.state.value.confirmDelete)
            vm.onEvent(ReminderEditEvent.DialogDismissed)
            assertFalse(vm.state.value.confirmDelete)
            assertEquals(3, repository.getAll().size)

            vm.effects.test {
                vm.onEvent(ReminderEditEvent.DeleteClicked)
                vm.onEvent(ReminderEditEvent.DeleteConfirmed)
                assertEquals(ReminderEditEffect.Closed, awaitItem())
            }
            assertEquals(listOf(2L, 3L), repository.getAll().map { it.id })
            assertEquals(1, scheduler.rescheduleCount)
            assertFalse(vm.state.value.confirmDelete)
        }

    @Test
    fun `a reminder that is gone closes the screen`() =
        runTest {
            val vm = viewModel(id = 99)
            vm.effects.test { assertEquals(ReminderEditEffect.Closed, awaitItem()) }
            assertFalse(vm.state.value.loaded)
        }

    @Test
    fun `close does not save`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(ReminderEditEvent.LabelChanged("Не сохранять"))
                vm.onEvent(ReminderEditEvent.CloseClicked)
                assertEquals(ReminderEditEffect.Closed, awaitItem())
            }
            assertEquals(3, repository.getAll().size)
            assertEquals(0, scheduler.rescheduleCount)
        }
}
