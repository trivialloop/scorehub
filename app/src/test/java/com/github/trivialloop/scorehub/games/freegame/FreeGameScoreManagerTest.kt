package com.github.trivialloop.scorehub.games.freegame

import org.junit.Test
import org.junit.Assert.*

class FreeGameScoreManagerTest {

    @Test
    fun `new round has score 0 and is not complete`() {
        val round = FreeGameRound(1, playerId = 1L)
        assertEquals(0, round.score)
        assertFalse(round.isComplete)
    }

    @Test
    fun `completed round preserves score`() {
        val round = FreeGameRound(1, playerId = 1L, score = 7, isComplete = true)
        assertEquals(7, round.score)
        assertTrue(round.isComplete)
    }

    @Test
    fun `getTotal returns 0 with no rounds`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums only completed rounds for this player`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FreeGameRound(1, 1L, score = 3, isComplete = true),
            FreeGameRound(2, 1L, score = 5, isComplete = true),
            FreeGameRound(3, 1L, score = 2, isComplete = false)
        )
        assertEquals(8, player.getTotal(rounds))
    }

    @Test
    fun `getTotal excludes other players rounds`() {
        val alice = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val bob   = FreeGamePlayerState(2L, "Bob",   0x00FF00)
        val rounds = listOf(
            FreeGameRound(1, 1L, score = 4, isComplete = true),
            FreeGameRound(2, 2L, score = 9, isComplete = true)
        )
        assertEquals(4, alice.getTotal(rounds))
        assertEquals(9, bob.getTotal(rounds))
    }

    @Test
    fun `getTotal handles negative scores`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FreeGameRound(1, 1L, score = 5,  isComplete = true),
            FreeGameRound(2, 1L, score = -2, isComplete = true)
        )
        assertEquals(3, player.getTotal(rounds))
    }

    @Test
    fun `score increments correctly with all buttons`() {
        val round = FreeGameRound(1, 1L)
        round.score += 1; assertEquals(1, round.score)
        round.score += 2; assertEquals(3, round.score)
        round.score += 5; assertEquals(8, round.score)
    }

    @Test
    fun `score decrements correctly with all buttons`() {
        val round = FreeGameRound(1, 1L, score = 10)
        round.score -= 1; assertEquals(9, round.score)
        round.score -= 2; assertEquals(7, round.score)
        round.score -= 5; assertEquals(2, round.score)
    }

    @Test
    fun `score can go negative`() {
        val round = FreeGameRound(1, 1L, score = 2)
        round.score -= 5
        assertEquals(-3, round.score)
    }

    @Test
    fun `completing a round includes it in total`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val round  = FreeGameRound(1, 1L, score = 7)
        val rounds = mutableListOf(round)
        assertEquals(0, player.getTotal(rounds))
        round.isComplete = true
        assertEquals(7, player.getTotal(rounds))
    }

    @Test
    fun `multiple players accumulate independently`() {
        val alice = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val bob   = FreeGamePlayerState(2L, "Bob",   0x00FF00)
        val rounds = listOf(
            FreeGameRound(1, 1L, score = 3, isComplete = true),
            FreeGameRound(2, 2L, score = 7, isComplete = true),
            FreeGameRound(3, 1L, score = 5, isComplete = true),
            FreeGameRound(4, 2L, score = 2, isComplete = true)
        )
        assertEquals(8, alice.getTotal(rounds))
        assertEquals(9, bob.getTotal(rounds))
    }

    @Test
    fun `getTotal accumulates correctly over many rounds`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = (1..5).map { i ->
            FreeGameRound(i, 1L, score = i * 10, isComplete = true)
        }
        assertEquals(150, player.getTotal(rounds))
    }
}
