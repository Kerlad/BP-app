package ru.bpdiary.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.core.ui.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.Status
import ru.bpdiary.domain.Draft
import ru.bpdiary.domain.DraftResult
import ru.bpdiary.domain.reduce

/** Every `core:ui` component on one scrolling screen. Debug builds only; also the basis of the screenshot tests. */
@Composable
fun ComponentShowcase(
    direction: Direction,
    dark: Boolean,
) {
    BpTheme(direction = direction, dark = dark) {
        val colors = BpTheme.colors
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colors.bg)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShowcaseTopBar()
            ShowcaseHomeCard()
            ShowcaseToggles()
            ShowcaseChips()
            ShowcaseSteppers()
            ShowcaseKeypad()
            ShowcaseChartLine()
            BpToast(text = stringResource(R.string.showcase_toast))
            ShowcaseNav()
        }
    }
}

@Composable
private fun ShowcaseTopBar() {
    BpTopBar(
        title = stringResource(R.string.showcase_title),
        navigationIcon = BpIcon.Close,
        navigationDescription = stringResource(R.string.showcase_close_description),
        onNavigate = {},
    )
}

@Composable
private fun ShowcaseHomeCard() {
    BpCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.showcase_when),
                style = BpTheme.typography.secondary,
                color = BpTheme.colors.mut,
            )
            StatusChip(Status.HIGH)
        }
        BigReading(sys = 128, dia = 82, size = BigReadingSize.Hero)
        PulseRow(pulse = 71, outOfRange = false, caption = stringResource(R.string.showcase_pulse_caption))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(Status.NORMAL)
        StatusChip(Status.LOW)
        PulseRow(pulse = 104, outOfRange = true, iconSize = 16.dp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BigReading(sys = 128, dia = 82, size = BigReadingSize.Entry)
        BigReading(sys = 135, dia = 85, size = BigReadingSize.Row)
    }
}

@Composable
private fun ShowcaseToggles() {
    var arm by remember { mutableIntStateOf(0) }
    var period by remember { mutableIntStateOf(1) }
    var on by remember { mutableStateOf(true) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SegmentedToggle(
            options = listOf(stringResource(R.string.showcase_arm_left), stringResource(R.string.showcase_arm_right)),
            selectedIndex = arm,
            onSelect = { arm = it },
        )
        BpSwitch(checked = on, onCheckedChange = { on = it })
        BpSwitch(checked = !on, onCheckedChange = { on = !it })
    }
    SegmentedToggle(
        options = listOf(stringResource(R.string.showcase_period_7), stringResource(R.string.showcase_period_30)),
        selectedIndex = period,
        onSelect = { period = it },
    )
}

@Composable
private fun ShowcaseChips() {
    var filter by remember { mutableIntStateOf(1) }
    var tags by remember { mutableStateOf(setOf(1)) }
    FilterChipRow(
        items =
            listOf(
                stringResource(R.string.showcase_filter_all),
                stringResource(R.string.showcase_filter_high),
                stringResource(R.string.showcase_filter_normal),
                stringResource(R.string.showcase_filter_low),
            ),
        selected = setOf(filter),
        multiSelect = false,
        onToggle = { filter = it },
    )
    FilterChipRow(
        items =
            listOf(
                stringResource(R.string.showcase_tag_good),
                stringResource(R.string.showcase_tag_headache),
                stringResource(R.string.showcase_tag_dizziness),
                stringResource(R.string.showcase_tag_weakness),
                stringResource(R.string.showcase_tag_after_medicine),
                stringResource(R.string.showcase_tag_after_exercise),
            ),
        selected = tags,
        multiSelect = true,
        onToggle = { tags = if (it in tags) tags - it else tags + it },
    )
}

@Composable
private fun ShowcaseSteppers() {
    var value by remember { mutableIntStateOf(135) }
    BpCard(padding = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.showcase_stepper_high_sys),
                style = BpTheme.typography.body,
                color = BpTheme.colors.ink,
            )
            Stepper(
                display = "≥$value",
                onDecrement = { value -= 5 },
                onIncrement = { value += 5 },
                decrementDescription = stringResource(R.string.showcase_decrease_description),
                incrementDescription = stringResource(R.string.showcase_increase_description),
                canDecrement = value > 110,
                canIncrement = false,
            )
        }
    }
}

@Composable
private fun ShowcaseKeypad() {
    var draft by remember { mutableStateOf(Draft(sys = "128", dia = "82", pulse = "7")) }
    NumPad(
        onKey = { key -> (reduce(draft, key) as? DraftResult.Updated)?.let { draft = it.draft } },
        action = NumPadAction.Save,
    )
}

@Composable
private fun ShowcaseChartLine() {
    val colors = BpTheme.colors
    Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
        DashedThresholdLine(y = 12f, color = colors.hi, chartWidth = 300f)
    }
}

@Composable
private fun ShowcaseNav() {
    var selected by remember { mutableIntStateOf(0) }
    BpBottomNav(
        items =
            listOf(
                BpNavItem(BpIcon.Home, stringResource(R.string.showcase_nav_home)),
                BpNavItem(BpIcon.MenuBook, stringResource(R.string.showcase_nav_diary)),
                BpNavItem(BpIcon.Monitoring, stringResource(R.string.showcase_nav_charts)),
                BpNavItem(BpIcon.Notifications, stringResource(R.string.showcase_nav_reminders)),
            ),
        selectedIndex = selected,
        onSelect = { selected = it },
    )
}
