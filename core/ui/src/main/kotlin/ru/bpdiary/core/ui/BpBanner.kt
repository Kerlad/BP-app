package ru.bpdiary.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpTheme

/** A message on the `acs` tint with a pill button under it; the whole banner is not clickable, only the button. */
@Composable
fun BpBanner(
    text: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BpTheme.shapes.card))
                .background(colors.acs)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = BpTheme.typography.body, color = colors.ink)
        Text(
            text = action,
            style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
            color = colors.aci,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(colors.ac)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
