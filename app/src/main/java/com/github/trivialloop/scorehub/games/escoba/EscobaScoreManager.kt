package com.github.trivialloop.scorehub.games.escoba

data class EscobaRound(
    val roundNumber: Int,
    val inPlayScores: MutableMap<Long, Int> = mutableMapOf(),  // in-play via +/−
    val handScores: MutableMap<Long, Int?> = mutableMapOf()     // end-of-round (null = not entered)
) {
    /** True once all players have entered their end-of-round score. */
    fun isComplete(playerIds: List<Long>): Boolean =
        playerIds.all { handScores[it] != null }

    /** In play is still open if no player has entered their end-of-round score yet. */
    fun isInPlayEditable(): Boolean =
        handScores.values.all { it == null }

    /** True if any +/− has been pressed (any player > 0). */
    fun hasInPlayActivity(): Boolean =
        inPlayScores.values.any { it > 0 }

    /** Total score for a player this round (in play + hand). */
    fun roundTotal(playerId: Long): Int =
        (inPlayScores[playerId] ?: 0) + (handScores[playerId] ?: 0)
}

data class EscobaPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<EscobaRound>): Int =
        rounds.sumOf { it.roundTotal(playerId) }
}
