package com.github.trivialloop.scorehub.games.cribbage

/**
 * Represents a single Cribbage round.
 *
 * In Cribbage (2 players), players alternate holding the crib.
 * - [dealerId]    : player who holds the crib this round. Enters hand score FIRST.
 * - [nonDealerId] : the pone. Enters hand score SECOND.
 *
 * Score entry order within a round:
 *  1. Pegging ("En jeu") — both players simultaneously via +/− buttons.
 *     Also editable: dealer's end-of-round hand (can be filled at any time during pegging phase).
 *  2. Once dealer hand is entered → pone hand becomes editable.
 *  3. Once pone hand is entered  → crib becomes editable.
 *  4. Once crib is entered       → round is complete, new round appended.
 *
 * Locking of the previous round:
 *  - Previous round's pegging and hand/crib scores remain editable until the NEW round
 *    has any pegging score entered (i.e. sum of pegging for either player > 0).
 */
data class CribbageRound(
    val roundNumber: Int,
    val dealerId: Long,
    val nonDealerId: Long
) {
    // In-play (pegging / "En jeu") scores — start at 0
    val peggingScores: MutableMap<Long, Int> = mutableMapOf(
        dealerId    to 0,
        nonDealerId to 0
    )

    // End-of-round hand scores (null = not yet entered)
    val handScores: MutableMap<Long, Int?> = mutableMapOf(
        dealerId    to null,
        nonDealerId to null
    )

    // Crib score — only the dealer has one (null = not yet entered)
    var cribScore: Int? = null

    // ── State helpers ──────────────────────────────────────────────────────────

    /** True once the DEALER's hand score has been entered (dealer goes first). */
    fun isDealerHandEntered(): Boolean = handScores[dealerId] != null

    /** True once the PONE's hand score has been entered (pone goes second). */
    fun isPoneHandEntered(): Boolean = handScores[nonDealerId] != null

    /** True once the crib score has been entered (round fully complete). */
    fun isComplete(): Boolean = cribScore != null

    /**
     * Pegging is editable as long as the dealer has NOT yet entered a hand score.
     * Once the dealer submits their hand, pegging is locked for both players.
     */
    fun isPeggingEditable(): Boolean = !isDealerHandEntered()

    /**
     * Returns true if this round has any pegging activity (either player > 0).
     * Used to determine when the previous round should be locked.
     */
    fun hasPeggingActivity(): Boolean =
        (peggingScores[dealerId] ?: 0) > 0 || (peggingScores[nonDealerId] ?: 0) > 0

    // ── Score totals ───────────────────────────────────────────────────────────

    /** Total score contribution for a player from this round (pegging + hand + crib if dealer). */
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

/** Visual state of a cell. */
enum class CribbageCellColor { DEFAULT, GREEN, RED }

/**
 * Editability / visual state of a score cell.
 *
 * EDITABLE       — can be tapped / incremented right now.
 * LOCKED_SOON    — will become editable once prerequisites are met (grayed, but not permanent).
 * LOCKED_NEVER   — this player never has this type of score in this round (e.g. pone has no crib).
 * LOCKED_PREV    — previous round is now fully locked.
 */
enum class CellState { EDITABLE, LOCKED_SOON, LOCKED_NEVER, LOCKED_PREV }