package com.github.trivialloop.scorehub.games.skyjo

data class SkyjoRound(
    val roundNumber: Int,
    val scores: MutableMap<Long, Int?> = mutableMapOf(),       // playerId -> score brut
    val finalScores: MutableMap<Long, Int?> = mutableMapOf(),  // playerId -> score après pénalité
    var finisherId: Long? = null                               // joueur qui a terminé la manche
) {
    fun isComplete(playerIds: List<Long>): Boolean {
        return playerIds.all { finalScores[it] != null }
    }

    fun allScoresEntered(playerIds: List<Long>): Boolean {
        return playerIds.all { scores[it] != null }
    }

    /**
     * Calculates final scores with Skyjo rules:
     * - The player who ended the round (finisher) gets their score doubled if they don't have
     *   the strictly lowest score alone.
     * - The lowest score(s) are colored green, the highest red.
     */
    fun computeFinalScores(playerIds: List<Long>) {
        if (!allScoresEntered(playerIds)) return

        val rawScores = playerIds.mapNotNull { id -> scores[id]?.let { id to it } }.toMap()
        val minScore = rawScores.values.minOrNull() ?: return
        val maxScore = rawScores.values.maxOrNull() ?: return

        val playersWithMin = rawScores.filter { it.value == minScore }.keys
        val finisherIsAloneLowest = finisherId != null &&
                playersWithMin.size == 1 &&
                playersWithMin.contains(finisherId)

        for (id in playerIds) {
            val raw = scores[id] ?: continue
            val penalized = finisherId == id && !finisherIsAloneLowest
            finalScores[id] = if (penalized) raw * 2 else raw
        }
    }

    fun getCellColor(playerId: Long, playerIds: List<Long>): SkyjoCellColor {
        if (!allScoresEntered(playerIds)) return SkyjoCellColor.DEFAULT
        val raw = scores[playerId] ?: return SkyjoCellColor.DEFAULT
        val allRaw = playerIds.mapNotNull { scores[it] }
        val minScore = allRaw.minOrNull() ?: return SkyjoCellColor.DEFAULT
        val maxScore = allRaw.maxOrNull() ?: return SkyjoCellColor.DEFAULT

        val playersWithMin = playerIds.filter { scores[it] == minScore }
        val finisherIsAloneLowest = finisherId != null &&
                playersWithMin.size == 1 &&
                playersWithMin.contains(finisherId)

        return when {
            raw == maxScore && raw != minScore -> SkyjoCellColor.RED
            raw == minScore -> SkyjoCellColor.GREEN
            finisherId == playerId && !finisherIsAloneLowest -> SkyjoCellColor.ORANGE
            else -> SkyjoCellColor.DEFAULT
        }
    }
}

enum class SkyjoCellColor { DEFAULT, GREEN, ORANGE, RED }

data class SkyjoPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<SkyjoRound>): Int {
        return rounds.sumOf { it.finalScores[playerId] ?: 0 }
    }
}
