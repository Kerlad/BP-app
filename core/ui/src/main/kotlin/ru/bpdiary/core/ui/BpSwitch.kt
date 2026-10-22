package ru.bpdiary.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.designsystem.BpTheme

private const val KNOB_ANIMATION_MS = 150
private val KnobTravel = 20.dp

/**
 * Switch from the handoff: 52×32 track, 24 dp knob that travels 20 dp in 150 ms.
 * On: track `ac`, knob `aci`. Off: track `ln`, knob `sf`.
 */
@Composable
fun BpSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = BpTheme.colors
    val knobX = animateDpAsState(if (checked) KnobTravel else 0.dp, tween(KNOB_ANIMATION_MS), label = "knob").value
    val semanticsModifier =
        if (contentDescription !=
            null
        ) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .minTouchTarget()
                .then(semanticsModifier)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 52.dp, height = 32.dp)
                    .background(if (checked) colors.ac else colors.ln, CircleShape)
                    .padding(4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .offset(x = knobX)
                        .size(24.dp)
                        .background(if (checked) colors.aci else colors.sf, CircleShape),
            )
        }
    }
}
