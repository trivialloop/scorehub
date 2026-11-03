package com.github.trivialloop.scorehub.games.scrabble

const val SCRABBLE_MIN_SCORE = 1
const val SCRABBLE_MAX_SCORE = 999

/** A single score entry must be a whole number between [SCRABBLE_MIN_SCORE] and [SCRABBLE_MAX_SCORE]. */
fun isValidScrabbleScore(value: Int?): Boolean =
    value != null && value in SCRABBLE_MIN_SCORE..SCRABBLE_MAX_SCORE

/**
 * One turn of one player.
 *
 * During a turn the player can add several positive scores (several words, bonus...).
 * The turn is closed when the player passes the hand ([isComplete] = true).
 * A closed turn with no entry is a pass and scores 0.
 */
data class ScrabbleTurn(
    val roundNumber: Int,
    val playerId: Long,
    val entries: MutableList<Int> = mutableListOf(),
    var isComplete: Boolean = false
) {
    /** Sum of every entry of this turn (in-progress turns included). */
    val score: Int get() = entries.sum()

    /** Small text shown in the cell, e.g. "12+4". Empty when there is no entry. */
    val expression: String get() = entries.joinToString("+")
}

data class ScrabblePlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    /**
     * Running total, including the entries of a turn still in progress.
     * [adjustments] = end-of-game rack adjustments (see [ScrabbleEndGame]), empty during the game.
     */
    fun getTotal(turns: List<ScrabbleTurn>, adjustments: Map<Long, Int> = emptyMap()): Int =
        turns.filter { it.playerId == playerId }.sumOf { it.score } + (adjustments[playerId] ?: 0)

    /** The in-progress turn of this player, or null. */
    fun currentTurn(turns: List<ScrabbleTurn>): ScrabbleTurn? =
        turns.lastOrNull { it.playerId == playerId && !it.isComplete }
}

/** Official end-of-game rule: leftover tiles are subtracted, the player who went out gets their sum. */
object ScrabbleEndGame {

    /**
     * @param racks      value of the tiles left on each player's rack (negative values are treated as 0)
     * @param wentOutId  player who played all their tiles, or null when nobody did (blocked game)
     * @return the signed adjustment to add to each player's total
     */
    fun computeAdjustments(racks: Map<Long, Int>, wentOutId: Long?): Map<Long, Int> {
        val result = racks.mapValues { (_, v) -> -v.coerceAtLeast(0) }.toMutableMap()
        if (wentOutId != null && wentOutId in racks) {
            result[wentOutId] = racks.filterKeys { it != wentOutId }.values.sumOf { it.coerceAtLeast(0) }
        }
        return result
    }
}
