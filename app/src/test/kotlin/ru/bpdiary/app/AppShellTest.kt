package ru.bpdiary.app

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.ui.assertAccessible
import ru.bpdiary.feature.charts.ChartsRoute
import ru.bpdiary.feature.diary.DiaryRoute
import ru.bpdiary.feature.entry.EntryRoute
import ru.bpdiary.feature.home.HomeRoute
import ru.bpdiary.feature.onboarding.DisclaimerRoute
import ru.bpdiary.feature.onboarding.OnboardingRoute
import ru.bpdiary.feature.reminders.ReminderEditRoute
import ru.bpdiary.feature.reminders.RemindersRoute
import ru.bpdiary.feature.settings.SettingsRoute

/** The shell around the real routes with plain screens inside: what is tested is the navigation, not the screens. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h780dp-xxhdpi")
class AppShellTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var navController: NavHostController

    private fun show(start: Any = HomeRoute) {
        rule.setContent {
            navController = rememberNavController()
            BpTheme(Direction.SAGE, dark = false) {
                AppShell(navController) {
                    NavHost(navController, startDestination = start) {
                        composable<HomeRoute> { Text("Экран: главная") }
                        composable<DiaryRoute> { LongList() }
                        composable<ChartsRoute> { Text("Экран: графики") }
                        composable<RemindersRoute> { Text("Экран: напоминания") }
                        composable<ReminderEditRoute> { Text("Экран: правка напоминания") }
                        composable<EntryRoute> { Text("Экран: ввод") }
                        composable<SettingsRoute> { Text("Экран: настройки") }
                        composable<OnboardingRoute> { Text("Экран: онбординг") }
                        composable<DisclaimerRoute> { Text("Экран: дисклеймер") }
                    }
                }
            }
        }
    }

    @Composable
    private fun LongList() {
        val state = rememberLazyListState()
        LazyColumn(state = state, modifier = Modifier.testTag("diary")) {
            items((0 until ROWS).toList()) { Text("Строка $it") }
        }
    }

    private fun tab(label: String) = rule.onNodeWithText(label)

    @Test
    fun `the three tabs are shown on the main screens, Home is selected first`() {
        show()
        listOf("Главная", "Дневник", "Графики").forEach { tab(it).assertExists() }
        tab("Напоминания").assertDoesNotExist()
        tab("Главная").assertIsSelected()
        tab("Дневник").assertIsNotSelected()
        // The tabs have names and touch targets of at least 48 dp (docs/10 §10.6).
        rule.assertAccessible()
    }

    @Test
    fun `a tab opens its screen and becomes selected`() {
        show()
        tab("Графики").performClick()
        rule.onNodeWithText("Экран: графики").assertExists()
        tab("Графики").assertIsSelected()
        tab("Главная").assertIsNotSelected()
    }

    @Test
    fun `switching tabs keeps the scroll position of the diary`() {
        show()
        tab("Дневник").performClick()
        rule.onNodeWithTag("diary").performScrollToIndex(SCROLLED_TO)
        rule.onNodeWithText("Строка $SCROLLED_TO").assertExists()
        rule.onNodeWithText("Строка 0").assertDoesNotExist()

        tab("Графики").performClick()
        tab("Дневник").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Строка $SCROLLED_TO").assertExists()
        rule.onNodeWithText("Строка 0").assertDoesNotExist()
    }

    @Test
    fun `back from another tab goes to Home, not through the visited tabs`() {
        show()
        tab("Дневник").performClick()
        tab("Графики").performClick()
        rule.runOnUiThread { navController.popBackStack() }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: главная").assertExists()
    }

    @Test
    fun `the tab is selected again by tapping it twice without stacking screens`() {
        show()
        tab("Дневник").performClick()
        tab("Дневник").performClick()
        rule.runOnUiThread { navController.popBackStack() }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: главная").assertExists()
    }

    @Test
    fun `full-screen destinations hide the bottom navigation`() {
        show()
        rule.runOnUiThread { navController.navigate(RemindersRoute) }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: напоминания").assertExists()
        tab("Главная").assertDoesNotExist()

        rule.runOnUiThread { navController.navigate(ReminderEditRoute()) }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: правка напоминания").assertExists()
        tab("Главная").assertDoesNotExist()

        rule.runOnUiThread { navController.popBackStack(HomeRoute, inclusive = false) }
        rule.waitForIdle()
        tab("Главная").assertIsSelected()
    }

    @Test
    fun `the notification request opens a new reading over the current screen`() {
        show()
        tab("Дневник").performClick()
        rule.runOnUiThread { navController.openNewEntry() }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: ввод").assertExists()
        tab("Дневник").assertDoesNotExist()

        rule.runOnUiThread { navController.popBackStack() }
        rule.waitForIdle()
        tab("Дневник").assertIsSelected()
    }

    @Test
    fun `a second notification tap does not stack another Entry`() {
        show()
        rule.runOnUiThread {
            navController.openNewEntry()
            navController.openNewEntry()
            navController.popBackStack()
        }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: главная").assertExists()
        rule.runOnUiThread { assertTrue(navController.currentDestination?.hasRoute<HomeRoute>() == true) }
    }

    @Test
    fun `onboarding and the disclaimer have no bottom navigation`() {
        show(start = OnboardingRoute)
        rule.onNodeWithText("Экран: онбординг").assertExists()
        tab("Главная").assertDoesNotExist()
    }

    @Test
    fun `a notification cannot skip the onboarding or the disclaimer`() {
        show(start = OnboardingRoute)
        rule.runOnUiThread { navController.openNewEntry() }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: онбординг").assertExists()
        rule.onNodeWithText("Экран: ввод").assertDoesNotExist()

        rule.runOnUiThread { navController.navigate(DisclaimerRoute) }
        rule.waitForIdle()
        rule.runOnUiThread { navController.openNewEntry() }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: дисклеймер").assertExists()
        rule.onNodeWithText("Экран: ввод").assertDoesNotExist()
    }

    @Test
    fun `after the disclaimer the app opens on Home and back leaves it`() {
        show(start = DisclaimerRoute)
        rule.runOnUiThread { navController.openAppAfterDisclaimer() }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: главная").assertExists()
        tab("Главная").assertIsSelected()
        rule.runOnUiThread { assertTrue(navController.previousBackStackEntry == null) }
    }

    @Test
    fun `after deleting all data the app starts again from the onboarding with nothing to go back to`() {
        show()
        tab("Дневник").performClick()
        rule.runOnUiThread { navController.navigate(SettingsRoute) }
        rule.waitForIdle()
        tab("Дневник").assertDoesNotExist()

        rule.runOnUiThread { navController.restartFromOnboarding() }
        rule.waitForIdle()
        rule.onNodeWithText("Экран: онбординг").assertExists()
        tab("Главная").assertDoesNotExist()
        rule.runOnUiThread { assertTrue(navController.previousBackStackEntry == null) }
    }

    private companion object {
        const val ROWS = 100
        const val SCROLLED_TO = 60
    }
}
