package ru.bpdiary.data.database

import androidx.room.Database
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
    }
}

/** Default reminders from the design handoff. Created once, when the database file is created. */
object DefaultRemindersCallback : RoomDatabase.Callback() {
    // The only place where Russian text lives outside resources (docs/03 §3.2): it is stored data, not UI text.
    private const val INSERT_DEFAULTS =
        "INSERT INTO reminders (minutes_of_day, label, days_mask, enabled) VALUES " +
            "(480, 'Утреннее измерение', 127, 1), " +
            "(840, 'Дневное измерение', 31, 0), " +
            "(1260, 'Вечернее измерение', 127, 1)"

    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(INSERT_DEFAULTS)
    }
}
