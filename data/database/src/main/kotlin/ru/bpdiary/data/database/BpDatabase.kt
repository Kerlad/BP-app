package ru.bpdiary.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReadingEntity::class, ReminderEntity::class],
    version = BpDatabase.VERSION,
    exportSchema = true,
)
abstract class BpDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao

    abstract fun readingMaintenanceDao(): ReadingMaintenanceDao

    abstract fun reminderDao(): ReminderDao

    companion object {
        const val VERSION = 1
        const val NAME = "bp_diary.db"

        /**
         * No migrations yet: the schema stays at v1 until the first release.
         * `fallbackToDestructiveMigration()` is forbidden (health data kept for years).
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): BpDatabase {
            val builder =
                Room
                    .databaseBuilder(context, BpDatabase::class.java, NAME)
                    .addCallback(DefaultRemindersCallback)
            MIGRATIONS.forEach { builder.addMigrations(it) }
            return builder.build()
        }
    }
}

/**
 * The three reminders of the design handoff. Also used by "Delete all data" to start over.
 * The labels are stored data, not UI text, so they are the one place where Russian text lives outside resources
 * (docs/03 §3.2).
 */
object DefaultReminders {
    private const val ALL_DAYS = 127
    private const val WORKDAYS = 31

    val ENTITIES: List<ReminderEntity> =
        listOf(
            ReminderEntity(minutesOfDay = 480, label = "Утреннее измерение", daysMask = ALL_DAYS, enabled = true),
            ReminderEntity(minutesOfDay = 840, label = "Дневное измерение", daysMask = WORKDAYS, enabled = false),
            ReminderEntity(minutesOfDay = 1260, label = "Вечернее измерение", daysMask = ALL_DAYS, enabled = true),
        )
}

/** Fills the reminders table when the database file is created. */
object DefaultRemindersCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        for (r in DefaultReminders.ENTITIES) {
            db.execSQL(
                "INSERT INTO reminders (minutes_of_day, label, days_mask, enabled) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(r.minutesOfDay, r.label, r.daysMask, if (r.enabled) 1 else 0),
            )
        }
    }
}
