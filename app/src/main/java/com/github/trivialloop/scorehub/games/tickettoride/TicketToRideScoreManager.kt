package com.github.trivialloop.scorehub.games.tickettoride

/**
 * Ticket to Ride scoring — one-shot tally per player (no rounds).
 *
 * Official scoring (Days of Wonder rules):
 *  - Each claimed route (tronçon) scores points based on its length (number of train cars):
 *    1→1, 2→2, 3→4, 4→7, 5→10, 6→15 points. [routeCounts] tracks how many routes of each
 *    length a player claimed during the game.
 *  - Each completed Destination Ticket adds its printed point value to the score.
 *  - Each failed (uncompleted) Destination Ticket SUBTRACTS its printed point value.
 *  - The player(s) with the Longest Continuous Path get a flat +10 bonus. Ties are allowed:
 *    multiple players can have [hasLongestPath] = true simultaneously.
 */
data class TicketToRidePlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val routeCounts: MutableMap<Int, Int> = ROUTE_LENGTHS.associateWith { 0 }.toMutableMap(),
    val completedTickets: MutableList<Int> = mutableListOf(),
    val failedTickets: MutableList<Int> = mutableListOf(),
    var hasLongestPath: Boolean = false
) {
    /** Points earned from claimed routes only. */
    fun getRoutePoints(): Int =
        routeCounts.entries.sumOf { (length, count) -> (ROUTE_POINTS[length] ?: 0) * count }

    /** Sum of all completed destination ticket values. */
    fun getCompletedTicketsPoints(): Int = completedTickets.sum()

    /** Sum of all failed destination ticket values (positive number; subtracted in getTotal). */
    fun getFailedTicketsPoints(): Int = failedTickets.sum()

    /** +10 if this player has (or shares) the longest continuous path, else 0. */
    fun getLongestPathBonus(): Int = if (hasLongestPath) LONGEST_PATH_BONUS else 0

    /** Final score: routes + completed tickets − failed tickets + longest path bonus. */
    fun getTotal(): Int =
        getRoutePoints() + getCompletedTicketsPoints() - getFailedTicketsPoints() + getLongestPathBonus()

    companion object {
        /** Route (tronçon) lengths tracked, in cars. */
        val ROUTE_LENGTHS: List<Int> = listOf(1, 2, 3, 4, 5, 6)

        /** Official points per route length. */
        val ROUTE_POINTS: Map<Int, Int> = mapOf(
            1 to 1,
            2 to 2,
            3 to 4,
            4 to 7,
            5 to 10,
            6 to 15
        )

        const val LONGEST_PATH_BONUS = 10

        /** Reasonable upper bound for how many routes of one length a single player can claim. */
        const val MAX_ROUTE_COUNT = 12

        /** Selectable point values for a destination ticket (covers all official maps). */
        val TICKET_VALUES: List<Int> = (1..30).toList()
    }
}
