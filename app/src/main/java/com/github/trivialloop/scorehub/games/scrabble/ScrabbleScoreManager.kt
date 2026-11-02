package com.github.trivialloop.scorehub.games.scrabble

/**
 * One turn of one player.
 *
 * During a turn the player can add several scores (several words, bonus, end-of-game
 * tile penalty...). Entries are signed: a negative entry subtracts points.
 * The turn is closed when the player passes the hand ([isComplete] = true).
 */
data class ScrabbleTurn(
    val roundNumber: Int,
    val playerId: Long,
    val entries: MutableList<Int> = mutableListOf(),
    var isComplete: Boolean = false
) {
    /** Sum of every entry of this turn (in-progress turns included). */
    val score: Int get() = entries.sum()
}

data class ScrabblePlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    /** Running total, including the entries of a turn still in progress. */
    fun getTotal(turns: List<ScrabbleTurn>): Int =
        turns.filter { it.playerId == playerId }.sumOf { it.score }

    /** The in-progress turn of this player, or null. */
    fun currentTurn(turns: List<ScrabbleTurn>): ScrabbleTurn? =
        turns.lastOrNull { it.playerId == playerId && !it.isComplete }
}
