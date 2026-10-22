package ru.bpdiary.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "readings",
    indices = [
        Index(value = ["taken_at"]),
        Index(value = ["profile_id", "taken_at"]),
    ],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis, UTC. */
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    /** Offset at the moment of measurement, in seconds. */
    @ColumnInfo(name = "tz_offset") val tzOffsetSeconds: Int,
    @ColumnInfo(name = "sys") val sys: Int,
    @ColumnInfo(name = "dia") val dia: Int,
    /** NULL when the pulse is unknown (import only). */
    @ColumnInfo(name = "pulse") val pulse: Int?,
    /** "LEFT" or "RIGHT". */
    @ColumnInfo(name = "arm") val arm: String,
    /** JSON array of Tag keys, e.g. ["HEADACHE"]. */
    @ColumnInfo(name = "tags") val tags: String,
    @ColumnInfo(name = "comment") val comment: String,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Soft delete for "Undo"; purged after 24 hours. */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)
