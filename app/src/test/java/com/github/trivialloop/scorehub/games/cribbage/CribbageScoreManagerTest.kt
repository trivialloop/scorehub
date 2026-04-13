package com.github.trivialloop.scorehub.games.cribbage

import org.junit.Test
import org.junit.Assert.*

class CribbageScoreManagerTest {

    // ─── CribbageRound — initial state ────────────────────────────────────────

    @Test
    fun `pegging scores start at 0 for both players`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        assertEquals(0, round.peggingScores[1L])
        assertEquals(0, round.peggingScores[2L])
    }

    @Test
    fun `hand scores start as null for both players`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        assertNull(round.handScores[1L])
        assertNull(round.handScores[2L])
    }

    @Test
    fun `crib score starts as null`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        assertNull(round.cribScore)
    }

    // ─── State helpers ────────────────────────────────────────────────────────

    @Test
    fun `isPoneHandEntered returns false when pone hand is null`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        assertFalse(round.isPoneHandEntered())
    }

    @Test
    fun `isPoneHandEntered returns true when pone hand is set`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.handScores[2L] = 10
        assertTrue(round.isPoneHandEntered())
    }

    @Test
    fun `isDealerHandEntered returns false when dealer hand is null`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        assertFalse(round.isDealerHandEntered())
    }

    @Test
    fun `isDealerHandEntered returns true when dealer hand is set`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.handScores[1L] = 12
        assertTrue(round.isDealerHandEntered())
    }

    @Test
    fun `isComplete returns false when crib is null`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.handScores[1L] = 10
        round.handScores[2L] = 8
        assertFalse(round.isComplete())
    }

    @Test
    fun `isComplete returns true when crib is set`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.handScores[1L] = 10
        round.handScores[2L] = 8
        round.cribScore = 6
        assertTrue(round.isComplete())
    }

    @Test
    fun `isPeggingEditable returns true before pone hand entered`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        assertTrue(round.isPeggingEditable())
    }

    @Test
    fun `isPeggingEditable returns false after pone hand entered`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.handScores[2L] = 10  // pone enters hand
        assertFalse(round.isPeggingEditable())
    }

    // ─── roundTotal ───────────────────────────────────────────────────────────

    @Test
    fun `roundTotal for dealer includes pegging plus hand plus crib`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.peggingScores[1L] = 4
        round.handScores[1L]    = 12
        round.cribScore         = 6
        assertEquals(22, round.roundTotal(1L))
    }

    @Test
    fun `roundTotal for pone does not include crib`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.peggingScores[2L] = 3
        round.handScores[2L]    = 8
        round.cribScore         = 10   // crib belongs to dealer, not pone
        assertEquals(11, round.roundTotal(2L))
    }

    @Test
    fun `roundTotal returns only pegging when hand and crib are null`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.peggingScores[1L] = 7
        assertEquals(7, round.roundTotal(1L))
    }

    @Test
    fun `roundTotal returns 0 for a new round`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        assertEquals(0, round.roundTotal(1L))
        assertEquals(0, round.roundTotal(2L))
    }

    @Test
    fun `roundTotal handles partial entry - only pegging and pone hand set`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.peggingScores[1L] = 5
        round.handScores[2L]    = 9   // pone (player 2) entered, dealer has not
        // dealer total = pegging only (hand still null)
        assertEquals(5, round.roundTotal(1L))
        // pone total = pegging + hand
        assertEquals(9, round.roundTotal(2L))
    }

    // ─── CribbagePlayerState.getTotal ─────────────────────────────────────────

    @Test
    fun `getTotal returns 0 for no rounds`() {
        val player = CribbagePlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums roundTotal across all rounds`() {
        val player = CribbagePlayerState(1L, "Alice", 0xFF0000)

        val round1 = CribbageRound(1, dealerId = 1L, nonDealerId = 2L).apply {
            peggingScores[1L] = 4
            handScores[1L]    = 10
            cribScore         = 6   // Alice is dealer round 1
        }
        val round2 = CribbageRound(2, dealerId = 2L, nonDealerId = 1L).apply {
            peggingScores[1L] = 3
            handScores[1L]    = 8
            // Alice is pone in round 2 — no crib
        }

        // Round 1: Alice dealer → 4 + 10 + 6 = 20
        // Round 2: Alice pone  → 3 + 8 = 11
        assertEquals(31, player.getTotal(listOf(round1, round2)))
    }

    @Test
    fun `getTotal only includes crib when player is dealer`() {
        val alice = CribbagePlayerState(1L, "Alice", 0xFF0000)
        val bob   = CribbagePlayerState(2L, "Bob",   0x00FF00)

        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L).apply {
            peggingScores[1L] = 2
            peggingScores[2L] = 3
            handScores[1L]    = 10
            handScores[2L]    = 8
            cribScore         = 7
        }

        // Alice (dealer): 2 + 10 + 7 = 19
        assertEquals(19, alice.getTotal(listOf(round)))
        // Bob (pone): 3 + 8 = 11
        assertEquals(11, bob.getTotal(listOf(round)))
    }

    @Test
    fun `getTotal with partial round counts only what is entered`() {
        val player = CribbagePlayerState(1L, "Alice", 0xFF0000)
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L).apply {
            peggingScores[1L] = 5
            // hand and crib not yet entered
        }
        assertEquals(5, player.getTotal(listOf(round)))
    }

    // ─── Dealer alternation ───────────────────────────────────────────────────

    @Test
    fun `dealer alternates correctly across rounds`() {
        // Simulate the alternation logic used in CribbageGameActivity.addNewRound()
        val playerIds = longArrayOf(10L, 20L)
        val rounds = mutableListOf<CribbageRound>()

        repeat(4) { idx ->
            val dealerIndex = idx % 2
            val poneIndex   = 1 - dealerIndex
            rounds.add(CribbageRound(idx + 1, playerIds[dealerIndex], playerIds[poneIndex]))
        }

        // Round 1: players[0] deals
        assertEquals(10L, rounds[0].dealerId)
        assertEquals(20L, rounds[0].nonDealerId)

        // Round 2: players[1] deals
        assertEquals(20L, rounds[1].dealerId)
        assertEquals(10L, rounds[1].nonDealerId)

        // Round 3: back to players[0]
        assertEquals(10L, rounds[2].dealerId)

        // Round 4: back to players[1]
        assertEquals(20L, rounds[3].dealerId)
    }

    // ─── Win condition ────────────────────────────────────────────────────────

    @Test
    fun `game ends when player reaches exactly 121`() {
        val alice = CribbagePlayerState(1L, "Alice", 0xFF0000)
        val bob   = CribbagePlayerState(2L, "Bob",   0x00FF00)

        val rounds = buildList {
            // Create enough rounds to reach 121 for Alice
            var total = 0
            var roundNum = 1
            while (total < 121) {
                val toAdd = minOf(30, 121 - total)
                add(CribbageRound(roundNum++, dealerId = 1L, nonDealerId = 2L).apply {
                    peggingScores[1L] = toAdd
                    peggingScores[2L] = 0
                })
                total += toAdd
            }
        }

        assertTrue(alice.getTotal(rounds) >= 121)
    }

    @Test
    fun `player below 121 does not trigger win`() {
        val player = CribbagePlayerState(1L, "Alice", 0xFF0000)
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L).apply {
            peggingScores[1L] = 5
            handScores[1L]    = 10
            cribScore         = 5
        }
        // 5 + 10 + 5 = 20 < 121
        assertTrue(player.getTotal(listOf(round)) < 121)
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `crib score 0 is valid and counts as entered`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.handScores[2L] = 10
        round.handScores[1L] = 8
        round.cribScore      = 0
        assertTrue(round.isComplete())
        assertEquals(0, round.roundTotal(1L) - 8) // dealer total = pegging(0) + hand(8) + crib(0)
    }

    @Test
    fun `hand score 0 is valid and treated as entered`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.handScores[2L] = 0  // pone entered 0
        assertTrue(round.isPoneHandEntered())
    }

    @Test
    fun `pegging can be incremented multiple times`() {
        val round = CribbageRound(1, dealerId = 1L, nonDealerId = 2L)
        round.peggingScores[1L] = 0
        repeat(10) { round.peggingScores[1L] = (round.peggingScores[1L] ?: 0) + 1 }
        assertEquals(10, round.peggingScores[1L])
    }

    @Test
    fun `getTotal accumulates correctly over many rounds`() {
        val alice = CribbagePlayerState(1L, "Alice", 0xFF0000)

        val rounds = (1..5).map { i ->
            CribbageRound(i, dealerId = 1L, nonDealerId = 2L).apply {
                peggingScores[1L] = 2   // 2 per round
                handScores[1L]    = 10  // 10 per round
                cribScore         = 4   // 4 per round (Alice always dealer here)
            }
        }
        // 5 rounds × (2 + 10 + 4) = 5 × 16 = 80
        assertEquals(80, alice.getTotal(rounds))
    }

    @Test
    fun `pone total never includes crib even across many rounds`() {
        val bob = CribbagePlayerState(2L, "Bob", 0x00FF00)

        val rounds = (1..3).map { i ->
            CribbageRound(i, dealerId = 1L, nonDealerId = 2L).apply {
                peggingScores[2L] = 3
                handScores[2L]    = 9
                cribScore         = 20  // large crib should never appear in bob's total
            }
        }
        // Bob pone: 3 rounds × (3 + 9) = 36; crib (20×3=60) excluded
        assertEquals(36, bob.getTotal(rounds))
    }
}
