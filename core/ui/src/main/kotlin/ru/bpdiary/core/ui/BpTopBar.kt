package ru.bpdiary.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter

/**
 * Top bar of the full-screen screens: 52 dp, a 44 dp round button on the left (`close` or `arrow_back`)
 * and the title in 20/700. [trailing] is for actions such as "Удалить" on the edit screen.
 */
@Composable
fun BpTopBar(
    title: String,
    navigationIcon: BpIcon,
    navigationDescription: String,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundIconButton(navigationIcon, navigationDescription, onNavigate)
        Text(
            text = title,
            style = BpTheme.typography.panelTitle,
            color = BpTheme.colors.ink,
            // Two lines at large font scales rather than a title cut mid-word.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** 44 dp round icon button (48 dp touch target), also used for `tune` on Home. */
@Composable
fun RoundIconButton(
    icon: BpIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val colors = BpTheme.colors
    Box(
        modifier =
            modifier
                .minTouchTarget()
                .clickableWithRole(Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .then(if (filled) Modifier.background(colors.sf) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = icon.painter(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.ink),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
