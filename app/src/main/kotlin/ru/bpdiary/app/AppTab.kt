package ru.bpdiary.app

import androidx.annotation.StringRes
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.feature.charts.ChartsRoute
import ru.bpdiary.feature.diary.DiaryRoute
import ru.bpdiary.feature.entry.EntryRoute
import ru.bpdiary.feature.home.HomeRoute
import ru.bpdiary.feature.onboarding.DisclaimerRoute
import ru.bpdiary.feature.onboarding.OnboardingRoute
import ru.bpdiary.feature.reminders.RemindersRoute
import kotlin.reflect.KClass

/** The four main screens: only they show the bottom navigation (docs/02 §2.4). The order is the order on screen. */
enum class AppTab(
    val route: Any,
    val routeClass: KClass<out Any>,
    val icon: BpIcon,
    @StringRes val label: Int,
) {
    HOME(HomeRoute, HomeRoute::class, BpIcon.Home, R.string.tab_home),
    DIARY(DiaryRoute, DiaryRoute::class, BpIcon.MenuBook, R.string.tab_diary),
    CHARTS(ChartsRoute, ChartsRoute::class, BpIcon.Monitoring, R.string.tab_charts),
    REMINDERS(RemindersRoute, RemindersRoute::class, BpIcon.Notifications, R.string.tab_reminders),
}

/** The tab the destination belongs to; null on the full-screen destinations. */
fun NavDestination?.tab(): AppTab? =
    this?.let { destination ->
        AppTab.entries.firstOrNull { tab -> destination.hierarchy.any { it.hasRoute(tab.routeClass) } }
    }

/**
 * Switches tab keeping the state of the tab left and restoring the one entered, so the diary stays where it was
 * scrolled. Taps on the current tab do nothing more.
 */
fun NavController.navigateToTab(tab: AppTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * «Новое измерение» from the reminder notification (D-14). A reading that is already being entered stays, and the
 * onboarding or the disclaimer is never skipped: without the accepted disclaimer the app does not go on (docs/05 §5.8).
 */
fun NavController.openNewEntry() {
    val current = currentDestination
    if (current?.hasRoute<EntryRoute>() == true) return
    if (current?.hasRoute<OnboardingRoute>() == true || current?.hasRoute<DisclaimerRoute>() == true) return
    navigate(EntryRoute())
}

/** The disclaimer is accepted: Home becomes the bottom of the stack, back leaves the app. */
fun NavController.openAppAfterDisclaimer() {
    navigate(HomeRoute) { popUpTo(graph.id) { inclusive = true } }
}

/** «Удалить все данные» is done: the back stack is dropped and the app starts again from the onboarding. */
fun NavController.restartFromOnboarding() {
    navigate(OnboardingRoute) { popUpTo(graph.id) { inclusive = true } }
}
