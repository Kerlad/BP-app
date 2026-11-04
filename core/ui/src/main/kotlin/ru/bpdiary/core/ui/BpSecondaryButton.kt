package ru.bpdiary.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter

private const val DISABLED_ALPHA = 0.4f

/** A quieter action next to [BpPrimaryButton]: `acs` with `ink` text, at least 48 dp high. */
@Composable
fun BpSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: BpIcon? = null,
    enabled: Boolean = true,
) {
    val colors = BpTheme.colors
    Row(
        modifier =
            modifier
                .heightIn(min = MinTouchTarget)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(RoundedCornerShape(BpTheme.shapes.card))
                .background(colors.acs)
                .clickableWithRole(Role.Button, enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                painter = icon.painter(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.ink),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(text, style = BpTheme.typography.secondary, color = colors.ink)
    }
}
