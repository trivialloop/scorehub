package com.github.trivialloop.scorehub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [Player::class, GameResult::class, ScoreSheet::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameResultDao(): GameResultDao
    abstract fun scoreSheetDao(): ScoreSheetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Adds players.uuid (unique) and players.extraUuids. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE players ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE players ADD COLUMN extraUuids TEXT NOT NULL DEFAULT ''")

                // Collect ids first and close the cursor before updating the table
                val ids = mutableListOf<Long>()
                db.query("SELECT id FROM players").use { c ->
                    while (c.moveToNext()) ids.add(c.getLong(0))
                }
                ids.forEach { id ->
                    db.execSQL(
                        "UPDATE players SET uuid = ? WHERE id = ?",
                        arrayOf<Any>(UUID.randomUUID().toString(), id)
                    )
                }

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_players_uuid ON players (uuid)")
            }
        }

        /** Adds the score_sheets table. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `score_sheets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`gameType` TEXT NOT NULL, `playedAt` INTEGER NOT NULL, " +
                        "`fileName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_score_sheets_gameType` ON `score_sheets` (`gameType`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scorehub_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
