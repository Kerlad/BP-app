package ru.bpdiary.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BoostedFontScaleTest {
    @get:Rule
    val rule = createComposeRule()

    private fun scaleSeenInside(
        system: Float,
        boost: Float,
    ): Float {
        var seen = 0f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, system)) {
                BoostedFontScale(boost) { seen = LocalDensity.current.fontScale }
            }
        }
        return seen
    }

    @Test
    fun `the boost multiplies the system font scale`() {
        assertEquals(1.15f * 1.3f, scaleSeenInside(system = 1.15f, boost = 1.3f), 0.0001f)
    }

    @Test
    fun `no boost leaves the system scale alone`() {
        assertEquals(1.3f, scaleSeenInside(system = 1.3f, boost = 1f), 0f)
    }

    @Test
    fun `the screen density is not touched`() {
        var density = 0f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.5f, 1f)) {
                BoostedFontScale(1.3f) { density = LocalDensity.current.density }
            }
        }
        assertEquals(2.5f, density, 0f)
    }
}
