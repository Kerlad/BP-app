package ru.bpdiary.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.bpdiary.core.model.Direction

/**
 * First screenshots (Step 3.3). Run `./gradlew recordRoborazziDebug` to (re)record and
 * `./gradlew verifyRoborazziDebug` to check; a plain `testDebugUnitTest` only runs the assertions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1700dp-xxhdpi")
class ShowcaseSnapshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun snapshot(
        direction: Direction,
        dark: Boolean,
        name: String,
    ) {
        rule.setContent { ComponentShowcase(direction, dark) }
        rule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun sageLight() = snapshot(Direction.SAGE, dark = false, name = "showcase_sage_light")

    @Test
    fun sageDark() = snapshot(Direction.SAGE, dark = true, name = "showcase_sage_dark")

    @Test
    fun peachLight() = snapshot(Direction.PEACH, dark = false, name = "showcase_peach_light")

    @Test
    fun plumDark() = snapshot(Direction.PLUM, dark = true, name = "showcase_plum_dark")
}
