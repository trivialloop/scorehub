package com.github.trivialloop.scorehub.data.backup

import com.github.trivialloop.scorehub.data.Player
import org.junit.Assert.*
import org.junit.Test

class BackupMergePlannerTest {

    private fun local(id: Long, uuid: String, name: String, extras: List<String> = emptyList()) =
        Player(id = id, name = name, color = 0, uuid = uuid, extraUuids = extras)

    private fun src(uuid: String, name: String, extras: List<String> = emptyList()) =
        BackupPlayer(uuid, extras, name, 0, 0L)

    private fun game(type: String, at: Long, vararg results: Pair<String, Int>) =
        BackupGame(type, at, results.map { BackupResult(it.first, it.second, false, false) })

    private fun backup(players: List<BackupPlayer>, games: List<BackupGame> = emptyList()) =
        BackupFile(appVersion = "1", exportedAt = 0, players = players, games = games)

    private fun analyze(b: BackupFile, locals: List<Player>, keys: Set<GameKey> = emptySet()) =
        BackupMergePlanner.analyze(b, locals, keys)

    private fun statusOf(a: ImportAnalysis, uuid: String) =
        a.sourcePlayers.first { it.player.uuid == uuid }.status

    // ─── Auto-matching ────────────────────────────────────────────────────────

    @Test
    fun `same uuid is matched automatically`() {
        val a = analyze(backup(listOf(src("u1", "Alice"))), listOf(local(1, "u1", "Al")))
        assertEquals(SourceStatus.AutoMatched(1, MatchReason.SAME_UUID), statusOf(a, "u1"))
    }

    @Test
    fun `source uuid found in local extra uuids is matched`() {
        val a = analyze(backup(listOf(src("x", "Alice"))), listOf(local(1, "u1", "Al", listOf("x"))))
        assertEquals(SourceStatus.AutoMatched(1, MatchReason.LINKED_UUID), statusOf(a, "x"))
    }

    @Test
    fun `local uuid found in source extra uuids is matched`() {
        val a = analyze(backup(listOf(src("s", "Alice", listOf("u1")))), listOf(local(1, "u1", "Al")))
        assertEquals(SourceStatus.AutoMatched(1, MatchReason.LINKED_UUID), statusOf(a, "s"))
    }

    @Test
    fun `unmatched source needs a decision`() {
        val a = analyze(backup(listOf(src("u9", "Charlie"))), listOf(local(1, "u1", "Al")))
        assertTrue(statusOf(a, "u9") is SourceStatus.NeedsDecision)
    }

    @Test
    fun `source matching two local players is ambiguous`() {
        val a = analyze(
            backup(listOf(src("s", "X", listOf("l1", "l2")))),
            listOf(local(1, "l1", "A"), local(2, "l2", "B"))
        )
        val st = statusOf(a, "s") as SourceStatus.NeedsDecision
        assertEquals(setOf(1L, 2L), st.conflictingLocalIds)
    }

    @Test
    fun `two sources matching the same local player are both ambiguous`() {
        val a = analyze(
            backup(listOf(src("s1", "A", listOf("l1")), src("s2", "B", listOf("l1")))),
            listOf(local(1, "l1", "Al"))
        )
        assertTrue(statusOf(a, "s1") is SourceStatus.NeedsDecision)
        assertTrue(statusOf(a, "s2") is SourceStatus.NeedsDecision)
    }

    // ─── Suggestions ──────────────────────────────────────────────────────────

    @Test
    fun `suggested name avoids existing local names`() {
        val a = analyze(backup(listOf(src("u9", "Charlie"))), listOf(local(1, "u1", "Charlie")))
        val st = statusOf(a, "u9") as SourceStatus.NeedsDecision
        assertEquals("Charlie (2)", st.suggestedName)
        assertEquals(1L, st.sameNameLocalId)
    }

