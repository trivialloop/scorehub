package com.github.trivialloop.scorehub.games.sevenwonders

import org.junit.Test
import org.junit.Assert.*

class SevenWondersScoreManagerTest {

    // ─── getCoinPoints ─────────────────────────────────────────────────────────

    @Test
    fun `getCoinPoints returns 0 when no coins entered`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getCoinPoints())
    }

    @Test
    fun `getCoinPoints divides by 3 rounding down`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000, coins = 11)
        assertEquals(3, ps.getCoinPoints())
    }

    @Test
    fun `getCoinPoints exact multiple of 3`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000, coins = 9)
        assertEquals(3, ps.getCoinPoints())
    }

    @Test
    fun `getCoinPoints with 0 or 1 or 2 coins scores 0`() {
        assertEquals(0, SevenWondersPlayerScore(1L, "A", 0, coins = 0).getCoinPoints())
        assertEquals(0, SevenWondersPlayerScore(1L, "A", 0, coins = 1).getCoinPoints())
        assertEquals(0, SevenWondersPlayerScore(1L, "A", 0, coins = 2).getCoinPoints())
    }

    // ─── getScienceScore ──────────────────────────────────────────────────────

    @Test
    fun `getScienceScore returns 0 when nothing entered`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getScienceScore())
    }

    @Test
    fun `getScienceScore squares each symbol count`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            scienceCompass = 3, scienceGear = 0, scienceTablet = 0
        )
        // 3^2 = 9, no sets
        assertEquals(9, ps.getScienceScore())
    }

    @Test
    fun `getScienceScore sums all three squares`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            scienceCompass = 2, scienceGear = 3, scienceTablet = 1
        )
        // 4 + 9 + 1 = 14, sets = min(2,3,1) = 1 -> +7
        assertEquals(21, ps.getScienceScore())
    }

    @Test
    fun `getScienceScore adds 7 per complete set`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            scienceCompass = 4, scienceGear = 4, scienceTablet = 4
        )
        // 16+16+16=48, sets=4 -> +28 = 76 (matches official max example)
        assertEquals(76, ps.getScienceScore())
    }

    @Test
    fun `getScienceScore with asymmetric high counts`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            scienceCompass = 5, scienceGear = 2, scienceTablet = 2
        )
        // 25+4+4=33, sets=2 -> +14 = 47
        assertEquals(47, ps.getScienceScore())
    }

    @Test
    fun `getScienceSetCount returns minimum of the three symbols`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            scienceCompass = 5, scienceGear = 2, scienceTablet = 3
        )
        assertEquals(2, ps.getScienceSetCount())
    }

    @Test
    fun `getScienceSetCount is 0 when one symbol missing`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            scienceCompass = 5, scienceGear = 0, scienceTablet = 3
        )
        assertEquals(0, ps.getScienceSetCount())
    }

    // ─── getTotal ─────────────────────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 for a fresh player`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getTotal())
    }

    @Test
    fun `getTotal combines all seven categories`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            militaryPoints = 5,
            coins = 10,          // -> 3 pts
            wonderPoints = 7,
            civilianPoints = 12,
            commercePoints = 4,
            guildPoints = 8,
            scienceCompass = 2, scienceGear = 2, scienceTablet = 2 // 4+4+4+7 = 19
        )
        // 5 + 3 + 7 + 12 + 4 + 8 + 19 = 58
        assertEquals(58, ps.getTotal())
    }

    @Test
    fun `getTotal handles negative military points`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            militaryPoints = -3,
            coins = 6,           // -> 2 pts
            wonderPoints = 0,
            civilianPoints = 0,
            commercePoints = 0,
            guildPoints = 0
        )
        // -3 + 2 = -1
        assertEquals(-1, ps.getTotal())
    }

    @Test
    fun `getTotal ignores other players data`() {
        val alice = SevenWondersPlayerScore(1L, "Alice", 0xFF0000, wonderPoints = 10)
        val bob = SevenWondersPlayerScore(2L, "Bob", 0x00FF00, wonderPoints = 20)
        assertEquals(10, alice.getTotal())
        assertEquals(20, bob.getTotal())
    }

    // ─── isComplete ───────────────────────────────────────────────────────────

    @Test
    fun `isComplete returns false when nothing filled`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns false when science symbols partially filled`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            militaryPoints = 0, coins = 0, wonderPoints = 0,
            civilianPoints = 0, commercePoints = 0, guildPoints = 0,
            scienceCompass = 1, scienceGear = 1 // tablet missing
        )
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns true when all fields filled including zeros`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            militaryPoints = 0, coins = 0, wonderPoints = 0,
            civilianPoints = 0, commercePoints = 0, guildPoints = 0,
            scienceCompass = 0, scienceGear = 0, scienceTablet = 0
        )
        assertTrue(ps.isComplete())
    }

    @Test
    fun `isComplete returns true with all fields non-zero`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            militaryPoints = 5, coins = 12, wonderPoints = 7,
            civilianPoints = 15, commercePoints = 3, guildPoints = 6,
            scienceCompass = 2, scienceGear = 3, scienceTablet = 1
        )
        assertTrue(ps.isComplete())
    }

    // ─── SevenWondersValues ────────────────────────────────────────────────────

    @Test
    fun `military values include negative range down to -6`() {
        assertEquals(-6, SevenWondersValues.MILITARY_VALUES.first())
        assertEquals(18, SevenWondersValues.MILITARY_VALUES.last())
    }

    @Test
    fun `science symbol values start at 0`() {
        assertEquals(0, SevenWondersValues.SCIENCE_SYMBOL_VALUES.first())
    }
}
