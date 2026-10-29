package ru.bpdiary.app.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scrolling the diary of 2 000 readings: no janky frames at the 95th percentile (docs/10 §10.7).
 * Look at the 95th percentile of the frame duration and at the frame overrun in the report. Run on a real phone.
 */
@RunWith(AndroidJUnit4::class)
class DiaryScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun seed() = seedDemoData()

    @Test
    fun scrollDiary() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
            startupMode = StartupMode.WARM,
            iterations = ITERATIONS,
            setupBlock = {
                startActivityAndWait()
                tapText(DIARY_TAB)
                device.wait(Until.hasObject(By.scrollable(true)), LIST_TIMEOUT_MS)
            },
        ) {
            val list = device.findObject(By.scrollable(true))
            // Keep away from the screen edges: they are the system gesture areas.
            list.setGestureMargin(device.displayWidth / EDGE_DIVIDER)
            repeat(FLINGS) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }

    private companion object {
        const val DIARY_TAB = "Дневник"
        const val ITERATIONS = 5
        const val FLINGS = 5
        const val EDGE_DIVIDER = 5
        const val LIST_TIMEOUT_MS = 5_000L
    }
}
