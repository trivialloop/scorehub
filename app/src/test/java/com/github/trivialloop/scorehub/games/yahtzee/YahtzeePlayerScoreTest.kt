package com.github.trivialloop.scorehub.games.yahtzee

import org.junit.Test
import org.junit.Assert.*

class YahtzeePlayerScoreTest {

    @Test
    fun `test upper total calculation`() {
        val playerScore = YahtzeePlayerScore(1L, "Test Player", 0xFF0000)
        
        playerScore.scores[YahtzeeCategory.ONES] = 3
        playerScore.scores[YahtzeeCategory.TWOS] = 6
        playerScore.scores[YahtzeeCategory.THREES] = 9
        playerScore.scores[YahtzeeCategory.FOURS] = 12
        playerScore.scores[YahtzeeCategory.FIVES] = 15
        playerScore.scores[YahtzeeCategory.SIXES] = 18
        
        assertEquals(63, playerScore.getUpperTotal())
    }

    @Test
    fun `test bonus when upper total is 63 or more`() {
        val playerScore = YahtzeePlayerScore(1L, "Test Player", 0xFF0000)
        
        playerScore.scores[YahtzeeCategory.ONES] = 3
        playerScore.scores[YahtzeeCategory.TWOS] = 6
        playerScore.scores[YahtzeeCategory.THREES] = 9
        playerScore.scores[YahtzeeCategory.FOURS] = 12
        playerScore.scores[YahtzeeCategory.FIVES] = 15
        playerScore.scores[YahtzeeCategory.SIXES] = 18
        
        assertEquals(35, playerScore.getBonus())
    }

    @Test
    fun `test no bonus when upper total is less than 63`() {
        val playerScore = YahtzeePlayerScore(1L, "Test Player", 0xFF0000)
        
        playerScore.scores[YahtzeeCategory.ONES] = 2
        playerScore.scores[YahtzeeCategory.TWOS] = 4
        playerScore.scores[YahtzeeCategory.THREES] = 6
        playerScore.scores[YahtzeeCategory.FOURS] = 8
        playerScore.scores[YahtzeeCategory.FIVES] = 10
        playerScore.scores[YahtzeeCategory.SIXES] = 12
        
        assertEquals(0, playerScore.getBonus())
        assertEquals(21, playerScore.getBonusProgress())
    }

    @Test
    fun `test lower total calculation`() {
        val playerScore = YahtzeePlayerScore(1L, "Test Player", 0xFF0000)
        
        playerScore.scores[YahtzeeCategory.CHANCE] = 20
        playerScore.scores[YahtzeeCategory.THREE_OF_KIND] = 15
        playerScore.scores[YahtzeeCategory.FOUR_OF_KIND] = 20
        playerScore.scores[YahtzeeCategory.FULL_HOUSE] = 25
        playerScore.scores[YahtzeeCategory.SMALL_STRAIGHT] = 30
        playerScore.scores[YahtzeeCategory.LARGE_STRAIGHT] = 40
        playerScore.scores[YahtzeeCategory.YAHTZEE] = 50
        
        assertEquals(200, playerScore.getLowerTotal())
    }

    @Test
    fun `test grand total calculation with bonus`() {
        val playerScore = YahtzeePlayerScore(1L, "Test Player", 0xFF0000)
        
        // Upper section (63 points -> bonus)
        playerScore.scores[YahtzeeCategory.ONES] = 3
        playerScore.scores[YahtzeeCategory.TWOS] = 6
        playerScore.scores[YahtzeeCategory.THREES] = 9
        playerScore.scores[YahtzeeCategory.FOURS] = 12
        playerScore.scores[YahtzeeCategory.FIVES] = 15
        playerScore.scores[YahtzeeCategory.SIXES] = 18
        
        // Lower section
        playerScore.scores[YahtzeeCategory.CHANCE] = 20
        playerScore.scores[YahtzeeCategory.THREE_OF_KIND] = 15
        playerScore.scores[YahtzeeCategory.FOUR_OF_KIND] = 20
        playerScore.scores[YahtzeeCategory.FULL_HOUSE] = 25
        playerScore.scores[YahtzeeCategory.SMALL_STRAIGHT] = 30
        playerScore.scores[YahtzeeCategory.LARGE_STRAIGHT] = 40
        playerScore.scores[YahtzeeCategory.YAHTZEE] = 50
        
        // 63 (upper) + 35 (bonus) + 200 (lower) = 298
        assertEquals(298, playerScore.getGrandTotal())
    }

    @Test
    fun `test isComplete returns false when not all scores filled`() {
        val playerScore = YahtzeePlayerScore(1L, "Test Player", 0xFF0000)
        
        playerScore.scores[YahtzeeCategory.ONES] = 3
        playerScore.scores[YahtzeeCategory.TWOS] = 6
        
        assertFalse(playerScore.isComplete())
    }

    @Test
    fun `test isComplete returns true when all scores filled`() {
        val playerScore = YahtzeePlayerScore(1L, "Test Player", 0xFF0000)
        
        YahtzeeCategory.values().forEach { category ->
            playerScore.scores[category] = 0
        }
        
        assertTrue(playerScore.isComplete())
    }

    @Test
    fun `test yahtzee category possible values`() {
        val expectedOnes = listOf(0, 1, 2, 3, 4, 5)
        val expectedFullHouse = listOf(0, 25)
        val expectedYahtzee = listOf(0, 50)
        
        assertEquals(expectedOnes, YahtzeeCategory.ONES.getPossibleValues())
        assertEquals(expectedFullHouse, YahtzeeCategory.FULL_HOUSE.getPossibleValues())
        assertEquals(expectedYahtzee, YahtzeeCategory.YAHTZEE.getPossibleValues())
    }

    @Test
    fun `test empty score calculation`() {
        val playerScore = YahtzeePlayerScore(1L, "Test Player", 0xFF0000)
        
        assertEquals(0, playerScore.getUpperTotal())
        assertEquals(0, playerScore.getLowerTotal())
        assertEquals(0, playerScore.getGrandTotal())
        assertEquals(63, playerScore.getBonusProgress())
    }
}
