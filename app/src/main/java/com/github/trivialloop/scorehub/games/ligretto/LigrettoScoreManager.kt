package com.github.trivialloop.scorehub.games.ligretto

/**
 * Ligretto scoring.
 *
 * At the end of each real-time round, every player reports:
 *  - [cardsPlayed] : how many cards they placed onto the shared central piles this round.
 *  - [stackLeft]   : how many cards remained in their own Ligretto stack (0-10) when the round ended.
 *
 * Round score = cardsPlayed - 2 * stackLeft.
 *
 * [finisherId] is purely cosmetic (tints the round label with the color of whoever called
 * "Ligretto!" first) — it does not affect scoring, since every player's score only depends
 * on their own cardsPlayed/stackLeft values.
 */
data class LigrettoRound(
    val roundNumber: Int,
    val cardsPlayed: MutableMap<Long, Int?> = mutableMapOf(),
    val stackLeft: MutableMap<Long, Int?> = mutableMapOf(),
    var finisherId: Long? = null
) {
    fun allScoresEntered(playerIds: List<Long>): Boolean =
        playerIds.all { cardsPlayed[it] != null && stackLeft[it] != null }

    fun isComplete(playerIds: List<Long>): Boolean = allScoresEntered(playerIds)

    /** Score for a single player this round, or null if not yet fully entered. */
    fun getScore(playerId: Long): Int? {
        val played = cardsPlayed[playerId] ?: return null
        val left = stackLeft[playerId] ?: return null
        return played - 2 * left
    }
}

data class LigrettoPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<LigrettoRound>): Int =
        rounds.sumOf { it.getScore(playerId) ?: 0 }
}

/** Display color role for a completed round's score cell, relative to the other players that round. */
enum class LigrettoCellColor { DEFAULT, GREEN, RED }

fun LigrettoRound.getCellColor(playerId: Long, playerIds: List<Long>): LigrettoCellColor {
    if (!allScoresEntered(playerIds)) return LigrettoCellColor.DEFAULT
    val scores = playerIds.mapNotNull { id -> getScore(id) }
    if (scores.size < 2) return LigrettoCellColor.DEFAULT
    val min = scores.min()
    val max = scores.max()
    if (min == max) return LigrettoCellColor.DEFAULT
    val mine = getScore(playerId) ?: return LigrettoCellColor.DEFAULT
    return when (mine) {
        max -> LigrettoCellColor.GREEN
        min -> LigrettoCellColor.RED
        else -> LigrettoCellColor.DEFAULT
    }
}
