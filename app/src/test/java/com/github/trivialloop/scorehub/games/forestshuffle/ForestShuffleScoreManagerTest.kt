package com.github.trivialloop.scorehub.games.forestshuffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestShuffleScoreManagerTest {

    private fun newPlayer() = ForestShufflePlayerScore(1L, "Alice", 0xFF0000)

    @Test
    fun `getTotal returns 0 when no scores entered`() {
        assertEquals(0, newPlayer().getTotal())
    }

    @Test
    fun `getTotal sums all four categories`() {
        val ps = newPlayer()
        ps.scores[ForestShuffleCategory.TREES] = 20
        ps.scores[ForestShuffleCategory.TOP_BOTTOM] = 14
        ps.scores[ForestShuffleCategory.LEFT_RIGHT] = 31
        ps.scores[ForestShuffleCategory.CAVE] = 6
        assertEquals(71, ps.getTotal())
    }

    @Test
    fun `getTotal treats missing categories as 0`() {
        val ps = newPlayer()
        ps.scores[ForestShuffleCategory.TREES] = 9
        assertEquals(9, ps.getTotal())
    }

    @Test
    fun `getTotal handles an explicit null like a missing score`() {
        val ps = newPlayer()
        ps.scores[ForestShuffleCategory.CAVE] = null
        ps.scores[ForestShuffleCategory.LEFT_RIGHT] = 4
        assertEquals(4, ps.getTotal())
    }

    @Test
    fun `there are exactly four categories in official scoring order`() {
        assertEquals(
            listOf(
                ForestShuffleCategory.TREES,
                ForestShuffleCategory.TOP_BOTTOM,
                ForestShuffleCategory.LEFT_RIGHT,
                ForestShuffleCategory.CAVE
            ),
            ForestShuffleCategory.entries
        )
    }

    @Test
    fun `possible values range from 0 to the maximum inclusive`() {
        ForestShuffleCategory.entries.forEach { category ->
            val values = category.getPossibleValues()
            assertEquals(0, values.first())
            assertEquals(FOREST_SHUFFLE_MAX_SCORE, values.last())
            assertEquals(FOREST_SHUFFLE_MAX_SCORE + 1, values.size)
            assertTrue(values == values.sorted())
        }
    }

    @Test
    fun `highest total identifies the winner`() {
        val a = newPlayer().apply { scores[ForestShuffleCategory.TREES] = 30 }
        val b = ForestShufflePlayerScore(2L, "Bob", 0x00FF00).apply {
            scores[ForestShuffleCategory.TREES] = 10
            scores[ForestShuffleCategory.CAVE] = 25
        }
        assertEquals(b, listOf(a, b).maxByOrNull { it.getTotal() })
    }
}
