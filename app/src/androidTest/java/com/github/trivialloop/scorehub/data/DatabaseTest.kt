package com.github.trivialloop.scorehub.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var playerDao: PlayerDao
    private lateinit var gameResultDao: GameResultDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        
        playerDao = database.playerDao()
        gameResultDao = database.gameResultDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrievePlayer() = runBlocking {
        val player = Player(name = "Alice", color = 0xFF0000)
        val playerId = playerDao.insertPlayer(player)
        
        val retrievedPlayer = playerDao.getPlayerById(playerId)
        
        assertNotNull(retrievedPlayer)
        assertEquals("Alice", retrievedPlayer?.name)
        assertEquals(0xFF0000, retrievedPlayer?.color)
    }

    @Test
    fun getAllPlayersSortedByName() = runBlocking {
        playerDao.insertPlayer(Player(name = "Charlie", color = 0xFF0000))
        playerDao.insertPlayer(Player(name = "Alice", color = 0x00FF00))
        playerDao.insertPlayer(Player(name = "Bob", color = 0x0000FF))
        
        val players = playerDao.getAllPlayers().first()
        
        assertEquals(3, players.size)
        assertEquals("Alice", players[0].name)
        assertEquals("Bob", players[1].name)
        assertEquals("Charlie", players[2].name)
    }

    @Test
    fun updatePlayer() = runBlocking {
        val player = Player(name = "Alice", color = 0xFF0000)
        val playerId = playerDao.insertPlayer(player)
        
        val updatedPlayer = Player(id = playerId, name = "Alicia", color = 0x00FF00)
        playerDao.updatePlayer(updatedPlayer)
        
        val retrievedPlayer = playerDao.getPlayerById(playerId)
        
        assertEquals("Alicia", retrievedPlayer?.name)
        assertEquals(0x00FF00, retrievedPlayer?.color)
    }

    @Test
    fun deletePlayer() = runBlocking {
        val player = Player(name = "Alice", color = 0xFF0000)
        val playerId = playerDao.insertPlayer(player)
        
        val playerToDelete = playerDao.getPlayerById(playerId)!!
        playerDao.deletePlayer(playerToDelete)
        
        val retrievedPlayer = playerDao.getPlayerById(playerId)
        
        assertNull(retrievedPlayer)
    }

    @Test
    fun getPlayerByName() = runBlocking {
        playerDao.insertPlayer(Player(name = "Alice", color = 0xFF0000))
        
        val player = playerDao.getPlayerByName("Alice")
        
        assertNotNull(player)
        assertEquals("Alice", player?.name)
    }

    @Test
    fun insertGameResults() = runBlocking {
        val player1Id = playerDao.insertPlayer(Player(name = "Alice", color = 0xFF0000))
        val player2Id = playerDao.insertPlayer(Player(name = "Bob", color = 0x00FF00))
        
        val results = listOf(
            GameResult(
                gameType = "yahtzee",
                playerId = player1Id,
                playerName = "Alice",
                score = 250,
                isWinner = true,
                isDraw = false
            ),
            GameResult(
                gameType = "yahtzee",
                playerId = player2Id,
                playerName = "Bob",
                score = 200,
                isWinner = false,
                isDraw = false
            )
        )
        
        gameResultDao.insertGameResults(results)
        
        val wins = gameResultDao.getWinsByPlayer(player1Id, "yahtzee")
        assertEquals(1, wins)
    }

    @Test
    fun getTop20ByGameType() = runBlocking {
        val player1Id = playerDao.insertPlayer(Player(name = "Alice", color = 0xFF0000))
        val player2Id = playerDao.insertPlayer(Player(name = "Bob", color = 0x00FF00))
        
        // Insert 25 game results
        val results = mutableListOf<GameResult>()
        repeat(25) { i ->
            results.add(
                GameResult(
                    gameType = "yahtzee",
                    playerId = if (i % 2 == 0) player1Id else player2Id,
                    playerName = if (i % 2 == 0) "Alice" else "Bob",
                    score = 100 + i * 10,
                    isWinner = false,
                    isDraw = false
                )
            )
        }
        gameResultDao.insertGameResults(results)
        
        val top20 = gameResultDao.getTop20ByGameType("yahtzee")
        
        assertEquals(20, top20.size)
        // Should be sorted by score descending
        assertTrue(top20[0].score >= top20[1].score)
    }

    @Test
    fun getPlayerStatistics() = runBlocking {
        val playerId = playerDao.insertPlayer(Player(name = "Alice", color = 0xFF0000))
        
        val results = listOf(
            GameResult(
                gameType = "yahtzee",
                playerId = playerId,
                playerName = "Alice",
                score = 250,
                isWinner = true,
                isDraw = false,
                playedAt = 1000
            ),
            GameResult(
                gameType = "yahtzee",
                playerId = playerId,
                playerName = "Alice",
                score = 300,
                isWinner = false,
                isDraw = true,
                playedAt = 2000
            ),
            GameResult(
                gameType = "yahtzee",
                playerId = playerId,
                playerName = "Alice",
                score = 150,
                isWinner = false,
                isDraw = false,
                playedAt = 3000
            )
        )
        
        gameResultDao.insertGameResults(results)
        
        val gamesPlayed = gameResultDao.getGamesPlayedByPlayer(playerId, "yahtzee")
        val wins = gameResultDao.getWinsByPlayer(playerId, "yahtzee")
        val draws = gameResultDao.getDrawsByPlayer(playerId, "yahtzee")
        val bestScore = gameResultDao.getBestScoreByPlayer(playerId, "yahtzee")
        val worstScore = gameResultDao.getWorstScoreByPlayer(playerId, "yahtzee")
        
        assertEquals(3, gamesPlayed)
        assertEquals(1, wins)
        assertEquals(1, draws)
        assertEquals(300, bestScore)
        assertEquals(150, worstScore)
    }

    @Test
    fun updatePlayerNameInResults() = runBlocking {
        val playerId = playerDao.insertPlayer(Player(name = "Alice", color = 0xFF0000))
        
        gameResultDao.insertGameResult(
            GameResult(
                gameType = "yahtzee",
                playerId = playerId,
                playerName = "Alice",
                score = 250,
                isWinner = true,
                isDraw = false
            )
        )
        
        gameResultDao.updatePlayerNameInResults(playerId, "Alicia")
        
        val top20 = gameResultDao.getTop20ByGameType("yahtzee")
        assertEquals("Alicia", top20[0].playerName)
    }
}
