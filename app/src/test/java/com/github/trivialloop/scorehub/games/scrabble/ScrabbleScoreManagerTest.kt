package com.github.trivialloop.scorehub.games.scrabble

import org.junit.Assert.*
import org.junit.Test

class ScrabbleEndGameTest {

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
    fun `getTotal adds the adjustment of the player`() {
        val alice = ScrabblePlayerState(1L, "Alice", 0)
        val turns = listOf(ScrabbleTurn(1, 1L, mutableListOf(40, 20), true))
        assertEquals(60, alice.getTotal(turns))
        assertEquals(53, alice.getTotal(turns, mapOf(1L to -7)))
        assertEquals(60, alice.getTotal(turns, mapOf(2L to -7)))
    }
}
