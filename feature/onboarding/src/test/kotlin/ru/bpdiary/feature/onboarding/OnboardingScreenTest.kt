package ru.bpdiary.feature.onboarding

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.ThresholdPreset
import java.time.LocalTime

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class OnboardingScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null

    private val reminders =
        listOf(
            OnboardingReminder(1, LocalTime.of(8, 0), "Утреннее измерение", true),
            OnboardingReminder(2, LocalTime.of(14, 0), "Дневное измерение", false),
            OnboardingReminder(3, LocalTime.of(21, 0), "Вечернее измерение", true),
        )
    private val first = OnboardingUiState(step = 0)
    private val levels = OnboardingUiState(step = 1, thresholds = Thresholds())
    private val remindersStep = OnboardingUiState(step = 2, thresholds = Thresholds(), reminders = reminders)
    private val withHints = remindersStep.copy(notificationsMissing = true, exactAlarmsMissing = true)

    private fun show(
        state: OnboardingUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
        openSettings: Boolean = false,
        onEvent: (OnboardingEvent) -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            Scaled(fontScale) {
                BpTheme(
                    Direction.SAGE,
                    dark,
                ) { OnboardingScreen(state, onEvent, notificationsOpenSettings = openSettings) }
            }
        }
    }

    private fun snapshot(
        name: String,
        state: OnboardingUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        show(state, dark, fontScale)
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun purposeSageLight() = snapshot("onboarding_purpose_sage_light", first)

    @Test
    fun levelsSageLight() = snapshot("onboarding_levels_sage_light", levels)

    @Test
    fun remindersSageDark() = snapshot("onboarding_reminders_sage_dark", remindersStep, dark = true)

    @Test
    fun remindersWithHintsSageLight() = snapshot("onboarding_reminders_hints_sage_light", withHints)

    @Test
    fun remindersWithHintsSageLightFontScale2() =
        snapshot("onboarding_reminders_hints_sage_light_fs2", withHints, fontScale = 2f)

    @Test
    fun `the purpose step names the diary and what it is for`() {
        show(first)
        rule.onNodeWithText("Дневник давления").assertExists()
        rule.onNodeWithText("Записывайте измерения, следите за динамикой и показывайте историю врачу.").assertExists()
        rule.onNodeWithContentDescription("Шаг 1 из 3").assertExists()
    }

    @Test
    fun `the levels step marks the preset that matches`() {
        show(levels)
        rule.onNodeWithText("Ваши уровни").assertExists()
        rule.onNodeWithText("135/85").assertIsSelected()
        rule.onNodeWithText("140/90").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Шаг 2 из 3").assertExists()
    }

    @Test
    fun `a preset that would break the low levels cannot be chosen`() {
        val limits = Thresholds(highSys = 200, highDia = 90, lowSys = 70, lowDia = 85, pulseLow = 85, pulseHigh = 90)
        show(OnboardingUiState(step = 1, thresholds = limits))
        rule.onNodeWithText("135/85").assertIsNotEnabled()
        rule.onNodeWithText("140/90").assertIsEnabled()
    }

    @Test
    fun `the reminders step lists the reminders with their switches`() {
        show(remindersStep)
        rule.onNodeWithText("Утреннее измерение").assertExists()
        rule.onNodeWithContentDescription("Утреннее измерение, 08:00").assertIsOn()
        rule.onNodeWithContentDescription("Дневное измерение, 14:00").assertIsOff()
        rule.onNodeWithText("Разрешить").assertDoesNotExist()
        rule.onNodeWithText("Напоминания могут приходить с задержкой").assertDoesNotExist()
    }

    @Test
    fun `missing permissions are explained and the next button stays active`() {
        show(withHints)
        rule.onNodeWithText("Чтобы напоминания приходили, разрешите уведомления").assertExists()
        rule.onNodeWithText("Разрешить").assertExists()
        rule.onNodeWithText("Напоминания могут приходить с задержкой").assertExists()
        rule.onNodeWithText("Настроить").assertExists()
        rule.onNodeWithText("Далее").assertIsEnabled()
    }

    @Test
    fun `after the second denial the notification button opens the settings`() {
        show(withHints, openSettings = true)
        rule.onNodeWithText("Разрешить").assertDoesNotExist()
        rule.onNodeWithText("Открыть настройки").assertExists()
    }

    @Test
    fun `controls send their events`() {
        val events = mutableListOf<OnboardingEvent>()
        show(withHints) { events += it }
        rule.onNodeWithContentDescription("Дневное измерение, 14:00").performClick()
        rule.onNodeWithText("Разрешить").performClick()
        rule.onNodeWithText("Настроить").performClick()
        rule.onNodeWithText("Далее").performClick()
        assertEquals(
            listOf(
                OnboardingEvent.ReminderToggled(2, enabled = true),
                OnboardingEvent.NotificationsClicked,
                OnboardingEvent.ExactAlarmsClicked,
                OnboardingEvent.NextClicked,
            ),
            events,
        )
    }

    @Test
    fun `a preset card sends its preset`() {
        val events = mutableListOf<OnboardingEvent>()
        show(levels) { events += it }
        rule.onNodeWithText("140/90").performClick()
        assertEquals(listOf(OnboardingEvent.PresetSelected(ThresholdPreset.CLINIC)), events)
    }
}

@Composable
internal fun Scaled(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
}
