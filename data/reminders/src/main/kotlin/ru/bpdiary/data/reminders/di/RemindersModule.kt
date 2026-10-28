package ru.bpdiary.data.reminders.di

import android.app.AlarmManager
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.bpdiary.data.reminders.AlarmReminderScheduler
import ru.bpdiary.data.reminders.NotificationReminderNotifier
import ru.bpdiary.domain.ReminderNotifier
import ru.bpdiary.domain.ReminderScheduler

@Module
@InstallIn(SingletonComponent::class)
internal object RemindersProvidesModule {
    @Provides
    fun alarmManager(
        @ApplicationContext context: Context,
    ): AlarmManager = context.getSystemService(AlarmManager::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RemindersBindingsModule {
    @Binds
    abstract fun scheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    abstract fun notifier(impl: NotificationReminderNotifier): ReminderNotifier
}
