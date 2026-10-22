package ru.bpdiary.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.ui.ComponentShowcase

/**
 * Open with `adb shell am start -n ru.bpdiary.app.debug/ru.bpdiary.app.debug.ShowcaseActivity`.
 * Extra `direction` (PEACH, SAGE, PLUM) switches the colour direction; the theme follows the system.
 */
class ShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val direction = intent.getStringExtra("direction")?.let { runCatching { Direction.valueOf(it) }.getOrNull() }
        setContent { ComponentShowcase(direction = direction ?: Direction.SAGE, dark = isSystemInDarkTheme()) }
    }
}
