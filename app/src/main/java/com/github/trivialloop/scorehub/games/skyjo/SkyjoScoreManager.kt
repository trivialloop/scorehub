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

    /**
     * Returns the display color for a player's cell in a completed round.
     *
     * Finisher (background color):
     *   - GREEN if they are the strictly sole lowest scorer
     *   - RED   otherwise (tied for lowest, or higher than lowest)
     *
     * Other players (text color):
     *   - GREEN if their raw score equals the global minimum (finisher included)
     *   - RED   if their raw score equals the global maximum (finisher included)
     *   - DEFAULT otherwise
     */
    fun getCellColor(playerId: Long, playerIds: List<Long>): SkyjoCellColor {
        if (!allScoresEntered(playerIds)) return SkyjoCellColor.DEFAULT
        val raw = scores[playerId] ?: return SkyjoCellColor.DEFAULT
        val allRaw = playerIds.mapNotNull { scores[it] }
        val minScore = allRaw.minOrNull() ?: return SkyjoCellColor.DEFAULT
        val maxScore = allRaw.maxOrNull() ?: return SkyjoCellColor.DEFAULT

        val finisherIsStrictlyLowest = finisherId != null &&
                scores[finisherId] == minScore &&
                allRaw.count { it == minScore } == 1

        return if (playerId == finisherId) {
            // Finisher: background color indicator
            if (finisherIsStrictlyLowest) SkyjoCellColor.GREEN else SkyjoCellColor.RED
        } else {
            // Regular player: text color indicator
            when (raw) {
                minScore -> SkyjoCellColor.GREEN
                maxScore -> SkyjoCellColor.RED
                else     -> SkyjoCellColor.DEFAULT
            }
        }
    }
}

enum class SkyjoCellColor { DEFAULT, GREEN, RED }

data class SkyjoPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<SkyjoRound>): Int {
        return rounds.sumOf { it.finalScores[playerId] ?: 0 }
    }
}
