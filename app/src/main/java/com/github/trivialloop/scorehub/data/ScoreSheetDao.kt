package com.github.trivialloop.scorehub.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScoreSheetDao {
    @Insert
    suspend fun insert(sheet: ScoreSheet): Long

    @Query("SELECT * FROM score_sheets ORDER BY playedAt DESC, id DESC")
    suspend fun getAll(): List<ScoreSheet>

    @Query("SELECT * FROM score_sheets WHERE gameType = :gameType ORDER BY playedAt DESC, id DESC")
    suspend fun getByGame(gameType: String): List<ScoreSheet>

    @Query("SELECT * FROM score_sheets WHERE id = :id")
    suspend fun getById(id: Long): ScoreSheet?

    @Query("SELECT COUNT(*) FROM score_sheets WHERE gameType = :gameType")
    suspend fun countByGame(gameType: String): Int

    @Query("SELECT * FROM score_sheets WHERE gameType = :gameType ORDER BY playedAt ASC, id ASC LIMIT :limit")
    suspend fun getOldest(gameType: String, limit: Int): List<ScoreSheet>

    @Query("DELETE FROM score_sheets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM score_sheets")
    suspend fun deleteAll()
}
