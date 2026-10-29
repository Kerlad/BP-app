package ru.bpdiary.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * Main action such as «Записать давление»: height 62, `ac` with `aci` 18/700, icon 26, radius `r`.
 * A disabled button is drawn with alpha .4 like the disabled steppers and takes no clicks.
 */
@Composable
fun BpPrimaryButton(
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
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(RoundedCornerShape(BpTheme.shapes.card))
                .background(colors.ac)
                .clickableWithRole(Role.Button, enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                painter = icon.painter(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.aci),
                modifier = Modifier.size(26.dp),
            )
        }
        Text(text, style = BpTheme.typography.button, color = colors.aci)
    }
}
