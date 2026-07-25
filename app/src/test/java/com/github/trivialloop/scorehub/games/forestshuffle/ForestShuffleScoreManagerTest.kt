package com.github.trivialloop.scorehub.games.forestshuffle

import org.junit.Test
import org.junit.Assert.*

class ForestShuffleScoreManagerTest {

    private fun player(
        id: Long, name: String,
        counts: Map<TreeSpecies, Int?> = emptyMap(),
        saplings: Int? = 0,
        silverFirAttached: Int? = 0
    ) = ForestShufflePlayerScore(
        playerId = id, playerName = name, playerColor = 0xFF0000,
        treeCounts = counts.toMutableMap(),
        saplings = saplings,
        silverFirAttachedCards = silverFirAttached
    )

    // ─── totalTreeCount / distinctSpeciesCount ────────────────────────────────

    @Test
    fun `totalTreeCount sums all species plus saplings`() {
        val p = player(1, "A", mapOf(TreeSpecies.BIRCH to 3, TreeSpecies.OAK to 2), saplings = 1)
        assertEquals(6, p.totalTreeCount())
    }

    @Test
    fun `totalTreeCount treats missing species as 0`() {
        val p = player(1, "A", mapOf(TreeSpecies.BIRCH to 3))
        assertEquals(3, p.totalTreeCount())
    }

    @Test
    fun `distinctSpeciesCount counts only species with count greater than 0`() {
        val p = player(1, "A", mapOf(TreeSpecies.BIRCH to 2, TreeSpecies.OAK to 0, TreeSpecies.BEECH to 1))
        assertEquals(2, p.distinctSpeciesCount())
    }

    // ─── Birch / Douglas Fir — fixed rate ─────────────────────────────────────

    @Test
    fun `Birch scores 1 point per card`() {
        val p = player(1, "A", mapOf(TreeSpecies.BIRCH to 5))
        assertEquals(5, ForestShuffleScoring.speciesScore(TreeSpecies.BIRCH, p, listOf(p)))
    }

    @Test
    fun `Douglas Fir scores 5 points per card`() {
        val p = player(1, "A", mapOf(TreeSpecies.DOUGLAS_FIR to 3))
        assertEquals(15, ForestShuffleScoring.speciesScore(TreeSpecies.DOUGLAS_FIR, p, listOf(p)))
    }

    // ─── Beech — threshold ─────────────────────────────────────────────────────

    @Test
    fun `Beech scores 0 when fewer than 4`() {
        val p = player(1, "A", mapOf(TreeSpecies.BEECH to 3))
        assertEquals(0, ForestShuffleScoring.speciesScore(TreeSpecies.BEECH, p, listOf(p)))
    }

    @Test
    fun `Beech scores 5 per card when 4 or more`() {
        val p = player(1, "A", mapOf(TreeSpecies.BEECH to 4))
        assertEquals(20, ForestShuffleScoring.speciesScore(TreeSpecies.BEECH, p, listOf(p)))
    }

    @Test
    fun `Beech scores 0 with zero copies`() {
        val p = player(1, "A", mapOf(TreeSpecies.BEECH to 0))
        assertEquals(0, ForestShuffleScoring.speciesScore(TreeSpecies.BEECH, p, listOf(p)))
    }

    // ─── Horse Chestnut — n squared ────────────────────────────────────────────

    @Test
    fun `Horse Chestnut scores n squared`() {
        val p = player(1, "A", mapOf(TreeSpecies.HORSE_CHESTNUT to 4))
        assertEquals(16, ForestShuffleScoring.speciesScore(TreeSpecies.HORSE_CHESTNUT, p, listOf(p)))
    }

    @Test
    fun `Horse Chestnut with single card scores 1`() {
        val p = player(1, "A", mapOf(TreeSpecies.HORSE_CHESTNUT to 1))
        assertEquals(1, ForestShuffleScoring.speciesScore(TreeSpecies.HORSE_CHESTNUT, p, listOf(p)))
    }

    // ─── Oak — needs all 8 species ─────────────────────────────────────────────

    @Test
    fun `Oak scores 0 without all 8 species`() {
        val p = player(1, "A", mapOf(
            TreeSpecies.OAK to 2, TreeSpecies.BIRCH to 1, TreeSpecies.BEECH to 1,
            TreeSpecies.DOUGLAS_FIR to 1, TreeSpecies.HORSE_CHESTNUT to 1,
            TreeSpecies.LINDEN to 1, TreeSpecies.SYCAMORE to 1
            // SILVER_FIR missing -> only 7 distinct species
        ))
        assertEquals(0, ForestShuffleScoring.speciesScore(TreeSpecies.OAK, p, listOf(p)))
    }

    @Test
    fun `Oak scores 10 per card with all 8 species present`() {
        val p = player(1, "A", mapOf(
            TreeSpecies.OAK to 2, TreeSpecies.BIRCH to 1, TreeSpecies.BEECH to 1,
            TreeSpecies.DOUGLAS_FIR to 1, TreeSpecies.HORSE_CHESTNUT to 1,
            TreeSpecies.LINDEN to 1, TreeSpecies.SYCAMORE to 1, TreeSpecies.SILVER_FIR to 1
        ))
        assertEquals(20, ForestShuffleScoring.speciesScore(TreeSpecies.OAK, p, listOf(p)))
    }

    // ─── Sycamore — self-referential on total tree count ───────────────────────

    @Test
    fun `Sycamore scores 1 point per tree in the forest including itself`() {
        val p = player(1, "A", mapOf(TreeSpecies.SYCAMORE to 2, TreeSpecies.BIRCH to 3), saplings = 1)
        // total trees = 2 (sycamore) + 3 (birch) + 1 (sapling) = 6
        assertEquals(12, ForestShuffleScoring.speciesScore(TreeSpecies.SYCAMORE, p, listOf(p)))
    }

