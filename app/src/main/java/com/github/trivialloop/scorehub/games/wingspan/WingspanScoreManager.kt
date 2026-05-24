package com.github.trivialloop.scorehub.games.wingspan

data class WingspanPlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val scores: MutableMap<WingspanCategory, Int?> = mutableMapOf()
) {
    fun getTotal(): Int = WingspanCategory.entries.sumOf { scores[it] ?: 0 }
    fun isComplete(): Boolean = WingspanCategory.entries.all { scores[it] != null }
}

enum class WingspanCategory {
    BIRDS_FOREST,     // Forest habitat — green   (0–45)
    BIRDS_GRASSLAND,  // Grassland habitat — yellow (0–45)
    BIRDS_WETLAND,    // Wetland habitat — blue   (0–45)
    BONUS_CARDS,      // 0–30
    END_OF_ROUND,     // 0–35
    EGGS,             // 0–40
    FOOD_ON_CARDS,    // 0–20
    TUCKED_CARDS;     // 0–25

    fun getPossibleValues(): List<Int> = when (this) {
        BIRDS_FOREST,
        BIRDS_GRASSLAND,
        BIRDS_WETLAND  -> (0..45).toList()
        BONUS_CARDS    -> (0..30).toList()
        END_OF_ROUND   -> (0..35).toList()
        EGGS           -> (0..40).toList()
        FOOD_ON_CARDS  -> (0..20).toList()
        TUCKED_CARDS   -> (0..25).toList()
    }
}
