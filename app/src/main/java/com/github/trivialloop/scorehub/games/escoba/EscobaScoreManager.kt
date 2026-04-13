package com.github.trivialloop.scorehub.games.escoba

data class EscobaRound(
    val roundNumber: Int,
    val scores: MutableMap<Long, Int?> = mutableMapOf()
) {
    fun allScoresEntered(playerIds: List<Long>): Boolean =
        playerIds.all { scores[it] != null }
}

data class EscobaPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<EscobaRound>): Int =
        rounds.sumOf { it.scores[playerId] ?: 0 }
}
