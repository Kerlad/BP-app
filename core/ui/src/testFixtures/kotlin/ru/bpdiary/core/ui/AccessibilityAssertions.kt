package ru.bpdiary.core.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider

/** The smallest touch target (docs/04 §4.4 and the accessibility rules). */
private const val MIN_TARGET_DP = 48

/** Pixel rounding of a target laid out at 47.99 dp is not a finding. */
private const val ROUNDING_PX = 1f

/**
 * Accessibility checks on the semantics tree, run on the JVM. The checks of Compose itself
 * (`enableAccessibilityChecks`) only work with a real accessibility service on a device: under Robolectric they pass
 * even for a 20 dp clickable without a label, so they cannot guard anything here.
 *
 * Every actionable node (click, toggle, selection) as TalkBack sees it, merged with its children, must have
 * - a name: text or content description, and
 * - a touch target of at least 48 dp both ways.
 *
 * [allowSmallTargets] names nodes by their label that are knowingly smaller; keep it empty unless a finding is
 * explained in `docs/qa-checklist.md`.
 */
fun SemanticsNodeInteractionsProvider.assertAccessible(allowSmallTargets: Set<String> = emptySet()) {
    val findings = mutableListOf<String>()
    val actionable =
        SemanticsMatcher("is actionable") { node ->
            val config = node.config
            SemanticsActions.OnClick in config || SemanticsProperties.ToggleableState in config
        }
    onAllNodes(actionable).fetchSemanticsNodes().forEach { node ->
        val config = node.config
        val texts = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        val descriptions = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val name = (descriptions + texts).filter { it.isNotBlank() }.joinToString(", ")
        // The touch bounds are clipped to the visible part, so a control scrolled out of sight reads as 0 x 0;
        // the size of the node itself is not.
        val bounds = node.touchBoundsInRoot
        val density = node.layoutInfo.density.density
        val width = maxOf(node.size.width.toFloat(), bounds.width)
        val height = maxOf(node.size.height.toFloat(), bounds.height)
        val minPx = MIN_TARGET_DP * density
        // Names are written as \uXXXX escapes: the test report of a Windows build garbles Cyrillic.
        if (name.isEmpty()) {
            findings += "a control without a name, ${width / density} x ${height / density} dp"
        } else if (name !in allowSmallTargets && (width + ROUNDING_PX < minPx || height + ROUNDING_PX < minPx)) {
            val size = "${width / density} x ${height / density} dp"
            findings += "\"${name.escaped()}\" is $size, must be at least $MIN_TARGET_DP"
        }
    }
    check(findings.isEmpty()) { "Accessibility findings:\n" + findings.joinToString("\n") { "  - $it" } }
}

private fun String.escaped(): String =
    map { if (it.code < ASCII_LIMIT) it.toString() else "\\u%04x".format(it.code) }.joinToString("")

private const val ASCII_LIMIT = 128
