package com.github.trivialloop.scorehub.games.harmonies

import org.junit.Test
import org.junit.Assert.*

class HarmoniesScoreManagerTest {

    // ─── getCategoryTotal ─────────────────────────────────────────────────────

    @Test
    fun `getCategoryTotal returns 0 when no scores entered`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getCategoryTotal())
    }

    @Test
    fun `getCategoryTotal sums all five categories`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[HarmoniesCategory.TREES] = 11
        ps.scores[HarmoniesCategory.MOUNTAINS] = 8
        ps.scores[HarmoniesCategory.FIELDS] = 15
        ps.scores[HarmoniesCategory.BUILDINGS] = 10
        ps.scores[HarmoniesCategory.RIVER] = 12
        // 11 + 8 + 15 + 10 + 12 = 56
        assertEquals(56, ps.getCategoryTotal())
    }

    @Test
    fun `getCategoryTotal ignores null categories treating them as 0`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[HarmoniesCategory.TREES] = 7
        assertEquals(7, ps.getCategoryTotal())
    }

    @Test
    fun `getCategoryTotal with all zeros returns 0`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        HarmoniesCategory.entries.forEach { ps.scores[it] = 0 }
        assertEquals(0, ps.getCategoryTotal())
    }

    // ─── getAnimalsTotal ──────────────────────────────────────────────────────

    @Test
    fun `getAnimalsTotal returns 0 with no entries`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getAnimalsTotal())
    }

    @Test
    fun `getAnimalsTotal sums all animal card entries`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.animalEntries.addAll(listOf(12, 8, 20, 5))
        assertEquals(45, ps.getAnimalsTotal())
    }

    @Test
    fun `getAnimalsTotal with a single entry`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.animalEntries.add(24)
        assertEquals(24, ps.getAnimalsTotal())
    }

    @Test
    fun `animal entries can include zero-point cards`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.animalEntries.addAll(listOf(0, 0, 15))
        assertEquals(15, ps.getAnimalsTotal())
        assertEquals(3, ps.animalEntries.size)
    }

    @Test
    fun `many animal cards accumulate correctly`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.animalEntries.addAll(listOf(4, 8, 12, 16, 20))
        assertEquals(60, ps.getAnimalsTotal())
    }

    // ─── getTotal ─────────────────────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 for a fresh player`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getTotal())
    }

    @Test
    fun `getTotal combines categories and animal cards`() {
        val ps = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        ps.scores[HarmoniesCategory.TREES] = 7
        ps.scores[HarmoniesCategory.RIVER] = 10
        ps.animalEntries.addAll(listOf(12, 8))
        // 7 + 10 + 12 + 8 = 37
        assertEquals(37, ps.getTotal())
    }

    @Test
    fun `getTotal ignores other players data`() {
        val alice = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        val bob = HarmoniesPlayerScore(2L, "Bob", 0x00FF00)
        alice.scores[HarmoniesCategory.TREES] = 7
        alice.animalEntries.add(10)
        bob.scores[HarmoniesCategory.MOUNTAINS] = 3
        assertEquals(17, alice.getTotal())
        assertEquals(3, bob.getTotal())
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
    fun `HarmoniesCategory has exactly 5 fixed categories`() {
        assertEquals(5, HarmoniesCategory.entries.size)
    }

    // ─── HARMONIES_ANIMAL_CARD_VALUES ────────────────────────────────────────

    @Test
    fun `animal card values range 0 to 30`() {
        assertEquals(0, HARMONIES_ANIMAL_CARD_VALUES.first())
        assertEquals(30, HARMONIES_ANIMAL_CARD_VALUES.last())
        assertEquals(31, HARMONIES_ANIMAL_CARD_VALUES.size)
    }

    // ─── Multi-player independence ─────────────────────────────────────────────

    @Test
    fun `two players category and animal totals are independent`() {
        val alice = HarmoniesPlayerScore(1L, "Alice", 0xFF0000)
        val bob = HarmoniesPlayerScore(2L, "Bob", 0x00FF00)

        alice.scores[HarmoniesCategory.TREES] = 7
        alice.scores[HarmoniesCategory.RIVER] = 10
        alice.animalEntries.addAll(listOf(5, 5))
        bob.scores[HarmoniesCategory.MOUNTAINS] = 3
        bob.scores[HarmoniesCategory.BUILDINGS] = 20
        bob.animalEntries.add(9)

        assertEquals(27, alice.getTotal())
        assertEquals(32, bob.getTotal())
    }
}
