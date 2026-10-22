package ru.bpdiary.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpTheme

/**
 * Horizontally scrolling row of pills that never wraps.
 *
 * - [multiSelect] = false: diary filter. The selected pill is `ac`/`aci`, the others are plain.
 * - [multiSelect] = true: feeling tags. The selected pill has an `acs` fill and an `ac` outline,
 *   the others a `ln` outline.
 */
@Composable
fun FilterChipRow(
    items: List<String>,
    selected: Set<Int>,
    multiSelect: Boolean,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, label ->
            val isSelected = index in selected
            Box(
                modifier =
                    Modifier
                        .minTouchTarget()
                        .selectable(
                            selected = isSelected,
                            role = if (multiSelect) Role.Checkbox else Role.RadioButton,
                            onClick = { onToggle(index) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (multiSelect) TagPill(label, isSelected) else FilterPill(label, isSelected)
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
) {
    val colors = BpTheme.colors
    Text(
        text = label,
        style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W600),
        color = if (selected) colors.aci else colors.ink,
        maxLines = 1,
        softWrap = false,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(if (selected) colors.ac else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun TagPill(
    label: String,
    selected: Boolean,
) {
    val colors = BpTheme.colors
    Text(
        text = label,
        style = BpTheme.typography.caption.copy(fontWeight = FontWeight.W600),
        color = colors.ink,
        maxLines = 1,
        softWrap = false,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(if (selected) colors.acs else Color.Transparent)
                .border(BorderStroke(1.5.dp, if (selected) colors.ac else colors.ln), CircleShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
