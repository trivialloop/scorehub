package com.github.trivialloop.scorehub.games.flip7

import org.junit.Test
import org.junit.Assert.*

class Flip7ScoreManagerTest {

    // ─── Flip7Turn.score — choseZero ─────────────────────────────────────────

    @Test
    fun `choseZero turn scores 0`() {
        val turn = Flip7Turn(1, 1L, choseZero = true, isComplete = true)
        assertEquals(0, turn.score)
    }

    @Test
    fun `incomplete turn scores 0`() {
        val turn = Flip7Turn(1, 1L, selectedCards = listOf(1, 2, 3))
        assertEquals(0, turn.score)
    }

    // ─── Flip7Turn.score — basic card sum ─────────────────────────────────────

    @Test
    fun `score equals sum of selected cards when less than 7`() {
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(1, 2, 3, 4),   // sum = 10
            isComplete    = true
        )
        assertEquals(10, turn.score)
    }

    @Test
    fun `score with zero card selected`() {
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(0, 5, 7),   // sum = 12
            isComplete    = true
        )
        assertEquals(12, turn.score)
    }

    // ─── Flip7Turn.score — 7-card bonus ───────────────────────────────────────

    @Test
    fun `score adds 15 when exactly 7 cards selected`() {
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(0, 1, 2, 3, 4, 5, 6),   // sum=21 + 15 = 36
            isComplete    = true
        )
        assertEquals(36, turn.score)
    }

    @Test
    fun `no bonus when fewer than 7 cards`() {
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(0, 1, 2, 3, 4, 5),   // sum=15, no bonus
            isComplete    = true
        )
        assertEquals(15, turn.score)
    }

    // ─── Flip7Turn.score — x2 bonus ───────────────────────────────────────────

    @Test
    fun `x2 bonus doubles card sum (fewer than 7 cards)`() {
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(3, 4),   // sum=7, x2 → 14
            bonusX2       = true,
            isComplete    = true
        )
        assertEquals(14, turn.score)
    }

    @Test
    fun `x2 bonus applied before 7-card bonus`() {
        // sum=21, x2=42, +15 → 57
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(0, 1, 2, 3, 4, 5, 6),
            bonusX2       = true,
            isComplete    = true
        )
        assertEquals(57, turn.score)
    }

    // ─── Flip7Turn.score — plus bonuses ───────────────────────────────────────

    @Test
    fun `plus bonuses added after x2`() {
        // sum=10, x2 → 20, +2+4 = 26
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(4, 6),
            bonusX2       = true,
            bonusPlus     = listOf(2, 4),
            isComplete    = true
        )
        assertEquals(26, turn.score)
    }

    @Test
    fun `plus bonuses added without x2`() {
        // sum=5, +8+10 = 23
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(2, 3),
            bonusPlus     = listOf(8, 10),
            isComplete    = true
        )
        assertEquals(23, turn.score)
    }

    @Test
    fun `all bonuses combined with 7-card flip`() {
        // sum=28 (0+1+2+3+4+7+11), x2=56, +15=71, +2+4=77
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(0, 1, 2, 3, 4, 7, 11),
            bonusX2       = true,
            bonusPlus     = listOf(2, 4),
            isComplete    = true
        )
        assertEquals(77, turn.score)
    }

    // ─── Flip7Turn.score — edge cases ─────────────────────────────────────────

    @Test
    fun `empty card selection scores 0 plus bonuses`() {
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = emptyList(),
            bonusPlus     = listOf(6),
            isComplete    = true
        )
        // sum=0, no 7-card bonus, x2 not checked → 0 + 6 = 6
        assertEquals(6, turn.score)
    }

    @Test
    fun `max possible score 7 cards (6-12) x2 plus all bonuses`() {
        // cards 6+7+8+9+10+11+12 = 63, x2=126, +15=141, +2+4+6+8+8+10=38 → 179
        val turn = Flip7Turn(
            roundNumber   = 1,
            playerId      = 1L,
            selectedCards = listOf(6, 7, 8, 9, 10, 11, 12),
            bonusX2       = true,
            bonusPlus     = listOf(2, 4, 6, 8, 8, 10),
            isComplete    = true
        )
        assertEquals(179, turn.score)
    }

    // ─── Flip7PlayerState.getTotal ────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 with no turns`() {
        val player = Flip7PlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums only completed turns for this player`() {
        val player = Flip7PlayerState(1L, "Alice", 0xFF0000)
        val turns = listOf(
            Flip7Turn(1, 1L, selectedCards = listOf(3, 4), isComplete = true),
            Flip7Turn(2, 1L, selectedCards = listOf(5, 6), isComplete = true),
            Flip7Turn(3, 1L) // not complete
        )
        // 7 + 11 = 18
        assertEquals(18, player.getTotal(turns))
    }

    @Test
    fun `getTotal excludes other players turns`() {
        val alice = Flip7PlayerState(1L, "Alice", 0xFF0000)
        val bob   = Flip7PlayerState(2L, "Bob",   0x00FF00)
        val turns = listOf(
            Flip7Turn(1, 1L, selectedCards = listOf(5), isComplete = true),   // Alice: 5
            Flip7Turn(1, 2L, selectedCards = listOf(10), isComplete = true)   // Bob: 10
        )
        assertEquals(5,  alice.getTotal(turns))
        assertEquals(10, bob.getTotal(turns))
    }

    @Test
    fun `getTotal includes zero turns`() {
        val player = Flip7PlayerState(1L, "Alice", 0xFF0000)
        val turns = listOf(
            Flip7Turn(1, 1L, selectedCards = listOf(8), isComplete = true),
            Flip7Turn(2, 1L, choseZero = true, isComplete = true)
        )
        assertEquals(8, player.getTotal(turns))
    }

    // ─── Flip7PlayerState.currentTurn ────────────────────────────────────────

    @Test
    fun `currentTurn returns null when no incomplete turn`() {
        val player = Flip7PlayerState(1L, "Alice", 0xFF0000)
        val turns = listOf(Flip7Turn(1, 1L, isComplete = true))
        assertNull(player.currentTurn(turns))
    }

    @Test
    fun `currentTurn returns last incomplete turn for player`() {
        val player = Flip7PlayerState(1L, "Alice", 0xFF0000)
        val incomplete = Flip7Turn(2, 1L)
        val turns = listOf(
            Flip7Turn(1, 1L, isComplete = true),
            incomplete
        )
        assertEquals(incomplete, player.currentTurn(turns))
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    @Test
    fun `FLIP7_CARD_VALUES contains 0 through 12`() {
        assertEquals((0..12).toList(), FLIP7_CARD_VALUES)
    }

    @Test
    fun `FLIP7_MAX_CARDS is 7`() {
        assertEquals(7, FLIP7_MAX_CARDS)
    }

    @Test
    fun `FLIP7_SCORE_LIMIT is 200`() {
        assertEquals(200, FLIP7_SCORE_LIMIT)
    }

    @Test
    fun `FLIP7_BONUS_PLUS_VALUES contains expected values`() {
        assertEquals(listOf(2, 4, 6, 8, 8, 10), FLIP7_BONUS_PLUS_VALUES)
    }
}
