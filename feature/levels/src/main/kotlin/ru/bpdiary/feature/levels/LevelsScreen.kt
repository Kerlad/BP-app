package ru.bpdiary.feature.levels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.bpdiary.app.feature.levels.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.BpSwitch
import ru.bpdiary.core.ui.BpTopBar
import ru.bpdiary.core.ui.Stepper
import ru.bpdiary.domain.THRESHOLD_STEP
import ru.bpdiary.domain.ThresholdField
import ru.bpdiary.domain.ThresholdPreset
import ru.bpdiary.domain.matches
import ru.bpdiary.domain.stepped
import ru.bpdiary.domain.valueOf
import ru.bpdiary.domain.withPreset

private const val DISABLED_ALPHA = 0.4f
private const val LARGE_FONT_SCALE = 1.3f
private val PresetValueSize = 20.sp

/** Levels (handoff screen 6). Stateless; [systemDark] decides the switch while the theme follows the system. */
@Composable
fun LevelsScreen(
    state: LevelsUiState,
    onEvent: (LevelsEvent) -> Unit,
    modifier: Modifier = Modifier,
    systemDark: Boolean = isSystemInDarkTheme(),
) {
    val colors = BpTheme.colors
    BackHandler { onEvent(LevelsEvent.BackClicked) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 10.dp, end = 20.dp, bottom = 24.dp),
    ) {
        BpTopBar(
            title = stringResource(R.string.levels_title),
            navigationIcon = BpIcon.ArrowBack,
            navigationDescription = stringResource(R.string.levels_back_description),
            onNavigate = { onEvent(LevelsEvent.BackClicked) },
        )
        val th = state.thresholds ?: return@Column
        Column(
            modifier = Modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.levels_intro), style = BpTheme.typography.secondary, color = colors.mut)
            Presets(th, onEvent)
            Groups(th, onEvent)
            DarkThemeRow(
                checked = state.themeMode == ThemeMode.DARK || (state.themeMode == ThemeMode.SYSTEM && systemDark),
                onChange = { onEvent(LevelsEvent.DarkThemeChanged(it)) },
            )
            Text(
                text = stringResource(R.string.levels_footnote),
                style = BpTheme.typography.micro,
                color = colors.mut,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsRow { onEvent(LevelsEvent.SettingsClicked) }
        }
    }
}

