package com.github.trivialloop.scorehub.utils

import com.github.trivialloop.scorehub.GameRegistry
import com.github.trivialloop.scorehub.data.GameSummary
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.data.PlayerGameStats

object GeneralStatsCalculator {

    // ─── "By game" view ───────────────────────────────────────────────────────

    data class RankedPlayer(val player: Player, val wins: Int, val countedGames: Int) {
        val winPercentage: Float get() = if (countedGames > 0) wins * 100f / countedGames else 0f
    }

    data class BestScore(val player: Player, val score: Int)

    data class GameStatsCard(
        val definition: GameRegistry.GameDefinition,
        val sessions: Int,
        val lastPlayedAt: Long,
        val bestPlayer: RankedPlayer?,
        val bestScore: BestScore?
    )

    fun buildGameCards(
        games: List<GameRegistry.GameDefinition>,
        players: List<Player>,
        stats: List<PlayerGameStats>,
        summaries: List<GameSummary>
    ): List<GameStatsCard> {
        val playersById = players.associateBy { it.id }
        val summaryByType = summaries.associateBy { it.gameType }

        return games.mapNotNull { def ->
            val summary = summaryByType[def.gameType] ?: return@mapNotNull null
            val gameStats = stats.filter { it.gameType == def.gameType && it.playerId in playersById }

            val bestPlayer = gameStats
                .filter { it.countedGames > 0 }
                .sortedWith(
                    compareByDescending<PlayerGameStats> { it.wins.toFloat() / it.countedGames }
                        .thenByDescending { it.wins }
                        .thenByDescending { it.countedGames }
                        .thenBy { it.playerId }
                )
                .firstOrNull()
                ?.let { RankedPlayer(playersById.getValue(it.playerId), it.wins, it.countedGames) }

            val bestScore = (if (def.lowerIsBetter) gameStats.minByOrNull { it.minScore }
                             else gameStats.maxByOrNull { it.maxScore })
                ?.let {
                    BestScore(
                        playersById.getValue(it.playerId),
                        if (def.lowerIsBetter) it.minScore else it.maxScore
                    )
                }

            GameStatsCard(def, summary.sessions, summary.lastPlayedAt, bestPlayer, bestScore)
        }.sortedByDescending { it.lastPlayedAt }
    }

    // ─── "By player" view ─────────────────────────────────────────────────────

    data class PlayerGameLine(
        val definition: GameRegistry.GameDefinition,
        val totalGames: Int,
        val countedGames: Int,
        val wins: Int,
        val draws: Int
    ) {
        val winPercentage: Float get() = if (countedGames > 0) wins * 100f / countedGames else 0f
    }

    data class PlayerStatsCard(
        val player: Player,
        val totalGames: Int,
        val countedGames: Int,
        val wins: Int,
        val draws: Int,
        val lines: List<PlayerGameLine>
    ) {
        val losses: Int get() = (countedGames - wins - draws).coerceAtLeast(0)
        val winPercentage: Float get() = if (countedGames > 0) wins * 100f / countedGames else 0f
    }

    fun buildPlayerCards(players: List<Player>, stats: List<PlayerGameStats>): List<PlayerStatsCard> {
        val statsByPlayer = stats.groupBy { it.playerId }
        return players.mapNotNull { player ->
            val lines = (statsByPlayer[player.id] ?: return@mapNotNull null)
                .mapNotNull { s ->
                    GameRegistry.findByType(s.gameType)?.let {
                        PlayerGameLine(it, s.totalGames, s.countedGames, s.wins, s.draws)
                    }
                }
                .sortedByDescending { it.totalGames }
            if (lines.isEmpty()) return@mapNotNull null
            PlayerStatsCard(
                player       = player,
                totalGames   = lines.sumOf { it.totalGames },
                countedGames = lines.sumOf { it.countedGames },
                wins         = lines.sumOf { it.wins },
                draws        = lines.sumOf { it.draws },
                lines        = lines
            )
        }.sortedWith(
            compareByDescending<PlayerStatsCard> { it.winPercentage }
                .thenByDescending { it.wins }
                .thenByDescending { it.totalGames }
        )
    }
}
