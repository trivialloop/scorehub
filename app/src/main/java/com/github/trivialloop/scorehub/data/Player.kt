package com.github.trivialloop.scorehub.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.util.UUID

@Entity(tableName = "players", indices = [Index(value = ["uuid"], unique = true)])
data class Player(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int, // Color ARGB
    val createdAt: Long = System.currentTimeMillis(),
    /** Stable identity, survives export/import. The SQL default only exists for the migration. */
    @ColumnInfo(defaultValue = "''")
    val uuid: String = UUID.randomUUID().toString(),
    /** Identities of the same person on other devices (linked during an import). */
    @ColumnInfo(defaultValue = "''")
    val extraUuids: List<String> = emptyList()
) {
    /** Every UUID that designates this player. */
    fun identities(): Set<String> = extraUuids.toSet() + uuid
}

class StringListConverter {
    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString(SEPARATOR)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object { const val SEPARATOR = "," }   // UUIDs never contain a comma
}
