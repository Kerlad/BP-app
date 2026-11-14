package ru.bpdiary.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Below this height the keypad and Save scroll with the form (review A-05). */
private val CompactHeight = 480.dp

/**
 * The form above the keypad and Save (review A-05). While the system keyboard is open (comment) the form gets
 * all the height; on a low screen (landscape, split screen) the form, keypad and Save scroll together instead of
 * squeezing the form to nothing; otherwise the form scrolls above a fixed keypad.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdaptiveEntryLayout(
    form: @Composable ColumnScope.() -> Unit,
    keys: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imeVisible = WindowInsets.isImeVisible
    BoxWithConstraints(modifier) {
        // One tree for every mode, only the modifiers change: moving the form to another branch would recreate the
        // comment field, drop its focus and close the keyboard it has just opened.
        val scrollAll = !imeVisible && maxHeight < CompactHeight
        val outerScroll = rememberScrollState()
        val formScroll = rememberScrollState()
        Column(Modifier.fillMaxSize().then(if (scrollAll) Modifier.verticalScroll(outerScroll) else Modifier)) {
            Column(
                modifier = if (scrollAll) Modifier else Modifier.weight(1f).verticalScroll(formScroll),
                verticalArrangement = Arrangement.spacedBy(Gap),
                content = form,
            )
            if (!imeVisible) keys()
        }
    }
}
