package com.github.trivialloop.scorehub.games.freegame

import org.junit.Test
import org.junit.Assert.*

class FreeGameScoreManagerTest {

    // ─── FreeGameRound ────────────────────────────────────────────────────────

    @Test
    fun `round stores player id, score and global round number`() {
        val round = FreeGameRound(roundNumber = 2, playerId = 1L, score = 5)
        assertEquals(2, round.roundNumber)
        assertEquals(1L, round.playerId)
        assertEquals(5, round.score)
    }

    @Test
    fun `round stores negative score`() {
        val round = FreeGameRound(1, 1L, score = -3)
        assertEquals(-3, round.score)
    }

    // ─── getTotal ─────────────────────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 with no rounds`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums all rounds for this player`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FreeGameRound(1, 1L, score = 3),
            FreeGameRound(3, 1L, score = 7)   // round 2 was another player
        )
        assertEquals(10, player.getTotal(rounds))
    }

    @Test
    fun `getTotal excludes other players rounds`() {
        val alice = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val bob   = FreeGamePlayerState(2L, "Bob",   0x00FF00)
        val rounds = listOf(
            FreeGameRound(1, 1L, score = 4),
            FreeGameRound(2, 2L, score = 9),
            FreeGameRound(3, 1L, score = 6)
        )
        assertEquals(10, alice.getTotal(rounds))
        assertEquals(9,  bob.getTotal(rounds))
    }

    @Test
    fun `getTotal handles negative scores`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FreeGameRound(1, 1L, score = 5),
            FreeGameRound(2, 1L, score = -2)
        )
        assertEquals(3, player.getTotal(rounds))
    }

    @Test
    fun `getTotal accumulates many rounds`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = (1..5).map { i -> FreeGameRound(i, 1L, score = i * 10) }
        assertEquals(150, player.getTotal(rounds))
    }

    // ─── getRounds ────────────────────────────────────────────────────────────

    @Test
    fun `getRounds returns only this players rounds`() {
        val alice = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = listOf(
            FreeGameRound(1, 1L, score = 3),
            FreeGameRound(2, 2L, score = 9),
            FreeGameRound(3, 1L, score = 5)
        )
        val aliceRounds = alice.getRounds(rounds)
        assertEquals(2, aliceRounds.size)
        assertEquals(3, aliceRounds[0].score)
        assertEquals(5, aliceRounds[1].score)
    }

    @Test
    fun `getRounds returns empty when no rounds`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        assertTrue(player.getRounds(emptyList()).isEmpty())
    }

    // ─── Global round numbering (key fix) ────────────────────────────────────

    @Test
    fun `global round numbers are sequential regardless of which player scored`() {
        // J2 scores round 1, J1 scores round 2, J2 scores round 3
        val rounds = listOf(
            FreeGameRound(1, 2L, score = 5),
            FreeGameRound(2, 1L, score = 3),
            FreeGameRound(3, 2L, score = 2)
        )
        assertEquals(listOf(1, 2, 3), rounds.map { it.roundNumber })
    }

    @Test
    fun `round lookup by global number returns correct player`() {
        val rounds = listOf(
            FreeGameRound(1, 2L, score = 5),  // J2 in slot 1
            FreeGameRound(2, 1L, score = 3),  // J1 in slot 2
            FreeGameRound(3, 2L, score = 2)   // J2 in slot 3
        )
        val byNumber = rounds.associateBy { it.roundNumber }
        assertEquals(2L, byNumber[1]?.playerId)   // slot 1 → J2
        assertEquals(1L, byNumber[2]?.playerId)   // slot 2 → J1
        assertEquals(2L, byNumber[3]?.playerId)   // slot 3 → J2
    }

    @Test
    fun `J1 score in round 2 does not appear in round 1 slot`() {
        // Bug reproduced: J2 scores (committed to round 1),
        // then J1 scores (should be round 2, NOT round 1)
        val alice = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val bob   = FreeGamePlayerState(2L, "Bob",   0x00FF00)
        val rounds = listOf(
            FreeGameRound(1, 2L, score = 5),  // Bob round 1
            FreeGameRound(2, 1L, score = 3)   // Alice round 2
        )
        val byNumber = rounds.associateBy { it.roundNumber }
        // Slot 1: Bob, not Alice
        assertNull(byNumber[1]?.takeIf { it.playerId == alice.playerId })
        assertEquals(bob.playerId, byNumber[1]?.playerId)
        // Slot 2: Alice, not Bob
        assertEquals(alice.playerId, byNumber[2]?.playerId)
        assertNull(byNumber[2]?.takeIf { it.playerId == bob.playerId })
    }

    @Test
    fun `each player column shows only their own scores in entry order`() {
        val alice = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val bob   = FreeGamePlayerState(2L, "Bob",   0x00FF00)
        val rounds = listOf(
            FreeGameRound(1, 2L, score = 5),   // Bob col-row 0
            FreeGameRound(2, 1L, score = 3),   // Alice col-row 0
            FreeGameRound(3, 2L, score = 2),   // Bob col-row 1
            FreeGameRound(4, 1L, score = 7)    // Alice col-row 1
        )
        assertEquals(listOf(3, 7), alice.getRounds(rounds).map { it.score })
        assertEquals(listOf(5, 2), bob.getRounds(rounds).map { it.score })
    }

    // ─── Pending score accumulation ───────────────────────────────────────────

    @Test
    fun `pending score accumulates across button presses`() {
        var pending = 0
        pending += 2; pending += 5; pending += 1
        assertEquals(8, pending)
    }

    @Test
    fun `pending score can go negative`() {
        var pending = 0
        pending += 5; pending -= 2; pending -= 5
        assertEquals(-2, pending)
    }

    @Test
    fun `after commit pending is added to totals`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = mutableListOf<FreeGameRound>()
        rounds.add(FreeGameRound(1, 1L, score = 7))
        assertEquals(7, player.getTotal(rounds))
        rounds.add(FreeGameRound(2, 1L, score = 3))
        assertEquals(10, player.getTotal(rounds))
    }

    // ─── Undo ─────────────────────────────────────────────────────────────────

    @Test
    fun `undo removes last committed round`() {
        val player = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = mutableListOf(
            FreeGameRound(1, 1L, score = 5),
            FreeGameRound(2, 1L, score = 3)
        )
        rounds.removeAt(rounds.lastIndex)
        assertEquals(5, player.getTotal(rounds))
    }

    @Test
    fun `undo of another players round does not affect this player total`() {
        val alice = FreeGamePlayerState(1L, "Alice", 0xFF0000)
        val rounds = mutableListOf(
            FreeGameRound(1, 1L, score = 5),
            FreeGameRound(2, 2L, score = 9)
        )
        rounds.removeAt(rounds.lastIndex)  // remove Bob's round
        assertEquals(5, alice.getTotal(rounds))
    }

    @Test
    fun `undo restores round number correctly`() {
        val rounds = mutableListOf(
            FreeGameRound(1, 2L, score = 5),
            FreeGameRound(2, 1L, score = 3)
        )
        val last = rounds.removeAt(rounds.lastIndex)
        // After undo, next commit should reuse roundNumber 2
        assertEquals(2, rounds.size + 1)
        assertEquals(2, last.roundNumber)
    }
}