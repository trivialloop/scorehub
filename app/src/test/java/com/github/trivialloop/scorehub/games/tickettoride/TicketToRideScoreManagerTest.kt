package com.github.trivialloop.scorehub.games.tickettoride

import org.junit.Test
import org.junit.Assert.*

class TicketToRideScoreManagerTest {

    // ─── getRoutePoints ────────────────────────────────────────────────────────

    @Test
    fun `getRoutePoints returns 0 when no routes claimed`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getRoutePoints())
    }

    @Test
    fun `getRoutePoints uses official points table`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.routeCounts[1] = 1  // 1
        ps.routeCounts[2] = 1  // 2
        ps.routeCounts[3] = 1  // 4
        ps.routeCounts[4] = 1  // 7
        ps.routeCounts[5] = 1  // 10
        ps.routeCounts[6] = 1  // 15
        // 1+2+4+7+10+15 = 39
        assertEquals(39, ps.getRoutePoints())
    }

    @Test
    fun `getRoutePoints multiplies count by length points`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.routeCounts[6] = 3 // 3 * 15 = 45
        ps.routeCounts[1] = 4 // 4 * 1 = 4
        assertEquals(49, ps.getRoutePoints())
    }

    @Test
    fun `route points table matches official values`() {
        assertEquals(1, TicketToRidePlayerScore.ROUTE_POINTS[1])
        assertEquals(2, TicketToRidePlayerScore.ROUTE_POINTS[2])
        assertEquals(4, TicketToRidePlayerScore.ROUTE_POINTS[3])
        assertEquals(7, TicketToRidePlayerScore.ROUTE_POINTS[4])
        assertEquals(10, TicketToRidePlayerScore.ROUTE_POINTS[5])
        assertEquals(15, TicketToRidePlayerScore.ROUTE_POINTS[6])
    }

    // ─── ticketEntries — completed / failed (signed entries) ──────────────────

    @Test
    fun `getCompletedTicketsPoints sums only positive entries`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.ticketEntries.addAll(listOf(7, 12, -5, 5))
        // positives: 7 + 12 + 5 = 24
        assertEquals(24, ps.getCompletedTicketsPoints())
    }

    @Test
    fun `getCompletedTicketsPoints returns 0 with no entries`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getCompletedTicketsPoints())
    }

    @Test
    fun `getCompletedTicketsPoints returns 0 when all entries are failed`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.ticketEntries.addAll(listOf(-10, -8))
        assertEquals(0, ps.getCompletedTicketsPoints())
    }

    @Test
    fun `getFailedTicketsPoints sums magnitude of negative entries`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.ticketEntries.addAll(listOf(-10, -8, 5))
        // magnitudes: 10 + 8 = 18 (returned as positive)
        assertEquals(18, ps.getFailedTicketsPoints())
    }

    @Test
    fun `getFailedTicketsPoints returns 0 with no failed entries`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.ticketEntries.addAll(listOf(7, 12))
        assertEquals(0, ps.getFailedTicketsPoints())
    }

    // ─── getTicketsTotal ────────────────────────────────────────────────────────

    @Test
    fun `getTicketsTotal returns 0 with no entries`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getTicketsTotal())
    }

    @Test
    fun `getTicketsTotal sums signed entries directly`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.ticketEntries.addAll(listOf(12, 8, -5))
        // 12 + 8 - 5 = 15
        assertEquals(15, ps.getTicketsTotal())
    }

    @Test
    fun `getTicketsTotal can be negative when failures outweigh completions`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.ticketEntries.addAll(listOf(3, -10, -8))
        // 3 - 10 - 8 = -15
        assertEquals(-15, ps.getTicketsTotal())
    }

    @Test
    fun `getTicketsTotal equals completed minus failed`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.ticketEntries.addAll(listOf(20, -6, 4, -9))
        assertEquals(ps.getCompletedTicketsPoints() - ps.getFailedTicketsPoints(), ps.getTicketsTotal())
    }

    // ─── longest path bonus ───────────────────────────────────────────────────

    @Test
    fun `getLongestPathBonus is 0 by default`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getLongestPathBonus())
    }

    @Test
    fun `getLongestPathBonus is 10 when flagged`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000).apply { hasLongestPath = true }
        assertEquals(10, ps.getLongestPathBonus())
    }

    @Test
    fun `longest path bonus can be shared by multiple players (tie)`() {
        val alice = TicketToRidePlayerScore(1L, "Alice", 0xFF0000).apply { hasLongestPath = true }
        val bob   = TicketToRidePlayerScore(2L, "Bob",   0x00FF00).apply { hasLongestPath = true }
        assertEquals(10, alice.getLongestPathBonus())
        assertEquals(10, bob.getLongestPathBonus())
    }

    // ─── getTotal ─────────────────────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 for a fresh player`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getTotal())
    }

    @Test
    fun `getTotal combines routes, tickets and bonus`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.routeCounts[4] = 2       // 2*7 = 14
        ps.ticketEntries.add(12)    // +12
        ps.ticketEntries.add(8)     // +8
        ps.ticketEntries.add(-5)    // -5
        ps.hasLongestPath = true    // +10
        // 14 + 12 + 8 - 5 + 10 = 39
        assertEquals(39, ps.getTotal())
    }

    @Test
    fun `getTotal can be negative when failed tickets outweigh everything else`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.routeCounts[1] = 1        // +1
        ps.ticketEntries.add(-20)    // -20
        assertEquals(-19, ps.getTotal())
    }

    @Test
    fun `getTotal ignores other players data`() {
        val alice = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        val bob   = TicketToRidePlayerScore(2L, "Bob",   0x00FF00)
        alice.routeCounts[6] = 1     // 15
        bob.routeCounts[6] = 2       // 30
        assertEquals(15, alice.getTotal())
        assertEquals(30, bob.getTotal())
    }

    // ─── Sign toggling (icon tap behaviour) ────────────────────────────────────

    @Test
    fun `negating an entry flips it from completed to failed`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.ticketEntries.add(12)
        assertEquals(12, ps.getCompletedTicketsPoints())
        assertEquals(0, ps.getFailedTicketsPoints())

        // Simulate tapping the icon: flip the sign in place
        val idx = 0
        ps.ticketEntries[idx] = -ps.ticketEntries[idx]

        assertEquals(0, ps.getCompletedTicketsPoints())
        assertEquals(12, ps.getFailedTicketsPoints())
        assertEquals(-12, ps.getTicketsTotal())
    }

    @Test
    fun `negating a failed entry flips it back to completed`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        ps.ticketEntries.add(-9)
        val idx = 0
        ps.ticketEntries[idx] = -ps.ticketEntries[idx]
        assertEquals(9, ps.ticketEntries[idx])
        assertEquals(9, ps.getCompletedTicketsPoints())
    }

    // ─── ROUTE_LENGTHS / defaults ─────────────────────────────────────────────

    @Test
    fun `ROUTE_LENGTHS contains 1 through 6`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), TicketToRidePlayerScore.ROUTE_LENGTHS)
    }

    @Test
    fun `routeCounts is initialized to 0 for all lengths`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        TicketToRidePlayerScore.ROUTE_LENGTHS.forEach { length ->
            assertEquals(0, ps.routeCounts[length])
        }
    }

    @Test
    fun `ticketEntries is empty by default`() {
        val ps = TicketToRidePlayerScore(1L, "Alice", 0xFF0000)
        assertTrue(ps.ticketEntries.isEmpty())
    }

    @Test
    fun `TICKET_VALUES starts at 1`() {
        assertEquals(1, TicketToRidePlayerScore.TICKET_VALUES.first())
    }

    @Test
    fun `MAX_ROUTE_COUNT defaults to 20 for every length`() {
        TicketToRidePlayerScore.ROUTE_LENGTHS.forEach { length ->
            assertEquals(20, TicketToRidePlayerScore.MAX_ROUTE_COUNT[length])
        }
    }
}
