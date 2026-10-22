package ru.bpdiary.data.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings WHERE deleted_at IS NULL ORDER BY taken_at DESC LIMIT 1")
    fun observeLast(): Flow<ReadingEntity?>

    @Query(
        """SELECT * FROM readings
           WHERE deleted_at IS NULL AND taken_at BETWEEN :from AND :to
           ORDER BY taken_at ASC""",
    )
    fun observeRange(
        from: Long,
        to: Long,
    ): Flow<List<ReadingEntity>>

    /**
     * Diary page source. [status] is HIGH, LOW or NORMAL; an empty string means all statuses.
     * The conditions repeat the classification priority: HIGH first, then LOW.
     */
    @Query(
        """SELECT * FROM readings
           WHERE deleted_at IS NULL
             AND (:status = ''
                  OR (:status = 'HIGH' AND (sys >= :hs OR dia >= :hd))
                  OR (:status = 'LOW' AND NOT (sys >= :hs OR dia >= :hd) AND (sys <= :ls OR dia <= :ld))
                  OR (:status = 'NORMAL' AND NOT (sys >= :hs OR dia >= :hd) AND NOT (sys <= :ls OR dia <= :ld)))
           ORDER BY taken_at DESC""",
    )
    fun pagingSource(
        status: String,
        hs: Int,
        hd: Int,
        ls: Int,
        ld: Int,
    ): PagingSource<Int, ReadingEntity>

    @Insert
    suspend fun insert(entity: ReadingEntity): Long

    @Update
    suspend fun update(entity: ReadingEntity)

    @Query("UPDATE readings SET deleted_at = :now WHERE id = :id")
    suspend fun softDelete(
        id: Long,
        now: Long,
    )

    @Query("UPDATE readings SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM readings WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: Long): ReadingEntity?

    @Query("SELECT * FROM readings WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: Long): Flow<ReadingEntity?>

    @Query("SELECT COUNT(*) FROM readings WHERE deleted_at IS NULL")
    fun observeCount(): Flow<Int>
}
