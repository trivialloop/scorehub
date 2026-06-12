package com.github.trivialloop.scorehub.games.qwixx

import org.junit.Test
import org.junit.Assert.*

class QwixxScoreManagerTest {

    // ─── QwixxRowState — numbers ──────────────────────────────────────────────

    @Test
    fun `ascending row numbers go from 2 to 12`() {
        val row = QwixxRowState(QwixxColor.RED)
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), row.numbers)
    }

    @Test
    fun `descending row numbers go from 12 to 2`() {
        val row = QwixxRowState(QwixxColor.GREEN)
        assertEquals(listOf(12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2), row.numbers)
    }

    @Test
    fun `ascending last number is 12`() {
        assertEquals(12, QwixxRowState(QwixxColor.RED).lastNumber)
        assertEquals(12, QwixxRowState(QwixxColor.YELLOW).lastNumber)
    }

    @Test
    fun `descending last number is 2`() {
        assertEquals(2, QwixxRowState(QwixxColor.GREEN).lastNumber)
        assertEquals(2, QwixxRowState(QwixxColor.BLUE).lastNumber)
    }

    // ─── QwixxRowState.canCheck ───────────────────────────────────────────────

    @Test
    fun `can check first number in empty row`() {
        val row = QwixxRowState(QwixxColor.RED)
        assertTrue(row.canCheck(2))
    }

    @Test
    fun `can check any number in empty ascending row`() {
        val row = QwixxRowState(QwixxColor.RED)
        assertTrue(row.canCheck(7))
    }

    @Test
    fun `cannot check number before last checked in ascending row`() {
        val row = QwixxRowState(QwixxColor.RED)
        row.check(6)
        assertFalse(row.canCheck(4))
        assertFalse(row.canCheck(5))
    }

    @Test
    fun `can check number after last checked in ascending row`() {
        val row = QwixxRowState(QwixxColor.RED)
        row.check(6)
        assertTrue(row.canCheck(7))
        assertTrue(row.canCheck(10))
    }

    @Test
    fun `cannot check number before last checked in descending row`() {
        val row = QwixxRowState(QwixxColor.GREEN)
        row.check(8)   // sequence: 12 11 10 9 8 ... → 8 is index 4
        assertFalse(row.canCheck(12))
        assertFalse(row.canCheck(9))
    }

    @Test
    fun `can check number after last checked in descending row`() {
        val row = QwixxRowState(QwixxColor.GREEN)
        row.check(8)
        assertTrue(row.canCheck(7))
        assertTrue(row.canCheck(3))
    }

    @Test
    fun `cannot check last number without 5 other checks`() {
        val row = QwixxRowState(QwixxColor.RED)
        row.check(2); row.check(3); row.check(4); row.check(5)
        assertFalse(row.canCheck(12))
    }

    @Test
    fun `can check last number with exactly 5 other checks`() {
        val row = QwixxRowState(QwixxColor.RED)
        row.check(2); row.check(3); row.check(4); row.check(5); row.check(6)
        assertTrue(row.canCheck(12))
    }

    @Test
    fun `cannot check already checked number`() {
        val row = QwixxRowState(QwixxColor.RED)
        row.check(5)
        assertFalse(row.canCheck(5))
    }

    @Test
    fun `cannot check any number when row is locked`() {
        val row = QwixxRowState(QwixxColor.RED)
        repeat(5) { i -> row.check(i + 2) }   // check 2,3,4,5,6
        row.check(12)                           // lock
        assertTrue(row.locked)
        assertFalse(row.canCheck(7))
        assertFalse(row.canCheck(8))
    }

    // ─── QwixxRowState.check ─────────────────────────────────────────────────

    @Test
    fun `check returns true when valid`() {
        val row = QwixxRowState(QwixxColor.RED)
        assertTrue(row.check(5))
        assertTrue(row.checked.contains(5))
    }

    @Test
    fun `check returns false when invalid`() {
        val row = QwixxRowState(QwixxColor.RED)
        row.check(8)
        assertFalse(row.check(5))   // 5 is before 8 in sequence
        assertFalse(row.checked.contains(5))
    }

    @Test
    fun `checking last number sets locked to true`() {
        val row = QwixxRowState(QwixxColor.RED)
        repeat(5) { i -> row.check(i + 2) }
        assertFalse(row.locked)
        row.check(12)
        assertTrue(row.locked)
    }

    @Test
    fun `checking last number in descending row locks the row`() {
        val row = QwixxRowState(QwixxColor.GREEN)
        // Check 12, 11, 10, 9, 8 (5 checks), then check 2 (last)
        listOf(12, 11, 10, 9, 8).forEach { row.check(it) }
        row.check(2)
        assertTrue(row.locked)
    }

    // ─── QwixxRowState.score ─────────────────────────────────────────────────

    @Test
    fun `score is 0 with no checks`() {
        assertEquals(0, QwixxRowState(QwixxColor.RED).score())
    }

    @Test
    fun `score is 1 with 1 check`() {
        val row = QwixxRowState(QwixxColor.RED).apply { check(5) }
        assertEquals(1, row.score())
    }

    @Test
    fun `score is 3 with 2 checks`() {
        val row = QwixxRowState(QwixxColor.RED).apply { check(5); check(6) }
        assertEquals(3, row.score())
    }

    @Test
    fun `score is 6 with 3 checks`() {
        val row = QwixxRowState(QwixxColor.RED).apply { check(3); check(4); check(5) }
        assertEquals(6, row.score())
    }

    @Test
    fun `score follows triangular formula`() {
        val row = QwixxRowState(QwixxColor.RED)
        for (n in 2..7) row.check(n)   // 6 checks
        assertEquals(21, row.score())  // 1+2+3+4+5+6 = 21
    }

    @Test
    fun `score is 91 with all 12 checks plus lock`() {
        val row = QwixxRowState(QwixxColor.RED)
        for (n in 2..11) row.check(n)   // 10 checks
        row.check(12)                    // last number → also locks (locked = true)
        // 12 numbers + 1 lock cell = 13 boxes → 13*14/2 = 91
        assertEquals(91, row.score())
    }

    @Test
    fun `score is 78 with 12 checks when not locked`() {
        // Simulate 12 checks manually without triggering the lock
        // (edge case: locked flag false but 12 items in checked)
        val row = QwixxRowState(QwixxColor.RED)
        for (n in 2..12) row.checked.add(n)   // bypass check() to avoid setting locked
        // locked = false, so no +1
        assertEquals(78, row.score())
    }

    @Test
    fun `score adds 1 for lock cell when locked`() {
        val row = QwixxRowState(QwixxColor.RED)
        // 5 checks then check 12 to lock
        for (n in 2..6) row.check(n)   // 5 checks
        row.check(12)                  // 6th check + lock
        // 6 numbers + 1 lock = 7 boxes → 7*8/2 = 28
        assertEquals(28, row.score())
    }

    // ─── QwixxPlayerState.totalScore ─────────────────────────────────────────

    @Test
    fun `total score is 0 with nothing checked`() {
        val player = QwixxPlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.totalScore())
    }

    @Test
    fun `total score sums all color rows`() {
        val player = QwixxPlayerState(1L, "Alice", 0xFF0000)
        player.rowState(QwixxColor.RED).check(5)        // 1 pt
        player.rowState(QwixxColor.YELLOW).check(3)     // 1 pt
        player.rowState(QwixxColor.YELLOW).check(4)     // → 3 pts total for yellow
        player.rowState(QwixxColor.GREEN).check(12)     // 1 pt
        player.rowState(QwixxColor.BLUE).check(10)      // 1 pt
        // red=1, yellow=3, green=1, blue=1 = 6
        assertEquals(6, player.totalScore())
    }

    @Test
    fun `each penalty deducts 5 points`() {
        val player = QwixxPlayerState(1L, "Alice", 0xFF0000)
        player.rowState(QwixxColor.RED).check(5)
        player.rowState(QwixxColor.RED).check(6)
        player.rowState(QwixxColor.RED).check(7)   // 6 pts
        player.penalties = 2                        // -10
        assertEquals(-4, player.totalScore())
    }

    @Test
    fun `total score can be negative with many penalties`() {
        val player = QwixxPlayerState(1L, "Alice", 0xFF0000)
        player.penalties = 4   // -20, no color checks
        assertEquals(-20, player.totalScore())
    }

    // ─── QwixxGameState.checkEndCondition ────────────────────────────────────

    @Test
    fun `game does not end with 3 penalties`() {
        val player = QwixxPlayerState(1L, "Alice", 0xFF0000).apply { penalties = 3 }
        val state  = QwixxGameState(listOf(player))
        assertFalse(state.checkEndCondition())
    }

    @Test
    fun `game ends when a player reaches 4 penalties`() {
        val player = QwixxPlayerState(1L, "Alice", 0xFF0000).apply { penalties = 4 }
        val state  = QwixxGameState(listOf(player))
        assertTrue(state.checkEndCondition())
    }

    @Test
    fun `game does not end with 1 locked color`() {
        val state = QwixxGameState(listOf(QwixxPlayerState(1L, "Alice", 0xFF0000)))
        state.lockState.lock(QwixxColor.RED)
        assertFalse(state.checkEndCondition())
    }

    @Test
    fun `game ends when 2 colors are locked`() {
        val state = QwixxGameState(listOf(QwixxPlayerState(1L, "Alice", 0xFF0000)))
        state.lockState.lock(QwixxColor.RED)
        state.lockState.lock(QwixxColor.YELLOW)
        assertTrue(state.checkEndCondition())
    }

    @Test
    fun `game ends when 3 colors are locked`() {
        val state = QwixxGameState(listOf(QwixxPlayerState(1L, "Alice", 0xFF0000)))
        state.lockState.lock(QwixxColor.RED)
        state.lockState.lock(QwixxColor.GREEN)
        state.lockState.lock(QwixxColor.BLUE)
        assertTrue(state.checkEndCondition())
    }

    // ─── QwixxGameState.syncLocks ────────────────────────────────────────────

    @Test
    fun `syncLocks propagates player lock to global state`() {
        val alice  = QwixxPlayerState(1L, "Alice", 0xFF0000)
        val bob    = QwixxPlayerState(2L, "Bob",   0x00FF00)
        val state  = QwixxGameState(listOf(alice, bob))

        // Alice locks red row manually
        for (n in 2..11) alice.rowState(QwixxColor.RED).check(n)
        alice.rowState(QwixxColor.RED).check(12)
        assertTrue(alice.rowState(QwixxColor.RED).locked)

        state.syncLocks()

        // Global lock should be set
        assertTrue(state.lockState.isLocked(QwixxColor.RED))
        // Bob's row should also be locked now
        assertTrue(bob.rowState(QwixxColor.RED).locked)
    }

    @Test
    fun `syncLocks does not lock colors that are not locked by any player`() {
        val alice = QwixxPlayerState(1L, "Alice", 0xFF0000)
        val state = QwixxGameState(listOf(alice))
        state.syncLocks()
        assertFalse(state.lockState.isLocked(QwixxColor.RED))
        assertFalse(state.lockState.isLocked(QwixxColor.YELLOW))
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `checking same number twice has no effect`() {
        val row = QwixxRowState(QwixxColor.RED)
        row.check(5)
        row.check(5)
        assertEquals(1, row.checked.size)
        assertEquals(1, row.score())
    }

    @Test
    fun `two players have independent row states`() {
        val alice = QwixxPlayerState(1L, "Alice", 0xFF0000)
        val bob   = QwixxPlayerState(2L, "Bob",   0x00FF00)

        alice.rowState(QwixxColor.RED).check(5)
        assertFalse(bob.rowState(QwixxColor.RED).checked.contains(5))
    }

    @Test
    fun `score is consistent with penalty and color checks`() {
        val player = QwixxPlayerState(1L, "Alice", 0xFF0000)
        // Red: 2,3,4,5,6 → 5 checks = 15 pts
        for (n in 2..6) player.rowState(QwixxColor.RED).check(n)
        // Yellow: 2 → 1 pt
        player.rowState(QwixxColor.YELLOW).check(2)
        // 1 penalty = -5
        player.penalties = 1
        // 15 + 1 - 5 = 11
        assertEquals(11, player.totalScore())
    }

    // ─── QwixxRound / QwixxRoundPhase ────────────────────────────────────────
    // A round now has only two phases:
    //   ALL           – every player (active and non-active) checks a number or passes
    //   ACTIVE_SECOND – the active player's mandatory/optional second action

    @Test
    fun `a new round starts in the ALL phase`() {
        val round = QwixxRound(roundNumber = 1, activePlayerIndex = 0)
        assertEquals(QwixxRoundPhase.ALL, round.phase)
    }

    @Test
    fun `a new round has nobody finished yet and no active check`() {
        val round = QwixxRound(roundNumber = 1, activePlayerIndex = 0)
        assertTrue(round.playersFinished.isEmpty())
        assertFalse(round.activeCheckedFirst)
    }

    @Test
    fun `a new round has no colors locked this turn`() {
        val round = QwixxRound(roundNumber = 1, activePlayerIndex = 0)
        assertTrue(round.colorsLockedThisTurn.isEmpty())
    }

    @Test
    fun `QwixxRoundPhase only has ALL and ACTIVE_SECOND`() {
        assertEquals(
            listOf(QwixxRoundPhase.ALL, QwixxRoundPhase.ACTIVE_SECOND),
            QwixxRoundPhase.entries.toList()
        )
    }

    @Test
    fun `playersFinished can track any player including the active one`() {
        val round = QwixxRound(roundNumber = 1, activePlayerIndex = 0)
        round.playersFinished.add(round.activePlayerIndex)
        round.playersFinished.add(1)
        assertTrue(round.playersFinished.contains(0))
        assertTrue(round.playersFinished.contains(1))
    }
}
