package ru.bpdiary.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter

private const val DISABLED_ALPHA = 0.4f

/**
 * "− value +": round 40 dp buttons `sf2` (with a 48 dp touch target) around the value in 22/nw.
 * A button that would break a rule is disabled (alpha .4); no error is ever shown (docs/05 §5.6).
 * The range and invariants belong to the caller, which says through [canDecrement]/[canIncrement] what is allowed.
 */
@Composable
fun Stepper(
    display: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementDescription: String,
    incrementDescription: String,
    modifier: Modifier = Modifier,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepButton(BpIcon.Remove, decrementDescription, canDecrement, onDecrement)
        Text(
            text = display,
            style = BpTheme.typography.numRow,
            color = BpTheme.colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // 62 dp as in the handoff; wider at large font scales instead of cutting «≥135».
            modifier = Modifier.widthIn(min = 62.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        StepButton(BpIcon.Add, incrementDescription, canIncrement, onIncrement)
    }
}

@Composable
private fun StepButton(
    icon: BpIcon,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .minTouchTarget()
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickableWithRole(Role.Button, enabled = enabled, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(BpTheme.colors.sf2),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = icon.painter(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(BpTheme.colors.ink),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
