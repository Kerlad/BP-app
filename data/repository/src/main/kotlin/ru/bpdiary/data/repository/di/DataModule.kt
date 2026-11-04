package ru.bpdiary.data.repository.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.bpdiary.core.common.SystemTimeProvider
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.data.database.BpDatabase
import ru.bpdiary.data.database.ReadingDao
import ru.bpdiary.data.database.ReadingMaintenanceDao
import ru.bpdiary.data.database.ReminderDao
import ru.bpdiary.data.datastore.SettingsDataStore
import ru.bpdiary.data.datastore.createSettingsDataStore
import ru.bpdiary.data.repository.ReadingImporterImpl
import ru.bpdiary.data.repository.ReadingRepositoryImpl
import ru.bpdiary.data.repository.ReminderRepositoryImpl
import ru.bpdiary.data.repository.SettingsRepositoryImpl
import ru.bpdiary.data.repository.ThresholdsRepositoryImpl
import ru.bpdiary.domain.ReadingImporter
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.ReminderRepository
import ru.bpdiary.domain.SettingsRepository
import ru.bpdiary.domain.ThresholdsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidesModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): BpDatabase = BpDatabase.create(context)

    @Provides
    fun readingDao(db: BpDatabase): ReadingDao = db.readingDao()

    @Provides
    fun readingMaintenanceDao(db: BpDatabase): ReadingMaintenanceDao = db.readingMaintenanceDao()

    @Provides
    fun reminderDao(db: BpDatabase): ReminderDao = db.reminderDao()

    @Provides
    @Singleton
    fun settingsDataStore(
        @ApplicationContext context: Context,
    ): SettingsDataStore = createSettingsDataStore(context)

    /** The only production source of time (D-09). */
    @Provides
    @Singleton
    fun timeProvider(): TimeProvider = SystemTimeProvider()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryBindingsModule {
    @Binds
    abstract fun readings(impl: ReadingRepositoryImpl): ReadingRepository

    @Binds
    abstract fun readingImporter(impl: ReadingImporterImpl): ReadingImporter

    @Binds
    abstract fun reminders(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    abstract fun thresholds(impl: ThresholdsRepositoryImpl): ThresholdsRepository

    @Binds
    abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
}
