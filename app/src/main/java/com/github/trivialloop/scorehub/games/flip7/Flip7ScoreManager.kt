package com.github.trivialloop.scorehub.games.flip7

/**
 * Represents one turn (round entry) for a single player in Flip 7.
 *
 * A turn ends either with:
 *  - [score] set to a computed value (normal turn with cards selected)
 *  - or the player chose 0 (skipped / flipped to 0)
 */
data class Flip7Turn(
    val roundNumber: Int,
    val playerId: Long,

    /** Cards selected: values 0–12, at most 7 */
    val selectedCards: List<Int> = emptyList(),

    /** Bonus modifiers selected */
    val bonusPlus: List<Int> = emptyList(),  // e.g. [2, 4, 6]
    val bonusX2: Boolean = false,

    /** True if the player chose 0 this turn */
    val choseZero: Boolean = false,

    /** True once the turn is finalized */
    val isComplete: Boolean = false
) {
    /**
     * Computes the final score for this turn:
     *  1. Sum of selected card values
     *  2. +15 if exactly 7 cards are selected
     *  3. ×2 if bonusX2 is selected
     *  4. + sum of all bonusPlus values
     */
    val score: Int
        get() {
            if (choseZero) return 0
            if (!isComplete) return 0
            var base = selectedCards.sum()
            if (selectedCards.size == 7) base += 15
            if (bonusX2) base *= 2
            base += bonusPlus.sum()
            return base
        }
}

data class Flip7PlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    /** Running total across all completed turns for this player. */
    fun getTotal(turns: List<Flip7Turn>): Int =
        turns
            .filter { it.playerId == playerId && it.isComplete }
            .sumOf { it.score }

    /** Returns the in-progress turn for this player, or null. */
    fun currentTurn(turns: List<Flip7Turn>): Flip7Turn? =
        turns.lastOrNull { it.playerId == playerId && !it.isComplete }
}

// ─── Constants ────────────────────────────────────────────────────────────────

/** All available card values (2 columns: 0–6 left, 7–12 right). */
val FLIP7_CARD_VALUES = (0..12).toList()

/** Maximum cards a player can select per turn. */
const val FLIP7_MAX_CARDS = 7

/** Bonus values available (the +X bonuses). */
val FLIP7_BONUS_PLUS_VALUES = listOf(2, 4, 6, 8, 8, 10)

/** Score limit that triggers end-of-game check. */
const val FLIP7_SCORE_LIMIT = 200
