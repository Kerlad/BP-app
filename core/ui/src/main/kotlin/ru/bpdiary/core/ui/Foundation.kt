package ru.bpdiary.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpColors
import ru.bpdiary.core.model.Status

/** Minimum touch target (docs/04 §4.4 and the accessibility rules). */
internal val MinTouchTarget = 48.dp

internal fun Modifier.minTouchTarget(): Modifier = heightIn(min = MinTouchTarget).widthIn(min = MinTouchTarget)

internal fun Modifier.clickableWithRole(
    role: Role,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clickable(enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)

/** Text colour and soft background of a status. */
internal data class StatusColors(
    val color: Color,
    val soft: Color,
)

internal fun BpColors.forStatus(status: Status): StatusColors =
    when (status) {
        Status.HIGH -> StatusColors(hi, hiS)
        Status.NORMAL -> StatusColors(ok, okS)
        Status.LOW -> StatusColors(lo, loS)
    }