    @Test
    fun `suggested names do not collide with each other`() {
        val a = analyze(backup(listOf(src("u1", "Sam"), src("u2", "Sam"))), emptyList())
        val names = a.playersNeedingDecision.map { (it.status as SourceStatus.NeedsDecision).suggestedName }
        assertEquals(listOf("Sam", "Sam (2)"), names)
    }

    @Test
    fun `uniqueName keeps the suffix within the max length`() {
        val base = "ABCDEFGHIJKLMNOPQRST" // 20 chars
        val result = BackupMergePlanner.uniqueName(base, setOf(base))
        assertEquals(PLAYER_NAME_MAX_LENGTH, result.length)
        assertTrue(result.endsWith(" (2)"))
    }

    @Test
    fun `uniqueName truncates names that are too long`() {
        assertEquals(20, BackupMergePlanner.uniqueName("A".repeat(30), emptySet()).length)
    }

    // ─── Games ────────────────────────────────────────────────────────────────

    @Test
    fun `games are split by gameType and playedAt`() {
        val a = analyze(
            backup(
                listOf(src("u1", "A")),
                listOf(game("yahtzee", 1, "u1" to 10), game("yahtzee", 2, "u1" to 20), game("skyjo", 1, "u1" to 5))
            ),
            listOf(local(1, "u1", "A")),
            keys = setOf(GameKey("yahtzee", 1))
        )
        assertEquals(listOf(GameKey("yahtzee", 2), GameKey("skyjo", 1)), a.newGames.map { GameKey(it.gameType, it.playedAt) })
        assertEquals(1, a.alreadyPresentGames.size)
    }

    @Test
    fun `a game partially known locally is ignored as a whole`() {
        val a = analyze(
            backup(listOf(src("u1", "A"), src("u2", "B")), listOf(game("yahtzee", 1, "u1" to 10, "u2" to 20))),
            listOf(local(1, "u1", "A")),
            keys = setOf(GameKey("yahtzee", 1))
        )
        assertTrue(a.newGames.isEmpty())
        assertEquals(1, a.alreadyPresentGames.size)
    }

    @Test
    fun `results of unknown players are dropped`() {
        val a = analyze(
            backup(listOf(src("u1", "A")), listOf(game("yahtzee", 1, "u1" to 10, "ghost" to 99))),
            emptyList()
        )
        assertEquals(1, a.newGames.single().results.size)
    }

