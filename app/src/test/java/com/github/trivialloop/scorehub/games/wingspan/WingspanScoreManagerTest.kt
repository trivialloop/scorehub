package com.github.trivialloop.scorehub.games.wingspan

import org.junit.Test
import org.junit.Assert.*

class WingspanScoreManagerTest {

    @Test
    fun `getTotal returns 0 when no scores entered`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getTotal())
    }

    @Test
    fun `getTotal sums all categories`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[WingspanCategory.BIRDS] = 40
        ps.scores[WingspanCategory.BONUS_CARDS] = 10
        ps.scores[WingspanCategory.END_OF_ROUND] = 9
        ps.scores[WingspanCategory.EGGS] = 8
        ps.scores[WingspanCategory.FOOD_ON_CARDS] = 5
        ps.scores[WingspanCategory.TUCKED_CARDS] = 3
        assertEquals(75, ps.getTotal())
    }

    @Test
    fun `getTotal ignores null categories`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[WingspanCategory.BIRDS] = 30
        // All others null -> treated as 0
        assertEquals(30, ps.getTotal())
    }

    @Test
    fun `isComplete returns false when not all categories filled`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[WingspanCategory.BIRDS] = 30
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns true when all categories filled`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        WingspanCategory.entries.forEach { ps.scores[it] = 5 }
        assertTrue(ps.isComplete())
    }

    @Test
    fun `isComplete returns true with zero scores`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        WingspanCategory.entries.forEach { ps.scores[it] = 0 }
        assertTrue(ps.isComplete())
    }

    @Test
    fun `getTotal with zeros returns 0`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        WingspanCategory.entries.forEach { ps.scores[it] = 0 }
        assertEquals(0, ps.getTotal())
    }
}
