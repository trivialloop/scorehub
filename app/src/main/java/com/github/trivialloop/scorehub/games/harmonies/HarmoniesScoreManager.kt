package com.github.trivialloop.scorehub.games.harmonies

/**
 * Harmonies (Libellud, 2024) scoring.
 *
 * Harmonies is a spatial tile/token-placement game — like Akropolis or Wingspan, ScoreHub does
 * not simulate the personal board itself. Instead, each player enters the point subtotal they
 * computed for each scoring category from their physical board, and the app sums them.
 *
 * Categories (see the official scoresheet):
 *  - Trees      : sum of all Tree scores (a Tree is 1 green token on 0/1/2 brown tokens,
 *                 height 1/2/3 scoring 1/3/7 pts each).
 *  - Mountains  : sum of all Mountain scores (a stack of 1-3 grey tokens, height 1/2/3 scoring
 *                 1/3/7 pts each, but only if adjacent to another Mountain — otherwise 0).
 *  - Fields     : 5 pts per separate group of 2+ contiguous yellow tokens.
 *  - Buildings  : 5 pts per Building (1 red token on brown/grey/red) surrounded by at least
 *                 3 different token colors — otherwise 0.
 *  - River      : points for the length of the longest river of blue tokens (Side A), or for
 *                 the islands created by water (Side B) — either way, a single subtotal.
 *  - Animals    : sum of points scored across all completed/partial Animal cards (+ optional
 *                 Nature's Spirit card, scored the same way).
 */
data class HarmoniesPlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val scores: MutableMap<HarmoniesCategory, Int?> = mutableMapOf()
) {
    fun getTotal(): Int = HarmoniesCategory.entries.sumOf { scores[it] ?: 0 }

    fun isComplete(): Boolean = HarmoniesCategory.entries.all { scores[it] != null }
}

enum class HarmoniesCategory {
    TREES,
    MOUNTAINS,
    FIELDS,
    BUILDINGS,
    RIVER,
    ANIMALS;

    /** Selectable values for the picker dialog for this category. */
    fun getPossibleValues(): List<Int> = when (this) {
        TREES, MOUNTAINS -> (0..50).toList()
        FIELDS, BUILDINGS -> (0..50 step 5).toList()
        RIVER -> (0..40).toList()
        ANIMALS -> (0..99).toList()
    }
}
