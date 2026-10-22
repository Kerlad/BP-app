package ru.bpdiary.domain

import kotlinx.coroutines.flow.Flow
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.ThemeMode

interface SettingsRepository {
    val lastArm: Flow<Arm>
    val themeMode: Flow<ThemeMode>
    val direction: Flow<Direction>
    val fontScaleBoost: Flow<Float>
    val onboardingDone: Flow<Boolean>

    /** Version of the accepted disclaimer, 0 if never accepted. */
    val disclaimerAck: Flow<Int>

    suspend fun setLastArm(arm: Arm)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDirection(direction: Direction)

    suspend fun setFontScaleBoost(boost: Float)

    suspend fun setOnboardingDone(done: Boolean)

    suspend fun setDisclaimerAck(version: Int)

    /** Everything back to the defaults, thresholds included. */
    suspend fun clearAll()
}
