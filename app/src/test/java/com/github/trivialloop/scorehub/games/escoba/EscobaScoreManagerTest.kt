package com.github.trivialloop.scorehub.games.escoba

import org.junit.Test
import org.junit.Assert.*

class EscobaScoreManagerTest {

    // ─── EscobaRound.isComplete ───────────────────────────────────────────────

    @Test
    fun `isComplete returns false when no hand scores entered`() {
        val round = EscobaRound(1)
        assertFalse(round.isComplete(listOf(1L, 2L)))
    }

    @Test
    fun `isComplete returns false when only one hand score entered`() {
        val round = EscobaRound(1)
        round.handScores[1L] = 5
        assertFalse(round.isComplete(listOf(1L, 2L)))
    }

    @Test
    fun `isComplete returns true when all hand scores entered`() {
        val round = EscobaRound(1)
        round.handScores[1L] = 5
        round.handScores[2L] = 3
        assertTrue(round.isComplete(listOf(1L, 2L)))
    }

    @Test
    fun `isComplete returns true when hand score is zero`() {
        val round = EscobaRound(1)
        round.handScores[1L] = 0
        round.handScores[2L] = 0
        assertTrue(round.isComplete(listOf(1L, 2L)))
    }

    @Test
    fun `isComplete with three players requires all three hand scores`() {
        val round = EscobaRound(1)
        round.handScores[1L] = 4
        round.handScores[2L] = 6
        assertFalse(round.isComplete(listOf(1L, 2L, 3L)))
        round.handScores[3L] = 2
        assertTrue(round.isComplete(listOf(1L, 2L, 3L)))
    }

    // ─── EscobaRound.isInPlayEditable ─────────────────────────────────────────

    @Test
    fun `isInPlayEditable returns true when no hand scores entered`() {
        val round = EscobaRound(1)
        assertTrue(round.isInPlayEditable())
    }

    @Test
    fun `isInPlayEditable returns false when any hand score is entered`() {
        val round = EscobaRound(1)
        round.handScores[1L] = 5
        assertFalse(round.isInPlayEditable())
    }

    @Test
    fun `isInPlayEditable returns false when all hand scores entered`() {
        val round = EscobaRound(1)
        round.handScores[1L] = 5
        round.handScores[2L] = 3
        assertFalse(round.isInPlayEditable())
    }

    // ─── EscobaRound.hasInPlayActivity ────────────────────────────────────────

    @Test
    fun `hasInPlayActivity returns false when in play map is empty`() {
        val round = EscobaRound(1)
        assertFalse(round.hasInPlayActivity())
    }

    @Test
    fun `hasInPlayActivity returns false when all in play scores are 0`() {
        val round = EscobaRound(1)
        round.inPlayScores[1L] = 0
        round.inPlayScores[2L] = 0
        assertFalse(round.hasInPlayActivity())
    }

    @Test
    fun `hasInPlayActivity returns true when one player has in play score`() {
        val round = EscobaRound(1)
        round.inPlayScores[1L] = 3
        round.inPlayScores[2L] = 0
        assertTrue(round.hasInPlayActivity())
    }

    @Test
    fun `hasInPlayActivity returns true when all players have in play scores`() {
        val round = EscobaRound(1)
        round.inPlayScores[1L] = 2
        round.inPlayScores[2L] = 1
        assertTrue(round.hasInPlayActivity())
    }

    // ─── EscobaRound.roundTotal ───────────────────────────────────────────────

    @Test
    fun `roundTotal returns 0 for fresh round`() {
        val round = EscobaRound(1)
        assertEquals(0, round.roundTotal(1L))
    }

    @Test
    fun `roundTotal sums in play and hand scores`() {
        val round = EscobaRound(1)
        round.inPlayScores[1L] = 4
        round.handScores[1L] = 7
        assertEquals(11, round.roundTotal(1L))
    }

    @Test
    fun `roundTotal returns only in play when hand score is null`() {
        val round = EscobaRound(1)
        round.inPlayScores[1L] = 6
        assertEquals(6, round.roundTotal(1L))
    }

