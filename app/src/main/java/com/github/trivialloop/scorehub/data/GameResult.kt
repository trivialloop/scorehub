package com.github.trivialloop.scorehub.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_results")
data class GameResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameType: String, // "yams" for now
    val playerId: Long,
    val playerName: String,
    val score: Int,
    val isWinner: Boolean,
    val isDraw: Boolean,
    val playedAt: Long = System.currentTimeMillis()
)
