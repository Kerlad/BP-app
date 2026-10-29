package ru.bpdiary.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** «Крупный шрифт» multiplies the font scale of the system instead of replacing it (docs/04 §4.7). */
@Composable
fun BoostedFontScale(
    boost: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * boost),
        content = content,
    )
}
