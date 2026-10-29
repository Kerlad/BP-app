package ru.bpdiary.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.onboarding.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.BpPrimaryButton

/** The mandatory disclaimer (docs/09 §9.6): the button stays inactive until the box is ticked. */
@Composable
fun DisclaimerScreen(
    state: DisclaimerUiState,
    onEvent: (DisclaimerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.disclaimer_title),
                style = BpTheme.typography.screenTitle,
                color = colors.ink,
                modifier = Modifier.padding(top = 24.dp).semantics { heading() },
            )
            BpCard {
                Text(stringResource(R.string.disclaimer_text), style = BpTheme.typography.body, color = colors.ink)
            }
            AcknowledgeRow(state.checked) { onEvent(DisclaimerEvent.CheckedChanged(it)) }
        }
        BpPrimaryButton(
            text = stringResource(R.string.disclaimer_continue),
            onClick = { onEvent(DisclaimerEvent.ContinueClicked) },
            enabled = state.checked,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun AcknowledgeRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = BpTheme.colors
    val shape = RoundedCornerShape(BpTheme.shapes.key / 2)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(shape)
                    .background(if (checked) colors.ac else Color.Transparent)
                    .border(BorderStroke(2.dp, if (checked) colors.ac else colors.mut), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Image(
                    painter = BpIcon.Check.painter(),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.aci),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(stringResource(R.string.disclaimer_check), style = BpTheme.typography.body, color = colors.ink)
    }
}
