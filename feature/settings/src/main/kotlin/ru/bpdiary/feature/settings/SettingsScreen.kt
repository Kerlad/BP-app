package ru.bpdiary.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.settings.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.BpTopBar

/** Settings (docs/05 §5.7): theme, text size, «Напоминания», «О приложении», «Удалить все данные». Stateless. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
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
            title = stringResource(R.string.settings_title),
            navigationIcon = BpIcon.ArrowBack,
            navigationDescription = stringResource(R.string.settings_back_description),
            onNavigate = { onEvent(SettingsEvent.BackClicked) },
        )
        Column(modifier = Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ThemeSection(state.themeMode) { onEvent(SettingsEvent.ThemeSelected(it)) }
            TextSizeSection(state.textSize) { onEvent(SettingsEvent.TextSizeSelected(it)) }
            SettingsRow(stringResource(R.string.settings_reminders), colors.ink) {
                onEvent(SettingsEvent.RemindersClicked)
            }
            SettingsRow(stringResource(R.string.settings_about), colors.ink) { onEvent(SettingsEvent.AboutClicked) }
            SettingsRow(stringResource(R.string.settings_delete_all), colors.hi, showChevron = false) {
                onEvent(SettingsEvent.DeleteClicked)
            }
        }
    }
    when (state.dialog) {
        DeleteDialog.None -> Unit
        DeleteDialog.First -> FirstDialog(onEvent)
        DeleteDialog.Second -> SecondDialog(onEvent)
    }
}

private val THEMES = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

@Composable
private fun ThemeSection(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    OptionsSection(
        title = stringResource(R.string.settings_theme_section),
        labels =
            listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark),
            ),
        selectedIndex = THEMES.indexOf(selected),
        onSelect = { onSelect(THEMES[it]) },
    )
}

@Composable
private fun TextSizeSection(
    selected: TextSize,
    onSelect: (TextSize) -> Unit,
) {
    OptionsSection(
        title = stringResource(R.string.settings_text_size_section),
        labels =
            listOf(
                stringResource(R.string.settings_text_size_normal),
                stringResource(R.string.settings_text_size_large),
                stringResource(R.string.settings_text_size_extra_large),
            ),
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(TextSize.entries[it]) },
    )
}

/**
 * A titled card with one row per option. Rows, not a segmented pill: «Очень крупный» does not fit a pill on
 * 360 dp, and with the largest text the theme pill cut off «Тёмная» too.
 */
@Composable
private fun OptionsSection(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    BpCard {
        Text(
            text = title,
            style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
            color = BpTheme.colors.ink,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        Column(Modifier.selectableGroup()) {
            labels.forEachIndexed { index, label ->
                OptionRow(label, index == selectedIndex) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = BpTheme.typography.body.copy(fontWeight = if (selected) FontWeight.W700 else FontWeight.W500),
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Image(
                painter = BpIcon.Check.painter(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.ac),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** A card row in the style of the «Настройки» entry on Levels. */
@Composable
private fun SettingsRow(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    BpCard(padding = 0.dp, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = BpTheme.typography.cardTitle, color = color, modifier = Modifier.weight(1f))
            if (showChevron) {
                Image(
                    painter = BpIcon.ChevronRight.painter(),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(BpTheme.colors.mut),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun FirstDialog(onEvent: (SettingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.DialogDismissed) },
        title = { Text(stringResource(R.string.settings_delete_first_title)) },
        text = { Text(stringResource(R.string.settings_delete_first_text)) },
        confirmButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DeleteFirstConfirmed) }) {
                Text(stringResource(R.string.settings_delete_first_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DialogDismissed) }) {
                Text(stringResource(R.string.settings_delete_cancel))
            }
        },
    )
}

/** The last word: the red `hi` button «Удалить навсегда» (D-26). */
@Composable
private fun SecondDialog(onEvent: (SettingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.DialogDismissed) },
        title = { Text(stringResource(R.string.settings_delete_second_title)) },
        text = { Text(stringResource(R.string.settings_delete_second_text)) },
        confirmButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DeleteForeverConfirmed) }) {
                Text(stringResource(R.string.settings_delete_forever), color = BpTheme.colors.hi)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DialogDismissed) }) {
                Text(stringResource(R.string.settings_delete_cancel))
            }
        },
    )
}
