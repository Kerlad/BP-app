package ru.bpdiary.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter

/**
 * Bottom navigation: 74 dp, background `sf2`. The active item is a 58×30 `acs` pill with an `ink` 700 label;
 * the others use `mut` 500. Each item is a 48 dp+ touch target.
 */
@Composable
fun BpBottomNav(
    items: List<BpNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(74.dp)
                .background(colors.sf2)
                .padding(top = 10.dp)
                .selectableGroup(),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 58.dp, height = 30.dp)
                            .background(if (selected) colors.acs else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = item.icon.painter(),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(if (selected) colors.ink else colors.mut),
                        modifier = Modifier.size(23.dp),
                    )
                }
                Text(
                    text = item.label,
                    style = if (selected) BpTheme.typography.navActive else BpTheme.typography.navInactive,
                    color = if (selected) colors.ink else colors.mut,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
