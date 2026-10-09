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

    @Query("SELECT * FROM game_results WHERE gameType = :gameType ORDER BY score ASC, playedAt ASC, id ASC LIMIT 20")
    suspend fun getTop20ByGameTypeAsc(gameType: String): List<GameResult>

    @Query("SELECT MAX(playedAt) FROM game_results WHERE gameType = :gameType")
    suspend fun getLastPlayedAt(gameType: String): Long?

    @Query("SELECT COUNT(DISTINCT playedAt) FROM game_results WHERE gameType = :gameType")
    suspend fun getTotalSessionCount(gameType: String): Int

    // ── General statistics (aggregates, no schema change) ─────────────────────

    /** One row per (player, game). "Counted" = sessions shared with at least one other player. */
    @Query("""
        SELECT r.playerId AS playerId, r.gameType AS gameType,
               COUNT(*) AS totalGames,
               COUNT(s.playedAt) AS countedGames,
               SUM(r.isWinner) AS wins,
               SUM(r.isDraw) AS draws,
               MAX(r.score) AS maxScore,
               MIN(r.score) AS minScore
        FROM game_results r
        LEFT JOIN (
            SELECT gameType, playedAt FROM game_results
            GROUP BY gameType, playedAt HAVING COUNT(*) > 1
        ) s ON s.gameType = r.gameType AND s.playedAt = r.playedAt
        GROUP BY r.playerId, r.gameType
    """)
    suspend fun getAllPlayerGameStats(): List<PlayerGameStats>

    @Query("""
        SELECT gameType, MAX(playedAt) AS lastPlayedAt, COUNT(DISTINCT playedAt) AS sessions
        FROM game_results GROUP BY gameType
    """)
    suspend fun getGameSummaries(): List<GameSummary>

    @Query("SELECT * FROM game_results ORDER BY id ASC")
    suspend fun getAllResults(): List<GameResult>

    @Query("SELECT DISTINCT gameType, playedAt FROM game_results")
    suspend fun getAllGameKeys(): List<com.github.trivialloop.scorehub.data.backup.GameKey>

    @Query("DELETE FROM game_results")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM game_results")
    suspend fun count(): Int
}

data class PlayerWins(
    val playerId: Long,
    val wins: Int
)

data class PlayerGameStats(
    val playerId: Long,
    val gameType: String,
    val totalGames: Int,
    val countedGames: Int,
    val wins: Int,
    val draws: Int,
    val maxScore: Int,
    val minScore: Int
)

data class GameSummary(
    val gameType: String,
    val lastPlayedAt: Long,
    val sessions: Int
)
