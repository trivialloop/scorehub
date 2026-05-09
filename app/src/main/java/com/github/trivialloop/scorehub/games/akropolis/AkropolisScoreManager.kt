package com.github.trivialloop.scorehub.games.akropolis

data class AkropolisPlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val stars: MutableMap<AkropolisColor, Int?> = mutableMapOf(),
    val districts: MutableMap<AkropolisColor, Int?> = mutableMapOf(),
    var stones: Int? = null
) {
    fun getDistrictTotal(color: AkropolisColor): Int {
        val s = stars[color] ?: return 0
        val d = districts[color] ?: return 0
        return s * d
    }

    fun getTotal(): Int {
        val colorTotal = AkropolisColor.entries.sumOf { getDistrictTotal(it) }
        return colorTotal + (stones ?: 0)
    }

    fun isComplete(): Boolean {
        return AkropolisColor.entries.all { stars[it] != null && districts[it] != null } &&
                stones != null
    }
}

enum class AkropolisColor {
    BLUE,
    YELLOW,
    RED,
    PURPLE,
    GREEN
}
