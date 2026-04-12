package com.github.trivialloop.scorehub.games.cactus

data class CactusRound(
    val roundNumber: Int,
    val rawScores: MutableMap<Long, Int?> = mutableMapOf(),  // score de manche (0–40)
    val points: MutableMap<Long, Int?> = mutableMapOf(),     // points calculés (0 ou 1)
    var finisherId: Long? = null                             // joueur qui a terminé la manche
) {
    fun allScoresEntered(playerIds: List<Long>): Boolean =
        playerIds.all { rawScores[it] != null }

    fun isComplete(playerIds: List<Long>): Boolean =
        playerIds.all { points[it] != null }

    /**
     * Computes points for each player based on raw scores.
     *
     * Finisher:
     *   - Gets 1 point if score <= 5 AND strictly lower than ALL other players.
     *   - Gets 0 otherwise.
     *
     * Other players:
     *   Case A — finisher WINS (got 1 pt):
     *     Non-finisher gets 1 pt if score == global min (all players).
     *     Since finisher won, their score IS the global min → no non-finisher matches → all get 0.
     *
     *   Case B — finisher LOSES (got 0 pt):
     *     Non-finisher gets 1 pt if score == min among NON-FINISHERS only.
     *     Ties are allowed.
     */
    fun computePoints(playerIds: List<Long>) {
        if (!allScoresEntered(playerIds)) return

        val raw = playerIds.mapNotNull { id -> rawScores[id]?.let { id to it } }.toMap()
        val finisherId = this.finisherId ?: return
        val finisherRaw = raw[finisherId] ?: return

        val nonFinisherIds = playerIds.filter { it != finisherId }
        val nonFinisherRaws = nonFinisherIds.mapNotNull { raw[it] }

        // Finisher: wins if score <= 5 AND strictly lower than all others
        val finisherWins = finisherRaw <= 5 && nonFinisherRaws.all { finisherRaw < it }
        points[finisherId] = if (finisherWins) 1 else 0

        if (finisherWins) {
            // Case A: finisher holds the global min → no non-finisher can match it
            for (id in nonFinisherIds) points[id] = 0
        } else {
            // Case B: award 1 pt to non-finisher(s) with the lowest score among themselves
            val minNonFinisher = nonFinisherRaws.minOrNull()
            for (id in nonFinisherIds) {
                val score = raw[id] ?: continue
                points[id] = if (minNonFinisher != null && score == minNonFinisher) 1 else 0
            }
        }
    }

    /**
     * Returns the display color for a player's cell, based on RAW SCORES.
     *
     * Finisher (background color):
     *   - GREEN if they got 1 point
     *   - RED   if they got 0 point
     *
     * Other players (text color), based on raw scores among ALL players:
     *   - GREEN  if raw score == global minimum
     *   - RED    if raw score == global maximum
     *   - DEFAULT otherwise
     */
    fun getCellColor(playerId: Long, playerIds: List<Long>): CactusCellColor {
        if (!allScoresEntered(playerIds)) return CactusCellColor.DEFAULT
        val raw = rawScores[playerId] ?: return CactusCellColor.DEFAULT
        val allRaw = playerIds.mapNotNull { rawScores[it] }
        val minScore = allRaw.minOrNull() ?: return CactusCellColor.DEFAULT
        val maxScore = allRaw.maxOrNull() ?: return CactusCellColor.DEFAULT

        return if (playerId == finisherId) {
            if (points[playerId] == 1) CactusCellColor.GREEN else CactusCellColor.RED
        } else {
            when (raw) {
                minScore -> CactusCellColor.GREEN
                maxScore -> CactusCellColor.RED
                else     -> CactusCellColor.DEFAULT
            }
        }
    }
}

enum class CactusCellColor { DEFAULT, GREEN, RED }

data class CactusPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<CactusRound>): Int =
        rounds.sumOf { it.points[playerId] ?: 0 }
}