package com.github.trivialloop.scorehub.games.scrabble

import org.junit.Assert.*
import org.junit.Test

class ScrabbleScoreManagerTest {

    private fun turn(round: Int, player: Long, vararg scores: Int, done: Boolean = true) =
        ScrabbleTurn(round, player, scores.toMutableList(), done)

    // ─── Score validation (1..999) ────────────────────────────────────────────

    @Test
    fun `score bounds are 1 and 999`() {
        assertEquals(1, SCRABBLE_MIN_SCORE)
        assertEquals(999, SCRABBLE_MAX_SCORE)
    }

    @Test
    fun `valid scores are accepted`() {
        assertTrue(isValidScrabbleScore(1))
        assertTrue(isValidScrabbleScore(24))
        assertTrue(isValidScrabbleScore(999))
    }

    @Test
    fun `zero is refused`() = assertFalse(isValidScrabbleScore(0))

    @Test
    fun `negative scores are refused`() {
        assertFalse(isValidScrabbleScore(-1))
        assertFalse(isValidScrabbleScore(-50))
    }

    @Test
    fun `scores above 999 are refused`() = assertFalse(isValidScrabbleScore(1000))

    @Test
    fun `missing or unparsable score is refused`() = assertFalse(isValidScrabbleScore(null))

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
    fun `a closed turn without entry is a pass and scores 0`() {
        val t = ScrabbleTurn(1, 1L, isComplete = true)
        assertEquals(0, t.score)
        assertEquals("", t.expression)
    }

    @Test
    fun `expression joins the entries with a plus`() {
        assertEquals("12+4", turn(1, 1L, 12, 4).expression)
    }

    @Test
    fun `expression of a single entry is that entry`() {
        assertEquals("12", turn(1, 1L, 12).expression)
    }

    @Test
    fun `total is the sum shown next to the expression`() {
        val t = turn(1, 1L, 12, 4)
        assertEquals(16, t.score)
        assertEquals("12+4", t.expression)
    }

    // ─── Editing a turn (each sub-score editable) ─────────────────────────────

    @Test
    fun `editing one entry updates the total and the expression`() {
        val t = turn(1, 1L, 12, 4)
        t.entries[0] = 20
        assertEquals(24, t.score)
        assertEquals("20+4", t.expression)
    }

    @Test
    fun `editing every entry works`() {
        val t = turn(1, 1L, 12, 4)
        val edited = listOf(7, 9)
        t.entries.clear(); t.entries.addAll(edited)
        assertEquals(16, t.score)
        assertEquals("7+9", t.expression)
    }

    @Test
    fun `clearing an entry removes it from the total`() {
        val t = turn(1, 1L, 12, 4)
        t.entries.removeAt(0)
        assertEquals(4, t.score)
        assertEquals("4", t.expression)
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
    fun `getTotal counts a pass as 0`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val turns = listOf(turn(1, 1L, 20), ScrabbleTurn(2, 1L, isComplete = true))
        assertEquals(20, alice.getTotal(turns))
    }

    @Test
    fun `getTotal adds the adjustment of the player`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val turns = listOf(turn(1, 1L, 40, 20))
        assertEquals(60, alice.getTotal(turns))
        assertEquals(53, alice.getTotal(turns, mapOf(1L to -7)))
        assertEquals(60, alice.getTotal(turns, mapOf(2L to -7)))
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

    // ─── End of game adjustments ──────────────────────────────────────────────

    @Test
    fun `everyone loses their rack when nobody went out`() {
        val adj = ScrabbleEndGame.computeAdjustments(mapOf(1L to 7, 2L to 12), wentOutId = null)
        assertEquals(mapOf(1L to -7, 2L to -12), adj)
    }

    @Test
    fun `player who went out gains the sum of the other racks`() {
        val adj = ScrabbleEndGame.computeAdjustments(mapOf(1L to 0, 2L to 9, 3L to 4), wentOutId = 1L)
        assertEquals(13, adj[1L])
        assertEquals(-9, adj[2L])
        assertEquals(-4, adj[3L])
    }

    @Test
    fun `the rack of the player who went out is ignored`() {
        val adj = ScrabbleEndGame.computeAdjustments(mapOf(1L to 5, 2L to 8), wentOutId = 1L)
        assertEquals(8, adj[1L])
        assertEquals(-8, adj[2L])
    }

    @Test
    fun `negative rack values are treated as zero`() {
        val adj = ScrabbleEndGame.computeAdjustments(mapOf(1L to -3, 2L to 6), wentOutId = null)
        assertEquals(0, adj[1L])
        assertEquals(-6, adj[2L])
    }

    @Test
    fun `unknown player who went out changes nothing`() {
        val adj = ScrabbleEndGame.computeAdjustments(mapOf(1L to 3), wentOutId = 99L)
        assertEquals(mapOf(1L to -3), adj)
    }

    @Test
    fun `adjustments are zero-sum when a player went out`() {
        val adj = ScrabbleEndGame.computeAdjustments(mapOf(1L to 0, 2L to 11, 3L to 6, 4L to 2), wentOutId = 1L)
        assertEquals(0, adj.values.sum())
    }

    @Test
    fun `final totals combine turns and adjustments`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val bob = ScrabblePlayerState(2L, "Bob", 0)
        val turns = listOf(turn(1, 1L, 40, 21), turn(1, 2L, 30))
        val adj = ScrabbleEndGame.computeAdjustments(mapOf(1L to 0, 2L to 9), wentOutId = 1L)
        assertEquals(70, alice.getTotal(turns, adj))   // 61 + 9
        assertEquals(21, bob.getTotal(turns, adj))     // 30 - 9
    }
}
