package com.github.trivialloop.scorehub.data.backup

import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.data.Player
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

sealed class BackupException(message: String) : Exception(message) {
    class NotABackup : BackupException("Not a ScoreHub backup file")
    class UnsupportedVersion(val found: Int, val supported: Int) :
        BackupException("Backup format $found is newer than supported $supported")
    class Malformed(detail: String) : BackupException("Malformed backup: $detail")
    class UnknownGameTypes(val gameTypes: Set<String>) :
        BackupException("Unknown game types: $gameTypes")
}

object BackupSerializer {

    fun toJson(file: BackupFile): String {
        val root = JSONObject()
            .put("format", file.format)
            .put("formatVersion", file.formatVersion)
            .put("appVersion", file.appVersion)
            .put("exportedAt", file.exportedAt)
            .put("players", JSONArray().also { arr ->
                file.players.forEach { p ->
                    arr.put(
                        JSONObject()
                            .put("uuid", p.uuid)
                            .put("extraUuids", JSONArray(p.extraUuids))
                            .put("name", p.name)
                            .put("color", p.color)
                            .put("createdAt", p.createdAt)
                    )
                }
            })
            .put("games", JSONArray().also { arr ->
                file.games.forEach { g ->
                    arr.put(
                        JSONObject()
                            .put("gameType", g.gameType)
                            .put("playedAt", g.playedAt)
                            .put("results", JSONArray().also { rs ->
                                g.results.forEach { r ->
                                    rs.put(
                                        JSONObject()
                                            .put("playerUuid", r.playerUuid)
                                            .put("score", r.score)
                                            .put("isWinner", r.isWinner)
                                            .put("isDraw", r.isDraw)
                                    )
                                }
                            })
                    )
                }
            })
        return root.toString(2)
    }

    /** Unknown JSON fields are ignored on purpose (forward compatibility). */
    fun parse(text: String): BackupFile {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException.NotABackup()
        }
        if (root.optString("format") != BACKUP_FORMAT) throw BackupException.NotABackup()

        val version = root.optInt("formatVersion", -1)
        if (version < 1) throw BackupException.Malformed("formatVersion")
        if (version > BACKUP_FORMAT_VERSION) {
            throw BackupException.UnsupportedVersion(version, BACKUP_FORMAT_VERSION)
        }

        try {
            val players = root.getJSONArray("players").objects().map { p ->
                BackupPlayer(
                    uuid = p.getString("uuid"),
                    extraUuids = p.optJSONArray("extraUuids")?.strings() ?: emptyList(),
                    name = p.getString("name"),
                    color = p.getInt("color"),
                    createdAt = p.getLong("createdAt")
                )
            }
            val games = root.getJSONArray("games").objects().map { g ->
                BackupGame(
                    gameType = g.getString("gameType"),
                    playedAt = g.getLong("playedAt"),
                    results = g.getJSONArray("results").objects().map { r ->
                        BackupResult(
                            playerUuid = r.getString("playerUuid"),
                            score = r.getInt("score"),
                            isWinner = r.getBoolean("isWinner"),
                            isDraw = r.getBoolean("isDraw")
                        )
                    }
                )
            }
            return BackupFile(
                format = BACKUP_FORMAT,
                formatVersion = version,
                appVersion = root.optString("appVersion"),
                exportedAt = root.optLong("exportedAt"),
                players = players,
                games = games
            )
        } catch (e: JSONException) {
            throw BackupException.Malformed(e.message ?: "invalid structure")
        }
    }

    /** Game types found in the file that this app version does not know. */
    fun unknownGameTypes(file: BackupFile, knownGameTypes: Set<String>): Set<String> =
        file.games.map { it.gameType }.filter { it !in knownGameTypes }.toSet()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}

object BackupExporter {

    /**
     * Builds the backup from raw DB content.
     * Results whose player no longer exists (deleted players) are ignored.
     */
    fun build(
        players: List<Player>,
        results: List<GameResult>,
        appVersion: String,
        exportedAt: Long
    ): BackupFile {
        val uuidById = players.associate { it.id to it.uuid }

        val games = results
            .filter { it.playerId in uuidById }
            .groupBy { it.gameType to it.playedAt }
            .map { (key, rows) ->
                BackupGame(
                    gameType = key.first,
                    playedAt = key.second,
                    results = rows.sortedBy { it.id }.map {
                        BackupResult(uuidById.getValue(it.playerId), it.score, it.isWinner, it.isDraw)
                    }
                )
            }
            .sortedBy { it.playedAt }

        return BackupFile(
            appVersion = appVersion,
            exportedAt = exportedAt,
            players = players.map {
                BackupPlayer(it.uuid, it.extraUuids, it.name, it.color, it.createdAt)
            },
            games = games
        )
    }
}
