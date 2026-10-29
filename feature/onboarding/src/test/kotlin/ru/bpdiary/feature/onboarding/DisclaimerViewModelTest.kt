package ru.bpdiary.feature.onboarding

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.bpdiary.core.model.DISCLAIMER_VERSION
import ru.bpdiary.domain.fake.FakeSettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class DisclaimerViewModelTest {
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts unchecked, so the button is inactive`() {
        assertFalse(DisclaimerViewModel(settings).state.value.checked)
    }

    @Test
    fun `without the checkbox nothing is accepted`() =
        runTest {
            val vm = DisclaimerViewModel(settings)
            vm.effects.test {
                vm.onEvent(DisclaimerEvent.ContinueClicked)
                expectNoEvents()
            }
            assertEquals(0, settings.disclaimerAck.first())
        }

    @Test
    fun `with the checkbox the current version is stored and the app goes on`() =
        runTest {
            val vm = DisclaimerViewModel(settings)
            vm.onEvent(DisclaimerEvent.CheckedChanged(true))
            assertTrue(vm.state.value.checked)
            vm.effects.test {
                vm.onEvent(DisclaimerEvent.ContinueClicked)
                assertEquals(DisclaimerEffect.Accepted, awaitItem())
            }
            assertEquals(DISCLAIMER_VERSION, settings.disclaimerAck.first())
        }

    @Test
    fun `the checkbox can be cleared again`() {
        val vm = DisclaimerViewModel(settings)
        vm.onEvent(DisclaimerEvent.CheckedChanged(true))
        vm.onEvent(DisclaimerEvent.CheckedChanged(false))
        assertFalse(vm.state.value.checked)
    }
}