    @Test
    fun `Sycamore scores 0 when not played even with other trees`() {
        val p = player(1, "A", mapOf(TreeSpecies.SYCAMORE to 0, TreeSpecies.BIRCH to 5))
        assertEquals(0, ForestShuffleScoring.speciesScore(TreeSpecies.SYCAMORE, p, listOf(p)))
    }

    // ─── Linden — cross-player comparison ───────────────────────────────────────

    @Test
    fun `Linden scores 1 per card when not the highest`() {
        val p1 = player(1, "A", mapOf(TreeSpecies.LINDEN to 2))
        val p2 = player(2, "B", mapOf(TreeSpecies.LINDEN to 4))
        assertEquals(2, ForestShuffleScoring.speciesScore(TreeSpecies.LINDEN, p1, listOf(p1, p2)))
    }

    @Test
    fun `Linden scores 3 per card when strictly highest`() {
        val p1 = player(1, "A", mapOf(TreeSpecies.LINDEN to 4))
        val p2 = player(2, "B", mapOf(TreeSpecies.LINDEN to 2))
        assertEquals(12, ForestShuffleScoring.speciesScore(TreeSpecies.LINDEN, p1, listOf(p1, p2)))
    }

    @Test
    fun `Linden scores 3 per card for all players tied at the max`() {
        val p1 = player(1, "A", mapOf(TreeSpecies.LINDEN to 3))
        val p2 = player(2, "B", mapOf(TreeSpecies.LINDEN to 3))
        val p3 = player(3, "C", mapOf(TreeSpecies.LINDEN to 1))
        assertEquals(9, ForestShuffleScoring.speciesScore(TreeSpecies.LINDEN, p1, listOf(p1, p2, p3)))
        assertEquals(9, ForestShuffleScoring.speciesScore(TreeSpecies.LINDEN, p2, listOf(p1, p2, p3)))
        assertEquals(1, ForestShuffleScoring.speciesScore(TreeSpecies.LINDEN, p3, listOf(p1, p2, p3)))
    }

    @Test
    fun `Linden scores 0 when nobody played any`() {
        val p1 = player(1, "A", mapOf(TreeSpecies.LINDEN to 0))
        val p2 = player(2, "B", mapOf(TreeSpecies.LINDEN to 0))
        assertEquals(0, ForestShuffleScoring.speciesScore(TreeSpecies.LINDEN, p1, listOf(p1, p2)))
    }

    // ─── Silver Fir — attached cards, not own count ─────────────────────────────

    @Test
    fun `Silver Fir scores 2 points per attached dweller card`() {
        val p = player(1, "A", mapOf(TreeSpecies.SILVER_FIR to 2), silverFirAttached = 5)
        assertEquals(10, ForestShuffleScoring.speciesScore(TreeSpecies.SILVER_FIR, p, listOf(p)))
    }

    @Test
    fun `Silver Fir scores 0 with no attached cards even if trees are played`() {
        val p = player(1, "A", mapOf(TreeSpecies.SILVER_FIR to 3), silverFirAttached = 0)
        assertEquals(0, ForestShuffleScoring.speciesScore(TreeSpecies.SILVER_FIR, p, listOf(p)))
    }

    // ─── treesTotal ──────────────────────────────────────────────────────────

    @Test
    fun `treesTotal sums all species scores`() {
        val p = player(1, "A", mapOf(
            TreeSpecies.BIRCH to 2,       // 2
            TreeSpecies.DOUGLAS_FIR to 1  // 5
        ), silverFirAttached = 3) // silver fir 0 count -> 3*2=6
        assertEquals(2 + 5 + 6, ForestShuffleScoring.treesTotal(p, listOf(p)))
    }

    // ─── grandTotal ──────────────────────────────────────────────────────────

    @Test
    fun `grandTotal adds trees, top-bottom, left-right and cave`() {
        val p = player(1, "A", mapOf(TreeSpecies.BIRCH to 2)).apply {
            topBottomPoints = 10
            leftRightPoints = 7
            caveCards = 4
        }
        // trees = 2 (birch only)
        assertEquals(2 + 10 + 7 + 4, ForestShuffleScoring.grandTotal(p, listOf(p)))
    }

    @Test
    fun `grandTotal treats unset optional fields as 0`() {
        val p = player(1, "A", mapOf(TreeSpecies.BIRCH to 1))
        assertEquals(1, ForestShuffleScoring.grandTotal(p, listOf(p)))
    }

    // ─── isComplete ──────────────────────────────────────────────────────────

    @Test
    fun `isComplete is false when a tree species is missing`() {
        val counts = TreeSpecies.entries.associateWith { 0 as Int? }.toMutableMap()
        counts.remove(TreeSpecies.OAK)
        val p = ForestShufflePlayerScore(1, "A", 0xFF0000, counts, 0, 0, 0, 0, 0)
        assertFalse(p.isComplete())
    }

    @Test
    fun `isComplete is true when every field is explicitly filled with zeros`() {
        val counts = TreeSpecies.entries.associateWith { 0 as Int? }.toMutableMap()
        val p = ForestShufflePlayerScore(1, "A", 0xFF0000, counts, 0, 0, 0, 0, 0)
        assertTrue(p.isComplete())
    }

    @Test
    fun `isComplete is false when top-bottom is not yet entered`() {
        val counts = TreeSpecies.entries.associateWith { 0 as Int? }.toMutableMap()
        val p = ForestShufflePlayerScore(1, "A", 0xFF0000, counts, 0, 0, null, 0, 0)
        assertFalse(p.isComplete())
    }
}
