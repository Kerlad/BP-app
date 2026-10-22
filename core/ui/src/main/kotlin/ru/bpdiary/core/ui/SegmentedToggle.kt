package ru.bpdiary.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpTheme

private val TrackHeight = 36.dp
private val TrackPadding = 3.dp

/**
 * Pill track `sf` with the selected segment in `ac`/`aci` (arm selector, 7/30 days, ...).
 * Each segment has a 48 dp touch target although the pill is drawn 30 dp tall.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    Row(
        modifier =
            modifier
                .selectableGroup()
                .drawBehind {
                    val height = TrackHeight.toPx()
                    drawRoundRect(
                        color = colors.sf,
                        topLeft = Offset(0f, (size.height - height) / 2),
                        size = Size(size.width, height),
                        cornerRadius = CornerRadius(height / 2),
                    )
                }.padding(horizontal = TrackPadding),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .minTouchTarget()
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = BpTheme.typography.caption.copy(fontWeight = FontWeight.W600),
                    color = if (selected) colors.aci else colors.ink,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(if (selected) colors.ac else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}
