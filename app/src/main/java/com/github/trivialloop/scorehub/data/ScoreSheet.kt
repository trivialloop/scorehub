package com.github.trivialloop.scorehub.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One saved score sheet (PNG snapshot of the final grid). Key of the session = (gameType, playedAt). */
@Entity(tableName = "score_sheets", indices = [Index(value = ["gameType"])])
data class ScoreSheet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameType: String,
    val playedAt: Long,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis()
)
