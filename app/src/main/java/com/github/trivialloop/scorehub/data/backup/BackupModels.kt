package com.github.trivialloop.scorehub.data.backup

const val BACKUP_FORMAT = "scorehub-backup"
const val BACKUP_FORMAT_VERSION = 1

/** Same limit as android:maxLength in dialog_add_player.xml. */
const val PLAYER_NAME_MAX_LENGTH = 20

data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val appVersion: String,
    val exportedAt: Long,
    val players: List<BackupPlayer>,
    val games: List<BackupGame>
)

data class BackupPlayer(
    val uuid: String,
    val extraUuids: List<String>,
    val name: String,
    val color: Int,
    val createdAt: Long
) {
    fun identities(): Set<String> = extraUuids.toSet() + uuid
}

/** One game session = every row sharing (gameType, playedAt). */
data class BackupGame(
    val gameType: String,
    val playedAt: Long,
    val results: List<BackupResult>
)

data class BackupResult(
    val playerUuid: String,
    val score: Int,
    val isWinner: Boolean,
    val isDraw: Boolean
)
