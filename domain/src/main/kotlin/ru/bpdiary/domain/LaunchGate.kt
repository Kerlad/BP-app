package ru.bpdiary.domain

import ru.bpdiary.core.model.DISCLAIMER_VERSION

/** What the app shows first (docs/05 §5.8, D-18). */
enum class LaunchGate {
    /** First launch: the three onboarding screens, then the disclaimer. */
    ONBOARDING,

    /** Onboarding was seen, but the current disclaimer version has not been accepted. */
    DISCLAIMER,

    MAIN,
}

/** The disclaimer comes back whenever the accepted version is older than [DISCLAIMER_VERSION]. */
fun launchGate(
    onboardingDone: Boolean,
    disclaimerAck: Int,
): LaunchGate =
    when {
        !onboardingDone -> LaunchGate.ONBOARDING
        disclaimerAck < DISCLAIMER_VERSION -> LaunchGate.DISCLAIMER
        else -> LaunchGate.MAIN
    }
