package ru.bpdiary.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.core.ui.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter
import ru.bpdiary.domain.Key

/** The key digits do not scale above this system font scale: the 50 dp key grid has to fit 360 dp. */
private const val MAX_KEY_FONT_SCALE = 1.2f
private val KeyHeight = 50.dp
private val KeyGap = 8.dp

/** 3×4 keypad, keys 50 dp tall with radius `key`, gap 8 dp (docs/04, handoff screen 2). */
@Composable
fun NumPad(
    onKey: (Key) -> Unit,
    action: NumPadAction,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        listOf("123", "456", "789").forEach { digits ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                digits.forEach { digit -> DigitKey(digit, onKey) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            IconKey(BpIcon.Backspace, stringResource(R.string.key_backspace_description)) { onKey(Key.Del) }
            DigitKey('0', onKey)
            val actionDescription =
                stringResource(
                    if (action ==
                        NumPadAction.Save
                    ) {
                        R.string.key_save_description
                    } else {
                        R.string.key_next_description
                    },
                )
            IconKey(
                icon = if (action == NumPadAction.Save) BpIcon.Check else BpIcon.ArrowForward,
                description = actionDescription,
            ) { onKey(Key.Next) }
        }
    }
}

@Composable
private fun RowScope.DigitKey(
    digit: Char,
    onKey: (Key) -> Unit,
) {
    val colors = BpTheme.colors
    val description = stringResource(R.string.key_digit_description, digit.toString())
    KeyBox(background = colors.sf, description = description, onClick = { onKey(Key.Digit(digit)) }) {
        val density = LocalDensity.current
        val capped = Density(density.density, fontScale = density.fontScale.coerceAtMost(MAX_KEY_FONT_SCALE))
        CompositionLocalProvider(LocalDensity provides capped) {
            Text(text = digit.toString(), style = BpTheme.typography.key, color = colors.ink)
        }
    }
}

@Composable
private fun RowScope.IconKey(
    icon: BpIcon,
    description: String,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    KeyBox(background = colors.acs, description = description, onClick = onClick) {
        Image(
            painter = icon.painter(),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.ac),
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun RowScope.KeyBox(
    background: Color,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(KeyHeight)
                .clip(RoundedCornerShape(BpTheme.shapes.key))
                .background(background)
                .clickableWithRole(Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