@Composable
private fun Presets(
    th: Thresholds,
    onEvent: (LevelsEvent) -> Unit,
) {
    val card: @Composable (ThresholdPreset, Modifier) -> Unit = { preset, modifier ->
        PresetCard(
            preset = preset,
            selected = th.matches(preset),
            enabled = th.withPreset(preset) != null,
            onClick = { onEvent(LevelsEvent.PresetSelected(preset)) },
            modifier = modifier,
        )
    }
    // Side by side as in the handoff; stacked at large font scales so «Домашние измерения» does not break mid-word.
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThresholdPreset.entries.forEach { card(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThresholdPreset.entries.forEach { card(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PresetCard(
    preset: ThresholdPreset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = BpTheme.colors
    val shape = RoundedCornerShape(BpTheme.shapes.card)
    Column(
        modifier =
            modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(shape)
                .background(if (selected) colors.acs else colors.sf)
                .border(BorderStroke(1.5.dp, if (selected) colors.ac else Color.Transparent), shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.levels_preset_value, preset.highSys, preset.highDia),
            style = BpTheme.typography.numRow.copy(fontSize = PresetValueSize),
            color = colors.ink,
        )
        Text(
            text =
                stringResource(
                    if (preset == ThresholdPreset.HOME) R.string.levels_preset_home else R.string.levels_preset_clinic,
                ),
            style = BpTheme.typography.micro,
            color = colors.mut,
        )
    }
}

private data class LevelRow(
    val field: ThresholdField,
    val label: Int,
    val unit: Int,
    /** `levels_at_least`, `levels_at_most` or null for the plain pulse value. */
    val sign: Int?,
)

@Composable
private fun Groups(
    th: Thresholds,
    onEvent: (LevelsEvent) -> Unit,
) {
    val colors = BpTheme.colors
    Group(R.string.levels_group_high, colors.hi, th, onEvent) {
        listOf(
            LevelRow(
                ThresholdField.HIGH_SYS,
                R.string.levels_row_sys,
                R.string.levels_unit_from_mmhg,
                R.string.levels_at_least,
            ),
            LevelRow(
                ThresholdField.HIGH_DIA,
                R.string.levels_row_dia,
                R.string.levels_unit_from_mmhg,
                R.string.levels_at_least,
            ),
        )
    }
    Group(R.string.levels_group_low, colors.lo, th, onEvent) {
        listOf(
            LevelRow(
                ThresholdField.LOW_SYS,
                R.string.levels_row_sys,
                R.string.levels_unit_to_mmhg,
                R.string.levels_at_most,
            ),
            LevelRow(
                ThresholdField.LOW_DIA,
                R.string.levels_row_dia,
                R.string.levels_unit_to_mmhg,
                R.string.levels_at_most,
            ),
        )
    }
    Group(R.string.levels_group_pulse, colors.ok, th, onEvent) {
        listOf(
            LevelRow(ThresholdField.PULSE_LOW, R.string.levels_row_from, R.string.levels_unit_pulse, null),
            LevelRow(ThresholdField.PULSE_HIGH, R.string.levels_row_to, R.string.levels_unit_pulse, null),
        )
    }
}

@Composable
private fun Group(
    title: Int,
    color: Color,
    th: Thresholds,
    onEvent: (LevelsEvent) -> Unit,
    rows: () -> List<LevelRow>,
) {
    val colors = BpTheme.colors
    val groupTitle = stringResource(title)
    BpCard(padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp).semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(
                    groupTitle,
                    style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
                    color = colors.ink,
                )
            }
            rows().forEach { row -> LevelRowItem(groupTitle, row, th, onEvent) }
        }
    }
}

@Composable
private fun LevelRowItem(
    groupTitle: String,
    row: LevelRow,
    th: Thresholds,
    onEvent: (LevelsEvent) -> Unit,
) {
    val colors = BpTheme.colors
    val label = stringResource(row.label)
    val value = th.valueOf(row.field)
    // The stepper moves under the label at large font scales instead of squeezing it mid-word.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(end = 10.dp)) {
            Text(label, style = BpTheme.typography.body, color = colors.ink)
            Text(stringResource(row.unit), style = BpTheme.typography.micro, color = colors.mut)
        }
        Stepper(
            display = row.sign?.let { stringResource(it, value) } ?: "$value",
            onDecrement = { onEvent(LevelsEvent.Step(row.field, -THRESHOLD_STEP)) },
            onIncrement = { onEvent(LevelsEvent.Step(row.field, THRESHOLD_STEP)) },
            decrementDescription = stringResource(R.string.levels_decrease_description, groupTitle, label),
            incrementDescription = stringResource(R.string.levels_increase_description, groupTitle, label),
            canDecrement = th.stepped(row.field, -THRESHOLD_STEP) != null,
            canIncrement = th.stepped(row.field, THRESHOLD_STEP) != null,
        )
    }
}

@Composable
private fun DarkThemeRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = BpTheme.colors
    val label = stringResource(R.string.levels_dark_theme)
    BpCard(padding = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = BpIcon.DarkMode.painter(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.mut),
                modifier = Modifier.size(22.dp),
            )
            Text(label, style = BpTheme.typography.body, color = colors.ink, modifier = Modifier.weight(1f))
            BpSwitch(checked = checked, onCheckedChange = onChange, contentDescription = label)
        }
    }
}

/** D-11: the visible way into the settings, styled like the reminder card on Home. */
@Composable
private fun SettingsRow(onClick: () -> Unit) {
    val colors = BpTheme.colors
    BpCard(padding = 0.dp, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.levels_settings),
                style = BpTheme.typography.cardTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Image(
                painter = BpIcon.ChevronRight.painter(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.mut),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
