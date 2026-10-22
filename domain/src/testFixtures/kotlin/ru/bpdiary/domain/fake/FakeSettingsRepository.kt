package ru.bpdiary.domain.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.domain.SettingsRepository

class FakeSettingsRepository : SettingsRepository {
    private val arm = MutableStateFlow(Arm.LEFT)
    private val theme = MutableStateFlow(ThemeMode.SYSTEM)
    private val dir = MutableStateFlow(Direction.SAGE)
    private val boost = MutableStateFlow(1.0f)
    private val onboarding = MutableStateFlow(false)
    private val ack = MutableStateFlow(0)

    override val lastArm: Flow<Arm> = arm
    override val themeMode: Flow<ThemeMode> = theme
    override val direction: Flow<Direction> = dir
    override val fontScaleBoost: Flow<Float> = boost
    override val onboardingDone: Flow<Boolean> = onboarding
    override val disclaimerAck: Flow<Int> = ack

    override suspend fun setLastArm(arm: Arm) {
        this.arm.value = arm
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }

    override suspend fun setDirection(direction: Direction) {
        dir.value = direction
    }

    override suspend fun setFontScaleBoost(boost: Float) {
        this.boost.value = boost
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        onboarding.value = done
    }

    override suspend fun setDisclaimerAck(version: Int) {
        ack.value = version
    }

    override suspend fun clearAll() {
        arm.value = Arm.LEFT
        theme.value = ThemeMode.SYSTEM
        dir.value = Direction.SAGE
        boost.value = 1.0f
        onboarding.value = false
        ack.value = 0
    }
}
