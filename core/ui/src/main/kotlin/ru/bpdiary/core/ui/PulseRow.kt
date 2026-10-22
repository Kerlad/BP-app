package ru.bpdiary.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter

/**
 * Filled heart in `hi`, the value (700) and an optional caption such as "уд/мин · Левая рука".
 * The value turns `hi` when the pulse is out of range; the caption text comes from the caller.
 */
@Composable
fun PulseRow(
    pulse: Int,
    outOfRange: Boolean,
    modifier: Modifier = Modifier,
    caption: String? = null,
    iconSize: Dp = 18.dp,
) {
    val colors = BpTheme.colors
    val typography = BpTheme.typography
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Decorative: the value and the caption already say everything.
        Image(
            painter = BpIcon.FavoriteFilled.painter(),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.hi),
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = "$pulse",
            style = typography.body.copy(fontWeight = FontWeight.W700),
            color = if (outOfRange) colors.hi else colors.ink,
        )
        if (caption != null) Text(caption, style = typography.body, color = colors.mut)
    }
}
