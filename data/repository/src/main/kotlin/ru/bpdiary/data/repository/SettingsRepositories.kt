package ru.bpdiary.data.repository

import kotlinx.coroutines.flow.Flow
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.data.datastore.SettingsDataStore
import ru.bpdiary.domain.SettingsRepository
import ru.bpdiary.domain.ThresholdsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThresholdsRepositoryImpl
    @Inject
    constructor(
        private val store: SettingsDataStore,
    ) : ThresholdsRepository {
        override fun observe(): Flow<Thresholds> = store.thresholds

        override suspend fun set(thresholds: Thresholds) = store.setThresholds(thresholds)
    }

@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val store: SettingsDataStore,
    ) : SettingsRepository {
        override val lastArm: Flow<Arm> = store.lastArm
        override val themeMode: Flow<ThemeMode> = store.themeMode
        override val direction: Flow<Direction> = store.direction
        override val fontScaleBoost: Flow<Float> = store.fontScaleBoost
        override val onboardingDone: Flow<Boolean> = store.onboardingDone
        override val disclaimerAck: Flow<Int> = store.disclaimerAck

        override suspend fun setLastArm(arm: Arm) = store.setLastArm(arm)

        override suspend fun setThemeMode(mode: ThemeMode) = store.setThemeMode(mode)

        override suspend fun setDirection(direction: Direction) = store.setDirection(direction)

        override suspend fun setFontScaleBoost(boost: Float) = store.setFontScaleBoost(boost)

        override suspend fun setOnboardingDone(done: Boolean) = store.setOnboardingDone(done)

        override suspend fun setDisclaimerAck(version: Int) = store.setDisclaimerAck(version)

        override suspend fun clearAll() = store.clear()
    }
