package ru.bpdiary.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.model.Direction

private const val KEY_RATIO = 0.6f
private const val PEACH_CARD_RADIUS = 28
private const val SAGE_CARD_RADIUS = 14
private const val PLUM_CARD_RADIUS = 22

/** Corner radii. Chips are always pills. */
@Immutable
data class BpShapes(
    /** Card radius `r`. */
    val card: Dp,
    /** Keypad keys and input fields: `r * 0.6`. */
    val key: Dp = card * KEY_RATIO,
)

fun bpShapes(direction: Direction): BpShapes =
    when (direction) {
        Direction.PEACH -> BpShapes(PEACH_CARD_RADIUS.dp)
        Direction.SAGE -> BpShapes(SAGE_CARD_RADIUS.dp)
        Direction.PLUM -> BpShapes(PLUM_CARD_RADIUS.dp)
    }
