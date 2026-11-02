package com.github.trivialloop.scorehub.games.scrabble

import org.junit.Assert.*
import org.junit.Test

class ScrabbleScoreManagerTest {

    private fun turn(round: Int, player: Long, vararg scores: Int, done: Boolean = true) =
        ScrabbleTurn(round, player, scores.toMutableList(), done)

    // ─── ScrabbleTurn ─────────────────────────────────────────────────────────

    @Test
    fun `new turn is empty, incomplete and scores 0`() {
        val t = ScrabbleTurn(1, 1L)
        assertTrue(t.entries.isEmpty())
        assertFalse(t.isComplete)
        assertEquals(0, t.score)
    }

    @Test
    fun `turn score sums every entry`() {
        assertEquals(46, turn(1, 1L, 24, 12, 10).score)
    }

    @Test
    fun `negative entries are subtracted`() {
        assertEquals(15, turn(1, 1L, 20, -5).score)
    }

    @Test
    fun `turn score can be negative`() {
        assertEquals(-8, turn(1, 1L, -8).score)
    }

    @Test
    fun `zero entry is a valid entry`() {
        val t = turn(1, 1L, 0)
        assertEquals(1, t.entries.size)
        assertEquals(0, t.score)
    }

    // ─── getTotal ─────────────────────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 with no turns`() {
        assertEquals(0, ScrabblePlayerState(1L, "Alice", 0).getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums turns of this player only`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val bob = ScrabblePlayerState(2L, "Bob", 0)
        val turns = listOf(
            turn(1, 1L, 20, 10),
            turn(1, 2L, 35),
            turn(2, 1L, 15)
        )
        assertEquals(45, alice.getTotal(turns))
        assertEquals(35, bob.getTotal(turns))
    }

    @Test
    fun `getTotal includes the turn in progress`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val turns = listOf(turn(1, 1L, 20), turn(2, 1L, 8, done = false))
        assertEquals(28, alice.getTotal(turns))
    }

    @Test
    fun `end of game rack adjustments are plain signed entries`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val bob = ScrabblePlayerState(2L, "Bob", 0)
        val turns = listOf(
            turn(1, 1L, 40),
            turn(1, 2L, 30),
            turn(2, 1L, 12, 9),   // Alice goes out and adds Bob's leftover tiles (9)
            turn(2, 2L, -9)       // Bob subtracts his leftover tiles
        )
        assertEquals(61, alice.getTotal(turns))
        assertEquals(21, bob.getTotal(turns))
    }

    @Test
    fun `editing an entry updates the total`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val t = turn(1, 1L, 20, 10)
        t.entries[1] = 14
        assertEquals(34, alice.getTotal(listOf(t)))
    }

    @Test
    fun `deleting an entry updates the total`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val t = turn(1, 1L, 20, 10)
        t.entries.removeAt(0)
        assertEquals(10, alice.getTotal(listOf(t)))
    }

    // ─── currentTurn ──────────────────────────────────────────────────────────

    @Test
    fun `currentTurn returns null when every turn is complete`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        assertNull(alice.currentTurn(listOf(turn(1, 1L, 5))))
    }

    @Test
    fun `currentTurn returns the in-progress turn of this player`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val open = ScrabbleTurn(2, 1L)
        val turns = listOf(turn(1, 1L, 5), ScrabbleTurn(2, 2L), open)
        assertSame(open, alice.currentTurn(turns))
    }

    @Test
    fun `currentTurn ignores in-progress turns of other players`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        assertNull(alice.currentTurn(listOf(ScrabbleTurn(1, 2L))))
    }
}
