package com.github.trivialloop.scorehub.games.cactus

import org.junit.Test
import org.junit.Assert.*

class CactusScoreManagerTest {

    // ─── allScoresEntered / isComplete ────────────────────────────────────────

    @Test
    fun `allScoresEntered returns false when partial`() {
        val round = CactusRound(1).apply { rawScores[1L] = 5 }
        assertFalse(round.allScoresEntered(listOf(1L, 2L)))
    }

    @Test
    fun `allScoresEntered returns true when all entered`() {
        val round = CactusRound(1).apply { rawScores[1L] = 5; rawScores[2L] = 10 }
        assertTrue(round.allScoresEntered(listOf(1L, 2L)))
    }

    @Test
    fun `isComplete returns false before computePoints`() {
        val round = CactusRound(1).apply {
            finisherId = 1L; rawScores[1L] = 3; rawScores[2L] = 8
        }
        assertFalse(round.isComplete(listOf(1L, 2L)))
    }

    @Test
    fun `isComplete returns true after computePoints`() {
        val round = CactusRound(1).apply {
            finisherId = 1L; rawScores[1L] = 3; rawScores[2L] = 8
        }
        round.computePoints(listOf(1L, 2L))
        assertTrue(round.isComplete(listOf(1L, 2L)))
    }

    // ─── computePoints — finisher wins ────────────────────────────────────────

