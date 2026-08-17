package com.github.trivialloop.scorehub.games.ligretto

import org.junit.Test
import org.junit.Assert.*

class LigrettoScoreManagerTest {

    // ─── LigrettoRound.allScoresEntered / isComplete ──────────────────────────

    @Test
    fun `allScoresEntered returns false when nothing entered`() {
        val round = LigrettoRound(1)
        assertFalse(round.allScoresEntered(listOf(1L, 2L)))
    }

    @Test
    fun `allScoresEntered returns false when only cardsPlayed entered`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 15
        }
        assertFalse(round.allScoresEntered(listOf(1L, 2L)))
    }

    @Test
    fun `allScoresEntered returns false when only one player fully entered`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 15; stackLeft[1L] = 0
        }
        assertFalse(round.allScoresEntered(listOf(1L, 2L)))
    }

    @Test
    fun `allScoresEntered returns true when all players fully entered`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 15; stackLeft[1L] = 0
            cardsPlayed[2L] = 10; stackLeft[2L] = 3
        }
        assertTrue(round.allScoresEntered(listOf(1L, 2L)))
        assertTrue(round.isComplete(listOf(1L, 2L)))
    }

    @Test
    fun `allScoresEntered treats 0 as entered not missing`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 0; stackLeft[1L] = 0
        }
        assertTrue(round.allScoresEntered(listOf(1L)))
    }

    // ─── LigrettoRound.getScore ────────────────────────────────────────────────

    @Test
    fun `getScore returns null when cardsPlayed missing`() {
        val round = LigrettoRound(1).apply { stackLeft[1L] = 2 }
        assertNull(round.getScore(1L))
    }

    @Test
    fun `getScore returns null when stackLeft missing`() {
        val round = LigrettoRound(1).apply { cardsPlayed[1L] = 10 }
        assertNull(round.getScore(1L))
    }

    @Test
    fun `getScore computes played minus 2 times left`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 15; stackLeft[1L] = 4
        }
        // 15 - 2*4 = 7
        assertEquals(7, round.getScore(1L))
    }

    @Test
    fun `getScore with empty stack (finisher) has no penalty`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 22; stackLeft[1L] = 0
        }
        assertEquals(22, round.getScore(1L))
    }

    @Test
    fun `getScore can be negative when penalty exceeds cards played`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 2; stackLeft[1L] = 10
        }
        // 2 - 20 = -18
        assertEquals(-18, round.getScore(1L))
    }

    @Test
    fun `getScore of 0 played and 0 left is 0`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 0; stackLeft[1L] = 0
        }
        assertEquals(0, round.getScore(1L))
    }

    // ─── LigrettoPlayerState.getTotal ─────────────────────────────────────────

    @Test
    fun `getTotal returns 0 with no rounds`() {
        val player = LigrettoPlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums scores across rounds`() {
        val player = LigrettoPlayerState(1L, "Alice", 0xFF0000)
        val r1 = LigrettoRound(1).apply { cardsPlayed[1L] = 20; stackLeft[1L] = 0 }   // 20
        val r2 = LigrettoRound(2).apply { cardsPlayed[1L] = 8; stackLeft[1L] = 5 }    // 8 - 10 = -2
        assertEquals(18, player.getTotal(listOf(r1, r2)))
    }

    @Test
    fun `getTotal ignores rounds with missing data for this player`() {
        val player = LigrettoPlayerState(1L, "Alice", 0xFF0000)
        val r1 = LigrettoRound(1).apply { cardsPlayed[1L] = 12; stackLeft[1L] = 1 } // 10
        val r2 = LigrettoRound(2) // nothing entered for player 1
        assertEquals(10, player.getTotal(listOf(r1, r2)))
    }

    @Test
    fun `getTotal only counts scores for the correct player`() {
        val alice = LigrettoPlayerState(1L, "Alice", 0xFF0000)
        val bob = LigrettoPlayerState(2L, "Bob", 0x00FF00)
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 18; stackLeft[1L] = 2   // 14
            cardsPlayed[2L] = 9; stackLeft[2L] = 6    // -3
        }
        assertEquals(14, alice.getTotal(listOf(round)))
        assertEquals(-3, bob.getTotal(listOf(round)))
    }

    // ─── Score limit / winner logic (99 pts, highest wins) ────────────────────

    @Test
    fun `total below 99 does not trigger end of game`() {
        val player = LigrettoPlayerState(1L, "Alice", 0xFF0000)
        val round = LigrettoRound(1).apply { cardsPlayed[1L] = 50; stackLeft[1L] = 0 }
        assertFalse(player.getTotal(listOf(round)) >= 99)
    }

    @Test
    fun `total at or above 99 triggers end of game`() {
        val player = LigrettoPlayerState(1L, "Alice", 0xFF0000)
        val round = LigrettoRound(1).apply { cardsPlayed[1L] = 100; stackLeft[1L] = 0 }
        assertTrue(player.getTotal(listOf(round)) >= 99)
    }

    @Test
    fun `highest total wins on tie at limit`() {
        val alice = LigrettoPlayerState(1L, "Alice", 0xFF0000)
        val bob = LigrettoPlayerState(2L, "Bob", 0x00FF00)
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 99; stackLeft[1L] = 0
            cardsPlayed[2L] = 120; stackLeft[2L] = 0
        }
        val totals = mapOf(alice to alice.getTotal(listOf(round)), bob to bob.getTotal(listOf(round)))
        val maxScore = totals.values.max()
        val winners = totals.filter { it.value == maxScore }.keys
        assertEquals(1, winners.size)
        assertTrue(winners.contains(bob))
    }

    // ─── getCellColor ──────────────────────────────────────────────────────────

    @Test
    fun `getCellColor returns DEFAULT before round is complete`() {
        val round = LigrettoRound(1).apply { cardsPlayed[1L] = 10 }
        assertEquals(LigrettoCellColor.DEFAULT, round.getCellColor(1L, listOf(1L, 2L)))
    }

    @Test
    fun `getCellColor returns GREEN for highest round score`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 20; stackLeft[1L] = 0  // 20
            cardsPlayed[2L] = 5; stackLeft[2L] = 5   // -5
        }
        assertEquals(LigrettoCellColor.GREEN, round.getCellColor(1L, listOf(1L, 2L)))
        assertEquals(LigrettoCellColor.RED, round.getCellColor(2L, listOf(1L, 2L)))
    }

    @Test
    fun `getCellColor returns DEFAULT when all scores are equal`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 10; stackLeft[1L] = 0
            cardsPlayed[2L] = 12; stackLeft[2L] = 1
        }
        // both score 10
        assertEquals(LigrettoCellColor.DEFAULT, round.getCellColor(1L, listOf(1L, 2L)))
        assertEquals(LigrettoCellColor.DEFAULT, round.getCellColor(2L, listOf(1L, 2L)))
    }

    @Test
    fun `getCellColor middle score among three players returns DEFAULT`() {
        val round = LigrettoRound(1).apply {
            cardsPlayed[1L] = 20; stackLeft[1L] = 0  // 20 max
            cardsPlayed[2L] = 10; stackLeft[2L] = 0  // 10 middle
            cardsPlayed[3L] = 0; stackLeft[3L] = 5   // -10 min
        }
        assertEquals(LigrettoCellColor.GREEN, round.getCellColor(1L, listOf(1L, 2L, 3L)))
        assertEquals(LigrettoCellColor.DEFAULT, round.getCellColor(2L, listOf(1L, 2L, 3L)))
        assertEquals(LigrettoCellColor.RED, round.getCellColor(3L, listOf(1L, 2L, 3L)))
    }

    // ─── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `single player round completes with one entry`() {
        val round = LigrettoRound(1).apply { cardsPlayed[1L] = 30; stackLeft[1L] = 2 }
        assertTrue(round.isComplete(listOf(1L)))
        assertEquals(26, round.getScore(1L))
    }

    @Test
    fun `twelve players round can all be entered independently`() {
        val playerIds = (1L..12L).toList()
        val round = LigrettoRound(1)
        playerIds.forEachIndexed { idx, id ->
            round.cardsPlayed[id] = idx
            round.stackLeft[id] = 0
        }
        assertTrue(round.allScoresEntered(playerIds))
        assertEquals(11, round.getScore(12L))
    }

    @Test
    fun `finisherId is purely informational and does not affect score`() {
        val round = LigrettoRound(1).apply {
            finisherId = 1L
            cardsPlayed[1L] = 18; stackLeft[1L] = 3 // finisher but did NOT report 0 left; still computed normally
        }
        assertEquals(12, round.getScore(1L))
    }
}
