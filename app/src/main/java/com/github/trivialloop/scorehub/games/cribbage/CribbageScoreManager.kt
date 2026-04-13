package com.github.trivialloop.scorehub.games.cribbage

/**
 * Represents a single Cribbage round.
 *
 * In Cribbage (2 players), players alternate holding the crib.
 * - [dealerId] : player who holds the crib this round.
 * - [nonDealerId] : opponent (the "pone").
 *
 * Scoring within a round:
 *  - [peggingScores]  : in-play (pegging) points, editable with +/- buttons.
 *  - [handScores]     : end-of-round hand points, entered via keyboard.
 *  - [cribScore]      : dealer's crib points, entered via keyboard (dealer only).
 *
 * Entry order:
 *  1. Pegging is open for both players simultaneously (buttons).
 *  2. Pone hand is entered first (keyboard).
 *  3. Dealer hand is entered next.
 *  4. Crib is entered last.
 *  5. Once crib is saved → round is complete, new round appended.
 *
 * A subsequent round's pegging becomes editable only after the previous
 * round is fully locked (all three hand/crib fields set).
 */
data class CribbageRound(
    val roundNumber: Int,
    val dealerId: Long,       // player who holds the crib
    val nonDealerId: Long     // the pone
) {
    // In-play (pegging) scores — start at 0, editable via +/- while round is open
    val peggingScores: MutableMap<Long, Int> = mutableMapOf(
        dealerId    to 0,
        nonDealerId to 0
    )

    // End-of-round hand scores (null = not yet entered)
    val handScores: MutableMap<Long, Int?> = mutableMapOf(
        dealerId    to null,
        nonDealerId to null
    )

    // Crib score — only the dealer has one
    var cribScore: Int? = null

    // ── State helpers ──────────────────────────────────────────────────────────

    /** True once the pone's hand score has been entered. */
    fun isPoneHandEntered(): Boolean = handScores[nonDealerId] != null

    /** True once the dealer's hand score has been entered. */
    fun isDealerHandEntered(): Boolean = handScores[dealerId] != null

    /** True once the crib score has been entered (round fully complete). */
    fun isComplete(): Boolean = cribScore != null

    /**
     * Pegging is editable as long as no hand score has been entered yet in this round.
     * Once the pone submits their hand score, pegging is locked for both.
     */
    fun isPeggingEditable(): Boolean = !isPoneHandEntered()

    // ── Score totals ───────────────────────────────────────────────────────────

    /** Total score for a player up to and including this round. */
    fun roundTotal(playerId: Long): Int {
        val pegging = peggingScores[playerId] ?: 0
        val hand    = handScores[playerId] ?: 0
        val crib    = if (playerId == dealerId) (cribScore ?: 0) else 0
        return pegging + hand + crib
    }
}

/** Accumulates the running total for a player across all rounds. */
data class CribbagePlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<CribbageRound>): Int =
        rounds.sumOf { it.roundTotal(playerId) }
}

/** Cell color semantics used when rendering the grid. */
enum class CribbageCellColor { DEFAULT, GREEN, RED }
