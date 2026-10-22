package ru.bpdiary.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter

/** Toast: `ink` background, `bg` text, 14 dp radius, 2.4 s on screen above the navigation (timing is the caller's). */
@Composable
fun BpToast(
    text: String,
    modifier: Modifier = Modifier,
    icon: BpIcon = BpIcon.CheckCircle,
) {
    val colors = BpTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.ink)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = icon.painter(),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.bg),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W600),
            color = colors.bg,
        )
    }
}
