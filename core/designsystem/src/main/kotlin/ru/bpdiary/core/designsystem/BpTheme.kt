package ru.bpdiary.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import ru.bpdiary.core.model.Direction

val LocalBpColors =
    staticCompositionLocalOf<BpColors> { error("BpColors not provided: wrap the content in BpTheme") }
val LocalBpShapes =
    staticCompositionLocalOf<BpShapes> { error("BpShapes not provided: wrap the content in BpTheme") }

/** Access to the tokens inside [BpTheme]. */
object BpTheme {
    val colors: BpColors
        @Composable @ReadOnlyComposable
        get() = LocalBpColors.current

    val shapes: BpShapes
        @Composable @ReadOnlyComposable
        get() = LocalBpShapes.current
}

/**
 * Provides the BP tokens and a Material theme derived from them, so that stock components
 * (Switch, TimePicker, Snackbar) match.
 */
@Composable
fun BpTheme(
    direction: Direction = Direction.SAGE,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = remember(direction, dark) { bpColors(direction, dark) }
    val shapes = remember(direction) { bpShapes(direction) }
    CompositionLocalProvider(LocalBpColors provides colors, LocalBpShapes provides shapes) {
        MaterialTheme(colorScheme = colors.toMaterial(dark), content = content)
    }
}

internal fun BpColors.toMaterial(dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = ac,
            onPrimary = aci,
            primaryContainer = acs,
            onPrimaryContainer = ink,
            background = bg,
            onBackground = ink,
            surface = sf,
            onSurface = ink,
            surfaceVariant = sf2,
            onSurfaceVariant = mut,
            outline = ln,
            error = hi,
        )
    } else {
        lightColorScheme(
            primary = ac,
            onPrimary = aci,
            primaryContainer = acs,
            onPrimaryContainer = ink,
            background = bg,
            onBackground = ink,
            surface = sf,
            onSurface = ink,
            surfaceVariant = sf2,
            onSurfaceVariant = mut,
            outline = ln,
            error = hi,
        )
    }
