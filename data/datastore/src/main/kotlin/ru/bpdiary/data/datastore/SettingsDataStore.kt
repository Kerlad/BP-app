package ru.bpdiary.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Direction
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.model.problem

/** Typed access to `settings.preferences_pb`. Unknown or corrupted stored values fall back to the defaults. */
class SettingsDataStore(
    private val store: DataStore<Preferences>,
) {
    /** Emits only real changes, otherwise every unrelated write would redraw the charts. */
    val thresholds: Flow<Thresholds> =
        store.data
            .map { prefs ->
                val stored =
                    Thresholds(
                        highSys = prefs[HIGH_SYS] ?: DEFAULT_THRESHOLDS.highSys,
                        highDia = prefs[HIGH_DIA] ?: DEFAULT_THRESHOLDS.highDia,
                        lowSys = prefs[LOW_SYS] ?: DEFAULT_THRESHOLDS.lowSys,
                        lowDia = prefs[LOW_DIA] ?: DEFAULT_THRESHOLDS.lowDia,
                        pulseLow = prefs[PULSE_LOW] ?: DEFAULT_THRESHOLDS.pulseLow,
                        pulseHigh = prefs[PULSE_HIGH] ?: DEFAULT_THRESHOLDS.pulseHigh,
                    )
                if (stored.problem() == null) stored else DEFAULT_THRESHOLDS
            }.distinctUntilChanged()

    val lastArm: Flow<Arm> = enumFlow(LAST_ARM, DEFAULT_ARM)
    val themeMode: Flow<ThemeMode> = enumFlow(THEME_MODE, DEFAULT_THEME_MODE)
    val direction: Flow<Direction> = enumFlow(DIRECTION, DEFAULT_DIRECTION)
    val fontScaleBoost: Flow<Float> = valueFlow(FONT_SCALE_BOOST, DEFAULT_FONT_SCALE_BOOST)
    val onboardingDone: Flow<Boolean> = valueFlow(ONBOARDING_DONE, false)

    /** Version of the accepted disclaimer; 0 means never accepted. */
    val disclaimerAck: Flow<Int> = valueFlow(DISCLAIMER_ACK, 0)

    /** @throws IllegalArgumentException when [thresholds] break a range or the low < high rule. */
    suspend fun setThresholds(thresholds: Thresholds) {
        val problem = thresholds.problem()
        require(problem == null) { "Invalid thresholds: $problem" }
        store.edit {
            it[HIGH_SYS] = thresholds.highSys
            it[HIGH_DIA] = thresholds.highDia
            it[LOW_SYS] = thresholds.lowSys
            it[LOW_DIA] = thresholds.lowDia
            it[PULSE_LOW] = thresholds.pulseLow
            it[PULSE_HIGH] = thresholds.pulseHigh
        }
    }

    suspend fun setLastArm(arm: Arm) {
        store.edit { it[LAST_ARM] = arm.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDirection(direction: Direction) {
        store.edit { it[DIRECTION] = direction.name }
    }

    suspend fun setFontScaleBoost(boost: Float) {
        store.edit { it[FONT_SCALE_BOOST] = boost }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        store.edit { it[ONBOARDING_DONE] = done }
    }

    suspend fun setDisclaimerAck(version: Int) {
        store.edit { it[DISCLAIMER_ACK] = version }
    }

    /** "Delete all data": everything goes back to the defaults. */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    private inline fun <reified E : Enum<E>> enumFlow(
        key: Preferences.Key<String>,
        default: E,
    ): Flow<E> =
        store.data
            .map { prefs -> enumValues<E>().firstOrNull { it.name == prefs[key] } ?: default }
            .distinctUntilChanged()

    private fun <T> valueFlow(
        key: Preferences.Key<T>,
        default: T,
    ): Flow<T> = store.data.map { it[key] ?: default }.distinctUntilChanged()

    private companion object {
        val HIGH_SYS = intPreferencesKey("th_high_sys")
        val HIGH_DIA = intPreferencesKey("th_high_dia")
        val LOW_SYS = intPreferencesKey("th_low_sys")
        val LOW_DIA = intPreferencesKey("th_low_dia")
        val PULSE_LOW = intPreferencesKey("th_pulse_low")
        val PULSE_HIGH = intPreferencesKey("th_pulse_high")
        val LAST_ARM = stringPreferencesKey("last_arm")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DIRECTION = stringPreferencesKey("direction")
        val FONT_SCALE_BOOST = floatPreferencesKey("font_scale_boost")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DISCLAIMER_ACK = intPreferencesKey("disclaimer_ack")

        val DEFAULT_THRESHOLDS = Thresholds()
        val DEFAULT_ARM = Arm.LEFT
        val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        val DEFAULT_DIRECTION = Direction.SAGE
        const val DEFAULT_FONT_SCALE_BOOST = 1.0f
    }
}
