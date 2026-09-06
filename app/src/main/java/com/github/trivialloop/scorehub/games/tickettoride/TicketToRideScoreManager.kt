package com.github.trivialloop.scorehub.games.tickettoride

/**
 * Ticket to Ride scoring — one-shot tally per player (no rounds).
 *
 * Destination tickets are tracked as a single list of *signed* entries:
 * a positive value is a completed ticket (added to the score), a negative
 * value is a failed ticket (subtracted from the score, magnitude stored as
 * a negative number so the list itself sums to the net contribution).
 */
data class TicketToRidePlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val routeCounts: MutableMap<Int, Int> = ROUTE_LENGTHS.associateWith { 0 }.toMutableMap(),
    /** Signed destination ticket entries: positive = completed, negative = failed. */
    val ticketEntries: MutableList<Int> = mutableListOf(),
    var hasLongestPath: Boolean = false
) {
    /** Points earned from claimed routes only. */
    fun getRoutePoints(): Int =
        routeCounts.entries.sumOf { (length, count) -> (ROUTE_POINTS[length] ?: 0) * count }

    /** Sum of all completed destination ticket values (positive entries). */
    fun getCompletedTicketsPoints(): Int = ticketEntries.filter { it > 0 }.sum()

    /** Sum of all failed destination ticket values, as a positive number. */
    fun getFailedTicketsPoints(): Int = -ticketEntries.filter { it < 0 }.sum()

    /** Net contribution of all ticket entries (completed minus failed). */
    fun getTicketsTotal(): Int = ticketEntries.sum()

    /** +10 if this player has (or shares) the longest continuous path, else 0. */
    fun getLongestPathBonus(): Int = if (hasLongestPath) LONGEST_PATH_BONUS else 0

    /** Final score: routes + net tickets + longest path bonus. */
    fun getTotal(): Int = getRoutePoints() + getTicketsTotal() + getLongestPathBonus()

    companion object {
        val ROUTE_LENGTHS: List<Int> = listOf(1, 2, 3, 4, 5, 6)

        val ROUTE_POINTS: Map<Int, Int> = mapOf(
            1 to 1, 2 to 2, 3 to 4, 4 to 7, 5 to 10, 6 to 15
        )

        const val LONGEST_PATH_BONUS = 10

        val MAX_ROUTE_COUNT: Map<Int, Int> = ROUTE_LENGTHS.associateWith { 20 }

        /** Selectable point *magnitudes* for a destination ticket (sign is chosen separately). */
        val TICKET_VALUES: List<Int> = (1..30).toList()
    }
}
