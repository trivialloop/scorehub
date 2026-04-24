package com.github.trivialloop.scorehub.games.escoba

data class EscobaRound(
    val roundNumber: Int,
    val peggingScores: MutableMap<Long, Int> = mutableMapOf(),  // in-play via +/−
    val handScores: MutableMap<Long, Int?> = mutableMapOf()     // end-of-round (null = not entered)
) {
    /** True once all players have entered their end-of-round score. */
    fun isComplete(playerIds: List<Long>): Boolean =
        playerIds.all { handScores[it] != null }

    /** Pegging is still open if no player has entered their end-of-round score yet. */
    fun isPeggingEditable(): Boolean =
        handScores.values.all { it == null }

    /** True if any +/− has been pressed (any player > 0). */
    fun hasPeggingActivity(): Boolean =
        peggingScores.values.any { it > 0 }

    /** Total score for a player this round (pegging + hand). */
    fun roundTotal(playerId: Long): Int =
        (peggingScores[playerId] ?: 0) + (handScores[playerId] ?: 0)
}

data class EscobaPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<EscobaRound>): Int =
        rounds.sumOf { it.roundTotal(playerId) }
}
