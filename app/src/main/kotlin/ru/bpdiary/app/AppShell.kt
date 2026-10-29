package ru.bpdiary.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpBottomNav
import ru.bpdiary.core.ui.BpNavItem

/**
 * The frame around the destinations: the bottom navigation on the four main screens only. On those screens the
 * navigation bar of the system sits under the bottom navigation (`sf2` all the way down), so the screens above
 * do not pad for it again; on full-screen destinations the gesture area is `bg` (docs/02 §2.4).
 * [overlay] is drawn above the destinations and above the bottom navigation, for the toast and the snackbar.
 */
@Composable
fun AppShell(
    navController: NavController,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = BpTheme.colors
    val entry by navController.currentBackStackEntryAsState()
    val tab = entry?.destination.tab()
    val resources = LocalResources.current
    val items = remember(resources) { AppTab.entries.map { BpNavItem(it.icon, resources.getString(it.label)) } }
    Column(modifier.fillMaxSize().background(colors.bg)) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .then(if (tab != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            content()
            overlay()
        }
        if (tab != null) {
            Column(Modifier.background(colors.sf2).windowInsetsPadding(WindowInsets.navigationBars)) {
                BpBottomNav(
                    items = items,
                    selectedIndex = tab.ordinal,
                    onSelect = { navController.navigateToTab(AppTab.entries[it]) },
                )
            }
        }
    }
}
