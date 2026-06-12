package com.github.trivialloop.scorehub.games.qwixx

/**
 * Qwixx color rows.
 * RED and YELLOW go ascending (2..12).
 * GREEN and BLUE go descending (12..2).
 */
enum class QwixxColor {
    RED, YELLOW, GREEN, BLUE
}

/**
 * State of a single color row for one player.
 *
 * Numbers are stored as a set of checked values.
 * The "lock" checkbox is checked automatically when the last number is checked
 * (requires at least 5 other checks in that row first).
 *
 * Ascending rows (RED, YELLOW): numbers 2..12, locked when 12 is checked.
 * Descending rows (GREEN, BLUE): numbers 12..2, locked when 2 is checked.
 */
data class QwixxRowState(
    val color: QwixxColor,
    /** Numbers checked by this player in this row (not counting the lock). */
    val checked: MutableSet<Int> = mutableSetOf(),
    /** True once the lock checkbox is checked (last number + >= 5 others checked). */
    var locked: Boolean = false
) {
    /** Ordered sequence of numbers for this row. */
    val numbers: List<Int>
        get() = if (color == QwixxColor.RED || color == QwixxColor.YELLOW)
            (2..12).toList()
        else
            (12 downTo 2).toList()

    /** The "last" number whose check also triggers the lock. */
    val lastNumber: Int get() = if (color == QwixxColor.RED || color == QwixxColor.YELLOW) 12 else 2

    /**
     * Returns true if [number] can still be checked by this player.
     * Rules:
     *  - Row must not be locked.
     *  - Number must not already be checked.
     *  - All numbers that come before it in the sequence must already be either
     *    checked or "skipped" (i.e. the number must not be before the last-checked number).
     *  - The last number (12 for ascending, 2 for descending) requires >= 5 others checked first.
     */
    fun canCheck(number: Int): Boolean {
        if (locked) return false
        if (checked.contains(number)) return false
        // Find the position of this number in the sequence
        val seq = numbers
        val idx = seq.indexOf(number)
        if (idx < 0) return false
        // All numbers before this one in the sequence that are after the last checked must be uncheckable
        // i.e., the number must be >= the last checked number (in sequence order)
        val lastCheckedIdx = checked.mapNotNull { seq.indexOf(it).takeIf { i -> i >= 0 } }.maxOrNull() ?: -1
        if (idx < lastCheckedIdx) return false
        // Last number requires at least 5 others checked
        if (number == lastNumber && checked.size < 5) return false
        return true
    }

    /**
     * Checks [number]. If it is the last number, also sets [locked] = true.
     * Returns true if the check was performed.
     */
    fun check(number: Int): Boolean {
        if (!canCheck(number)) return false
        checked.add(number)
        if (number == lastNumber) locked = true
        return true
    }

    /**
     * Score for this row: triangular sum of number of checked values.
     * checked.size N → N*(N+1)/2  (1 check = 1pt, 2 = 3pt, 3 = 6pt … 12 = 78pt)
     * The lock checkbox itself counts as an extra check when computing the score
     * (locking means you have >= 6 checks: 5 + the last number, so the lock
     * adds one more to the count only if already included in [checked]).
     */
    /**
     * When locked, the 🔒 cell counts as one extra box on top of [checked].
     * Example: 6 numbers checked + lock = 7 boxes → 7*8/2 = 28 pts.
     */
    fun score(): Int {
        val n = checked.size + if (locked) 1 else 0
        return n * (n + 1) / 2
    }
}

/**
 * Full state for one player.
 */
data class QwixxPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val rows: Map<QwixxColor, QwixxRowState> = QwixxColor.entries.associateWith { QwixxRowState(it) },
    /** Number of penalty boxes checked (0..4). Each costs 5 points. */
    var penalties: Int = 0
) {
    fun rowState(color: QwixxColor) = rows[color]!!

    /** Total score: sum of color rows minus penalties. */
    fun totalScore(): Int {
        val colorScore = QwixxColor.entries.sumOf { rows[it]!!.score() }
        return colorScore - penalties * 5
    }
}

/**
 * Represents one full round of Qwixx.
 *
 * A round proceeds as follows:
 * 1. Every player (the active player and all the others) may check one number
 *    in any color row, or pass — all simultaneously / in any order.
 * 2. The active player may check a second number (can be in any row including penalty).
 *    If the active player did NOT check anything in step 1, they MUST check one number now
 *    (which CAN be a penalty box).
 *
 * [activePlayerIndex] is the index into the game's player list.
 * [phase] tracks whether we are in:
 *   ALL            – every player (active and non-active) checks one number or passes
 *   ACTIVE_SECOND  – active player's mandatory/optional second check
 */
enum class QwixxRoundPhase {
    ALL,
    ACTIVE_SECOND
}

data class QwixxRound(
    val roundNumber: Int,
    val activePlayerIndex: Int,
    var phase: QwixxRoundPhase = QwixxRoundPhase.ALL,
    /** Whether the active player checked something during the ALL phase. */
    var activeCheckedFirst: Boolean = false,
    /** All players (active and non-active) who have already passed or checked this round (indices into player list). */
    val playersFinished: MutableSet<Int> = mutableSetOf(),
    /**
     * Colors that were globally locked during THIS turn (i.e. a player just checked the last
     * number in that color row this round). Used by [QwixxGameActivity] to allow other players
     * who haven't acted yet to still check the last number themselves, earning their own lock,
     * even though the global lock is already set.
     */
    val colorsLockedThisTurn: MutableSet<QwixxColor> = mutableSetOf()
)

/**
 * Global lock state: which colors are locked, and who locked them.
 * A color becomes locked when any player checks the last number in that row.
 * Once locked, no player can check any number in that color.
 */
data class QwixxLockState(
    val lockedColors: MutableSet<QwixxColor> = mutableSetOf()
) {
    fun isLocked(color: QwixxColor) = lockedColors.contains(color)
    fun lock(color: QwixxColor) { lockedColors.add(color) }
    fun lockCount() = lockedColors.size
}

/**
 * Overall game state.
 */
data class QwixxGameState(
    val players: List<QwixxPlayerState>,
    val lockState: QwixxLockState = QwixxLockState(),
    var currentRound: QwixxRound? = null,
    var activePlayerIndex: Int = 0,
    var isOver: Boolean = false
) {
    /**
     * Returns true if the game-ending condition is met:
     * - 4 penalty boxes are checked by any single player, OR
     * - 2 or more colors are globally locked.
     */
    fun checkEndCondition(): Boolean {
        if (players.any { it.penalties >= 4 }) return true
        if (lockState.lockCount() >= 2) return true
        return false
    }

    /**
     * After any player checks the last number of a color row,
     * that color must be globally locked.
     */
    fun syncLocks() {
        for (player in players) {
            for (color in QwixxColor.entries) {
                if (player.rowState(color).locked) {
                    lockState.lock(color)
                }
            }
        }
        // Also propagate global lock back to all players' rows
        for (color in lockState.lockedColors) {
            for (player in players) {
                player.rowState(color).locked = true
            }
        }
    }
}
