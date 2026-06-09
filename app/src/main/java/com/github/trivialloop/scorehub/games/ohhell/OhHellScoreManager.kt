package com.github.trivialloop.scorehub.games.ohhell

/**
 * Total number of rounds for a game.
 * Formula: (52 - 1) / numPlayers * 2  (integer division)
 * Examples: 3p=34, 4p=24, 5p=20, 6p=16, 7p=14, 8p=12
 */
fun totalRoundsForPlayers(numPlayers: Int): Int = (51 / numPlayers) * 2

/**
 * Maximum number of cards (= max contract) for a given round.
 * Rounds ascend from 1 to peak then descend symmetrically.
 * The two middle rounds share the same peak value.
 */
fun maxCardsForRound(roundNumber: Int, totalRounds: Int): Int {
    val half = totalRounds / 2
    return if (roundNumber <= half) roundNumber else totalRounds - roundNumber + 1
}

/**
 * Returns the player index (in the original players list) that goes first
 * for the given round. Round 1 → index 0, round 2 → index 1, etc.
 */
fun startPlayerIndexForRound(roundNumber: Int, numPlayers: Int): Int =
    (roundNumber - 1) % numPlayers

/**
 * Returns the full bidding order (player indices in the original list)
 * for a given round.
 */
fun biddingOrderForRound(roundNumber: Int, numPlayers: Int): List<Int> {
    val start = startPlayerIndexForRound(roundNumber, numPlayers)
    return (0 until numPlayers).map { (start + it) % numPlayers }
}

data class OhHellRound(
    val roundNumber: Int,
    val maxCards: Int,
    val startPlayerIndex: Int,     // index in the original players list who bids first
    // playerId -> contract chosen (0..maxCards); null = not yet entered
    val contracts: MutableMap<Long, Int?> = mutableMapOf(),
    // playerId -> number of crosses (0 = success ✅, n = n❌)
    val results: MutableMap<Long, Int?> = mutableMapOf()
) {
    fun isContractPhaseComplete(playerIds: List<Long>): Boolean =
        playerIds.all { contracts[it] != null }

    fun isComplete(playerIds: List<Long>): Boolean =
        playerIds.all { results[it] != null }

    fun contractSum(playerIds: List<Long>): Int =
        playerIds.mapNotNull { contracts[it] }.sum()

    /**
     * Returns the playerId of the last player to bid in this round.
     * The last bidder in a round is the player just before the start player
     * (i.e. start - 1 mod numPlayers).
     */
    fun lastBidderIndex(numPlayers: Int): Int =
        (startPlayerIndex - 1 + numPlayers) % numPlayers

    /**
     * The forbidden contract value for the last bidder so that
     * the total of all contracts cannot equal maxCards.
     * Returns null if there is no forbidden value.
     */
    fun forbiddenContractForLastBidder(playerIds: List<Long>): Int? {
        val lastIdx = lastBidderIndex(playerIds.size)
        val lastId = playerIds[lastIdx]
        val sumOthers = playerIds.filter { it != lastId }.sumOf { contracts[it] ?: 0 }
        val forbidden = maxCards - sumOthers
        return if (forbidden in 0..maxCards) forbidden else null
    }

    /**
     * Returns the allowed contract values for the player at [playerIndex]
     * (index in the original players list).
     * All players except the last bidder may freely choose 0..maxCards.
     * The last bidder may not choose the value that makes total == maxCards.
     */
    fun allowedContracts(playerIndex: Int, playerIds: List<Long>): List<Int> {
        val all = (0..maxCards).toList()
        return if (playerIndex == lastBidderIndex(playerIds.size)) {
            val forbidden = forbiddenContractForLastBidder(playerIds)
            if (forbidden != null) all.filter { it != forbidden } else all
        } else all
    }

    /**
     * Score for this player in this round.
     *   Success (crosses == 0): 5 + 2 × contract
     *   Failure (crosses == n): −2 × n
     * Returns null if not yet complete.
     */
    fun getScore(playerId: Long): Int? {
        val contract = contracts[playerId] ?: return null
        val crosses = results[playerId] ?: return null
        return if (crosses == 0) 5 + 2 * contract else -(2 * crosses)
    }

    /**
     * Result emoji label: ✅ for success, repeated ❌ for each cross.
     * Never uses compact "❌×n" notation.
     */
    fun getResultLabel(playerId: Long): String {
        val crosses = results[playerId] ?: return ""
        return if (crosses == 0) "✅" else "❌".repeat(crosses)
    }

    /** Score label with sign for display (e.g. +11, -4). */
    fun getScoreLabel(playerId: Long): String {
        val score = getScore(playerId) ?: return ""
        return if (score >= 0) "+$score" else "$score"
    }
}

data class OhHellPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<OhHellRound>): Int =
        rounds.sumOf { it.getScore(playerId) ?: 0 }
}
