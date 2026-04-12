package com.github.trivialloop.scorehub.games.cactus

data class CactusRound(
    val roundNumber: Int,
    val scores: MutableMap<Long, Int?> = mutableMapOf()
) {
    fun isComplete(playerIds: List<Long>): Boolean {
        return playerIds.all { scores[it] != null }
    }

    fun allScoresEntered(playerIds: List<Long>): Boolean {
        return playerIds.all { scores[it] != null }
    }
}

data class CactusPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<CactusRound>): Int {
        return rounds.sumOf { it.scores[playerId] ?: 0 }
    }
}
