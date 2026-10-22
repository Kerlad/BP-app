package ru.bpdiary.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.core.ui.R
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.Status

/** Status pill: 13/700, padding 5×12. The status is always spelled out, never colour only. */
@Composable
fun StatusChip(
    status: Status,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors.forStatus(status)
    val label =
        stringResource(
            when (status) {
                Status.HIGH -> R.string.status_high
                Status.NORMAL -> R.string.status_normal
                Status.LOW -> R.string.status_low
            },
        )
    val description =
        stringResource(
            when (status) {
                Status.HIGH -> R.string.status_high_description
                Status.NORMAL -> R.string.status_normal_description
                Status.LOW -> R.string.status_low_description
            },
        )
    Text(
        text = label,
        style = BpTheme.typography.chip,
        color = colors.color,
        maxLines = 1,
        modifier =
            modifier
                .clip(CircleShape)
                .background(colors.soft)
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .clearAndSetSemantics { contentDescription = description },
    )
}
