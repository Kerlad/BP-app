package ru.bpdiary.app.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.bpdiary.core.common.DemoData
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.DISCLAIMER_VERSION
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.SettingsRepository
import javax.inject.Inject

/**
 * Benchmark build only. Fills the diary with [READINGS] readings and skips the onboarding and the disclaimer, so the
 * measured start and the diary scroll see a realistic database. Does nothing when the readings are already there.
 * `am broadcast` returns when the pending result is finished.
 */
@AndroidEntryPoint
class SeedReceiver : BroadcastReceiver() {
    @Inject lateinit var readings: ReadingRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var time: TimeProvider

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                settings.setOnboardingDone(true)
                settings.setDisclaimerAck(DISCLAIMER_VERSION)
                if (readings.observeCount().first() < READINGS) {
                    DemoData.bulk(READINGS, time).forEach { readings.save(it) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val READINGS = 2_000
    }
}
