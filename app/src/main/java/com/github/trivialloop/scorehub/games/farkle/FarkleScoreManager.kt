package com.github.trivialloop.scorehub.games.farkle

/**
 * Represents one round (turn) for a single player in Farkle.
 *
 * During a turn the player accumulates [rollEntries] via "Add" presses.
 * The turn ends either by:
 *  - [banked] = true  → the sum of rollEntries is added to the player's running total.
 *  - [farkled] = true → the score for this round is 0.
 *
 * While the turn is in progress (neither banked nor farkled) the cell shows
 * the individual roll entries plus the three action buttons (Add / Bank / Farkle).
 */
data class FarkleRound(
    val roundNumber: Int,
    val playerId: Long,
    /** Individual scores entered via "Add" during this turn. */
    val rollEntries: MutableList<Int> = mutableListOf(),
    var banked: Boolean = false,
    var farkled: Boolean = false
) {
    /** True once the turn is finished (banked or farkled). */
    val isComplete: Boolean get() = banked || farkled

    /** Running sum of the entries entered so far this turn. */
    val entrySum: Int get() = rollEntries.sum()

    /**
     * Final score for this round:
     *  - 0 if farkled or not yet complete
     *  - sum of roll entries if banked
     */
    val score: Int
        get() = when {
            farkled -> 0
            banked  -> entrySum
            else    -> 0
        }
}

data class FarklePlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    /** Running total across all completed rounds. */
    fun getTotal(rounds: List<FarkleRound>): Int =
        rounds.filter { it.playerId == playerId && it.isComplete }.sumOf { it.score }

    /** Returns the in-progress round for this player, or null. */
    fun currentRound(rounds: List<FarkleRound>): FarkleRound? =
        rounds.lastOrNull { it.playerId == playerId && !it.isComplete }
}