    @Test
    fun `roundTotal returns only hand when in play is 0`() {
        val round = EscobaRound(1)
        round.inPlayScores[1L] = 0
        round.handScores[1L] = 9
        assertEquals(9, round.roundTotal(1L))
    }

    @Test
    fun `roundTotal is independent per player`() {
        val round = EscobaRound(1)
        round.inPlayScores[1L] = 3
        round.handScores[1L] = 5
        round.inPlayScores[2L] = 1
        round.handScores[2L] = 8
        assertEquals(8, round.roundTotal(1L))
        assertEquals(9, round.roundTotal(2L))
    }

    @Test
    fun `roundTotal returns 0 for player not in the round`() {
        val round = EscobaRound(1)
        round.inPlayScores[1L] = 5
        round.handScores[1L] = 3
        assertEquals(0, round.roundTotal(99L))
    }

    // ─── EscobaPlayerState.getTotal ───────────────────────────────────────────

    @Test
    fun `getTotal returns 0 when no rounds`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums roundTotal across all rounds`() {
        val alice = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val round1 = EscobaRound(1).apply {
            inPlayScores[1L] = 2
            handScores[1L] = 5
        }
        val round2 = EscobaRound(2).apply {
            inPlayScores[1L] = 1
            handScores[1L] = 8
        }

        // round1: 2+5=7 | round2: 1+8=9 | total: 16
        assertEquals(16, alice.getTotal(listOf(round1, round2)))
    }

    @Test
    fun `getTotal with only in play scores`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = (1..3).map { i ->
            EscobaRound(i).apply { inPlayScores[1L] = 4 }
        }
        assertEquals(12, player.getTotal(rounds))
    }

    @Test
    fun `getTotal with only hand scores`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = (1..3).map { i ->
            EscobaRound(i).apply { handScores[1L] = 6 }
        }
        assertEquals(18, player.getTotal(rounds))
    }

    @Test
    fun `getTotal only counts scores for the correct player`() {
        val alice = EscobaPlayerState(1L, "Alice", 0xFF0000)
        val bob   = EscobaPlayerState(2L, "Bob",   0x00FF00)

        val round = EscobaRound(1).apply {
            inPlayScores[1L] = 3; handScores[1L] = 7
            inPlayScores[2L] = 5; handScores[2L] = 4
        }

        assertEquals(10, alice.getTotal(listOf(round)))
        assertEquals(9,  bob.getTotal(listOf(round)))
    }

    @Test
    fun `getTotal with zero scores returns 0`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = listOf(
            EscobaRound(1).apply { inPlayScores[1L] = 0; handScores[1L] = 0 },
            EscobaRound(2).apply { inPlayScores[1L] = 0; handScores[1L] = 0 }
        )
        assertEquals(0, player.getTotal(rounds))
    }

    @Test
    fun `getTotal accumulates correctly over many rounds`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = (1..5).map { i ->
            EscobaRound(i).apply {
                inPlayScores[1L] = 2
                handScores[1L] = 4
            }
        }
        // 5 × (2+4) = 30
        assertEquals(30, player.getTotal(rounds))
    }

    // ─── Game-over threshold ──────────────────────────────────────────────────

    @Test
    fun `total below 21 does not trigger end of game`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = (1..4).map { i ->
            EscobaRound(i).apply { inPlayScores[1L] = 0; handScores[1L] = 5 }
        }
        // 4 × 5 = 20, still below 21
        assertEquals(20, player.getTotal(rounds))
        assertFalse(player.getTotal(rounds) >= 21)
    }

    @Test
    fun `total equal to 21 triggers end of game`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = listOf(
            EscobaRound(1).apply { inPlayScores[1L] = 1; handScores[1L] = 9 },
            EscobaRound(2).apply { inPlayScores[1L] = 2; handScores[1L] = 9 }
        )
        // (1+9) + (2+9) = 10 + 11 = 21
        assertEquals(21, player.getTotal(rounds))
        assertTrue(player.getTotal(rounds) >= 21)
    }

    @Test
    fun `total above 21 triggers end of game`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = listOf(
            EscobaRound(1).apply { inPlayScores[1L] = 5; handScores[1L] = 10 },
            EscobaRound(2).apply { inPlayScores[1L] = 3; handScores[1L] = 10 }
        )
        // (5+10) + (3+10) = 15 + 13 = 28
        assertEquals(28, player.getTotal(rounds))
        assertTrue(player.getTotal(rounds) >= 21)
    }

    // ─── Winner determination ─────────────────────────────────────────────────

    @Test
    fun `player with highest total wins`() {
        val alice = EscobaPlayerState(1L, "Alice", 0xFF0000)
        val bob   = EscobaPlayerState(2L, "Bob",   0x00FF00)

        val rounds = listOf(
            EscobaRound(1).apply {
                inPlayScores[1L] = 2; handScores[1L] = 10
                inPlayScores[2L] = 1; handScores[2L] = 7
            },
            EscobaRound(2).apply {
                inPlayScores[1L] = 0; handScores[1L] = 8
                inPlayScores[2L] = 3; handScores[2L] = 12
            }
        )

        // Alice: (2+10)+(0+8) = 20 | Bob: (1+7)+(3+12) = 23
        assertEquals(20, alice.getTotal(rounds))
        assertEquals(23, bob.getTotal(rounds))

        val totals = mapOf(alice to alice.getTotal(rounds), bob to bob.getTotal(rounds))
        val maxScore = totals.values.maxOrNull()
        assertEquals(bob, totals.filter { it.value == maxScore }.keys.first())
    }

    @Test
    fun `draw when two players have the same total`() {
        val alice = EscobaPlayerState(1L, "Alice", 0xFF0000)
        val bob   = EscobaPlayerState(2L, "Bob",   0x00FF00)

        val rounds = listOf(
            EscobaRound(1).apply {
                inPlayScores[1L] = 3; handScores[1L] = 7
                inPlayScores[2L] = 3; handScores[2L] = 7
            }
        )

        assertEquals(10, alice.getTotal(rounds))
        assertEquals(10, bob.getTotal(rounds))

        val totals = mapOf(alice to alice.getTotal(rounds), bob to bob.getTotal(rounds))
        val winners = totals.filter { it.value == totals.values.maxOrNull() }.keys
        assertEquals(2, winners.size)
        assertTrue(winners.contains(alice))
        assertTrue(winners.contains(bob))
    }

    // ─── Previous round locking logic ─────────────────────────────────────────

    @Test
    fun `previous round stays editable when new round has no in play activity`() {
        val currentRound = EscobaRound(2).apply {
            inPlayScores[1L] = 0
            inPlayScores[2L] = 0
        }
        assertFalse(currentRound.hasInPlayActivity())
    }

    @Test
    fun `previous round locked once new round has in play activity`() {
        val currentRound = EscobaRound(2).apply {
            inPlayScores[1L] = 1
            inPlayScores[2L] = 0
        }
        assertTrue(currentRound.hasInPlayActivity())
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `single player round completes after one hand score`() {
        val round = EscobaRound(1)
        round.handScores[1L] = 7
        assertTrue(round.isComplete(listOf(1L)))
    }

    @Test
    fun `in play can be incremented and decremented independently per player`() {
        val round = EscobaRound(1)
        round.inPlayScores[1L] = 0
        round.inPlayScores[2L] = 0

        repeat(5) { round.inPlayScores[1L] = (round.inPlayScores[1L] ?: 0) + 1 }
        repeat(3) { round.inPlayScores[2L] = (round.inPlayScores[2L] ?: 0) + 1 }

        assertEquals(5, round.inPlayScores[1L])
        assertEquals(3, round.inPlayScores[2L])
        assertFalse(round.isComplete(listOf(1L, 2L)))
    }
}
