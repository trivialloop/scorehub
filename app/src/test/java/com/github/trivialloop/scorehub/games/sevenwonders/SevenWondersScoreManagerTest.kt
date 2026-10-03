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

    // ─── getGuildsTotal ───────────────────────────────────────────────────────

    @Test
    fun `getGuildsTotal returns 0 with no entries`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getGuildsTotal())
    }

    @Test
    fun `getGuildsTotal sums all guild card entries`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        ps.guildEntries.addAll(listOf(8, 5, 12))
        assertEquals(25, ps.getGuildsTotal())
    }

    @Test
    fun `getGuildsTotal with a single entry`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        ps.guildEntries.add(10)
        assertEquals(10, ps.getGuildsTotal())
    }

    @Test
    fun `guild entries can include zero-point cards`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        ps.guildEntries.addAll(listOf(0, 7))
        assertEquals(7, ps.getGuildsTotal())
        assertEquals(2, ps.guildEntries.size)
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
        assertEquals(9, ps.getScienceScore())
    }

    @Test
    fun `getScienceScore sums all three squares plus one set bonus`() {
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
        // 16+16+16=48, sets=4 -> +28 = 76
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

    // ─── getTotal ─────────────────────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 for a fresh player`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        assertEquals(0, ps.getTotal())
    }

    @Test
    fun `getTotal combines all seven categories including guild entries`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            militaryPoints = 5,
            coins = 10,          // -> 3 pts
            wonderPoints = 7,
            civilianPoints = 12,
            commercePoints = 4,
            scienceCompass = 2, scienceGear = 2, scienceTablet = 2 // 4+4+4+7 = 19
        )
        ps.guildEntries.addAll(listOf(6, 2)) // 8
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
            commercePoints = 0
        )
        // -3 + 2 = -1
        assertEquals(-1, ps.getTotal())
    }

    @Test
    fun `getTotal ignores other players data`() {
        val alice = SevenWondersPlayerScore(1L, "Alice", 0xFF0000, wonderPoints = 10)
        val bob = SevenWondersPlayerScore(2L, "Bob", 0x00FF00, wonderPoints = 20)
        alice.guildEntries.add(5)
        assertEquals(15, alice.getTotal())
        assertEquals(20, bob.getTotal())
    }

    // ─── isComplete ───────────────────────────────────────────────────────────

    @Test
    fun `isComplete returns false when nothing filled`() {
        val ps = SevenWondersPlayerScore(1L, "Alice", 0xFF0000)
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete ignores guild entries - empty guild list is valid`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            militaryPoints = 0, coins = 0, wonderPoints = 0,
            civilianPoints = 0, commercePoints = 0,
            scienceCompass = 0, scienceGear = 0, scienceTablet = 0
        )
        // No guild entries added, still complete
        assertTrue(ps.isComplete())
    }

    @Test
    fun `isComplete returns false when science symbols partially filled`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            militaryPoints = 0, coins = 0, wonderPoints = 0,
            civilianPoints = 0, commercePoints = 0,
            scienceCompass = 1, scienceGear = 1 // tablet missing
        )
        assertFalse(ps.isComplete())
    }

    @Test
    fun `isComplete returns true with all fixed fields filled and several guilds`() {
        val ps = SevenWondersPlayerScore(
            1L, "Alice", 0xFF0000,
            militaryPoints = 5, coins = 12, wonderPoints = 7,
            civilianPoints = 15, commercePoints = 3,
            scienceCompass = 2, scienceGear = 3, scienceTablet = 1
        )
        ps.guildEntries.addAll(listOf(4, 6, 9))
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

    @Test
    fun `guild card values start at 0`() {
        assertEquals(0, SevenWondersValues.GUILD_CARD_VALUES.first())
    }
}
