package com.github.trivialloop.scorehub.games.harmonies

import org.junit.Test
import org.junit.Assert.*

class HarmoniesScoreManagerTest {

    // ─── getTotal ─────────────────────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 when no scores entered`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getTotal())
    }

    @Test
    fun `getTotal sums all six categories`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[HarmoniesCategory.TREES] = 11
        ps.scores[HarmoniesCategory.MOUNTAINS] = 8
        ps.scores[HarmoniesCategory.FIELDS] = 15
        ps.scores[HarmoniesCategory.BUILDINGS] = 10
        ps.scores[HarmoniesCategory.RIVER] = 12
        ps.scores[HarmoniesCategory.ANIMALS] = 24
        // 11 + 8 + 15 + 10 + 12 + 24 = 80
        assertEquals(80, ps.getTotal())
    }

    @Test
    fun `getTotal ignores null categories treating them as 0`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[HarmoniesCategory.TREES] = 7
        // all other categories left null
        assertEquals(7, ps.getTotal())
    }

    @Test
    fun `getTotal with all zeros returns 0`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        HarmoniesCategory.entries.forEach { ps.scores[it] = 0 }
        assertEquals(0, ps.getTotal())
    }

    @Test
    fun `two players totals are independent`() {
        val alice = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        val bob = HarmoniesPlayerScore(2L, "Bob", 0x00FF00)

        alice.scores[HarmoniesCategory.TREES] = 7
        alice.scores[HarmoniesCategory.RIVER] = 10
        bob.scores[HarmoniesCategory.MOUNTAINS] = 3
        bob.scores[HarmoniesCategory.BUILDINGS] = 20

        assertEquals(17, alice.getTotal())
        assertEquals(23, bob.getTotal())
    }

    // ─── isComplete ───────────────────────────────────────────────────────────

    @Test
    fun `isComplete returns false when nothing filled`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns false when only some categories filled`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[HarmoniesCategory.TREES] = 5
        ps.scores[HarmoniesCategory.MOUNTAINS] = 0
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns true when all six categories filled`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        HarmoniesCategory.entries.forEach { ps.scores[it] = 5 }
        assertTrue(ps.isComplete())
    }

    @Test
    fun `isComplete returns true when all values are 0`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        HarmoniesCategory.entries.forEach { ps.scores[it] = 0 }
        assertTrue(ps.isComplete())
    }

    // ─── getPossibleValues ──────────────────────────────────────────────────────

    @Test
    fun `trees possible values range 0 to 50`() {
        val values = HarmoniesCategory.TREES.getPossibleValues()
        assertEquals(0, values.first())
        assertEquals(50, values.last())
        assertEquals(51, values.size)
    }

    @Test
    fun `mountains possible values range 0 to 50`() {
        val values = HarmoniesCategory.MOUNTAINS.getPossibleValues()
        assertEquals(0, values.first())
        assertEquals(50, values.last())
    }

    @Test
    fun `fields possible values are multiples of 5 up to 50`() {
        val values = HarmoniesCategory.FIELDS.getPossibleValues()
        assertEquals(listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50), values)
    }

    @Test
    fun `buildings possible values are multiples of 5 up to 50`() {
        val values = HarmoniesCategory.BUILDINGS.getPossibleValues()
        assertTrue(values.all { it % 5 == 0 })
        assertEquals(0, values.first())
        assertEquals(50, values.last())
    }

    @Test
    fun `river possible values range 0 to 40`() {
        val values = HarmoniesCategory.RIVER.getPossibleValues()
        assertEquals(0, values.first())
        assertEquals(40, values.last())
    }

    @Test
    fun `animals possible values range 0 to 99`() {
        val values = HarmoniesCategory.ANIMALS.getPossibleValues()
        assertEquals(0, values.first())
        assertEquals(99, values.last())
    }

    @Test
    fun `HarmoniesCategory has exactly 6 categories`() {
        assertEquals(6, HarmoniesCategory.entries.size)
    }
}
