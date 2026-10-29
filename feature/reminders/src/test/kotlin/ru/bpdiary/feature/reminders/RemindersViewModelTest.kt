package ru.bpdiary.feature.reminders

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.domain.DaysSummary
import ru.bpdiary.domain.fake.FakeReminderPermissions
import ru.bpdiary.domain.fake.FakeReminderRepository
import ru.bpdiary.domain.fake.FakeReminderScheduler
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {
    private val repository = FakeReminderRepository()
    private val scheduler = FakeReminderScheduler()
    private val permissions = FakeReminderPermissions()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): RemindersViewModel {
        val vm = RemindersViewModel(repository, scheduler, permissions)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `the defaults are listed by time with their days and switches`() =
        runTest {
            val items = viewModel().state.value.items!!
            assertEquals(listOf(1L, 2L, 3L), items.map { it.id })
            assertEquals(listOf(DaysSummary.Daily, DaysSummary.Workdays, DaysSummary.Daily), items.map { it.days })
            assertEquals(listOf(true, false, true), items.map { it.enabled })
            assertEquals(LocalTime.of(8, 0), items.first().time)
        }

    @Test
    fun `nothing is shown before the first load`() {
        assertNull(RemindersUiState().items)
    }

    @Test
    fun `a reminder added earlier in the day goes to the top whatever its id`() =
        runTest {
            val vm = viewModel()
            val monday = setOf(DayOfWeek.MONDAY)
            repository.save(Reminder(time = LocalTime.of(7, 0), label = "Рано", days = monday, enabled = true))
            assertEquals(
                "Рано",
                vm.state.value.items!!
                    .first()
                    .label,
            )
            assertEquals(
                DaysSummary.Listed(listOf(DayOfWeek.MONDAY)),
                vm.state.value.items!!
                    .first()
                    .days,
            )
        }

    @Test
    fun `the switch saves and re-plans the alarm`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(RemindersEvent.Toggled(id = 2, enabled = true))
            assertTrue(repository.getById(2)!!.enabled)
            assertEquals(1, scheduler.rescheduleCount)
            vm.onEvent(RemindersEvent.Toggled(id = 1, enabled = false))
            assertFalse(repository.getById(1)!!.enabled)
            assertFalse(
                vm.state.value.items!!
                    .first { it.id == 1L }
                    .enabled,
            )
            assertEquals(2, scheduler.rescheduleCount)
        }

    @Test
    fun `a switch of a deleted reminder changes nothing`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(RemindersEvent.Toggled(id = 99, enabled = true))
            assertEquals(0, scheduler.rescheduleCount)
            assertEquals(
                3,
                vm.state.value.items!!
                    .size,
            )
        }

    @Test
    fun `banners follow the permissions and refresh when the screen returns`() =
        runTest {
            permissions.notifications = false
            permissions.exactAlarms = false
            val vm = viewModel()
            assertTrue(vm.state.value.notificationsMissing)
            assertTrue(vm.state.value.exactAlarmsMissing)

            permissions.notifications = true
            assertTrue("not read again before the refresh", vm.state.value.notificationsMissing)
            vm.onEvent(RemindersEvent.PermissionsRefreshed)
            assertFalse(vm.state.value.notificationsMissing)
            assertTrue(vm.state.value.exactAlarmsMissing)
        }

    @Test
    fun `no banners when everything is allowed`() =
        runTest {
            val state = viewModel().state.value
            assertFalse(state.notificationsMissing)
            assertFalse(state.exactAlarmsMissing)
        }
}
