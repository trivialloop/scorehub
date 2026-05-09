package com.github.trivialloop.scorehub.games.akropolis

import org.junit.Test
import org.junit.Assert.*

class AkropolisScoreManagerTest {

    // ─── getDistrictTotal ─────────────────────────────────────────────────────

    @Test
    fun `getDistrictTotal returns 0 when stars is null`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        ps.districts[AkropolisColor.BLUE] = 3
        assertEquals(0, ps.getDistrictTotal(AkropolisColor.BLUE))
    }

    @Test
    fun `getDistrictTotal returns 0 when districts is null`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        ps.stars[AkropolisColor.BLUE] = 4
        assertEquals(0, ps.getDistrictTotal(AkropolisColor.BLUE))
    }

    @Test
    fun `getDistrictTotal returns 0 when both are null`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getDistrictTotal(AkropolisColor.BLUE))
    }

    @Test
    fun `getDistrictTotal returns stars times districts`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        ps.stars[AkropolisColor.BLUE] = 3
        ps.districts[AkropolisColor.BLUE] = 4
        assertEquals(12, ps.getDistrictTotal(AkropolisColor.BLUE))
    }

    @Test
    fun `getDistrictTotal returns 0 when stars is 0`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        ps.stars[AkropolisColor.RED] = 0
        ps.districts[AkropolisColor.RED] = 5
        assertEquals(0, ps.getDistrictTotal(AkropolisColor.RED))
    }

    @Test
    fun `getDistrictTotal returns 0 when districts is 0`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        ps.stars[AkropolisColor.GREEN] = 5
        ps.districts[AkropolisColor.GREEN] = 0
        assertEquals(0, ps.getDistrictTotal(AkropolisColor.GREEN))
    }

    @Test
    fun `getDistrictTotal is independent per color`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        ps.stars[AkropolisColor.BLUE]   = 2; ps.districts[AkropolisColor.BLUE]   = 3
        ps.stars[AkropolisColor.YELLOW] = 4; ps.districts[AkropolisColor.YELLOW] = 5
        assertEquals(6,  ps.getDistrictTotal(AkropolisColor.BLUE))
        assertEquals(20, ps.getDistrictTotal(AkropolisColor.YELLOW))
    }

    // ─── getTotal ─────────────────────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 when nothing is filled`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getTotal())
    }

    @Test
    fun `getTotal returns only stones when colors are empty`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        ps.stones = 7
        assertEquals(7, ps.getTotal())
    }

    @Test
    fun `getTotal sums all color subtotals plus stones`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        ps.stars[AkropolisColor.BLUE]   = 2; ps.districts[AkropolisColor.BLUE]   = 3  // 6
        ps.stars[AkropolisColor.YELLOW] = 1; ps.districts[AkropolisColor.YELLOW] = 4  // 4
        ps.stars[AkropolisColor.RED]    = 3; ps.districts[AkropolisColor.RED]    = 2  // 6
        ps.stars[AkropolisColor.PURPLE] = 0; ps.districts[AkropolisColor.PURPLE] = 5  // 0
        ps.stars[AkropolisColor.GREEN]  = 5; ps.districts[AkropolisColor.GREEN]  = 1  // 5
        ps.stones = 3
        // 6 + 4 + 6 + 0 + 5 + 3 = 24
        assertEquals(24, ps.getTotal())
    }

    @Test
    fun `getTotal treats null stars as 0 contribution`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        // BLUE: stars=null → subtotal=0, others filled
        ps.districts[AkropolisColor.BLUE] = 10
        ps.stars[AkropolisColor.YELLOW] = 2; ps.districts[AkropolisColor.YELLOW] = 3
        ps.stones = 5
        assertEquals(6 + 5, ps.getTotal())
    }

    @Test
    fun `getTotal treats null stones as 0`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        ps.stars[AkropolisColor.BLUE] = 3; ps.districts[AkropolisColor.BLUE] = 3
        // stones = null
        assertEquals(9, ps.getTotal())
    }

    // ─── isComplete ───────────────────────────────────────────────────────────

    @Test
    fun `isComplete returns false when nothing filled`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns false when stones is missing`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        for (color in AkropolisColor.entries) {
            ps.stars[color] = 1; ps.districts[color] = 1
        }
        // stones not set
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns false when one color stars is missing`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        for (color in AkropolisColor.entries) {
            ps.stars[color] = 1; ps.districts[color] = 1
        }
        ps.stones = 2
        ps.stars.remove(AkropolisColor.RED)
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns false when one color districts is missing`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        for (color in AkropolisColor.entries) {
            ps.stars[color] = 1; ps.districts[color] = 1
        }
        ps.stones = 2
        ps.districts.remove(AkropolisColor.GREEN)
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns true when all fields filled`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        for (color in AkropolisColor.entries) {
            ps.stars[color] = 2; ps.districts[color] = 3
        }
        ps.stones = 4
        assertTrue(ps.isComplete())
    }

    @Test
    fun `isComplete returns true when all values are 0`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        for (color in AkropolisColor.entries) {
            ps.stars[color] = 0; ps.districts[color] = 0
        }
        ps.stones = 0
        assertTrue(ps.isComplete())
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `getTotal with all zeros returns 0`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        for (color in AkropolisColor.entries) {
            ps.stars[color] = 0; ps.districts[color] = 0
        }
        ps.stones = 0
        assertEquals(0, ps.getTotal())
    }

    @Test
    fun `getTotal with max inputs returns correct value`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        for (color in AkropolisColor.entries) {
            ps.stars[color] = 99; ps.districts[color] = 99  // 9801 each
        }
        ps.stones = 99
        // 5 * 9801 + 99 = 49005 + 99 = 49104
        assertEquals(49104, ps.getTotal())
    }

    @Test
    fun `two players totals are independent`() {
        val alice = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        val bob   = AkropolisPlayerScore(2L, "Bob",   0x00FF00)

        alice.stars[AkropolisColor.BLUE] = 2; alice.districts[AkropolisColor.BLUE] = 5
        alice.stones = 3
        bob.stars[AkropolisColor.BLUE] = 4;   bob.districts[AkropolisColor.BLUE] = 3
        bob.stones = 1

        assertEquals(13, alice.getTotal())  // 10 + 3
        assertEquals(13, bob.getTotal())    // 12 + 1
    }

    @Test
    fun `AkropolisColor entries has exactly 5 colors`() {
        assertEquals(5, AkropolisColor.entries.size)
    }

    @Test
    fun `all five colors produce independent subtotals`() {
        val ps = AkropolisPlayerScore(1L, "Alice", 0xFF0000)
        val expected = mapOf(
            AkropolisColor.BLUE   to 6,
            AkropolisColor.YELLOW to 12,
            AkropolisColor.RED    to 20,
            AkropolisColor.PURPLE to 30,
            AkropolisColor.GREEN  to 42
        )
        val starsMap    = mapOf(AkropolisColor.BLUE to 2, AkropolisColor.YELLOW to 3,
            AkropolisColor.RED to 4, AkropolisColor.PURPLE to 5, AkropolisColor.GREEN to 6)
        val districtsMap = mapOf(AkropolisColor.BLUE to 3, AkropolisColor.YELLOW to 4,
            AkropolisColor.RED to 5, AkropolisColor.PURPLE to 6, AkropolisColor.GREEN to 7)
        for (color in AkropolisColor.entries) {
            ps.stars[color] = starsMap[color]!!
            ps.districts[color] = districtsMap[color]!!
        }
        for (color in AkropolisColor.entries) {
            assertEquals(expected[color], ps.getDistrictTotal(color))
        }
    }
}
