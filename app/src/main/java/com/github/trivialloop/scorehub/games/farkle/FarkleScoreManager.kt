package com.github.trivialloop.scorehub.games.farkle

/**
 * Represents one scoring entry added during a turn.
 */
data class RollEntry(
    val score: Int,
    val usedDice: Int,
    val label: String
)

/**
 * Represents one round (turn) for a single player in Farkle.
 *
 * During a turn the player accumulates [rollEntries] via "Add" presses.
 * The turn ends either by:
 *  - [banked] = true  → the sum of rollEntries is added to the player's running total.
 *  - [farkled] = true → the score for this round is 0.
 *
 * If the player performs 3 consecutive farkles,
 * all accumulated points are lost.
 */
data class FarkleRound(
    val roundNumber: Int,
    val playerId: Long,

    /** Individual scores entered via "Add" during this turn. */
    val rollEntries: MutableList<RollEntry> = mutableListOf(),

    var banked: Boolean = false,
    var farkled: Boolean = false,

    /**
     * True when this round caused the triple-farkle penalty.
     */
    var tripleFarklePenalty: Boolean = false,

    /**
     * Amount of points lost because of the triple farkle.
     */
    var penaltyPointsLost: Int = 0
) {

    /** True once the turn is finished (banked or farkled). */
    val isComplete: Boolean
        get() = banked || farkled

    /** Running sum of the entries entered so far this turn. */
    val entrySum: Int
        get() = rollEntries.sumOf { it.score }

    /**
     * Final displayed score for this round.
     */
    val score: Int
        get() = when {

            tripleFarklePenalty -> -penaltyPointsLost
            farkled -> 0
            banked -> entrySum
            else -> 0
        }
}

data class FarklePlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {

    /**
     * Running total across all completed rounds.
     *
     * Triple farkle resets the total to 0.
     */
    fun getTotal(rounds: List<FarkleRound>): Int {
        var total = 0
        rounds
            .filter {
                it.playerId == playerId && it.isComplete
            }
            .sortedBy {
                it.roundNumber
            }
            .forEach { round ->

                when {
                    round.tripleFarklePenalty -> {
                        total = 0
                    }
                    else -> {
                        total += round.score
                    }
                }
            }
        return total
    }

    /** Returns the in-progress round for this player, or null. */
    fun currentRound(rounds: List<FarkleRound>): FarkleRound? =
        rounds.lastOrNull {
            it.playerId == playerId && !it.isComplete
        }

    /**
     * Returns true if the player has 3 consecutive farkles.
     */
    fun hasTripleFarkle(rounds: List<FarkleRound>): Boolean {

        val playerRounds =
            rounds
                .filter {
                    it.playerId == playerId
                }
                .takeLast(3)

        return playerRounds.size == 3 &&
                playerRounds.all { it.farkled }
    }
}
