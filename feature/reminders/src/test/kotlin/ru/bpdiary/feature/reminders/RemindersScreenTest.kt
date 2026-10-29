package ru.bpdiary.feature.reminders

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
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
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.ui.assertAccessible
import ru.bpdiary.domain.DaysSummary
import ru.bpdiary.domain.WORKDAYS
import java.time.DayOfWeek
import java.time.LocalTime

/** `./gradlew recordRoborazziDebug` re-records, `verifyRoborazziDebug` compares. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class RemindersScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null

    private val items =
        listOf(
            ReminderItem(1, LocalTime.of(8, 0), "Утреннее измерение", DaysSummary.Daily, true),
            ReminderItem(2, LocalTime.of(14, 0), "Дневное измерение", DaysSummary.Workdays, false),
            ReminderItem(
                3,
                LocalTime.of(21, 0),
                "Вечернее измерение",
                DaysSummary.Listed(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
                true,
            ),
        )
    private val plain = RemindersUiState(items)
    private val banners = RemindersUiState(items, notificationsMissing = true, exactAlarmsMissing = true)

    private fun show(
        state: RemindersUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
        ask: NotificationAsk = NotificationAsk.REQUEST,
        onEvent: (RemindersEvent) -> Unit = {},
    ) {
        rule.setContent {
            view = LocalView.current
            Scaled(fontScale) {
                ru.bpdiary.core.designsystem.BpTheme(Direction.SAGE, dark) {
                    RemindersScreen(state, onEvent, notificationAsk = ask)
                }
            }
        }
    }

    private fun snapshot(
        name: String,
        state: RemindersUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
        ask: NotificationAsk = NotificationAsk.REQUEST,
    ) {
        show(state, dark, fontScale, ask)
        // A short screen was sometimes captured blank: draw the window once more before capturing.
        rule.runOnUiThread { view?.rootView?.invalidate() }
        rule.waitForIdle()
        rule.assertAccessible()
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun listSageLight() = snapshot("reminders_list_sage_light", plain)

    @Test
    fun listSageDark() = snapshot("reminders_list_sage_dark", plain, dark = true)

    @Test
    fun listWithBannersSageLight() = snapshot("reminders_banners_sage_light", banners)

    @Test
    fun listWithBannersSageDarkOpenSettings() =
        snapshot("reminders_banners_sage_dark", banners, dark = true, ask = NotificationAsk.OPEN_SETTINGS)

    @Test
    fun listSageLightFontScale2() = snapshot("reminders_banners_sage_light_fs2", banners, fontScale = 2f)

    @Test
    fun `texts, days and switches`() {
        show(plain)
        rule.onNodeWithText("Напоминания").assertExists()
        rule.onNodeWithText("08:00").assertExists()
        rule.onNodeWithText("Утреннее измерение").assertExists()
        rule.onNodeWithText("Ежедневно").assertExists()
        rule.onNodeWithText("Пн – Пт").assertExists()
        rule.onNodeWithText("Пн, Ср, Пт").assertExists()
        rule.onNodeWithContentDescription("Утреннее измерение, 08:00").assertIsOn()
        rule.onNodeWithContentDescription("Дневное измерение, 14:00").assertIsOff()
        rule.onNodeWithText("Добавить напоминание").assertExists()
    }

    @Test
    fun `no banners when permissions are granted`() {
        show(plain)
        rule.onNodeWithText("Чтобы напоминания приходили, разрешите уведомления").assertDoesNotExist()
        rule.onNodeWithText("Напоминания могут приходить с задержкой").assertDoesNotExist()
    }

    @Test
    fun `banners and the button of the notification banner`() {
        show(banners)
        rule.onNodeWithText("Чтобы напоминания приходили, разрешите уведомления").assertExists()
        rule.onNodeWithText("Напоминания могут приходить с задержкой").assertExists()
        rule.onNodeWithText("Разрешить").assertExists()
    }

    @Test
    fun `after the second denial the button leads to the settings`() {
        show(banners, ask = NotificationAsk.OPEN_SETTINGS)
        rule.onNodeWithText("Разрешить").assertDoesNotExist()
        rule.onNodeWithText("Открыть настройки").assertExists()
    }

    @Test
    fun `nothing but the title before the list is loaded`() {
        show(RemindersUiState())
        rule.onNodeWithText("Добавить напоминание").assertDoesNotExist()
    }

    @Test
    fun `controls send their events`() {
        val events = mutableListOf<RemindersEvent>()
        show(banners) { events += it }
        rule.onNodeWithText("Разрешить").performClick()
        rule.onNodeWithText("Настроить").performClick()
        rule.onNodeWithContentDescription("Дневное измерение, 14:00").performScrollTo().performClick()
        rule.onNodeWithText("Утреннее измерение").performScrollTo().performClick()
        rule.onNodeWithText("Добавить напоминание").performScrollTo().performClick()
        assertEquals(
            listOf(
                RemindersEvent.NotificationsBannerClicked,
                RemindersEvent.ExactAlarmsBannerClicked,
                RemindersEvent.Toggled(2, enabled = true),
                RemindersEvent.ItemClicked(1),
                RemindersEvent.AddClicked,
            ),
            events,
        )
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
