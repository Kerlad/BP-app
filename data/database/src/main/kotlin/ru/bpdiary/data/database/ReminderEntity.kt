package ru.bpdiary.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0..1439. */
    @ColumnInfo(name = "minutes_of_day") val minutesOfDay: Int,
    @ColumnInfo(name = "label") val label: String,
    /** Bit mask, Monday = 1 shl 0. */
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
)
