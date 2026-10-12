package com.github.trivialloop.scorehub.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.github.trivialloop.scorehub.GameRegistry
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.data.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupRepository(private val db: AppDatabase) {

    // ─── Export ───────────────────────────────────────────────────────────────

    suspend fun exportToJson(appVersion: String, exportedAt: Long = System.currentTimeMillis()): String {
        val file = db.withTransaction {
            BackupExporter.build(
                players = db.playerDao().getAllPlayersOnce(),
                results = db.gameResultDao().getAllResults(),
                appVersion = appVersion,
                exportedAt = exportedAt
            )
        }
        return BackupSerializer.toJson(file)
    }

    // ─── Import: parsing and analysis ────────────────────────────────────────

    /**
     * Parses the file and refuses it if it contains a game this app version does not know
     * (the user must update the app).
     */
    fun parseAndCheck(
        text: String,
        knownGameTypes: Set<String> = GameRegistry.ALL_GAMES.map { it.gameType }.toSet()
    ): BackupFile {
        val file = BackupSerializer.parse(text)
        val unknown = BackupSerializer.unknownGameTypes(file, knownGameTypes)
        if (unknown.isNotEmpty()) throw BackupException.UnknownGameTypes(unknown)
        return file
    }

    /** True when there is nothing to lose: the import can skip the overwrite/merge question. */
    suspend fun isDatabaseEmpty(): Boolean =
        db.playerDao().count() == 0 && db.gameResultDao().count() == 0

    suspend fun localPlayers(): List<Player> = db.playerDao().getAllPlayersOnce()

    suspend fun analyze(backup: BackupFile): ImportAnalysis =
        BackupMergePlanner.analyze(
            backup = backup,
            localPlayers = db.playerDao().getAllPlayersOnce(),
            localGameKeys = db.gameResultDao().getAllGameKeys().toSet()
        )

    // ─── Import: merge ────────────────────────────────────────────────────────

    /**
     * Applies a plan built by [BackupMergePlanner.plan]. All or nothing.
     * New players are inserted first so their local ids are known when results are written.
     */
    suspend fun applyMerge(plan: ImportPlan) {
        db.withTransaction {
            val playerDao = db.playerDao()

            val newIds = mutableMapOf<String, Long>()   // source uuid -> local id
            for (p in plan.newPlayers) {
                newIds[p.sourceUuid] = playerDao.insertImported(
                    Player(name = p.name, color = p.color, uuid = p.uuid, extraUuids = p.extraUuids)
                )
            }
            for (u in plan.identityUpdates) {
                playerDao.updateExtraUuids(u.localPlayerId, u.extraUuids)
            }

            db.gameResultDao().insertGameResults(plan.results.map { r ->
                GameResult(
                    gameType = r.gameType,
                    playerId = when (val ref = r.player) {
                        is PlayerRef.Existing -> ref.playerId
                        is PlayerRef.New -> newIds.getValue(ref.sourceUuid)
                    },
                    playerName = r.playerName,
                    score = r.score,
                    isWinner = r.isWinner,
                    isDraw = r.isDraw,
                    playedAt = r.playedAt            // never regenerated
                )
            })
        }
    }

    // ─── Import: overwrite ────────────────────────────────────────────────────

    /**
     * Replaces everything with the content of [backup], keeping every uuid.
     * Local ids change, so callers must also call [BackupPrefs.resetPlayerOrders].
     */
    suspend fun overwrite(backup: BackupFile) {
        db.withTransaction {
            db.gameResultDao().deleteAll()
            db.playerDao().deleteAll()

            val idByUuid = mutableMapOf<String, Long>()
            val nameByUuid = mutableMapOf<String, String>()
            for (p in backup.players) {
                idByUuid[p.uuid] = db.playerDao().insertImported(
                    Player(
                        name = p.name, color = p.color, createdAt = p.createdAt,
                        uuid = p.uuid, extraUuids = p.extraUuids
                    )
                )
                nameByUuid[p.uuid] = p.name
            }

            val results = backup.games
                .distinctBy { GameKey(it.gameType, it.playedAt) }
                .flatMap { g ->
                    g.results.mapNotNull { r ->
                        val id = idByUuid[r.playerUuid] ?: return@mapNotNull null   // unknown player: ignored
                        GameResult(
                            gameType = g.gameType, playerId = id,
                            playerName = nameByUuid.getValue(r.playerUuid),
                            score = r.score, isWinner = r.isWinner, isDraw = r.isDraw,
                            playedAt = g.playedAt
                        )
                    }
                }
            db.gameResultDao().insertGameResults(results)
        }
    }
}

/** Reading and writing the backup file through the Storage Access Framework (no permission needed). */
object BackupFiles {
    suspend fun write(resolver: ContentResolver, uri: Uri, json: String) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: throw java.io.IOException("Cannot open $uri")
    }

    suspend fun read(resolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw java.io.IOException("Cannot open $uri")
    }
}

/** Local player ids change after an overwrite, so the saved "last player order" becomes meaningless. */
object BackupPrefs {
    private val PREFS = listOf(
        "akropolis_prefs", "belote_prefs", "cactus_prefs", "cribbage_prefs", "escoba_prefs",
        "farkle_prefs", "flip7_prefs", "freegame_prefs", "hanginggardens_prefs", "harmonies_prefs",
        "ligretto_prefs", "ohhell_prefs", "qwixx_prefs", "sevenwonders_prefs", "skyjo_prefs",
        "tarot_prefs", "tickettoride_prefs", "wingspan_prefs", "yahtzee_prefs"
    )

    fun resetPlayerOrders(context: Context) {
        PREFS.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().remove("last_player_order").apply()
        }
    }
}
