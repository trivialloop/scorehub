package com.github.trivialloop.scorehub.games.escoba

import org.junit.Test
import org.junit.Assert.*

class EscobaScoreManagerTest {

    // ─── EscobaRound.allScoresEntered ─────────────────────────────────────────

    @Test
    fun `allScoresEntered returns false when no scores entered`() {
        val round = EscobaRound(1)
        val playerIds = listOf(1L, 2L, 3L)

        assertFalse(round.allScoresEntered(playerIds))
    }

    @Test
    fun `allScoresEntered returns false when only some scores entered`() {
        val round = EscobaRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.scores[1L] = 5
        round.scores[2L] = 3

        assertFalse(round.allScoresEntered(playerIds))
    }

    @Test
    fun `allScoresEntered returns true when all scores entered`() {
        val round = EscobaRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.scores[1L] = 5
        round.scores[2L] = 3
        round.scores[3L] = 7

        assertTrue(round.allScoresEntered(playerIds))
    }

    @Test
    fun `allScoresEntered returns true for single player`() {
        val round = EscobaRound(1)
        round.scores[1L] = 4

        assertTrue(round.allScoresEntered(listOf(1L)))
    }

    // ─── EscobaPlayerState.getTotal ───────────────────────────────────────────

    @Test
    fun `getTotal returns 0 when no rounds`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal returns 0 when rounds have no score for this player`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)
        val round = EscobaRound(1) // scores map is empty

        assertEquals(0, player.getTotal(listOf(round)))
    }

    @Test
    fun `getTotal sums scores across rounds`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val round1 = EscobaRound(1).apply { scores[1L] = 5 }
        val round2 = EscobaRound(2).apply { scores[1L] = 3 }
        val round3 = EscobaRound(3).apply { scores[1L] = 7 }

        assertEquals(15, player.getTotal(listOf(round1, round2, round3)))
    }

    @Test
    fun `getTotal ignores rounds where player has no score`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val round1 = EscobaRound(1).apply { scores[1L] = 6 }
        val round2 = EscobaRound(2) // Alice has no score here
        val round3 = EscobaRound(3).apply { scores[1L] = 4 }

        assertEquals(10, player.getTotal(listOf(round1, round2, round3)))
    }

    @Test
    fun `getTotal with zero scores returns 0`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val round1 = EscobaRound(1).apply { scores[1L] = 0 }
        val round2 = EscobaRound(2).apply { scores[1L] = 0 }

        assertEquals(0, player.getTotal(listOf(round1, round2)))
    }

    @Test
    fun `getTotal only sums scores for the correct player`() {
        val alice = EscobaPlayerState(1L, "Alice", 0xFF0000)
        val bob   = EscobaPlayerState(2L, "Bob",   0x00FF00)

        val round1 = EscobaRound(1).apply { scores[1L] = 5; scores[2L] = 10 }
        val round2 = EscobaRound(2).apply { scores[1L] = 3; scores[2L] = 8 }

        assertEquals(8,  alice.getTotal(listOf(round1, round2)))
        assertEquals(18, bob.getTotal(listOf(round1, round2)))
    }

    // ─── Game-over threshold ──────────────────────────────────────────────────

    @Test
    fun `total below 21 does not trigger end of game`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = (1..4).map { i -> EscobaRound(i).apply { scores[1L] = 5 } }
        // 4 × 5 = 20, still below 21
        assertEquals(20, player.getTotal(rounds))
        assertFalse(player.getTotal(rounds) >= 21)
    }

    @Test
    fun `total equal to 21 triggers end of game`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = listOf(
            EscobaRound(1).apply { scores[1L] = 10 },
            EscobaRound(2).apply { scores[1L] = 11 }
        )
        assertEquals(21, player.getTotal(rounds))
        assertTrue(player.getTotal(rounds) >= 21)
    }

    @Test
    fun `total above 21 triggers end of game`() {
        val player = EscobaPlayerState(1L, "Alice", 0xFF0000)

        val rounds = listOf(
            EscobaRound(1).apply { scores[1L] = 15 },
            EscobaRound(2).apply { scores[1L] = 10 }
        )
        assertEquals(25, player.getTotal(rounds))
        assertTrue(player.getTotal(rounds) >= 21)
    }

    // ─── Winner determination ─────────────────────────────────────────────────

    @Test
    fun `player with highest total wins`() {
        val alice = EscobaPlayerState(1L, "Alice", 0xFF0000)
        val bob   = EscobaPlayerState(2L, "Bob",   0x00FF00)

        val rounds = listOf(
            EscobaRound(1).apply { scores[1L] = 12; scores[2L] = 8 },
            EscobaRound(2).apply { scores[1L] = 10; scores[2L] = 15 }
        )

        val totals = mapOf(alice to alice.getTotal(rounds), bob to bob.getTotal(rounds))
        val maxScore = totals.values.maxOrNull()

        assertEquals(22, totals[alice])
        assertEquals(23, totals[bob])
        assertEquals(23, maxScore)
        assertEquals(bob, totals.filter { it.value == maxScore }.keys.first())
    }

    @Test
    fun `draw when two players have the same total`() {
        val alice = EscobaPlayerState(1L, "Alice", 0xFF0000)
        val bob   = EscobaPlayerState(2L, "Bob",   0x00FF00)

        val rounds = listOf(
            EscobaRound(1).apply { scores[1L] = 10; scores[2L] = 10 },
            EscobaRound(2).apply { scores[1L] = 12; scores[2L] = 12 }
        )

        val totals = mapOf(alice to alice.getTotal(rounds), bob to bob.getTotal(rounds))
        val maxScore = totals.values.maxOrNull()
        val winners = totals.filter { it.value == maxScore }.keys

        assertEquals(22, totals[alice])
        assertEquals(22, totals[bob])
        assertEquals(2, winners.size)
        assertTrue(winners.contains(alice))
        assertTrue(winners.contains(bob))
    }

    // ─── Cell color logic ─────────────────────────────────────────────────────
    // These helpers mirror the color-picking logic in EscobaGameActivity
    // so the rules are verifiable without Android context.

    private fun getCellColor(score: Int, allScores: List<Int>): String {
        val min = allScores.minOrNull() ?: return "DEFAULT"
        val max = allScores.maxOrNull() ?: return "DEFAULT"
        if (min == max) return "DEFAULT"   // all scores identical → neutral
        return when (score) {
            max  -> "GREEN"
            min  -> "RED"
            else -> "DEFAULT"
        }
    }

    @Test
    fun `highest score in round gets GREEN`() {
        val scores = listOf(3, 7, 5)
        assertEquals("GREEN", getCellColor(7, scores))
    }

    @Test
    fun `lowest score in round gets RED`() {
        val scores = listOf(3, 7, 5)
        assertEquals("RED", getCellColor(3, scores))
    }

    @Test
    fun `middle score in round gets DEFAULT`() {
        val scores = listOf(3, 7, 5)
        assertEquals("DEFAULT", getCellColor(5, scores))
    }

    @Test
    fun `all scores identical returns DEFAULT for everyone`() {
        val scores = listOf(5, 5, 5)
        assertEquals("DEFAULT", getCellColor(5, scores))
    }

    @Test
    fun `two players tied for max both get GREEN`() {
        val scores = listOf(7, 7, 3)
        assertEquals("GREEN", getCellColor(7, scores))
        assertEquals("RED",   getCellColor(3, scores))
    }

    @Test
    fun `two players tied for min both get RED`() {
        val scores = listOf(3, 3, 9)
        assertEquals("RED",   getCellColor(3, scores))
        assertEquals("GREEN", getCellColor(9, scores))
    }

    @Test
    fun `two-player round: high gets GREEN, low gets RED`() {
        val scores = listOf(4, 10)
        assertEquals("GREEN", getCellColor(10, scores))
        assertEquals("RED",   getCellColor(4,  scores))
    }

    @Test
    fun `single player round: only one score returns DEFAULT`() {
        val scores = listOf(8)
        // min == max → all same → neutral
        assertEquals("DEFAULT", getCellColor(8, scores))
    }

    @Test
    fun `zero score in round gets RED when others are higher`() {
        val scores = listOf(0, 5, 8)
        assertEquals("RED",     getCellColor(0, scores))
        assertEquals("DEFAULT", getCellColor(5, scores))
        assertEquals("GREEN",   getCellColor(8, scores))
    }
}
