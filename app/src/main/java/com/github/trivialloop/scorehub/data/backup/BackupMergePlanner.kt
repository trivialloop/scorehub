package com.github.trivialloop.scorehub.data.backup

import com.github.trivialloop.scorehub.data.Player
import java.util.UUID

data class GameKey(val gameType: String, val playedAt: Long)

/** What the user chose for a source player that could not be matched automatically. */
sealed interface PlayerDecision {
    data class LinkTo(val localPlayerId: Long) : PlayerDecision
    data class CreateNew(val name: String, val color: Int) : PlayerDecision
}

enum class MatchReason { SAME_UUID, LINKED_UUID }

sealed interface SourceStatus {
    /** Exactly one local player shares an identity, and no other source competes for it. */
    data class AutoMatched(val localPlayerId: Long, val reason: MatchReason) : SourceStatus
    /**
     * The user must decide.
     * @param suggestedName      collision-free name to pre-fill for "create new player"
     * @param conflictingLocalIds local players sharing an identity (ambiguous case), may be empty
     * @param sameNameLocalId    local player with the same name: a hint only, never pre-selected
     */
    data class NeedsDecision(
        val suggestedName: String,
        val conflictingLocalIds: Set<Long>,
        val sameNameLocalId: Long?
    ) : SourceStatus
}

data class SourcePlayer(val player: BackupPlayer, val status: SourceStatus)

data class ImportAnalysis(
    val sourcePlayers: List<SourcePlayer>,
    val newGames: List<BackupGame>,
    val alreadyPresentGames: List<BackupGame>
) {
    val playersNeedingDecision: List<SourcePlayer>
        get() = sourcePlayers.filter { it.status is SourceStatus.NeedsDecision }
}

enum class PlayerIssue { UNDECIDED, UNKNOWN_LOCAL, LOCAL_ALREADY_USED, NAME_EMPTY, NAME_TOO_LONG, NAME_TAKEN }

// ─── Plan ────────────────────────────────────────────────────────────────────

sealed interface PlayerRef {
    data class Existing(val playerId: Long) : PlayerRef
    /** Player created by this import, identified by the source uuid until it gets its row id. */
    data class New(val sourceUuid: String) : PlayerRef
}

data class NewPlayerPlan(
    val sourceUuid: String,
    val uuid: String,
    val extraUuids: List<String>,
    val name: String,
    val color: Int
)

data class IdentityUpdate(val localPlayerId: Long, val extraUuids: List<String>)

data class ResultPlan(
    val gameType: String,
    val playedAt: Long,          // never regenerated
    val player: PlayerRef,
    val playerName: String,
    val score: Int,
    val isWinner: Boolean,
    val isDraw: Boolean
)

data class PreviewEntry(val playerName: String, val score: Int)
data class PreviewGame(val gameType: String, val playedAt: Long, val entries: List<PreviewEntry>)

data class ImportPlan(
    val newPlayers: List<NewPlayerPlan>,
    val identityUpdates: List<IdentityUpdate>,
    val results: List<ResultPlan>,
    val newGames: List<PreviewGame>,
    val alreadyPresentGames: List<PreviewGame>
)

// ─── Planner ─────────────────────────────────────────────────────────────────

object BackupMergePlanner {

    /** Step 1: auto-match players and split games into new / already present. */
    fun analyze(
        backup: BackupFile,
        localPlayers: List<Player>,
        localGameKeys: Set<GameKey>
    ): ImportAnalysis {
        val candidates: Map<String, List<Player>> = backup.players.associate { s ->
            s.uuid to localPlayers.filter { it.identities().intersect(s.identities()).isNotEmpty() }
        }
        val sourcesPerLocal = candidates.values.flatten().groupingBy { it.id }.eachCount()

        // Auto only when the match is unambiguous in both directions
        val auto: Map<String, Long> = backup.players.mapNotNull { s ->
            val c = candidates.getValue(s.uuid)
            if (c.size == 1 && sourcesPerLocal[c[0].id] == 1) s.uuid to c[0].id else null
        }.toMap()
        val autoLocalIds = auto.values.toSet()

        val takenNames = localPlayers.map { it.name }.toMutableSet()
        val sourcePlayers = backup.players.map { s ->
            val localId = auto[s.uuid]
            if (localId != null) {
                val local = localPlayers.first { it.id == localId }
                val reason = if (local.uuid == s.uuid) MatchReason.SAME_UUID else MatchReason.LINKED_UUID
                SourcePlayer(s, SourceStatus.AutoMatched(localId, reason))
            } else {
                val suggested = uniqueName(s.name, takenNames)
                takenNames.add(suggested)
                SourcePlayer(
                    s,
                    SourceStatus.NeedsDecision(
                        suggestedName = suggested,
                        conflictingLocalIds = candidates.getValue(s.uuid).map { it.id }.toSet(),
                        sameNameLocalId = localPlayers
                            .firstOrNull { it.name == s.name.trim() && it.id !in autoLocalIds }?.id
                    )
                )
            }
        }

        val knownUuids = backup.players.map { it.uuid }.toSet()
        val games = backup.games
            .map { g -> g.copy(results = g.results.filter { it.playerUuid in knownUuids }) }
            .filter { it.results.isNotEmpty() }
            .distinctBy { GameKey(it.gameType, it.playedAt) }

        val (present, new) = games.partition { GameKey(it.gameType, it.playedAt) in localGameKeys }
        return ImportAnalysis(sourcePlayers, new, present)
    }