    @Test
    fun `finisher wins - score le 5 and strictly lowest - gets 1 pt`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 4; rawScores[2L] = 7; rawScores[3L] = 10
        }
        round.computePoints(listOf(1L, 2L, 3L))
        assertEquals(1, round.points[1L])
    }

    @Test
    fun `finisher wins with score exactly 5`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 5; rawScores[2L] = 8
        }
        round.computePoints(listOf(1L, 2L))
        assertEquals(1, round.points[1L])
    }

    @Test
    fun `finisher loses - score le 5 but tied - gets 0 pt`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 5; rawScores[2L] = 5
        }
        round.computePoints(listOf(1L, 2L))
        assertEquals(0, round.points[1L])
    }

    @Test
    fun `finisher loses - score gt 5 even if lowest - gets 0 pt`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 6; rawScores[2L] = 10
        }
        round.computePoints(listOf(1L, 2L))
        assertEquals(0, round.points[1L])
    }

    @Test
    fun `finisher loses - higher than others - gets 0 pt`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 15; rawScores[2L] = 3
        }
        round.computePoints(listOf(1L, 2L))
        assertEquals(0, round.points[1L])
    }

    // ─── computePoints — non-finishers when finisher WINS ─────────────────────

    @Test
    fun `non-finishers all get 0 when finisher wins`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 3; rawScores[2L] = 8; rawScores[3L] = 12
        }
        round.computePoints(listOf(1L, 2L, 3L))
        assertEquals(1, round.points[1L])
        assertEquals(0, round.points[2L])
        assertEquals(0, round.points[3L])
    }

    // ─── computePoints — non-finishers when finisher LOSES ────────────────────
    // Key rule: min is computed among NON-FINISHERS only.

    @Test
    fun `non-finisher with min score among non-finishers gets 1 pt`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 8; rawScores[2L] = 12; rawScores[3L] = 5
        }
        round.computePoints(listOf(1L, 2L, 3L))
        assertEquals(0, round.points[1L])
        assertEquals(0, round.points[2L])
        assertEquals(1, round.points[3L])
    }

    @Test
    fun `non-finisher gets 1 pt when finisher has lowest global raw but loses`() {
        // Finisher raw=8 (>5, loses), is also global min.
        // Min among non-finishers = 10 → player 2 gets 1 pt.
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 8; rawScores[2L] = 10; rawScores[3L] = 20
        }
        round.computePoints(listOf(1L, 2L, 3L))
        assertEquals(0, round.points[1L])
        assertEquals(1, round.points[2L])
        assertEquals(0, round.points[3L])
    }

    @Test
    fun `non-finishers tied for min among non-finishers both get 1 pt`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 10; rawScores[2L] = 4; rawScores[3L] = 4
        }
        round.computePoints(listOf(1L, 2L, 3L))
        assertEquals(0, round.points[1L])
        assertEquals(1, round.points[2L])
        assertEquals(1, round.points[3L])
    }

    @Test
    fun `non-finisher with higher score gets 0 pt when finisher loses`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 15; rawScores[2L] = 3; rawScores[3L] = 20
        }
        round.computePoints(listOf(1L, 2L, 3L))
        assertEquals(0, round.points[1L])
        assertEquals(1, round.points[2L])
        assertEquals(0, round.points[3L])
    }

    @Test
    fun `two player game finisher loses - other player gets 1 pt`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 20; rawScores[2L] = 5
        }
        round.computePoints(listOf(1L, 2L))
        assertEquals(0, round.points[1L])
        assertEquals(1, round.points[2L])
    }

    @Test
    fun `two player game finisher loses gt 5 - other gets 1 pt even if finisher has lower raw`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 8; rawScores[2L] = 15
        }
        round.computePoints(listOf(1L, 2L))
        assertEquals(0, round.points[1L])
        assertEquals(1, round.points[2L])
    }

    @Test
    fun `computePoints does nothing when not all scores entered`() {
        val round = CactusRound(1).apply {
            finisherId = 1L; rawScores[1L] = 5
        }
        round.computePoints(listOf(1L, 2L))
        assertTrue(round.points.isEmpty())
    }

    // ─── getTotal ─────────────────────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 when no rounds`() {
        assertEquals(0, CactusPlayerState(1L, "Alice", 0).getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums points across rounds`() {
        val player = CactusPlayerState(1L, "Alice", 0)
        val rounds = listOf(
            CactusRound(1).apply { points[1L] = 1 },
            CactusRound(2).apply { points[1L] = 0 },
            CactusRound(3).apply { points[1L] = 1 }
        )
        assertEquals(2, player.getTotal(rounds))
    }

    // ─── getCellColor — finisher ───────────────────────────────────────────────

    @Test
    fun `getCellColor finisher 1 pt returns GREEN`() {
        val round = CactusRound(1).apply {
            finisherId = 1L; rawScores[1L] = 3; rawScores[2L] = 8
        }
        round.computePoints(listOf(1L, 2L))
        assertEquals(CactusCellColor.GREEN, round.getCellColor(1L, listOf(1L, 2L)))
    }

    @Test
    fun `getCellColor finisher 0 pt returns RED`() {
        val round = CactusRound(1).apply {
            finisherId = 1L; rawScores[1L] = 10; rawScores[2L] = 3
        }
        round.computePoints(listOf(1L, 2L))
        assertEquals(CactusCellColor.RED, round.getCellColor(1L, listOf(1L, 2L)))
    }

    @Test
    fun `getCellColor finisher gt 5 returns RED`() {
        val round = CactusRound(1).apply {
            finisherId = 1L; rawScores[1L] = 6; rawScores[2L] = 20
        }
        round.computePoints(listOf(1L, 2L))
        assertEquals(CactusCellColor.RED, round.getCellColor(1L, listOf(1L, 2L)))
    }

    // ─── getCellColor — non-finisher (based on global raw min/max) ────────────

    @Test
    fun `getCellColor non-finisher with global min returns GREEN`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 10; rawScores[2L] = 3; rawScores[3L] = 7
        }
        round.computePoints(listOf(1L, 2L, 3L))
        assertEquals(CactusCellColor.GREEN, round.getCellColor(2L, listOf(1L, 2L, 3L)))
    }

    @Test
    fun `getCellColor non-finisher with global max returns RED`() {
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 5; rawScores[2L] = 3; rawScores[3L] = 30
        }
        round.computePoints(listOf(1L, 2L, 3L))
        assertEquals(CactusCellColor.RED, round.getCellColor(3L, listOf(1L, 2L, 3L)))
    }

    @Test
    fun `getCellColor non-finisher with middle raw among 4 players returns DEFAULT`() {
        // Finisher=1L (raw=20, loses), player2=5 (global min), player3=10 (middle), player4=30 (global max)
        // Player3 is neither global min nor global max → DEFAULT
        val round = CactusRound(1).apply {
            finisherId = 1L
            rawScores[1L] = 20; rawScores[2L] = 5; rawScores[3L] = 10; rawScores[4L] = 30
        }
        round.computePoints(listOf(1L, 2L, 3L, 4L))
        assertEquals(CactusCellColor.DEFAULT, round.getCellColor(3L, listOf(1L, 2L, 3L, 4L)))
    }

    @Test
    fun `getCellColor returns DEFAULT before all scores entered`() {
        val round = CactusRound(1).apply { rawScores[1L] = 5 }
        assertEquals(CactusCellColor.DEFAULT, round.getCellColor(1L, listOf(1L, 2L)))
    }
}