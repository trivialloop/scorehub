package com.github.trivialloop.scorehub.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY name ASC")
    fun getAllPlayers(): Flow<List<Player>>
    
    @Query("SELECT * FROM players WHERE id = :playerId")
    suspend fun getPlayerById(playerId: Long): Player?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: Player): Long
    
    @Update
    suspend fun updatePlayer(player: Player)
    
    @Delete
    suspend fun deletePlayer(player: Player)
    
    @Query("SELECT * FROM players WHERE name = :name LIMIT 1")
    suspend fun getPlayerByName(name: String): Player?

    @Query("SELECT * FROM players ORDER BY id ASC")
    suspend fun getAllPlayersOnce(): List<Player>

    @Query("UPDATE players SET extraUuids = :extraUuids WHERE id = :id")
    suspend fun updateExtraUuids(id: Long, extraUuids: List<String>)

    /** ABORT (not REPLACE): a uuid clash must fail the whole import, never delete a player. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImported(player: Player): Long

    @Query("DELETE FROM players")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM players")
    suspend fun count(): Int
}
