package com.github.trivialloop.scorehub.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int, // Couleur ARGB
    val createdAt: Long = System.currentTimeMillis()
)
