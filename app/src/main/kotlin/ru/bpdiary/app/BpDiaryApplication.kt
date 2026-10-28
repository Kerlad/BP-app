package ru.bpdiary.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ru.bpdiary.data.reminders.ReminderWork
import javax.inject.Inject

/** WorkManager is created on demand with Hilt's factory (its startup initializer is removed in the manifest). */
@HiltAndroidApp
class BpDiaryApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Initial reminder schedule after the database is created, then a daily watchdog (docs/07 §7.3).
        ReminderWork.start(this)
    }
}
