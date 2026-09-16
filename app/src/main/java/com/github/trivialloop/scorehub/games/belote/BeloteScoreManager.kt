package com.github.trivialloop.scorehub.games.belote

const val BELOTE_SCORE_LIMIT = 501
const val BELOTE_CONTRACT_THRESHOLD = 81
const val BELOTE_MAX_POINTS = 162
const val BELOTE_CAPOT_POINTS = 252
const val BELOTE_BELOTE_BONUS = 20

/**
 * A single hand (donne) of Belote.
 *
 * @param attackingTeam team index (0 or 1) that took the contract.
 * @param pointsMade raw card points won by the attacking team (0..162), including the
 *                    "dix de der" but NOT the belote bonus. Null when [isCapot] is true.
 * @param isCapot true when one team won all 8 tricks.
 * @param capotTeam the team that achieved the capot (only meaningful when [isCapot]).
 * @param beloteTeam the team that announced belote-rebelote this hand, if any.
 */
data class BeloteRound(
    val roundNumber: Int,
    val attackingTeam: Int,
    var pointsMade: Int? = null,
    var isCapot: Boolean = false,
    var capotTeam: Int? = null,
    var beloteTeam: Int? = null
) {
    val defendingTeam: Int get() = 1 - attackingTeam

    fun isComplete(): Boolean = isCapot || pointsMade != null

    /**
     * Computes each team's score for this hand.
     *
     * @param litigeCarry points pending from a previous 81-81 tie, to be awarded to
     *                    whichever team succeeds their contract this hand.
     * @return the per-team scores for this hand, and the updated carry for the next hand.
     */
    fun computeScores(litigeCarry: Int): Pair<Map<Int, Int>, Int> {
        val scores = mutableMapOf(0 to 0, 1 to 0)
        var newCarry = litigeCarry

        if (isCapot) {
            val winner = capotTeam ?: attackingTeam
            val loser = 1 - winner
            scores[winner] = BELOTE_CAPOT_POINTS + if (winner == attackingTeam) litigeCarry else 0
            scores[loser] = 0
            if (winner == attackingTeam) newCarry = 0
        } else {
            val made = pointsMade ?: 0
            when {
                made > BELOTE_CONTRACT_THRESHOLD -> {
                    scores[attackingTeam] = made + litigeCarry
                    scores[defendingTeam] = BELOTE_MAX_POINTS - made
                    newCarry = 0
                }
                made == BELOTE_CONTRACT_THRESHOLD -> {
                    scores[defendingTeam] = BELOTE_CONTRACT_THRESHOLD
                    scores[attackingTeam] = 0
                    newCarry = litigeCarry + BELOTE_CONTRACT_THRESHOLD
                }
                else -> {
                    scores[attackingTeam] = 0
                    scores[defendingTeam] = BELOTE_MAX_POINTS
                    // carry stays pending until an attacker succeeds
                }
            }
        }

        beloteTeam?.let { scores[it] = (scores[it] ?: 0) + BELOTE_BELOTE_BONUS }

        return scores.toMap() to newCarry
    }
}

/** Threads the litige carry through a list of hands. */
object BeloteScoring {
    fun computeRoundScores(rounds: List<BeloteRound>): List<Map<Int, Int>> {
        var carry = 0
        val results = mutableListOf<Map<Int, Int>>()
        for (round in rounds) {
            if (!round.isComplete()) {
                results.add(mapOf(0 to 0, 1 to 0))
                continue
            }
            val (scores, newCarry) = round.computeScores(carry)
            results.add(scores)
            carry = newCarry
        }
        return results
    }
}

data class BeloteTeamState(
    val teamIndex: Int,
    val player1Name: String,
    val player2Name: String,
    val teamColor: Int
) {
    val displayName: String get() = "$player1Name & $player2Name"

    fun getTotal(rounds: List<BeloteRound>): Int =
        BeloteScoring.computeRoundScores(rounds).sumOf { it[teamIndex] ?: 0 }
}