    @Test
    fun `a game left without results is dropped`() {
        val a = analyze(backup(listOf(src("u1", "A")), listOf(game("yahtzee", 1, "ghost" to 99))), emptyList())
        assertTrue(a.newGames.isEmpty())
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    private fun newPlayerAnalysis(vararg names: String, locals: List<Player> = emptyList()) =
        analyze(backup(names.mapIndexed { i, n -> src("s$i", n) }), locals)

    @Test
    fun `undecided players block the import`() {
        val a = newPlayerAnalysis("Charlie")
        assertEquals(PlayerIssue.UNDECIDED, BackupMergePlanner.validate(a, emptyMap(), emptyList())["s0"])
    }

    @Test
    fun `auto matched players need no decision`() {
        val locals = listOf(local(1, "u1", "Al"))
        val a = analyze(backup(listOf(src("u1", "Alice"))), locals)
        assertTrue(BackupMergePlanner.validate(a, emptyMap(), locals).isEmpty())
    }

    @Test
    fun `valid link and valid new player produce no issue`() {
        val locals = listOf(local(1, "u1", "Al"))
        val a = newPlayerAnalysis("Bob", "Eve", locals = locals)
        val d = mapOf(
            "s0" to PlayerDecision.LinkTo(1),
            "s1" to PlayerDecision.CreateNew("Eve", 0)
        )
        assertTrue(BackupMergePlanner.validate(a, d, locals).isEmpty())
    }

    @Test
    fun `two sources cannot link to the same local player`() {
        val locals = listOf(local(1, "u1", "Al"))
        val a = newPlayerAnalysis("Bob", "Eve", locals = locals)
        val d = mapOf("s0" to PlayerDecision.LinkTo(1), "s1" to PlayerDecision.LinkTo(1))
        assertEquals(mapOf("s1" to PlayerIssue.LOCAL_ALREADY_USED), BackupMergePlanner.validate(a, d, locals))
    }

    @Test
    fun `a source cannot link to a local player already auto matched`() {
        val locals = listOf(local(1, "u1", "Al"))
        val a = analyze(backup(listOf(src("u1", "Alice"), src("s9", "Bob"))), locals)
        val d = mapOf("s9" to PlayerDecision.LinkTo(1))
        assertEquals(PlayerIssue.LOCAL_ALREADY_USED, BackupMergePlanner.validate(a, d, locals)["s9"])
    }

    @Test
    fun `link to an unknown local player is rejected`() {
        val a = newPlayerAnalysis("Bob")
        val d = mapOf("s0" to PlayerDecision.LinkTo(42))
        assertEquals(PlayerIssue.UNKNOWN_LOCAL, BackupMergePlanner.validate(a, d, emptyList())["s0"])
    }

    @Test
    fun `new player name colliding with a local player is rejected`() {
        val locals = listOf(local(1, "u1", "Bob"))
        val a = newPlayerAnalysis("Bobby", locals = locals)
        val d = mapOf("s0" to PlayerDecision.CreateNew("Bob", 0))
        assertEquals(PlayerIssue.NAME_TAKEN, BackupMergePlanner.validate(a, d, locals)["s0"])
    }

    @Test
    fun `new player name is trimmed before the collision check`() {
        val locals = listOf(local(1, "u1", "Bob"))
        val a = newPlayerAnalysis("X", locals = locals)
        val d = mapOf("s0" to PlayerDecision.CreateNew("  Bob ", 0))
        assertEquals(PlayerIssue.NAME_TAKEN, BackupMergePlanner.validate(a, d, locals)["s0"])
    }

    @Test
    fun `two new players cannot share a name`() {
        val a = newPlayerAnalysis("A", "B")
        val d = mapOf("s0" to PlayerDecision.CreateNew("Sam", 0), "s1" to PlayerDecision.CreateNew("Sam", 0))
        assertEquals(mapOf("s1" to PlayerIssue.NAME_TAKEN), BackupMergePlanner.validate(a, d, emptyList()))
    }

    @Test
    fun `empty and too long names are rejected`() {
        val a = newPlayerAnalysis("A", "B")
        val d = mapOf(
            "s0" to PlayerDecision.CreateNew("   ", 0),
            "s1" to PlayerDecision.CreateNew("X".repeat(21), 0)
        )
        val issues = BackupMergePlanner.validate(a, d, emptyList())
        assertEquals(PlayerIssue.NAME_EMPTY, issues["s0"])
        assertEquals(PlayerIssue.NAME_TOO_LONG, issues["s1"])
    }

    // ─── Plan ─────────────────────────────────────────────────────────────────

    @Test
    fun `plan refuses invalid decisions`() {
        val a = newPlayerAnalysis("Bob")
        assertThrows(IllegalArgumentException::class.java) {
            BackupMergePlanner.plan(a, emptyMap(), emptyList())
        }
    }

    @Test
    fun `new player inherits the source identity`() {
        val a = analyze(backup(listOf(src("c3", "Charlie", listOf("c9")))), emptyList())
        val plan = BackupMergePlanner.plan(a, mapOf("c3" to PlayerDecision.CreateNew("Charlie", 5)), emptyList())
        val p = plan.newPlayers.single()
        assertEquals("c3", p.uuid)
        assertEquals(listOf("c9"), p.extraUuids)
        assertEquals(5, p.color)
    }

    @Test
    fun `new player gets a fresh uuid when the source overlapped local players`() {
        val locals = listOf(local(1, "l1", "A"), local(2, "l2", "B"))
        val a = analyze(backup(listOf(src("s", "X", listOf("l1", "l2")))), locals)
        val plan = BackupMergePlanner.plan(
            a, mapOf("s" to PlayerDecision.CreateNew("X", 0)), locals, newUuid = { "fresh" }
        )
        assertEquals("fresh", plan.newPlayers.single().uuid)
        assertTrue(plan.newPlayers.single().extraUuids.isEmpty())
    }

    @Test
    fun `linking merges identities into the local player`() {
        // extras(L) = (extras(L) + S.uuid + extras(S)) - L.uuid
        val locals = listOf(local(1, "L", "Al", extras = listOf("a")))
        val a = analyze(backup(listOf(src("S", "Alice", extras = listOf("a", "L", "z")))), locals)
        // S shares "L" and "a" with the local player: unique overlap, auto matched
        val plan = BackupMergePlanner.plan(a, emptyMap(), locals)
        assertEquals(IdentityUpdate(1, listOf("a", "S", "z")), plan.identityUpdates.single())
    }

    @Test
    fun `manual link adds the source identities and keeps the local uuid`() {
        val locals = listOf(local(1, "L", "Al"))
        val a = analyze(backup(listOf(src("S", "Alice", extras = listOf("t")))), locals)
        val plan = BackupMergePlanner.plan(a, mapOf("S" to PlayerDecision.LinkTo(1)), locals)
        assertEquals(IdentityUpdate(1, listOf("S", "t")), plan.identityUpdates.single())
        assertTrue(plan.newPlayers.isEmpty())
    }

    @Test
    fun `no identity update when nothing changes`() {
        val locals = listOf(local(1, "u1", "Al"))
        val a = analyze(backup(listOf(src("u1", "Alice"))), locals)
        assertTrue(BackupMergePlanner.plan(a, emptyMap(), locals).identityUpdates.isEmpty())
    }

    @Test
    fun `results are resolved to local ids and new players, with playedAt unchanged`() {
        val locals = listOf(local(1, "u1", "Al"))
        val b = backup(
            listOf(src("u1", "Alice"), src("c3", "Charlie")),
            listOf(game("yahtzee", 777, "u1" to 245, "c3" to 210))
        )
        val a = analyze(b, locals)
        val plan = BackupMergePlanner.plan(a, mapOf("c3" to PlayerDecision.CreateNew("Charlie", 0)), locals)

        assertEquals(PlayerRef.Existing(1), plan.results[0].player)
        assertEquals("Al", plan.results[0].playerName)       // local name wins
        assertEquals(PlayerRef.New("c3"), plan.results[1].player)
        assertTrue(plan.results.all { it.playedAt == 777L })
        assertEquals(listOf(PreviewEntry("Al", 245), PreviewEntry("Charlie", 210)), plan.newGames.single().entries)
    }

    @Test
    fun `already present games produce no result to insert but appear in the preview`() {
        val locals = listOf(local(1, "u1", "Al"))
        val b = backup(listOf(src("u1", "Alice")), listOf(game("skyjo", 5, "u1" to 41)))
        val a = analyze(b, locals, keys = setOf(GameKey("skyjo", 5)))
        val plan = BackupMergePlanner.plan(a, emptyMap(), locals)
        assertTrue(plan.results.isEmpty())
        assertEquals(1, plan.alreadyPresentGames.size)
    }

    @Test
    fun `new games are sorted by date`() {
        val b = backup(listOf(src("u1", "A")), listOf(game("a", 30, "u1" to 1), game("b", 10, "u1" to 1)))
        val plan = BackupMergePlanner.plan(analyze(b, listOf(local(1, "u1", "A"))), emptyMap(), listOf(local(1, "u1", "A")))
        assertEquals(listOf(10L, 30L), plan.newGames.map { it.playedAt })
    }
}
