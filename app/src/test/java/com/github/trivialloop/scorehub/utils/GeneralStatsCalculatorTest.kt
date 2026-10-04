package com.github.trivialloop.scorehub.utils

import com.github.trivialloop.scorehub.Equipment
import com.github.trivialloop.scorehub.GameRegistry
import com.github.trivialloop.scorehub.data.GameSummary
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.data.PlayerGameStats
import org.junit.Assert.*
import org.junit.Test

class GeneralStatsCalculatorTest {

    private fun game(type: String, lowerIsBetter: Boolean = false) = GameRegistry.GameDefinition(
        gameType = type, nameEnFallback = type, nameResId = 0, iconResId = 0,
        activityClass = Any::class.java, minPlayers = 2, maxPlayers = 4,
        lowerIsBetter = lowerIsBetter, equipment = setOf(Equipment.CARDS)
    )

    private val alice = Player(id = 1, name = "Alice", color = 0)
    private val bob = Player(id = 2, name = "Bob", color = 0)

    private fun stat(p: Long, type: String, counted: Int, wins: Int, min: Int, max: Int) =
        PlayerGameStats(p, type, counted, counted, wins, 0, max, min)

    @Test
    fun `games without results are hidden`() {
        val cards = GeneralStatsCalculator.buildGameCards(
            listOf(game("a"), game("b")), listOf(alice, bob),
            listOf(stat(1, "a", 2, 1, 10, 20)),
            listOf(GameSummary("a", 100L, 2))
        )
        assertEquals(listOf("a"), cards.map { it.definition.gameType })
    }

    @Test
    fun `best score uses min when lowerIsBetter`() {
        val stats = listOf(stat(1, "s", 2, 1, 30, 80), stat(2, "s", 2, 1, 25, 90))
        val summaries = listOf(GameSummary("s", 1L, 2))
        val low = GeneralStatsCalculator.buildGameCards(
            listOf(game("s", lowerIsBetter = true)), listOf(alice, bob), stats, summaries).single()
        val high = GeneralStatsCalculator.buildGameCards(
            listOf(game("s")), listOf(alice, bob), stats, summaries).single()
        assertEquals(25, low.bestScore?.score)
        assertEquals(90, high.bestScore?.score)
    }

    @Test
    fun `player cards sorted by win percentage`() {
        val stats = listOf(stat(1, "yahtzee", 4, 1, 0, 0), stat(2, "yahtzee", 4, 3, 0, 0))
        val cards = GeneralStatsCalculator.buildPlayerCards(listOf(alice, bob), stats)
        assertEquals(listOf("Bob", "Alice"), cards.map { it.player.name })
    }
}
