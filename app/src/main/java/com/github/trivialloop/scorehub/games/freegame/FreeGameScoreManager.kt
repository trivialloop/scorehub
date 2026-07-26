package com.github.trivialloop.scorehub.games.freegame

/**
 * One completed round in the global round sequence.
 *
 * A round belongs to one player and represents a continuous scoring
 * session that was closed either by the 2-second timer expiring or
 * by another player pressing a button.
 *
 * [roundNumber] is a global counter (1, 2, 3 …) shared across all players.
 */
data class FreeGameRound(
    val roundNumber: Int,
    val playerId: Long,
    val score: Int          // immutable once committed
)

data class FreeGamePlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    /** Sum of all committed rounds for this player. */
    fun getTotal(rounds: List<FreeGameRound>): Int =
        rounds.filter { it.playerId == playerId }.sumOf { it.score }

    /** Committed rounds for this player, in insertion order. */
    fun getRounds(rounds: List<FreeGameRound>): List<FreeGameRound> =
        rounds.filter { it.playerId == playerId }
}