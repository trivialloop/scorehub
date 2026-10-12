package com.github.trivialloop.scorehub.ui

import android.content.Context
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.backup.BackupException

/** Turns backup exceptions into user-facing messages. */
object BackupMessages {
    fun of(context: Context, e: Throwable): String = when (e) {
        is BackupException.NotABackup -> context.getString(R.string.backup_error_not_backup)
        is BackupException.UnsupportedVersion -> context.getString(R.string.backup_error_newer_version)
        is BackupException.UnknownGameTypes ->
            context.getString(R.string.backup_error_unknown_games, e.gameTypes.sorted().joinToString(", "))
        is BackupException.Malformed -> context.getString(R.string.backup_error_malformed)
        is java.io.IOException -> context.getString(R.string.backup_error_read)
        else -> context.getString(R.string.backup_error_generic, e.message ?: e.javaClass.simpleName)
    }
}
