package com.github.trivialloop.scorehub.games.cactus

import org.junit.Test
import org.junit.Assert.*

class CactusScoreManagerTest {

    // ─── CactusRound.allScoresEntered ─────────────────────────────────────────

    @Test
    fun `allScoresEntered returns false when no scores entered`() {
        val round = CactusRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        assertFalse(round.allScoresEntered(playerIds))
    }

    @Test
    fun `allScoresEntered returns false when only some scores entered`() {
        val round = CactusRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.scores[1L] = 0
        round.scores[2L] = 1
        assertFalse(round.allScoresEntered(playerIds))
    }

    @Test
    fun `allScoresEntered returns true when all scores entered`() {
        val round = CactusRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.scores[1L] = 0
        round.scores[2L] = 1
        round.scores[3L] = 0
        assertTrue(round.allScoresEntered(playerIds))
    }

    // ─── CactusRound.isComplete ───────────────────────────────────────────────

    @Test
    fun `isComplete returns false when not all players have scores`() {
        val round = CactusRound(1)
        val playerIds = listOf(1L, 2L)
        round.scores[1L] = 1
        assertFalse(round.isComplete(playerIds))
    }

    @Test
    fun `isComplete returns true when all players have scores`() {
        val round = CactusRound(1)
        val playerIds = listOf(1L, 2L)
        round.scores[1L] = 0
        round.scores[2L] = 1
        assertTrue(round.isComplete(playerIds))
    }

    // ─── CactusPlayerState.getTotal ───────────────────────────────────────────

    @Test
    fun `getTotal returns 0 when no rounds`() {
        val player = CactusPlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal returns 0 when all scores are 0`() {
        val player = CactusPlayerState(1L, "Alice", 0xFF0000)
        val playerIds = listOf(1L, 2L)

        val rounds = listOf(
            CactusRound(1).apply { scores[1L] = 0; scores[2L] = 1 },
            CactusRound(2).apply { scores[1L] = 0; scores[2L] = 0 }
        )

        assertEquals(0, player.getTotal(rounds))
    }

    @Test
    fun `getTotal sums scores correctly across rounds`() {
        val player = CactusPlayerState(1L, "Alice", 0xFF0000)

        val rounds = listOf(
            CactusRound(1).apply { scores[1L] = 1 },
            CactusRound(2).apply { scores[1L] = 0 },
            CactusRound(3).apply { scores[1L] = 1 },
            CactusRound(4).apply { scores[1L] = 1 }
        )

        assertEquals(3, player.getTotal(rounds))
    }

    @Test
    fun `getTotal ignores rounds with no score for this player`() {
        val player = CactusPlayerState(1L, "Alice", 0xFF0000)
        val round = CactusRound(1) // no score for player 1
        assertEquals(0, player.getTotal(listOf(round)))
    }

    @Test
    fun `getTotal reaches score limit correctly`() {
        val player = CactusPlayerState(1L, "Alice", 0xFF0000)
        val rounds = (1..10).map { i ->
            CactusRound(i).apply { scores[1L] = 1 }
        }
        assertEquals(10, player.getTotal(rounds))
    }

    // ─── Score values ─────────────────────────────────────────────────────────

    @Test
    fun `scores only accept 0 or 1`() {
        val round = CactusRound(1)
        round.scores[1L] = 0
        round.scores[2L] = 1
        assertEquals(0, round.scores[1L])
        assertEquals(1, round.scores[2L])
    }

    @Test
    fun `single player round is complete with one score`() {
        val round = CactusRound(1)
        val playerIds = listOf(1L)
        round.scores[1L] = 0
        assertTrue(round.isComplete(playerIds))
    }

    @Test
    fun `multiple rounds total accumulates correctly`() {
        val player = CactusPlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            CactusRound(1).apply { scores[1L] = 1 },
            CactusRound(2).apply { scores[1L] = 1 },
            CactusRound(3).apply { scores[1L] = 1 },
            CactusRound(4).apply { scores[1L] = 1 },
            CactusRound(5).apply { scores[1L] = 1 },
            CactusRound(6).apply { scores[1L] = 1 },
            CactusRound(7).apply { scores[1L] = 1 },
            CactusRound(8).apply { scores[1L] = 1 },
            CactusRound(9).apply { scores[1L] = 1 },
            CactusRound(10).apply { scores[1L] = 1 }
        )
        assertEquals(10, player.getTotal(rounds))
    }
}
