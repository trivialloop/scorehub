package com.github.trivialloop.scorehub.games.skyjo

import org.junit.Test
import org.junit.Assert.*

class SkyjoScoreManagerTest {

    // ─── SkyjoRound.allScoresEntered ──────────────────────────────────────────

    @Test
    fun `allScoresEntered returns false when no scores entered`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)

        assertFalse(round.allScoresEntered(playerIds))
    }

    @Test
    fun `allScoresEntered returns false when only some scores entered`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.scores[1L] = 5
        round.scores[2L] = 3

        assertFalse(round.allScoresEntered(playerIds))
    }

    @Test
    fun `allScoresEntered returns true when all scores entered`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.scores[1L] = 5
        round.scores[2L] = 3
        round.scores[3L] = 8

        assertTrue(round.allScoresEntered(playerIds))
    }

    // ─── SkyjoRound.isComplete ────────────────────────────────────────────────

    @Test
    fun `isComplete returns false when final scores not yet computed`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.scores[1L] = 4
        round.scores[2L] = 7
        // finalScores not filled yet

        assertFalse(round.isComplete(playerIds))
    }

    @Test
    fun `isComplete returns true when all final scores are set`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.scores[1L] = 4
        round.scores[2L] = 7
        round.finisherId = 1L
        round.computeFinalScores(playerIds)

        assertTrue(round.isComplete(playerIds))
    }

    // ─── computeFinalScores: no penalty ───────────────────────────────────────

    @Test
    fun `finisher has lowest score alone - no penalty applied`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.finisherId = 1L
        round.scores[1L] = 2   // sole lowest
        round.scores[2L] = 7
        round.scores[3L] = 5

        round.computeFinalScores(playerIds)

        assertEquals(2, round.finalScores[1L])   // no doubling
        assertEquals(7, round.finalScores[2L])
        assertEquals(5, round.finalScores[3L])
    }

    // ─── computeFinalScores: penalty ─────────────────────────────────────────

    @Test
    fun `finisher does not have lowest score - score is doubled`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.finisherId = 1L
        round.scores[1L] = 8   // not the lowest
        round.scores[2L] = 3   // lowest
        round.scores[3L] = 6

        round.computeFinalScores(playerIds)

        assertEquals(16, round.finalScores[1L])  // doubled
        assertEquals(3,  round.finalScores[2L])
        assertEquals(6,  round.finalScores[3L])
    }

    @Test
    fun `finisher ties for lowest score - score is doubled`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.finisherId = 1L
        round.scores[1L] = 4   // tied lowest — not sole lowest
        round.scores[2L] = 4

        round.computeFinalScores(playerIds)

        assertEquals(8, round.finalScores[1L])   // doubled (tie ≠ sole)
        assertEquals(4, round.finalScores[2L])
    }

    @Test
    fun `finisher has highest score - score is doubled`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.finisherId = 1L
        round.scores[1L] = 12
        round.scores[2L] = 5
        round.scores[3L] = 7

        round.computeFinalScores(playerIds)

        assertEquals(24, round.finalScores[1L])  // doubled
        assertEquals(5,  round.finalScores[2L])
        assertEquals(7,  round.finalScores[3L])
    }

    @Test
    fun `negative scores are doubled correctly when finisher is penalized`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.finisherId = 1L
        round.scores[1L] = -1  // not sole lowest
        round.scores[2L] = -2  // actual lowest

        round.computeFinalScores(playerIds)

        assertEquals(-2, round.finalScores[1L])  // -1 * 2 = -2
        assertEquals(-2, round.finalScores[2L])
    }

    // ─── getCellColor ─────────────────────────────────────────────────────────
    // New rules:
    //   Finisher : GREEN if strictly lowest alone, RED otherwise (background color)
    //   Others   : GREEN if score == global min, RED if score == global max, DEFAULT otherwise (text color)

    @Test
    fun `getCellColor returns DEFAULT when round is not complete`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.scores[1L] = 5
        // player 2 not entered

        assertEquals(SkyjoCellColor.DEFAULT, round.getCellColor(1L, playerIds))
    }

    @Test
    fun `getCellColor returns GREEN for sole minimum score non-finisher`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.finisherId = 3L
        round.scores[1L] = 2   // lowest
        round.scores[2L] = 8
        round.scores[3L] = 5   // finisher, not lowest

        assertEquals(SkyjoCellColor.GREEN, round.getCellColor(1L, playerIds))
    }

    @Test
    fun `getCellColor returns RED for maximum score non-finisher`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.finisherId = 3L
        round.scores[1L] = 2
        round.scores[2L] = 10  // highest
        round.scores[3L] = 5

        assertEquals(SkyjoCellColor.RED, round.getCellColor(2L, playerIds))
    }

    @Test
    fun `getCellColor returns DEFAULT for middle score non-finisher`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.finisherId = 3L
        round.scores[1L] = 2
        round.scores[2L] = 6   // middle
        round.scores[3L] = 10

        assertEquals(SkyjoCellColor.DEFAULT, round.getCellColor(2L, playerIds))
    }

    @Test
    fun `getCellColor finisher strictly lowest returns GREEN`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.finisherId = 1L
        round.scores[1L] = 1   // strictly lowest (sole)
        round.scores[2L] = 9

        assertEquals(SkyjoCellColor.GREEN, round.getCellColor(1L, playerIds))
    }

    @Test
    fun `getCellColor finisher not strictly lowest returns RED`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.finisherId = 1L
        round.scores[1L] = 9   // not lowest
        round.scores[2L] = 3

        assertEquals(SkyjoCellColor.RED, round.getCellColor(1L, playerIds))
    }

    @Test
    fun `getCellColor finisher ties for lowest returns RED`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.finisherId = 1L
        round.scores[1L] = 4
        round.scores[2L] = 4   // tie - finisher not strictly alone

        assertEquals(SkyjoCellColor.RED, round.getCellColor(1L, playerIds))
    }

    @Test
    fun `getCellColor non-finisher tied for min returns GREEN`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.finisherId = 3L
        round.scores[1L] = 4   // tied min with player 2
        round.scores[2L] = 4
        round.scores[3L] = 8

        // Both players 1 and 2 share the minimum → both green
        assertEquals(SkyjoCellColor.GREEN, round.getCellColor(1L, playerIds))
        assertEquals(SkyjoCellColor.GREEN, round.getCellColor(2L, playerIds))
    }

    @Test
    fun `getCellColor finisher is also global minimum among all - strictly alone`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.finisherId = 1L
        round.scores[1L] = 0   // sole lowest overall
        round.scores[2L] = 5
        round.scores[3L] = 8

        // Finisher is strictly lowest → GREEN background
        assertEquals(SkyjoCellColor.GREEN, round.getCellColor(1L, playerIds))
        // Player 2 is not min nor max → DEFAULT
        assertEquals(SkyjoCellColor.DEFAULT, round.getCellColor(2L, playerIds))
        // Player 3 is max → RED text
        assertEquals(SkyjoCellColor.RED, round.getCellColor(3L, playerIds))
    }

    // ─── SkyjoPlayerState.getTotal ────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 when no rounds`() {
        val player = SkyjoPlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums final scores across rounds`() {
        val player = SkyjoPlayerState(1L, "Alice", 0xFF0000)
        val playerIds = listOf(1L, 2L)

        val round1 = SkyjoRound(1).apply {
            finisherId = 2L
            scores[1L] = 5; scores[2L] = 8
            computeFinalScores(playerIds)
        }
        val round2 = SkyjoRound(2).apply {
            finisherId = 2L
            scores[1L] = 3; scores[2L] = 10
            computeFinalScores(playerIds)
        }

        assertEquals(8, player.getTotal(listOf(round1, round2)))
    }

    @Test
    fun `getTotal accounts for doubled finisher penalty`() {
        val player = SkyjoPlayerState(1L, "Alice", 0xFF0000)
        val playerIds = listOf(1L, 2L)

        val round = SkyjoRound(1).apply {
            finisherId = 1L          // Alice finishes
            scores[1L] = 6           // not the lowest
            scores[2L] = 2           // Bob has lowest
            computeFinalScores(playerIds)
        }
        // Alice's score is doubled: 6 * 2 = 12
        assertEquals(12, player.getTotal(listOf(round)))
    }

    @Test
    fun `getTotal ignores rounds with no final score for this player`() {
        val player = SkyjoPlayerState(1L, "Alice", 0xFF0000)
        val round = SkyjoRound(1) // finalScores empty

        assertEquals(0, player.getTotal(listOf(round)))
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `computeFinalScores does nothing when not all scores entered`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.finisherId = 1L
        round.scores[1L] = 5   // player 2 missing

        round.computeFinalScores(playerIds)

        assertTrue(round.finalScores.isEmpty())
    }

    @Test
    fun `two players both minimum score - finisher penalized, other stays`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L)
        round.finisherId = 1L
        round.scores[1L] = 3
        round.scores[2L] = 3   // tie

        round.computeFinalScores(playerIds)

        assertEquals(6, round.finalScores[1L])   // finisher doubled (not sole lowest)
        assertEquals(3, round.finalScores[2L])
    }

    @Test
    fun `single player round - finisher is sole lowest, no penalty`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L)
        round.finisherId = 1L
        round.scores[1L] = 7

        round.computeFinalScores(playerIds)

        assertEquals(7, round.finalScores[1L])
    }

    @Test
    fun `negative score for finisher who is sole lowest - no penalty`() {
        val round = SkyjoRound(1)
        val playerIds = listOf(1L, 2L, 3L)
        round.finisherId = 1L
        round.scores[1L] = -2  // sole lowest (min possible)
        round.scores[2L] = 0
        round.scores[3L] = 5

        round.computeFinalScores(playerIds)

        assertEquals(-2, round.finalScores[1L])  // no penalty
        assertEquals(0,  round.finalScores[2L])
        assertEquals(5,  round.finalScores[3L])
    }
}
