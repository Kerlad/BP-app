package ru.bpdiary.app.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/** Everything is found by text or description, never by coordinates (docs/10 §10.5). */
private const val WAIT_MS = 10_000L

/** The database and the settings are read asynchronously, so a screen needs a moment to show its texts. */
internal fun ComposeTestRule.waitForText(text: String) {
    waitUntil(WAIT_MS) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
}

internal fun ComposeTestRule.waitForDescription(description: String) {
    waitUntil(WAIT_MS) { onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
}

/** A part of a longer text, such as the note of a diary row «В норме · Левая рука». */
internal fun ComposeTestRule.waitForTextContaining(part: String) {
    waitUntil(WAIT_MS) { onAllNodes(hasText(part, substring = true)).fetchSemanticsNodes().isNotEmpty() }
}

internal fun ComposeTestRule.hasTextContaining(part: String): Boolean =
    onAllNodes(hasText(part, substring = true)).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeTestRule.tapText(text: String) {
    waitForText(text)
    onNodeWithText(text).performClick()
}

internal fun ComposeTestRule.tapDescription(description: String) {
    waitForDescription(description)
    onNodeWithContentDescription(description).performClick()
}

/**
 * First launch: three onboarding screens, then the disclaimer with its checkbox (docs/05 §5.8).
 * The permissions are only offered, never needed, so nothing system-wide is touched. Ends on an empty Home.
 */
internal fun ComposeTestRule.passOnboarding() {
    repeat(ONBOARDING_STEPS) { tapText("Далее") }
    tapText("Я прочитал(а) и понимаю")
    tapText("Продолжить")
    waitForText("Записать давление")
}

/** From Home: «Записать давление», keypad digits one by one, «Сохранить». Ends on Home with the toast. */
internal fun ComposeTestRule.recordReading(
    sys: String,
    dia: String,
    pulse: String,
) {
    tapText("Записать давление")
    (sys + dia + pulse).forEach { digit -> tapDescription("цифра $digit") }
    tapText("Сохранить")
}

private const val ONBOARDING_STEPS = 3
