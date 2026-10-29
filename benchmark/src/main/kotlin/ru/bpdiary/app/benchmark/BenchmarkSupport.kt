package ru.bpdiary.app.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** The `benchmark` build of `:app` (application id suffix `.benchmark`). */
internal const val TARGET_PACKAGE = "ru.bpdiary.app.benchmark"

private const val SEED_RECEIVER = "$TARGET_PACKAGE/ru.bpdiary.app.benchmark.SeedReceiver"
private const val UI_TIMEOUT_MS = 5_000L

/**
 * Fills the app's database with 2 000 readings and skips the onboarding (see `SeedReceiver` in `:app`).
 * `am broadcast` returns when the receiver has finished; running it again changes nothing.
 */
internal fun seedDemoData() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val output = device.executeShellCommand("am broadcast -n $SEED_RECEIVER")
    check("Broadcast completed" in output) { "The data seeder did not run: $output" }
    // The receiver started the app process, and a cold start needs the process to be gone.
    device.executeShellCommand("am force-stop $TARGET_PACKAGE")
}

/** A tap on the bottom navigation or on any control with this text. */
internal fun MacrobenchmarkScope.tapText(text: String) {
    val target = device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MS)
    // The report of a Windows build garbles Cyrillic, so the text is also written as escapes.
    checkNotNull(target) { "No «$text» on the screen: ${text.map { "\\u%04x".format(it.code) }}" }.click()
    device.waitForIdle()
}
