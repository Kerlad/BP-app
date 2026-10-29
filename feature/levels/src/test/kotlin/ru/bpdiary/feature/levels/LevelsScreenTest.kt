package ru.bpdiary.feature.levels

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsNotEnabled
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
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.ui.assertAccessible
import ru.bpdiary.domain.THRESHOLD_STEP
import ru.bpdiary.domain.ThresholdField
import ru.bpdiary.domain.ThresholdPreset

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class LevelsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null
    private val home = LevelsUiState(Thresholds(), ThemeMode.SYSTEM)

    /** Limits reached: high SYS at 200 (no «+»), low DIA just below high DIA, the clinic preset not allowed. */
    private val limits =
        LevelsUiState(
            Thresholds(highSys = 200, highDia = 90, lowSys = 70, lowDia = 85, pulseLow = 85, pulseHigh = 90),
            ThemeMode.DARK,
        )

    private fun show(
        state: LevelsUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
        onEvent: (LevelsEvent) -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            Scaled(fontScale) {
                BpTheme(Direction.SAGE, dark) { LevelsScreen(state, onEvent, systemDark = dark) }
            }
        }
    }

    private fun snapshot(
        name: String,
        state: LevelsUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        show(state, dark, fontScale)
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.assertAccessible()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun defaultSageLight() = snapshot("levels_default_sage_light", home)

    @Test
    fun defaultSageDark() = snapshot("levels_default_sage_dark", home, dark = true)

    @Test
    fun limitsSageDark() = snapshot("levels_limits_sage_dark", limits, dark = true)

    @Test
    fun defaultSageLightFontScale2() = snapshot("levels_default_sage_light_fs2", home, fontScale = 2f)

    @Test
    fun `texts and the active preset`() {
        show(home)
        rule.onNodeWithText("Уровни давления").assertExists()
        rule.onNodeWithText("135/85").assertIsSelected()
        rule.onNodeWithText("≥135").assertExists()
        rule.onNodeWithText("≤60").assertExists()
        rule.onNodeWithText("Целевые значения согласуйте с лечащим врачом.").performScrollTo().assertExists()
        rule.onNodeWithText("Настройки").performScrollTo().assertExists()
    }

    @Test
    fun `buttons that would break a rule are disabled`() {
        show(limits)
        rule.onNodeWithContentDescription("Повышенное, Верхнее (САД): увеличить").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Пониженное, Нижнее (ДАД): увеличить").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Пульс в норме, От: увеличить").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("135/85").assertIsNotEnabled()
    }

    @Test
    fun `controls send their events`() {
        val events = mutableListOf<LevelsEvent>()
        show(home) { events += it }
        rule.onNodeWithText("140/90").performClick()
        rule.onNodeWithContentDescription("Повышенное, Верхнее (САД): увеличить").performClick()
        rule.onNodeWithContentDescription("Пониженное, Нижнее (ДАД): уменьшить").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Тёмная тема").performScrollTo().performClick()
        rule.onNodeWithText("Настройки").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Назад").performScrollTo().performClick()
        assertEquals(
            listOf(
                LevelsEvent.PresetSelected(ThresholdPreset.CLINIC),
                LevelsEvent.Step(ThresholdField.HIGH_SYS, THRESHOLD_STEP),
                LevelsEvent.Step(ThresholdField.LOW_DIA, -THRESHOLD_STEP),
                LevelsEvent.DarkThemeChanged(true),
                LevelsEvent.SettingsClicked,
                LevelsEvent.BackClicked,
            ),
            events,
        )
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
