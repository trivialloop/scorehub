package com.github.trivialloop.scorehub.games.hanginggardens

import org.junit.Assert.*
import org.junit.Test

class HangingGardensScoreManagerTest {

    private fun player() = HangingGardensPlayerScore(1L, "Alice", 0xFF0000)

    @Test fun `fresh player total is 0`() = assertEquals(0, player().getTotal())

    @Test fun `flowers total sums the three colors`() {
        val p = player()
        p.flowers[HangingGardensFlower.BLUE] = 5
        p.flowers[HangingGardensFlower.RED] = 4
        p.flowers[HangingGardensFlower.YELLOW] = 3
        assertEquals(12, p.getFlowersTotal())
    }

    @Test fun `flower values go from 0 to 24`() {
        val v = HangingGardensFlower.BLUE.getPossibleValues()
        assertEquals(0, v.first()); assertEquals(24, v.last())
    }

    @Test fun `tree entries are tracked per species`() {
        val p = player()
        p.treeEntries.getValue(HangingGardensTree.DRAGON).addAll(listOf(2, 7))
        p.treeEntries.getValue(HangingGardensTree.PALM).add(3)
        assertEquals(9, p.getTreeTotal(HangingGardensTree.DRAGON))
        assertEquals(0, p.getTreeTotal(HangingGardensTree.CEDAR))
        assertEquals(12, p.getTreesTotal())
    }

    @Test fun `animals and humans accumulate independently`() {
        val p = player()
        p.animalEntries.addAll(listOf(2, 5, 0))
        p.humanEntries.addAll(listOf(7, 3))
        assertEquals(7, p.getAnimalsTotal())
        assertEquals(10, p.getHumansTotal())
    }

    @Test fun `objectives values go from 0 to 14`() {
        assertEquals(0, HANGING_GARDENS_OBJECTIVES_VALUES.first())
        assertEquals(14, HANGING_GARDENS_OBJECTIVES_VALUES.last())
    }

    @Test fun `irrigation offers 0 plus the 6 point values`() =
        assertEquals(listOf(0, 1, 2, 4, 7, 10, 15), HANGING_GARDENS_IRRIGATION_VALUES)

    @Test fun `total combines every block`() {
        val p = player()
        p.irrigation = 9
        p.flowers[HangingGardensFlower.BLUE] = 6
        p.treeEntries.getValue(HangingGardensTree.CEDAR).add(5)
        p.animalEntries.add(5)
        p.humanEntries.add(3)
        p.objectives = 8
        assertEquals(9 + 6 + 5 + 5 + 3 + 8, p.getTotal())
    }

    @Test fun `players data is independent`() {
        val a = player()
        val b = HangingGardensPlayerScore(2L, "Bob", 0x00FF00)
        a.treeEntries.getValue(HangingGardensTree.PALM).add(7)
        assertEquals(0, b.getTreesTotal())
    }

    @Test fun `dragon tree offers 2 and 7`() =
        assertEquals(listOf(2, 7), HangingGardensTree.DRAGON.getPossibleValues())

    @Test fun `cedar tree offers 2, 5 and 9`() =
        assertEquals(listOf(2, 5, 9), HangingGardensTree.CEDAR.getPossibleValues())

    @Test fun `palm tree offers 3, 7, 12 and 18`() =
        assertEquals(listOf(3, 7, 12, 18), HangingGardensTree.PALM.getPossibleValues())

    @Test fun `every species offers only positive values`() {
        HangingGardensTree.entries.forEach { tree ->
            assertTrue(tree.getPossibleValues().all { it > 0 })
        }
    }
}
