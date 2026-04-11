package com.github.trivialloop.scorehub.games.wingspan

data class WingspanPlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val scores: MutableMap<WingspanCategory, Int?> = mutableMapOf()
) {
    fun getTotal(): Int {
        return WingspanCategory.entries.sumOf { scores[it] ?: 0 }
    }

    fun isComplete(): Boolean {
        return WingspanCategory.entries.all { scores[it] != null }
    }
}

enum class WingspanCategory {
    BIRDS,
    BONUS_CARDS,
    END_OF_ROUND,
    EGGS,
    FOOD_ON_CARDS,
    TUCKED_CARDS;
}
