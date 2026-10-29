package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.bpdiary.core.model.DISCLAIMER_VERSION

class LaunchGateTest {
    @Test
    fun `a first launch starts with the onboarding`() {
        assertEquals(LaunchGate.ONBOARDING, launchGate(onboardingDone = false, disclaimerAck = 0))
    }

    @Test
    fun `the onboarding comes first even if a disclaimer version is stored`() {
        assertEquals(LaunchGate.ONBOARDING, launchGate(onboardingDone = false, disclaimerAck = DISCLAIMER_VERSION))
    }

    @Test
    fun `onboarding seen but the disclaimer not accepted shows only the disclaimer`() {
        assertEquals(LaunchGate.DISCLAIMER, launchGate(onboardingDone = true, disclaimerAck = 0))
    }

    @Test
    fun `an older accepted version asks again`() {
        assertEquals(LaunchGate.DISCLAIMER, launchGate(onboardingDone = true, disclaimerAck = DISCLAIMER_VERSION - 1))
    }

    @Test
    fun `the accepted current version goes to the app`() {
        assertEquals(LaunchGate.MAIN, launchGate(onboardingDone = true, disclaimerAck = DISCLAIMER_VERSION))
    }

    @Test
    fun `a newer stored version is not asked again`() {
        assertEquals(LaunchGate.MAIN, launchGate(onboardingDone = true, disclaimerAck = DISCLAIMER_VERSION + 1))
    }
}
