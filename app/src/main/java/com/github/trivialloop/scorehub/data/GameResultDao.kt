package com.github.trivialloop.scorehub.data

import androidx.room.*

@Dao
interface GameResultDao {
    @Insert
    suspend fun insertGameResult(gameResult: GameResult): Long

    @Insert
    suspend fun insertGameResults(gameResults: List<GameResult>)

    @Query("SELECT * FROM game_results WHERE gameType = :gameType ORDER BY score DESC, playedAt ASC, id ASC LIMIT 20")
    suspend fun getTop20ByGameType(gameType: String): List<GameResult>

    @Query("SELECT COUNT(*) FROM game_results WHERE playerId = :playerId AND gameType = :gameType")
    suspend fun getGamesPlayedByPlayer(playerId: Long, gameType: String): Int

    @Query("SELECT COUNT(DISTINCT playedAt) FROM game_results WHERE playerId = :playerId AND gameType = :gameType AND playedAt IN (SELECT playedAt FROM game_results WHERE gameType = :gameType GROUP BY playedAt HAVING COUNT(*) > 1)")
    suspend fun getCountedGamesPlayedByPlayer(playerId: Long, gameType: String): Int

    @Query("SELECT COUNT(*) FROM game_results WHERE playerId = :playerId AND gameType = :gameType AND isWinner = 1")
    suspend fun getWinsByPlayer(playerId: Long, gameType: String): Int

    @Query("SELECT COUNT(*) FROM game_results WHERE playerId = :playerId AND gameType = :gameType AND isDraw = 1")
    suspend fun getDrawsByPlayer(playerId: Long, gameType: String): Int

    @Query("SELECT MAX(score) FROM game_results WHERE playerId = :playerId AND gameType = :gameType")
    suspend fun getBestScoreByPlayer(playerId: Long, gameType: String): Int?

    @Query("SELECT MIN(score) FROM game_results WHERE playerId = :playerId AND gameType = :gameType")
    suspend fun getWorstScoreByPlayer(playerId: Long, gameType: String): Int?

    @Query("UPDATE game_results SET playerName = :newName WHERE playerId = :playerId")
    suspend fun updatePlayerNameInResults(playerId: Long, newName: String)

    @Query("SELECT playerId, COUNT(*) as wins FROM game_results WHERE gameType = :gameType AND isWinner = 1 GROUP BY playerId ORDER BY wins DESC LIMIT 1")
    suspend fun getPlayerWithMostWins(gameType: String): PlayerWins?
}

data class PlayerWins(
    val playerId: Long,
    val wins: Int
)
