package com.github.trivialloop.scorehub.games.forestshuffle

/**
 * Forest Shuffle (Lookout Games, 2023 — "Forêt Mixte" in French) scoring — one-shot tally per
 * player (no rounds).
 *
 * Card-by-card scoring depends on the whole forest, so ScoreHub does not simulate it: at the end of
 * the game each player computes the four subtotals of the official scoring order (see
 * [ForestShuffleCategory]) and enters them directly.
 */
data class ForestShufflePlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val scores: MutableMap<ForestShuffleCategory, Int?> = mutableMapOf()
) {
    /** Final score: sum of the four subtotals (a missing subtotal counts as 0). */
    fun getTotal(): Int = ForestShuffleCategory.entries.sumOf { scores[it] ?: 0 }
}

enum class ForestShuffleCategory {
    /** Points printed on the Tree cards. */
    TREES,
    /** Points of the cards attached to the top / bottom of the trees. */
    TOP_BOTTOM,
    /** Points of the cards attached to the left / right of the trees. */
    LEFT_RIGHT,
    /** 1 point per card placed under the Cave (plus the Cave's own scoring). */
    CAVE;

    /** Selectable values for the picker dialog. */
    fun getPossibleValues(): List<Int> = (0..FOREST_SHUFFLE_MAX_SCORE).toList()
}

/** Upper bound of every score picker. */
const val FOREST_SHUFFLE_MAX_SCORE = 200
