package ru.bpdiary.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpTheme

/** Card: surface `sf`, radius `r`, no shadow (docs/04 §4.4). Clickable when [onClick] is given. */
@Composable
fun BpCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(BpTheme.shapes.card)
    val base = modifier.clip(shape).background(BpTheme.colors.sf)
    val clickable = if (onClick != null) base.clickableWithRole(Role.Button, onClick = onClick) else base
    Column(modifier = clickable.padding(padding), content = content)
}
