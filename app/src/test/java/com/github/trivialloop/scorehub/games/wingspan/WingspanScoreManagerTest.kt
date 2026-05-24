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
    fun `getTotal sums all categories including 3 bird habitats`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[WingspanCategory.BIRDS_FOREST]    = 20
        ps.scores[WingspanCategory.BIRDS_GRASSLAND] = 10
        ps.scores[WingspanCategory.BIRDS_WETLAND]   = 10
        ps.scores[WingspanCategory.BONUS_CARDS]     = 10
        ps.scores[WingspanCategory.END_OF_ROUND]    = 9
        ps.scores[WingspanCategory.EGGS]            = 8
        ps.scores[WingspanCategory.FOOD_ON_CARDS]   = 5
        ps.scores[WingspanCategory.TUCKED_CARDS]    = 3
        assertEquals(75, ps.getTotal())
    }

    @Test
    fun `getTotal ignores null categories`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[WingspanCategory.BIRDS_FOREST] = 30
        // All others null -> treated as 0
        assertEquals(30, ps.getTotal())
    }

    @Test
    fun `getTotal with all three bird habitats summed correctly`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[WingspanCategory.BIRDS_FOREST]    = 45
        ps.scores[WingspanCategory.BIRDS_GRASSLAND] = 45
        ps.scores[WingspanCategory.BIRDS_WETLAND]   = 45
        // Other categories null
        assertEquals(135, ps.getTotal())
    }

    @Test
    fun `isComplete returns false when not all categories filled`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[WingspanCategory.BIRDS_FOREST] = 30
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns false when only one bird habitat filled`() {
        val ps = WingspanPlayerScore(1L, "Alice", 0xFF0000)
        WingspanCategory.entries.forEach { ps.scores[it] = 5 }
        ps.scores.remove(WingspanCategory.BIRDS_GRASSLAND)
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

    // ─── getPossibleValues ─────────────────────────────────────────────────────

    @Test
    fun `getPossibleValues bird forest is 0 to 45`() {
        val values = WingspanCategory.BIRDS_FOREST.getPossibleValues()
        assertEquals(0, values.first())
        assertEquals(45, values.last())
        assertEquals(46, values.size)
    }

    @Test
    fun `getPossibleValues bird grassland is 0 to 45`() {
        val values = WingspanCategory.BIRDS_GRASSLAND.getPossibleValues()
        assertEquals(46, values.size)
        assertEquals(45, values.last())
    }

    @Test
    fun `getPossibleValues bird wetland is 0 to 45`() {
        val values = WingspanCategory.BIRDS_WETLAND.getPossibleValues()
        assertEquals(46, values.size)
        assertEquals(45, values.last())
    }

    @Test
    fun `getPossibleValues bonus cards is 0 to 30`() {
        val values = WingspanCategory.BONUS_CARDS.getPossibleValues()
        assertEquals(0, values.first()); assertEquals(30, values.last())
    }

    @Test
    fun `getPossibleValues end of round is 0 to 35`() {
        val values = WingspanCategory.END_OF_ROUND.getPossibleValues()
        assertEquals(0, values.first()); assertEquals(35, values.last())
    }

    @Test
    fun `getPossibleValues eggs is 0 to 40`() {
        val values = WingspanCategory.EGGS.getPossibleValues()
        assertEquals(0, values.first()); assertEquals(40, values.last())
    }

    @Test
    fun `getPossibleValues food on cards is 0 to 20`() {
        val values = WingspanCategory.FOOD_ON_CARDS.getPossibleValues()
        assertEquals(0, values.first()); assertEquals(20, values.last())
    }

    @Test
    fun `getPossibleValues tucked cards is 0 to 25`() {
        val values = WingspanCategory.TUCKED_CARDS.getPossibleValues()
        assertEquals(0, values.first()); assertEquals(25, values.last())
    }
}
