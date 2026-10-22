package ru.bpdiary.data.database

import androidx.room.Dao
import androidx.room.Query

/** Physical deletion of readings; the everyday queries live in [ReadingDao]. */
@Dao
interface ReadingMaintenanceDao {
    /** Physically removes soft-deleted rows whose deletion is older than [before] (strictly). */
    @Query("DELETE FROM readings WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun purgeDeletedBefore(before: Long)

    /** "Delete all data" and import "Replace all". */
    @Query("DELETE FROM readings")
    suspend fun hardDeleteAll()

    /** Counts soft-deleted rows too; used to verify that "Delete all data" leaves nothing behind. */
    @Query("SELECT COUNT(*) FROM readings")
    suspend fun countIncludingDeleted(): Int
}
