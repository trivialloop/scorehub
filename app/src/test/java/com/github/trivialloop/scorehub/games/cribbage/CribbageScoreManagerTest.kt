package com.github.trivialloop.scorehub.games.cribbage

import org.junit.Test
import org.junit.Assert.*

class CribbageScoreManagerTest {

    // ─── CribbageRound — initial state ────────────────────────────────────────

    @Test
    fun `pegging scores start at 0 for both players`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        assertEquals(0, round.peggingScores[1L])
        assertEquals(0, round.peggingScores[2L])
    }

    @Test
    fun `hand scores start as null for both players`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        assertNull(round.handScores[1L])
        assertNull(round.handScores[2L])
    }

    @Test
    fun `crib score starts as null`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        assertNull(round.cribScore)
    }

    // ─── State helpers ────────────────────────────────────────────────────────

    @Test
    fun `isFirstPlayerHandEntered returns false when first player hand is null`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        assertFalse(round.isFirstPlayerHandEntered())
    }

    @Test
    fun `isFirstPlayerHandEntered returns true when first player hand is set`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.handScores[1L] = 10
        assertTrue(round.isFirstPlayerHandEntered())
    }

    @Test
    fun `isDealerHandEntered returns false when dealer hand is null`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        assertFalse(round.isDealerHandEntered())
    }

    @Test
    fun `isDealerHandEntered returns true when dealer hand is set`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.handScores[2L] = 12
        assertTrue(round.isDealerHandEntered())
    }

    @Test
    fun `isComplete returns false when crib is null`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.handScores[1L] = 10
        round.handScores[2L] = 8
        assertFalse(round.isComplete())
    }

    @Test
    fun `isComplete returns true when crib is set`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.handScores[1L] = 10
        round.handScores[2L] = 8
        round.cribScore = 6
        assertTrue(round.isComplete())
    }

    // ─── Pegging lock — locked by first player's hand entry ──────────────────

    @Test
    fun `isPeggingEditable returns true before first player hand entered`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        assertTrue(round.isPeggingEditable())
    }

    @Test
    fun `isPeggingEditable returns false once first player hand entered`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.handScores[1L] = 10  // first player enters hand
        assertFalse(round.isPeggingEditable())
    }

    @Test
    fun `isPeggingEditable is not affected by dealer hand entry alone`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        // Dealer enters hand — should not lock pegging (first player hasn't entered yet)
        round.handScores[2L] = 8
        assertTrue(round.isPeggingEditable())
    }

    // ─── Previous round locking — hasPeggingActivity ─────────────────────────

    @Test
    fun `hasPeggingActivity returns false when all pegging scores are 0`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        assertFalse(round.hasPeggingActivity())
    }

    @Test
    fun `hasPeggingActivity returns true when first player has pegging score`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.peggingScores[1L] = 3
        assertTrue(round.hasPeggingActivity())
    }

    @Test
    fun `hasPeggingActivity returns true when dealer has pegging score`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.peggingScores[2L] = 1
        assertTrue(round.hasPeggingActivity())
    }

    // ─── roundTotal ───────────────────────────────────────────────────────────

    @Test
    fun `roundTotal for dealer includes pegging plus hand plus crib`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.peggingScores[2L] = 4
        round.handScores[2L]    = 12
        round.cribScore         = 6
        assertEquals(22, round.roundTotal(2L))
    }

    @Test
    fun `roundTotal for first player does not include crib`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.peggingScores[1L] = 3
        round.handScores[1L]    = 8
        round.cribScore         = 10   // crib belongs to dealer only
        assertEquals(11, round.roundTotal(1L))
    }

    @Test
    fun `roundTotal returns only pegging when hand and crib are null`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.peggingScores[2L] = 7
        assertEquals(7, round.roundTotal(2L))
    }

    @Test
    fun `roundTotal returns 0 for a fresh round`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        assertEquals(0, round.roundTotal(1L))
        assertEquals(0, round.roundTotal(2L))
    }

    @Test
    fun `roundTotal handles partial entry - only pegging and first player hand set`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.peggingScores[2L] = 5
        round.handScores[1L]    = 9   // first player entered, dealer has not
        // dealer total = pegging only
        assertEquals(5, round.roundTotal(2L))
        // first player total = pegging + hand
        assertEquals(9, round.roundTotal(1L))
    }

    // ─── CribbagePlayerState.getTotal ─────────────────────────────────────────

    @Test
    fun `getTotal returns 0 for no rounds`() {
        val player = CribbagePlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums roundTotal across all rounds`() {
        val alice = CribbagePlayerState(1L, "Alice", 0xFF0000)

        // Round 1: Alice is first player (plays first), Bob is dealer
        val round1 = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L).apply {
            peggingScores[1L] = 3
            handScores[1L]    = 8
            // crib belongs to Bob (dealerId=2L), Alice gets none
        }
        // Round 2: Bob plays first, Alice is dealer (has crib)
        val round2 = CribbageRound(2, firstPlayerId = 2L, dealerId = 1L).apply {
            peggingScores[1L] = 4
            handScores[1L]    = 10
            cribScore         = 6   // Alice is dealer in round 2
        }

        // Round 1: Alice = 3 + 8 = 11
        // Round 2: Alice = 4 + 10 + 6 = 20
        assertEquals(31, alice.getTotal(listOf(round1, round2)))
    }

    @Test
    fun `getTotal only includes crib when player is dealer`() {
        val alice = CribbagePlayerState(1L, "Alice", 0xFF0000)
        val bob   = CribbagePlayerState(2L, "Bob",   0x00FF00)

        // Alice is first player, Bob is dealer (has crib)
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L).apply {
            peggingScores[1L] = 2
            peggingScores[2L] = 3
            handScores[1L]    = 10
            handScores[2L]    = 8
            cribScore         = 7
        }

        // Alice (first player): 2 + 10 = 12 (no crib)
        assertEquals(12, alice.getTotal(listOf(round)))
        // Bob (dealer): 3 + 8 + 7 = 18
        assertEquals(18, bob.getTotal(listOf(round)))
    }

    @Test
    fun `getTotal with partial round counts only what is entered`() {
        val player = CribbagePlayerState(1L, "Alice", 0xFF0000)
        val round  = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L).apply {
            peggingScores[1L] = 5
            // hand and crib not yet entered
        }
        assertEquals(5, player.getTotal(listOf(round)))
    }

    // ─── Alternation — first player and dealer alternate each round ───────────

    @Test
    fun `first player and dealer alternate correctly across four rounds`() {
        val playerIds = longArrayOf(10L, 20L)
        val rounds = (0 until 4).map { idx ->
            val firstPlayerIndex = idx % 2
            val dealerIndex      = 1 - firstPlayerIndex
            CribbageRound(idx + 1, playerIds[firstPlayerIndex], playerIds[dealerIndex])
        }

        // Round 1: players[0] plays first, players[1] deals
        assertEquals(10L, rounds[0].firstPlayerId)
        assertEquals(20L, rounds[0].dealerId)

        // Round 2: players[1] plays first, players[0] deals
        assertEquals(20L, rounds[1].firstPlayerId)
        assertEquals(10L, rounds[1].dealerId)

        // Round 3: back to players[0] first
        assertEquals(10L, rounds[2].firstPlayerId)
        assertEquals(20L, rounds[2].dealerId)

        // Round 4: back to players[1] first
        assertEquals(20L, rounds[3].firstPlayerId)
        assertEquals(10L, rounds[3].dealerId)
    }

    @Test
    fun `crib always belongs to the dealer not the first player`() {
        val playerIds = longArrayOf(10L, 20L)
        repeat(4) { idx ->
            val firstPlayerIndex = idx % 2
            val dealerIndex      = 1 - firstPlayerIndex
            val round = CribbageRound(idx + 1, playerIds[firstPlayerIndex], playerIds[dealerIndex])
            round.cribScore = 5

            val dealerTotal = round.roundTotal(playerIds[dealerIndex])
            val firstTotal  = round.roundTotal(playerIds[firstPlayerIndex])

            // Crib (5) only appears in dealer's total
            assertTrue("Dealer should have crib in round ${idx + 1}", dealerTotal >= 5)
            assertEquals("First player should not have crib in round ${idx + 1}", 0, firstTotal)
        }
    }

    // ─── Win condition ────────────────────────────────────────────────────────

    @Test
    fun `game ends when player reaches exactly 121`() {
        val alice = CribbagePlayerState(1L, "Alice", 0xFF0000)
        val rounds = buildList {
            var total = 0
            var roundNum = 1
            while (total < 121) {
                val toAdd = minOf(30, 121 - total)
                add(CribbageRound(roundNum++, firstPlayerId = 1L, dealerId = 2L).apply {
                    peggingScores[1L] = toAdd
                })
                total += toAdd
            }
        }
        assertTrue(alice.getTotal(rounds) >= 121)
    }

    @Test
    fun `player below 121 does not trigger win`() {
        val player = CribbagePlayerState(1L, "Alice", 0xFF0000)
        val round  = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L).apply {
            peggingScores[1L] = 5
            handScores[1L]    = 10
            // crib belongs to dealer (2L), not Alice
        }
        assertTrue(player.getTotal(listOf(round)) < 121)
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `crib score 0 is valid and round is complete`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.handScores[1L] = 10
        round.handScores[2L] = 8
        round.cribScore      = 0
        assertTrue(round.isComplete())
        assertEquals(8, round.roundTotal(2L))  // dealer: 0 pegging + 8 hand + 0 crib
    }

    @Test
    fun `hand score 0 for first player is valid and treated as entered`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.handScores[1L] = 0
        assertTrue(round.isFirstPlayerHandEntered())
        assertFalse(round.isPeggingEditable())  // first player entered → pegging locked
    }

    @Test
    fun `entering first player hand locks pegging immediately`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        assertTrue(round.isPeggingEditable())
        round.handScores[1L] = 5
        assertFalse(round.isPeggingEditable())
    }

    @Test
    fun `entering dealer hand alone does not lock pegging`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        round.handScores[2L] = 8  // dealer enters (unusual, but model allows it)
        assertTrue(round.isPeggingEditable())  // still editable
    }

    @Test
    fun `pegging can be incremented independently for each player`() {
        val round = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L)
        repeat(5) { round.peggingScores[1L] = (round.peggingScores[1L] ?: 0) + 1 }
        repeat(3) { round.peggingScores[2L] = (round.peggingScores[2L] ?: 0) + 1 }
        assertEquals(5, round.peggingScores[1L])
        assertEquals(3, round.peggingScores[2L])
    }

    @Test
    fun `getTotal accumulates correctly over many rounds`() {
        val alice = CribbagePlayerState(1L, "Alice", 0xFF0000)

        // Alice is first player every round for simplicity
        val rounds = (1..5).map { i ->
            CribbageRound(i, firstPlayerId = 1L, dealerId = 2L).apply {
                peggingScores[1L] = 2
                handScores[1L]    = 10
                // Alice is not dealer, so no crib
            }
        }
        // 5 × (2 + 10) = 60
        assertEquals(60, alice.getTotal(rounds))
    }

    @Test
    fun `dealer total accumulates crib across rounds where they deal`() {
        val bob = CribbagePlayerState(2L, "Bob", 0x00FF00)

        // Round 1: Alice first player (1L), Bob dealer (2L) — Bob has crib
        val round1 = CribbageRound(1, firstPlayerId = 1L, dealerId = 2L).apply {
            peggingScores[2L] = 3
            handScores[2L]    = 9
            cribScore         = 5   // Bob's crib
        }
        // Round 2: Bob first player (2L), Alice dealer (1L) — Bob has NO crib
        val round2 = CribbageRound(2, firstPlayerId = 2L, dealerId = 1L).apply {
            peggingScores[2L] = 4
            handScores[2L]    = 7
            cribScore         = 8   // Alice's crib, not Bob's
        }

        // Round 1: Bob = 3 + 9 + 5 = 17
        // Round 2: Bob = 4 + 7 = 11 (crib 8 goes to Alice)
        assertEquals(28, bob.getTotal(listOf(round1, round2)))
    }

    @Test
    fun `first player total never includes crib across multiple rounds`() {
        val alice = CribbagePlayerState(1L, "Alice", 0xFF0000)

        // 3 rounds where Alice is always first player (never dealer)
        val rounds = (1..3).map { i ->
            CribbageRound(i, firstPlayerId = 1L, dealerId = 2L).apply {
                peggingScores[1L] = 3
                handScores[1L]    = 9
                cribScore         = 20   // large crib — must NOT appear in Alice's total
            }
        }
        // Alice: 3 × (3 + 9) = 36; crib excluded
        assertEquals(36, alice.getTotal(rounds))
    }
}
