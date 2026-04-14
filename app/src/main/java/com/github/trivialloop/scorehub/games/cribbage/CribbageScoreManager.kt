package com.github.trivialloop.scorehub.games.cribbage

/**
 * Represents a single Cribbage round.
 *
 * Terminology:
 *  - [firstPlayerId] : plays first this round. Their colour appears on the round label.
 *                      They enter their end-of-round score FIRST.
 *  - [dealerId]      : deals the cards and holds the crib this round.
 *                      They enter their end-of-round score SECOND, then the crib.
 *
 * Score entry order:
 *  1. In-play ("En jeu") — both players simultaneously via +/− buttons.
 *     The first player can also enter their end-of-round score at any time during
 *     this phase (doing so locks pegging for both players).
 *  2. Once the first player's end-of-round score is entered
 *     → dealer end-of-round becomes editable.
 *  3. Once the dealer's end-of-round score is entered
 *     → crib becomes editable.
 *  4. Once crib is entered → round complete, new round appended.
 *
 * Previous round locking:
 *  A previous round's end-of-round and crib fields remain editable until the new
 *  round has ANY pegging activity (first +/− press in the new round).
 */
data class CribbageRound(
    val roundNumber: Int,
    val firstPlayerId: Long,   // plays first, colour on round label, enters score first
    val dealerId: Long         // deals cards, has crib, enters score second
) {
    // In-play scores — start at 0, both players edit via +/−
    val peggingScores: MutableMap<Long, Int> = mutableMapOf(
        firstPlayerId to 0,
        dealerId      to 0
    )

    // End-of-round hand scores (null = not yet entered)
    val handScores: MutableMap<Long, Int?> = mutableMapOf(
        firstPlayerId to null,
        dealerId      to null
    )

    // Crib score — only the dealer has one (null = not yet entered)
    var cribScore: Int? = null

    // ── State helpers ──────────────────────────────────────────────────────────

    /** True once the first player's end-of-round score has been entered. */
    fun isFirstPlayerHandEntered(): Boolean = handScores[firstPlayerId] != null

    /** True once the dealer's end-of-round score has been entered. */
    fun isDealerHandEntered(): Boolean = handScores[dealerId] != null

    /** True once the crib score has been entered (round fully complete). */
    fun isComplete(): Boolean = cribScore != null

    /**
     * Pegging is editable until the first player enters their end-of-round score.
     */
    fun isPeggingEditable(): Boolean = !isFirstPlayerHandEntered()

    /**
     * True if any +/− has been pressed in this round (either player > 0).
     * Used to lock the previous round once the new round has started.
     */
    fun hasPeggingActivity(): Boolean =
        (peggingScores[firstPlayerId] ?: 0) > 0 || (peggingScores[dealerId] ?: 0) > 0

    // ── Score totals ───────────────────────────────────────────────────────────

    /** Total score contribution from this round for a given player. */
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

/** Visual score comparison colour for a cell. */
enum class CribbageCellColor { DEFAULT, GREEN, RED }

/**
 * Editability / visual state of a score cell.
 *
 * EDITABLE     — can be tapped / incremented right now.
 * LOCKED_SOON  — will become editable once prerequisites are met (dimmed, temporary).
 * LOCKED_PREV  — cell belongs to a locked round (grayed, shows value if any).
 * LOCKED_NEVER — this player never has this score type this round (first player's crib).
 */
enum class CellState { EDITABLE, LOCKED_SOON, LOCKED_PREV, LOCKED_NEVER }
