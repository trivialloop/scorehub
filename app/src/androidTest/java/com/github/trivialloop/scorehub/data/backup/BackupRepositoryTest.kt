package com.github.trivialloop.scorehub.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.data.Player
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: BackupRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = BackupRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun result(playerId: Long, name: String, at: Long, score: Int) =
        GameResult(gameType = "yahtzee", playerId = playerId, playerName = name,
            score = score, isWinner = false, isDraw = false, playedAt = at)

    @Test
    fun exportIgnoresResultsOfDeletedPlayers() = runBlocking {
        val aliceId = db.playerDao().insertPlayer(Player(name = "Alice", color = 0))
        db.gameResultDao().insertGameResults(listOf(
            result(aliceId, "Alice", 100, 10), result(999, "Ghost", 100, 20)
        ))
        val file = BackupSerializer.parse(repo.exportToJson("1"))
        assertEquals(1, file.games.single().results.size)
    }

    @Test
    fun mergeWritesResultsWithLocalPlayerIds() = runBlocking {
        // Device 2: Al has local id 3 (ids 1 and 2 were used and deleted)
        db.playerDao().insertPlayer(Player(name = "tmp1", color = 0))
        db.playerDao().insertPlayer(Player(name = "tmp2", color = 0))
        val al = db.playerDao().insertPlayer(Player(name = "Al", color = 0, uuid = "L-al"))
        db.playerDao().deletePlayer(db.playerDao().getPlayerByName("tmp1")!!)
        db.playerDao().deletePlayer(db.playerDao().getPlayerByName("tmp2")!!)

        // Device 1 export: Alice (id 1 there) and Charlie (id 2 there)
        val backup = BackupFile(
            appVersion = "1", exportedAt = 0,
            players = listOf(
                BackupPlayer("S-alice", emptyList(), "Alice", 0, 0),
                BackupPlayer("S-charlie", emptyList(), "Charlie", 0, 0)
            ),
            games = listOf(BackupGame("yahtzee", 777, listOf(
                BackupResult("S-alice", 245, true, false),
                BackupResult("S-charlie", 210, false, false)
            )))
        )

        val locals = repo.localPlayers()
        val analysis = repo.analyze(backup)
        val plan = BackupMergePlanner.plan(
            analysis,
            mapOf(
                "S-alice" to PlayerDecision.LinkTo(al),
                "S-charlie" to PlayerDecision.CreateNew("Charlie", 5)
            ),
            locals
        )
        repo.applyMerge(plan)

        val charlie = db.playerDao().getPlayerByName("Charlie")!!
        val results = db.gameResultDao().getAllResults()
        assertEquals(2, results.size)
        assertEquals(al, results.first { it.score == 245 }.playerId)          // local id, not 1
        assertEquals(charlie.id, results.first { it.score == 210 }.playerId)  // fresh local id
        assertTrue(results.all { it.playedAt == 777L })

        // Link remembered: the local player now also answers to the source uuid
        assertTrue("S-alice" in db.playerDao().getPlayerById(al)!!.identities())
        assertEquals("S-charlie", charlie.uuid)
    }

    @Test
    fun secondImportOfTheSameFileAddsNothing() = runBlocking {
        val backup = BackupFile(
            appVersion = "1", exportedAt = 0,
            players = listOf(BackupPlayer("S-bob", emptyList(), "Bob", 0, 0)),
            games = listOf(BackupGame("yahtzee", 5, listOf(BackupResult("S-bob", 10, false, false))))
        )
        val decisions = mapOf("S-bob" to PlayerDecision.CreateNew("Bob", 0))

        repo.applyMerge(BackupMergePlanner.plan(repo.analyze(backup), decisions, repo.localPlayers()))
        val second = repo.analyze(backup)

        assertTrue(second.sourcePlayers.single().status is SourceStatus.AutoMatched)  // same uuid
        assertTrue(second.newGames.isEmpty())
        assertEquals(1, second.alreadyPresentGames.size)
    }

    @Test
    fun overwriteReplacesEverythingAndKeepsUuids() = runBlocking {
        db.playerDao().insertPlayer(Player(name = "Old", color = 0))
        db.gameResultDao().insertGameResult(result(1, "Old", 1, 1))

        val backup = BackupFile(
            appVersion = "1", exportedAt = 0,
            players = listOf(BackupPlayer("S-bob", listOf("S-old"), "Bob", 7, 123)),
            games = listOf(BackupGame("yahtzee", 9, listOf(
                BackupResult("S-bob", 50, true, false), BackupResult("ghost", 1, false, false)
            )))
        )
        repo.overwrite(backup)

        val players = db.playerDao().getAllPlayersOnce()
        assertEquals(listOf("Bob"), players.map { it.name })
        assertEquals("S-bob", players.single().uuid)
        assertEquals(listOf("S-old"), players.single().extraUuids)
        assertEquals(123L, players.single().createdAt)
        val results = db.gameResultDao().getAllResults()
        assertEquals(1, results.size)                       // ghost ignored
        assertEquals(players.single().id, results.single().playerId)
    }

    @Test
    fun aUuidClashRollsTheWholeImportBack() = runBlocking {
        db.playerDao().insertPlayer(Player(name = "A", color = 0, uuid = "dup"))
        val plan = ImportPlan(
            newPlayers = listOf(NewPlayerPlan("s", "dup", emptyList(), "B", 0)),
            identityUpdates = emptyList(), results = emptyList(),
            newGames = emptyList(), alreadyPresentGames = emptyList()
        )
        try { repo.applyMerge(plan); fail("expected a constraint failure") } catch (_: Exception) {}
        assertEquals(1, db.playerDao().count())
    }

    @Test
    fun parseAndCheckRefusesUnknownGameTypes() {
        val json = BackupSerializer.toJson(BackupFile(
            appVersion = "1", exportedAt = 0,
            players = listOf(BackupPlayer("a", emptyList(), "A", 0, 0)),
            games = listOf(BackupGame("future_game", 1, listOf(BackupResult("a", 1, false, false))))
        ))
        assertThrows(BackupException.UnknownGameTypes::class.java) { repo.parseAndCheck(json) }
    }
}
