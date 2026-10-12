package com.github.trivialloop.scorehub.data.backup

import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.data.Player
import org.junit.Assert.*
import org.junit.Test

class BackupSerializerTest {

    private fun sample() = BackupFile(
        appVersion = "1.17.0",
        exportedAt = 1750000000000,
        players = listOf(
            BackupPlayer("a1", emptyList(), "Alice", -1739917, 1L),
            BackupPlayer("b2", listOf("b9", "b8"), "Bob", -16537100, 2L)
        ),
        games = listOf(
            BackupGame("yahtzee", 1741780000000, listOf(
                BackupResult("a1", 245, true, false),
                BackupResult("b2", 198, false, false)
            ))
        )
    )

    @Test
    fun `round trip keeps every field`() {
        val file = sample()
        assertEquals(file, BackupSerializer.parse(BackupSerializer.toJson(file)))
    }

    @Test
    fun `round trip keeps large playedAt unchanged`() {
        val parsed = BackupSerializer.parse(BackupSerializer.toJson(sample()))
        assertEquals(1741780000000, parsed.games[0].playedAt)
    }

    @Test
    fun `random text is not a backup`() {
        assertThrows(BackupException.NotABackup::class.java) { BackupSerializer.parse("hello") }
    }

    @Test
    fun `json without the format signature is not a backup`() {
        assertThrows(BackupException.NotABackup::class.java) { BackupSerializer.parse("""{"a":1}""") }
    }

    @Test
    fun `newer format version is refused`() {
        val json = BackupSerializer.toJson(sample().copy(formatVersion = BACKUP_FORMAT_VERSION + 1))
        val e = assertThrows(BackupException.UnsupportedVersion::class.java) { BackupSerializer.parse(json) }
        assertEquals(BACKUP_FORMAT_VERSION + 1, e.found)
    }

    @Test
    fun `missing section is reported as malformed`() {
        val json = """{"format":"scorehub-backup","formatVersion":1}"""
        assertThrows(BackupException.Malformed::class.java) { BackupSerializer.parse(json) }
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = BackupSerializer.toJson(sample())
            .replaceFirst("\"players\"", "\"futureField\": 42, \"players\"")
        assertEquals(sample(), BackupSerializer.parse(json))
    }

    @Test
    fun `missing extraUuids defaults to empty`() {
        val json = """
            {"format":"scorehub-backup","formatVersion":1,"appVersion":"1","exportedAt":0,
             "players":[{"uuid":"a","name":"A","color":0,"createdAt":0}],"games":[]}
        """.trimIndent()
        assertTrue(BackupSerializer.parse(json).players[0].extraUuids.isEmpty())
    }

    @Test
    fun `unknownGameTypes lists types missing from the registry`() {
        val file = sample()
        assertEquals(setOf("yahtzee"), BackupSerializer.unknownGameTypes(file, emptySet()))
        assertTrue(BackupSerializer.unknownGameTypes(file, setOf("yahtzee")).isEmpty())
    }

    // ─── Exporter ─────────────────────────────────────────────────────────────

    private fun player(id: Long, uuid: String, name: String) =
        Player(id = id, name = name, color = 0, uuid = uuid)

    private fun result(id: Long, playerId: Long, type: String, at: Long, score: Int) =
        GameResult(id = id, gameType = type, playerId = playerId, playerName = "x",
            score = score, isWinner = false, isDraw = false, playedAt = at)

    @Test
    fun `exporter groups rows of one session into one game`() {
        val file = BackupExporter.build(
            players = listOf(player(1, "a1", "Alice"), player(2, "b2", "Bob")),
            results = listOf(
                result(1, 1, "yahtzee", 100, 10), result(2, 2, "yahtzee", 100, 20),
                result(3, 1, "skyjo", 200, 30)
            ),
            appVersion = "1", exportedAt = 0
        )
        assertEquals(2, file.games.size)
        assertEquals(listOf("a1", "b2"), file.games[0].results.map { it.playerUuid })
    }

    @Test
    fun `exporter references players by uuid and ignores deleted players`() {
        val file = BackupExporter.build(
            players = listOf(player(1, "a1", "Alice")),
            results = listOf(result(1, 1, "yahtzee", 100, 10), result(2, 99, "yahtzee", 100, 20)),
            appVersion = "1", exportedAt = 0
        )
        assertEquals(1, file.games.single().results.size)
        assertEquals("a1", file.games.single().results.single().playerUuid)
    }

    @Test
    fun `exporter drops a game whose players were all deleted`() {
        val file = BackupExporter.build(
            players = listOf(player(1, "a1", "Alice")),
            results = listOf(result(1, 99, "yahtzee", 100, 10)),
            appVersion = "1", exportedAt = 0
        )
        assertTrue(file.games.isEmpty())
    }

    @Test
    fun `exporter keeps playedAt untouched and exports players without games`() {
        val file = BackupExporter.build(
            players = listOf(player(1, "a1", "Alice"), player(2, "b2", "Bob")),
            results = listOf(result(1, 1, "yahtzee", 123456789, 10)),
            appVersion = "1", exportedAt = 0
        )
        assertEquals(123456789, file.games.single().playedAt)
        assertEquals(2, file.players.size)
    }
}