    /** Step 2: live validation. Empty map = the Import button can be enabled. Keys are source uuids. */
    fun validate(
        analysis: ImportAnalysis,
        decisions: Map<String, PlayerDecision>,
        localPlayers: List<Player>
    ): Map<String, PlayerIssue> {
        val issues = mutableMapOf<String, PlayerIssue>()
        val usedLocalIds = analysis.sourcePlayers
            .mapNotNull { (it.status as? SourceStatus.AutoMatched)?.localPlayerId }
            .toMutableSet()
        val usedNames = localPlayers.map { it.name }.toMutableSet()
        val localIds = localPlayers.map { it.id }.toSet()

        for (sp in analysis.playersNeedingDecision) {
            val uuid = sp.player.uuid
            val issue: PlayerIssue? = when (val d = decisions[uuid]) {
                null -> PlayerIssue.UNDECIDED
                is PlayerDecision.LinkTo -> when {
                    d.localPlayerId !in localIds -> PlayerIssue.UNKNOWN_LOCAL
                    !usedLocalIds.add(d.localPlayerId) -> PlayerIssue.LOCAL_ALREADY_USED
                    else -> null
                }
                is PlayerDecision.CreateNew -> {
                    val name = d.name.trim()
                    when {
                        name.isEmpty() -> PlayerIssue.NAME_EMPTY
                        name.length > PLAYER_NAME_MAX_LENGTH -> PlayerIssue.NAME_TOO_LONG
                        !usedNames.add(name) -> PlayerIssue.NAME_TAKEN
                        else -> null
                    }
                }
            }
            if (issue != null) issues[uuid] = issue
        }
        return issues
    }

    /** Step 3: everything the executor has to write. Requires valid decisions. */
    fun plan(
        analysis: ImportAnalysis,
        decisions: Map<String, PlayerDecision>,
        localPlayers: List<Player>,
        newUuid: () -> String = { UUID.randomUUID().toString() }
    ): ImportPlan {
        val issues = validate(analysis, decisions, localPlayers)
        require(issues.isEmpty()) { "Invalid decisions: $issues" }

        val localById = localPlayers.associateBy { it.id }
        val resolved = mutableMapOf<String, Pair<PlayerRef, String>>()   // source uuid -> (ref, display name)
        val newPlayers = mutableListOf<NewPlayerPlan>()
        val updates = mutableListOf<IdentityUpdate>()

        for (sp in analysis.sourcePlayers) {
            val s = sp.player
            val status = sp.status
            val localId: Long? = when (status) {
                is SourceStatus.AutoMatched -> status.localPlayerId
                is SourceStatus.NeedsDecision ->
                    (decisions.getValue(s.uuid) as? PlayerDecision.LinkTo)?.localPlayerId
            }

            if (localId != null) {
                val local = localById.getValue(localId)
                resolved[s.uuid] = PlayerRef.Existing(local.id) to local.name
                // The local player keeps its uuid and absorbs every identity of the source
                val merged = (local.extraUuids + s.uuid + s.extraUuids)
                    .filter { it != local.uuid }
                    .distinct()
                if (merged != local.extraUuids) updates += IdentityUpdate(local.id, merged)
            } else {
                val create = decisions.getValue(s.uuid) as PlayerDecision.CreateNew
                val name = create.name.trim()
                // If the source overlapped local players and the user still says "new person",
                // reusing its uuid would clash with the unique index: give it a fresh identity.
                val inherits = (status as SourceStatus.NeedsDecision).conflictingLocalIds.isEmpty()
                newPlayers += NewPlayerPlan(
                    sourceUuid = s.uuid,
                    uuid = if (inherits) s.uuid else newUuid(),
                    extraUuids = if (inherits) s.extraUuids.filter { it != s.uuid }.distinct() else emptyList(),
                    name = name,
                    color = create.color
                )
                resolved[s.uuid] = PlayerRef.New(s.uuid) to name
            }
        }

        fun preview(g: BackupGame) = PreviewGame(
            g.gameType, g.playedAt,
            g.results.map { PreviewEntry(resolved.getValue(it.playerUuid).second, it.score) }
        )

        val newGames = analysis.newGames.sortedBy { it.playedAt }
        return ImportPlan(
            newPlayers = newPlayers,
            identityUpdates = updates,
            results = newGames.flatMap { g ->
                g.results.map { r ->
                    val (ref, name) = resolved.getValue(r.playerUuid)
                    ResultPlan(g.gameType, g.playedAt, ref, name, r.score, r.isWinner, r.isDraw)
                }
            },
            newGames = newGames.map(::preview),
            alreadyPresentGames = analysis.alreadyPresentGames.sortedBy { it.playedAt }.map(::preview)
        )
    }

    /** "Name", then "Name (2)", "Name (3)"... always within PLAYER_NAME_MAX_LENGTH. */
    fun uniqueName(base: String, taken: Set<String>): String {
        val clean = base.trim().ifEmpty { "Player" }.take(PLAYER_NAME_MAX_LENGTH)
        if (clean !in taken) return clean
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = clean.take(PLAYER_NAME_MAX_LENGTH - suffix.length).trimEnd() + suffix
            if (candidate !in taken) return candidate
            n++
        }
    }
}
