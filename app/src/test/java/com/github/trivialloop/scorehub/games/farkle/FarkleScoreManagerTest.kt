package com.github.trivialloop.scorehub.games.farkle

import org.junit.Test
import org.junit.Assert.*

class FarkleScoreManagerTest {

    // ─── FarkleRound — initial state ──────────────────────────────────────────

    @Test
    fun `new round is not complete`() {
        val round = FarkleRound(1, playerId = 1L)
        assertFalse(round.isComplete)
    }

    @Test
    fun `new round entry sum is 0`() {
        val round = FarkleRound(1, playerId = 1L)
        assertEquals(0, round.entrySum)
    }

    @Test
    fun `new round score is 0`() {
        val round = FarkleRound(1, playerId = 1L)
        assertEquals(0, round.score)
    }

    // ─── FarkleRound — roll entries ───────────────────────────────────────────

    @Test
    fun `entrySum sums all roll entries`() {
        val round = FarkleRound(1, playerId = 1L, rollEntries = mutableListOf(300, 200, 50))
        assertEquals(550, round.entrySum)
    }

    @Test
    fun `entrySum with single entry`() {
        val round = FarkleRound(1, playerId = 1L, rollEntries = mutableListOf(1000))
        assertEquals(1000, round.entrySum)
    }

    // ─── FarkleRound — banked ─────────────────────────────────────────────────

    @Test
    fun `banked round is complete`() {
        val round = FarkleRound(1, playerId = 1L, rollEntries = mutableListOf(300), banked = true)
        assertTrue(round.isComplete)
    }

    @Test
    fun `banked round score equals entry sum`() {
        val round = FarkleRound(1, playerId = 1L, rollEntries = mutableListOf(300, 200), banked = true)
        assertEquals(500, round.score)
    }

    @Test
    fun `banked round with no entries scores 0`() {
        val round = FarkleRound(1, playerId = 1L, banked = true)
        assertEquals(0, round.score)
    }

    // ─── FarkleRound — farkled ────────────────────────────────────────────────

    @Test
    fun `farkled round is complete`() {
        val round = FarkleRound(1, playerId = 1L, farkled = true)
        assertTrue(round.isComplete)
    }

    @Test
    fun `farkled round score is always 0`() {
        val round = FarkleRound(1, playerId = 1L, rollEntries = mutableListOf(300, 500), farkled = true)
        assertEquals(0, round.score)
    }

    @Test
    fun `farkled and not banked - score is 0`() {
        val round = FarkleRound(1, playerId = 1L, rollEntries = mutableListOf(1000), farkled = true, banked = false)
        assertEquals(0, round.score)
    }

    // ─── FarklePlayerState.getTotal ───────────────────────────────────────────

    @Test
    fun `getTotal returns 0 with no rounds`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums only completed banked rounds for this player`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FarkleRound(1, 1L, rollEntries = mutableListOf(300, 200), banked = true),
            FarkleRound(2, 1L, rollEntries = mutableListOf(1000), banked = true),
            FarkleRound(3, 1L) // in progress — not counted
        )
        assertEquals(1500, player.getTotal(rounds))
    }

    @Test
    fun `getTotal excludes farkled rounds`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FarkleRound(1, 1L, rollEntries = mutableListOf(500), banked = true),
            FarkleRound(2, 1L, rollEntries = mutableListOf(300), farkled = true) // scores 0
        )
        assertEquals(500, player.getTotal(rounds))
    }

    @Test
    fun `getTotal ignores rounds belonging to other players`() {
        val alice = FarklePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FarkleRound(1, 1L, rollEntries = mutableListOf(400), banked = true),  // Alice
            FarkleRound(1, 2L, rollEntries = mutableListOf(900), banked = true)   // Bob — ignored
        )
        assertEquals(400, alice.getTotal(rounds))
    }

    @Test
    fun `getTotal accumulates across many rounds`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        val rounds = (1..10).map { i ->
            FarkleRound(i, 1L, rollEntries = mutableListOf(1000), banked = true)
        }
        assertEquals(10_000, player.getTotal(rounds))
    }

    // ─── FarklePlayerState.currentRound ──────────────────────────────────────

    @Test
    fun `currentRound returns null when no rounds`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        assertNull(player.currentRound(emptyList()))
    }

    @Test
    fun `currentRound returns the active round`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        val active = FarkleRound(2, 1L)
        val rounds = listOf(
            FarkleRound(1, 1L, banked = true),
            active
        )
        assertEquals(active, player.currentRound(rounds))
    }

    @Test
    fun `currentRound returns null when all rounds are complete`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FarkleRound(1, 1L, banked = true),
            FarkleRound(2, 1L, farkled = true)
        )
        assertNull(player.currentRound(rounds))
    }

    // ─── Game-over threshold ─────────────────────────────────────────────────

    @Test
    fun `total below 10000 does not trigger end`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FarkleRound(1, 1L, rollEntries = mutableListOf(5000), banked = true),
            FarkleRound(2, 1L, rollEntries = mutableListOf(4999), banked = true)
        )
        assertEquals(9999, player.getTotal(rounds))
        assertFalse(player.getTotal(rounds) >= 10_000)
    }

    @Test
    fun `total exactly 10000 triggers end`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FarkleRound(1, 1L, rollEntries = mutableListOf(5000), banked = true),
            FarkleRound(2, 1L, rollEntries = mutableListOf(5000), banked = true)
        )
        assertEquals(10_000, player.getTotal(rounds))
        assertTrue(player.getTotal(rounds) >= 10_000)
    }

    @Test
    fun `total above 10000 triggers end`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FarkleRound(1, 1L, rollEntries = mutableListOf(6000), banked = true),
            FarkleRound(2, 1L, rollEntries = mutableListOf(5000), banked = true)
        )
        assertEquals(11_000, player.getTotal(rounds))
        assertTrue(player.getTotal(rounds) >= 10_000)
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `multiple players - totals are independent`() {
        val alice = FarklePlayerState(1L, "Alice", 0xFF0000)
        val bob   = FarklePlayerState(2L, "Bob",   0x00FF00)
        val rounds = listOf(
            FarkleRound(1, 1L, rollEntries = mutableListOf(500), banked = true),
            FarkleRound(1, 2L, rollEntries = mutableListOf(300), banked = true),
            FarkleRound(2, 1L, rollEntries = mutableListOf(200), farkled = true),
            FarkleRound(2, 2L, rollEntries = mutableListOf(700), banked = true)
        )
        assertEquals(500, alice.getTotal(rounds))
        assertEquals(1000, bob.getTotal(rounds))
    }

    @Test
    fun `farkled round clears entries - score is 0`() {
        val round = FarkleRound(1, playerId = 1L)
        round.rollEntries.addAll(listOf(300, 200, 100))
        round.farkled = true
        round.rollEntries.clear()
        assertEquals(0, round.score)
        assertTrue(round.isComplete)
    }

    @Test
    fun `in-progress round is not counted in getTotal`() {
        val player = FarklePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FarkleRound(1, 1L, rollEntries = mutableListOf(9000), banked = true),
            FarkleRound(2, 1L, rollEntries = mutableListOf(500)) // in progress
        )
        // Only completed round counts
        assertEquals(9000, player.getTotal(rounds))
    }
}
