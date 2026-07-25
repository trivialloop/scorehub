package com.github.trivialloop.scorehub.games.freegame

data class FreeGameRound(
    val roundNumber: Int,
    val playerId: Long,
    var score: Int = 0,
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
