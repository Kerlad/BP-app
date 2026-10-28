package ru.bpdiary.feature.home

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.bpdiary.core.common.DemoData
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.GreetingSlot
import ru.bpdiary.domain.NextReminderLabel
import ru.bpdiary.domain.fake.FakeReminderRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class HomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null
    private val time = FixedTimeProvider.default()
    private val now = time.now().atZone(time.zone())
    private val reminders = FakeReminderRepository.defaults()

    private fun state(
        readings: List<Reading>,
        reminders: List<Reminder> = this.reminders,
    ) = homeContent(
        now = now,
        last = readings.maxByOrNull { it.takenAt },
        window = WeekWindow(now.toLocalDate(), readings),
        th = Thresholds(),
        reminders = reminders,
    )

    private val one =
        listOf(
            Reading(
                takenAt = Instant.parse("2026-10-20T04:24:00Z"),
                zoneOffset = ZoneOffset.ofHours(3),
                sys = 128,
                dia = 82,
                pulse = 71,
                arm = Arm.LEFT,
            ),
        )

    private val empty = state(emptyList())
    private val single = state(one)
    private val demo = state(DemoData.readings(time))
    private val remindersOff = state(DemoData.readings(time), reminders.map { it.copy(enabled = false) })

    private fun show(
        state: HomeUiState,
        direction: Direction = Direction.SAGE,
        dark: Boolean = false,
        fontScale: Float = 1f,
        onEvent: (HomeEvent) -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            Scaled(fontScale) {
                BpTheme(direction, dark) { HomeScreen(state, onEvent) }
            }
        }
    }

    private fun snapshot(
        name: String,
        state: HomeUiState,
        direction: Direction = Direction.SAGE,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        show(state, direction, dark, fontScale)
        // The short empty screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun emptySageLight() = snapshot("home_empty_sage_light", empty)

    @Test
    fun emptySageDark() = snapshot("home_empty_sage_dark", empty, dark = true)

    @Test
    fun singleSageLight() = snapshot("home_single_sage_light", single)

    @Test
    fun singleSageDark() = snapshot("home_single_sage_dark", single, dark = true)

    @Test
    fun demoSageLight() = snapshot("home_demo_sage_light", demo)

    @Test
    fun demoSageDark() = snapshot("home_demo_sage_dark", demo, dark = true)

    @Test
    fun demoPeachLight() = snapshot("home_demo_peach_light", demo, Direction.PEACH)

    @Test
    fun demoPlumDark() = snapshot("home_demo_plum_dark", demo, Direction.PLUM, dark = true)

    @Test
    fun remindersOffSageLight() = snapshot("home_reminders_off_sage_light", remindersOff)

    @Test
    fun remindersOffSageDark() = snapshot("home_reminders_off_sage_dark", remindersOff, dark = true)

    @Test
    fun demoSageLightFontScale2() = snapshot("home_demo_sage_light_fs2", demo, fontScale = 2f)

    @Test
    fun emptySageDarkFontScale2() = snapshot("home_empty_sage_dark_fs2", empty, dark = true, fontScale = 2f)

    @Test
    fun `texts of the demo state`() {
        show(demo)
        rule.onNodeWithText("Вторник, 20 октября").assertExists()
        rule.onNodeWithText("Доброе утро").assertExists()
        rule.onNodeWithText("Записать давление").assertExists()
        rule.onNodeWithText("7 дней").assertExists()
        rule.onNodeWithText("Сег").assertExists()
        rule.onNodeWithText("Следующее напоминание").assertExists()
        rule.onNodeWithText("21:00 · Вечернее измерение").assertExists()
        rule.onNodeWithContentDescription("Уровни давления").assertExists()
    }

    @Test
    fun `single reading shows its card and its own average`() {
        show(single)
        rule.onNodeWithText("Сегодня, 07:24").assertExists()
        rule.onNodeWithContentDescription("128 на 82 миллиметра ртутного столба").assertExists()
        rule.onNodeWithContentDescription("Статус: в норме").assertExists()
        rule.onNodeWithText("уд/мин · Левая рука").assertExists()
        rule.onNodeWithText("среднее 128/82").assertExists()
        rule.onNodeWithContentDescription("График за 7 дней, среднее 128 на 82").assertExists()
    }

    @Test
    fun `empty state has the welcome card and no week card`() {
        show(empty)
        rule.onNodeWithText("Запишите первое измерение, и здесь появится ваша динамика").assertExists()
        rule.onNodeWithText("7 дней").assertDoesNotExist()
    }

    @Test
    fun `week without readings says so (D-24)`() {
        val old = one.map { it.copy(takenAt = it.takenAt.minusSeconds(TEN_DAYS)) }
        show(state(old))
        rule.onNodeWithText("За неделю измерений нет").assertExists()
        rule.onNodeWithText("Сег").assertExists()
    }

    @Test
    fun `all reminders off (D-23)`() {
        show(remindersOff)
        rule.onNodeWithText("Напоминания выключены").performScrollTo().assertExists()
    }

    @Test
    fun `controls send their events`() {
        val events = mutableListOf<HomeEvent>()
        show(demo) { events += it }
        rule.onNodeWithContentDescription("Уровни давления").performClick()
        rule.onNodeWithText("Записать давление").performClick()
        rule.onNodeWithText("7 дней").performScrollTo().performClick()
        rule.onNodeWithText("Следующее напоминание").performScrollTo().performClick()
        assertEquals(
            listOf(HomeEvent.LevelsClicked, HomeEvent.AddClicked, HomeEvent.WeekClicked, HomeEvent.ReminderClicked),
            events,
        )
    }

    @Test
    fun `next reminder texts (docs 06 §6 6)`() {
        val texts = HomeTexts(ApplicationProvider.getApplicationContext<Context>().resources)
        val label = "Утреннее измерение"
        assertEquals("14:00 · $label", texts.nextReminder(NextReminderLabel.Today(LocalTime.of(14, 0), label)))
        assertEquals(
            "Завтра, 08:00 · $label",
            texts.nextReminder(NextReminderLabel.Tomorrow(LocalTime.of(8, 0), label)),
        )
        assertEquals(
            "Пн, 08:00 · $label",
            texts.nextReminder(NextReminderLabel.Weekday(DayOfWeek.MONDAY, LocalTime.of(8, 0), label)),
        )
        assertEquals("Напоминания выключены", texts.nextReminder(null))
        assertEquals(
            listOf("Доброе утро", "Добрый день", "Добрый вечер", "Доброй ночи"),
            GreetingSlot.entries.map { texts.greeting(it) },
        )
        assertEquals("Правая рука", texts.arm(Arm.RIGHT))
    }

    private companion object {
        const val TEN_DAYS = 10L * 24 * 60 * 60
    }
}

@Composable
private fun Scaled(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
}
