package com.github.trivialloop.scorehub.games.free_game

/**
 * Represents one scoring entry in a Free Game round.
 *
 * A round belongs to a single player (the one who scored).
 * The round is finalized automatically after 2 seconds of inactivity.
 */
data class FreeGameRound(
    val roundNumber: Int,
    val playerId: Long,
    var score: Int = 0,           // accumulated score for this round
    var isComplete: Boolean = false
)

data class FreeGamePlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<FreeGameRound>): Int =
        rounds.filter { it.playerId == playerId && it.isComplete }.sumOf { it.score }
}
