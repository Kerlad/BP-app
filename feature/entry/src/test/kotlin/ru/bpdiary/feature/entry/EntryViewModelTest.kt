package ru.bpdiary.feature.entry

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Field
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.DayLabel
import ru.bpdiary.domain.Key
import ru.bpdiary.domain.Reason
import ru.bpdiary.domain.ValidationError
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeSettingsRepository
import ru.bpdiary.domain.fake.FakeThresholdsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EntryViewModelTest {
    private val time = FixedTimeProvider.default()
    private val moscow = ZoneOffset.ofHours(3)
    private val readings = FakeReadingRepository(time)
    private val settings = FakeSettingsRepository()
    private var thresholds = FakeThresholdsRepository()

    /** Yesterday 21:40 Moscow, edited in the edit-flow tests. */
    private val stored =
        Reading(
            id = 7,
            takenAt = Instant.parse("2026-10-19T18:40:00Z"),
            zoneOffset = moscow,
            sys = 128,
            dia = 82,
            pulse = 71,
            arm = Arm.RIGHT,
            tags = setOf(Tag.HEADACHE),
            comment = "после кофе",
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        readingId: Long? = null,
        handle: SavedStateHandle = SavedStateHandle(),
    ): EntryViewModel {
        val vm = EntryViewModel(readingId, handle, readings, thresholds, settings, time)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun EntryViewModel.type(digits: String) = digits.forEach { onEvent(EntryEvent.KeyPressed(Key.Digit(it))) }

    @Test
    fun `typing 128 82 71 fills the fields with auto advance`() =
        runTest {
            val vm = viewModel()
            vm.type("1288271")
            val s = vm.state.value
            assertEquals("128", s.draft.sys)
            assertEquals("82", s.draft.dia)
            assertEquals("71", s.draft.pulse)
            assertEquals(Field.PULSE, s.draft.field)
            assertTrue(s.canSave)
            assertEquals(LiveStatus.Classified(Status.NORMAL, Hint.Within), s.liveStatus)
            assertEquals(DayLabel.Today, s.dayLabel)
            assertEquals(LocalTime.of(9, 12), s.time)
        }

    @Test
    fun `check key on the pulse saves the reading and goes home`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.type("1288271")
                vm.onEvent(EntryEvent.KeyPressed(Key.Next))
                assertEquals(EntryEffect.Saved(readingId = 1, edited = false, critical = false), awaitItem())
            }
            val saved = readings.getById(1)!!
            assertEquals(128, saved.sys)
            assertEquals(82, saved.dia)
            assertEquals(71, saved.pulse)
            assertEquals(Arm.LEFT, saved.arm)
            assertEquals(time.now(), saved.takenAt)
            assertEquals(moscow, saved.zoneOffset)
        }

    @Test
    fun `live status waits for two digits in both pressures`() =
        runTest {
            val vm = viewModel()
            vm.type("14")
            assertEquals(LiveStatus.Empty, vm.state.value.liveStatus)
            vm.type("59")
            assertEquals(LiveStatus.Empty, vm.state.value.liveStatus)
            vm.type("5")
            assertEquals(LiveStatus.Classified(Status.HIGH, Hint.Above(135, 85)), vm.state.value.liveStatus)
        }

    @Test
    fun `live status uses the current thresholds`() =
        runTest {
            thresholds = FakeThresholdsRepository(Thresholds(highSys = 150, highDia = 95, lowSys = 105, lowDia = 65))
            val vm = viewModel()
            vm.type("9562")
            assertEquals(LiveStatus.Classified(Status.LOW, Hint.Below(105, 65)), vm.state.value.liveStatus)
            thresholds.set(Thresholds(lowSys = 90, lowDia = 60))
            assertEquals(LiveStatus.Classified(Status.NORMAL, Hint.Within), vm.state.value.liveStatus)
        }

    @Test
    fun `save is disabled until all three fields are filled`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.type("12882")
                assertFalse(vm.state.value.canSave)
                vm.onEvent(EntryEvent.KeyPressed(Key.Next))
                vm.onEvent(EntryEvent.SaveClicked)
                expectNoEvents()
            }
            assertEquals(0, readings.storedCount)
        }

    @Test
    fun `habitual arrow after auto advance keeps focus on dia`() =
        runTest {
            val vm = viewModel()
            vm.type("142")
            vm.onEvent(EntryEvent.KeyPressed(Key.Next))
            vm.type("9176")
            val s = vm.state.value
            assertEquals("142", s.draft.sys)
            assertEquals("91", s.draft.dia)
            assertEquals("76", s.draft.pulse)
        }

    @Test
    fun `digit after tapping a filled field replaces it`() =
        runTest {
            readings.save(stored)
            val vm = viewModel(readingId = 7)
            vm.state.first { !it.loading }
            vm.onEvent(EntryEvent.FieldTapped(Field.SYS))
            vm.type("1")
            assertEquals("1", vm.state.value.draft.sys)
            vm.type("40")
            assertEquals("140", vm.state.value.draft.sys)
            assertEquals(Field.DIA, vm.state.value.draft.field)
        }

    @Test
    fun `first digit right after opening an edit replaces the sys value`() =
        runTest {
            readings.save(stored)
            val vm = viewModel(readingId = 7)
            vm.state.first { !it.loading }
            vm.type("1")
            assertEquals("1", vm.state.value.draft.sys)
            assertEquals("82", vm.state.value.draft.dia)
        }

    @Test
    fun `save on incomplete data highlights the empty field and focuses it`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.type("142")
                vm.onEvent(EntryEvent.FieldTapped(Field.PULSE))
                vm.onEvent(EntryEvent.SaveClicked)
                val s = vm.state.value
                assertEquals(Field.DIA, s.missing)
                assertEquals(Field.DIA, s.draft.field)
                expectNoEvents()
                vm.type("9")
                assertNull(vm.state.value.missing)
            }
            assertEquals(0, readings.storedCount)
        }

    @Test
    fun `sys not greater than dia is shown as an error and nothing is saved`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.type("809070")
                vm.onEvent(EntryEvent.SaveClicked)
                expectNoEvents()
            }
            assertEquals(ValidationError(Field.SYS, Reason.SYS_NOT_GREATER), vm.state.value.error)
            assertEquals(0, readings.storedCount)

            vm.onEvent(EntryEvent.KeyPressed(Key.Del))
            assertNull(vm.state.value.error)
        }

    @Test
    fun `out of range values are reported per field`() =
        runTest {
            val vm = viewModel()
            // "30" and "20" can still grow into 300 and 200, so the fields are switched by tapping (A-01).
            vm.type("30")
            vm.onEvent(EntryEvent.FieldTapped(Field.DIA))
            vm.type("20")
            vm.onEvent(EntryEvent.FieldTapped(Field.PULSE))
            vm.type("70")
            vm.onEvent(EntryEvent.SaveClicked)
            assertEquals(ValidationError(Field.SYS, Reason.SYS_RANGE), vm.state.value.error)

            vm.onEvent(EntryEvent.FieldTapped(Field.SYS))
            vm.onEvent(EntryEvent.KeyPressed(Key.Del))
            vm.onEvent(EntryEvent.KeyPressed(Key.Del))
            vm.type("12")
            vm.type("0")
            vm.onEvent(EntryEvent.SaveClicked)
            assertEquals(ValidationError(Field.DIA, Reason.DIA_RANGE), vm.state.value.error)

            vm.onEvent(EntryEvent.KeyPressed(Key.Del))
            vm.onEvent(EntryEvent.KeyPressed(Key.Del))
            vm.type("80")
            vm.onEvent(EntryEvent.KeyPressed(Key.Del))
            vm.onEvent(EntryEvent.KeyPressed(Key.Del))
            vm.type("9")
            vm.onEvent(EntryEvent.SaveClicked)
            assertEquals(ValidationError(Field.PULSE, Reason.PULSE_RANGE), vm.state.value.error)
        }

    @Test
    fun `critical values are flagged for the hint dialog`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.type("1859570")
                vm.onEvent(EntryEvent.SaveClicked)
                assertEquals(EntryEffect.Saved(readingId = 1, edited = false, critical = true), awaitItem())
            }
        }

    @Test
    fun `arm defaults to the last arm and the choice is remembered on save`() =
        runTest {
            settings.setLastArm(Arm.RIGHT)
            val vm = viewModel()
            assertEquals(Arm.RIGHT, vm.state.value.arm)
            vm.onEvent(EntryEvent.ArmSelected(Arm.LEFT))
            vm.type("1288271")
            vm.onEvent(EntryEvent.SaveClicked)
            assertEquals(Arm.LEFT, settings.lastArm.first())
            assertEquals(Arm.LEFT, readings.getById(1)!!.arm)
        }

    @Test
    fun `the comment is not cut inside an emoji`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(EntryEvent.CommentChanged("а".repeat(119) + "😀"))
            assertEquals(119, vm.state.value.comment.length)
        }

    @Test
    fun `tags and comment are saved and the comment is cut at 120 characters`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(EntryEvent.TagToggled(Tag.GOOD))
            vm.onEvent(EntryEvent.TagToggled(Tag.AFTER_MEDICINE))
            vm.onEvent(EntryEvent.TagToggled(Tag.GOOD))
            vm.onEvent(EntryEvent.CommentChanged("а".repeat(130)))
            assertEquals(120, vm.state.value.comment.length)
            vm.type("1288271")
            vm.onEvent(EntryEvent.SaveClicked)
            val saved = readings.getById(1)!!
            assertEquals(setOf(Tag.AFTER_MEDICINE), saved.tags)
            assertEquals("а".repeat(120), saved.comment)
        }

    @Test
    fun `closing an empty draft leaves at once`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(EntryEvent.CloseClicked)
                assertEquals(EntryEffect.Closed, awaitItem())
            }
        }

    @Test
    fun `closing a started draft asks first`() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.type("1")
                vm.onEvent(EntryEvent.CloseClicked)
                assertEquals(EntryDialog.Discard, vm.state.value.dialog)
                vm.onEvent(EntryEvent.DialogDismissed)
                assertNull(vm.state.value.dialog)
                assertEquals("1", vm.state.value.draft.sys)
                vm.onEvent(EntryEvent.CloseClicked)
                vm.onEvent(EntryEvent.DiscardConfirmed)
                assertEquals(EntryEffect.Closed, awaitItem())
            }
        }

    @Test
    fun `a tag alone also counts as a started draft`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(EntryEvent.TagToggled(Tag.WEAKNESS))
            vm.onEvent(EntryEvent.CloseClicked)
            assertEquals(EntryDialog.Discard, vm.state.value.dialog)
        }

    @Test
    fun `draft survives process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = viewModel(handle = handle)
            first.type("128")
            first.onEvent(EntryEvent.ArmSelected(Arm.RIGHT))
            first.onEvent(EntryEvent.TagToggled(Tag.DIZZINESS))
            first.onEvent(EntryEvent.CommentChanged("утром"))

            val second = viewModel(handle = handle)
            val s = second.state.value
            assertEquals("128", s.draft.sys)
            assertEquals(Field.DIA, s.draft.field)
            assertEquals(Arm.RIGHT, s.arm)
            assertEquals(setOf(Tag.DIZZINESS), s.tags)
            assertEquals("утром", s.comment)
        }

    @Test
    fun `edit mode is prefilled and can be saved at once`() =
        runTest {
            readings.save(stored)
            val vm = viewModel(readingId = 7)
            val s = vm.state.value
            assertTrue(s.isEdit)
            assertEquals("128", s.draft.sys)
            assertEquals("82", s.draft.dia)
            assertEquals("71", s.draft.pulse)
            assertEquals(Arm.RIGHT, s.arm)
            assertEquals(setOf(Tag.HEADACHE), s.tags)
            assertEquals("после кофе", s.comment)
            assertEquals(DayLabel.Yesterday, s.dayLabel)
            assertEquals(LocalTime.of(21, 40), s.time)
            assertTrue(s.canSave)
        }

    @Test
    fun `edit saves over the same reading and keeps its time`() =
        runTest {
            readings.save(stored)
            val vm = viewModel(readingId = 7)
            vm.effects.test {
                vm.onEvent(EntryEvent.KeyPressed(Key.Del))
                vm.type("5")
                vm.onEvent(EntryEvent.SaveClicked)
                assertEquals(EntryEffect.Saved(readingId = 7, edited = true, critical = false), awaitItem())
            }
            assertEquals(stored.copy(sys = 125), readings.getById(7))
            assertEquals(1, readings.storedCount)
        }

    @Test
    fun `closing an unchanged edit leaves at once, a changed one asks`() =
        runTest {
            readings.save(stored)
            val vm = viewModel(readingId = 7)
            vm.effects.test {
                vm.onEvent(EntryEvent.FieldTapped(Field.PULSE))
                vm.onEvent(EntryEvent.CloseClicked)
                assertEquals(EntryEffect.Closed, awaitItem())
                vm.onEvent(EntryEvent.TagToggled(Tag.GOOD))
                vm.onEvent(EntryEvent.CloseClicked)
                assertEquals(EntryDialog.Discard, vm.state.value.dialog)
            }
        }

    @Test
    fun `delete asks for confirmation, then soft deletes`() =
        runTest {
            readings.save(stored)
            val vm = viewModel(readingId = 7)
            vm.effects.test {
                vm.onEvent(EntryEvent.DeleteClicked)
                assertEquals(EntryDialog.Delete, vm.state.value.dialog)
                vm.onEvent(EntryEvent.DeleteConfirmed)
                assertEquals(EntryEffect.Deleted(7), awaitItem())
            }
            assertNull(readings.getById(7))
            assertEquals(1, readings.storedCount)
        }

    @Test
    fun `edit restored after process death keeps the user's changes`() =
        runTest {
            readings.save(stored)
            val handle = SavedStateHandle()
            val first = viewModel(readingId = 7, handle = handle)
            first.onEvent(EntryEvent.CommentChanged("исправлено"))

            val second = viewModel(readingId = 7, handle = handle)
            assertEquals("исправлено", second.state.value.comment)
        }

    @Test
    fun `an imported reading without pulse can be edited without adding one`() =
        runTest {
            readings.save(stored.copy(pulse = null))
            val vm = viewModel(readingId = 7)
            assertTrue(vm.state.value.canSave)
            vm.onEvent(EntryEvent.SaveClicked)
            assertNull(readings.getById(7)!!.pulse)
        }

    @Test
    fun `a missing reading closes the screen`() =
        runTest {
            val vm = viewModel(readingId = 99)
            vm.effects.test {
                assertEquals(EntryEffect.Closed, awaitItem())
            }
        }

    @Test
    fun `a failed write shows the error and Save works again`() =
        runTest {
            val vm = viewModel()
            readings.failWrites = true
            vm.effects.test {
                vm.type("1288271")
                vm.onEvent(EntryEvent.SaveClicked)
                assertTrue(vm.state.value.saveFailed)
                expectNoEvents()

                readings.failWrites = false
                vm.onEvent(EntryEvent.SaveClicked)
                assertEquals(EntryEffect.Saved(readingId = 1, edited = false, critical = false), awaitItem())
            }
            assertFalse(vm.state.value.saveFailed)
        }

    @Test
    fun `the time chip picks a date, then a time (V1-1)`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(EntryEvent.TimeClicked)
            assertEquals(EntryDialog.PickDate, vm.state.value.dialog)
            assertEquals(LocalDate.of(2026, 10, 20), vm.state.value.today)
            vm.onEvent(EntryEvent.DateChosen(LocalDate.of(2026, 10, 19)))
            assertEquals(EntryDialog.PickTime, vm.state.value.dialog)
            vm.onEvent(EntryEvent.TimeChosen(LocalTime.of(21, 40)))
            val s = vm.state.value
            assertNull(s.dialog)
            assertEquals(DayLabel.Yesterday, s.dayLabel)
            assertEquals(LocalTime.of(21, 40), s.time)

            vm.effects.test {
                vm.type("1288271")
                vm.onEvent(EntryEvent.SaveClicked)
                awaitItem()
            }
            val saved = readings.getById(1)!!
            assertEquals(Instant.parse("2026-10-19T18:40:00Z"), saved.takenAt)
            assertEquals(moscow, saved.zoneOffset)
        }

    @Test
    fun `a time in the future is refused with the future time error`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(EntryEvent.TimeClicked)
            vm.onEvent(EntryEvent.DateChosen(LocalDate.of(2026, 10, 20)))
            vm.onEvent(EntryEvent.TimeChosen(LocalTime.of(10, 0)))
            val s = vm.state.value
            assertNull(s.dialog)
            assertEquals(LocalTime.of(9, 12), s.time)
            assertEquals(Reason.FUTURE_TIME, s.error?.reason)
        }

    @Test
    fun `dismissing the pickers keeps the time`() =
        runTest {
            val vm = viewModel()
            vm.onEvent(EntryEvent.TimeClicked)
            vm.onEvent(EntryEvent.DateChosen(LocalDate.of(2026, 10, 18)))
            vm.onEvent(EntryEvent.DialogDismissed)
            assertNull(vm.state.value.dialog)
            assertEquals(DayLabel.Today, vm.state.value.dayLabel)
            assertEquals(LocalTime.of(9, 12), vm.state.value.time)
        }

    @Test
    fun `an edit moves the reading to the chosen moment`() =
        runTest {
            readings.save(stored)
            val vm = viewModel(readingId = 7)
            vm.onEvent(EntryEvent.TimeClicked)
            vm.onEvent(EntryEvent.DateChosen(LocalDate.of(2026, 10, 18)))
            vm.onEvent(EntryEvent.TimeChosen(LocalTime.of(8, 0)))
            vm.effects.test {
                vm.onEvent(EntryEvent.SaveClicked)
                assertEquals(EntryEffect.Saved(readingId = 7, edited = true, critical = false), awaitItem())
            }
            assertEquals(Instant.parse("2026-10-18T05:00:00Z"), readings.getById(7)!!.takenAt)
        }
}
