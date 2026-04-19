package com.github.trivialloop.scorehub.games.tarot

/**
 * Tarot scoring — Official French Tarot Federation rules.
 *
 * Zero-sum property: the sum of all player scores for any round equals 0.
 *
 * Formula:
 *   contractBase = (|pointsDiff| + 25) × contractMultiplier
 *   poigneeBonus = flat bonus per defender (goes to winning camp)
 *   petitPerDefender = 10 × contractMultiplier
 *   chelemPerDefender = 200 or 400
 *
 *   Each defender receives ±(contractBase + poigneeBonus) ± petit ± chelem
 *   Declarer receives exactly the negation of all defender totals (zero-sum).
 *
 * 3 players : 2 defenders → declarer = −(2 × defenderScore)
 * 4 players : 3 defenders → declarer = −(3 × defenderScore)
 * 5 players (partner) : 3 defenders + 1 partner (1×) → declarer balances all
 * 5 players (solo)    : 4 defenders → declarer = −(4 × defenderScore)
 */

enum class TarotContract(val multiplier: Int) {
    PRISE(1),
    GARDE(2),
    GARDE_SANS(4),
    GARDE_CONTRE(6)
}

enum class TarotPoigneeLevel(val bonus: Int) {
    NONE(0),
    SIMPLE(20),
    DOUBLE(30),
    TRIPLE(40)
}

enum class TarotChelem {
    NONE,
    ANNOUNCED_SUCCESS,   // +400 per defender transferred
    UNANNOUNCED_SUCCESS, // +200 per defender transferred
    ANNOUNCED_FAILURE    // −200 per defender transferred (declarer pays)
}

enum class TarotPetitAuBout {
    NONE,
    DECLARER,  // declarer team wins last trick with le Petit
    DEFENSE    // defense wins last trick with le Petit
}

data class TarotPoigneeOptions(
    val declarerPoignee: TarotPoigneeLevel = TarotPoigneeLevel.NONE,
    val defensePoignee: TarotPoigneeLevel = TarotPoigneeLevel.NONE
)

/**
 * A single Tarot round.
 *
 * @param associatedPlayerId  5-player only: called partner. null or == declarerId → solo.
 */
data class TarotRound(
    val roundNumber: Int,
    val declarerId: Long,
    val contract: TarotContract,
    val boutsCount: Int,
    val pointsMade: Int,
    val poignees: TarotPoigneeOptions = TarotPoigneeOptions(),
    val petitAuBout: TarotPetitAuBout = TarotPetitAuBout.NONE,
    val chelem: TarotChelem = TarotChelem.NONE,
    val associatedPlayerId: Long? = null
) {
    companion object {
        fun threshold(boutsCount: Int): Int = when (boutsCount) {
            0 -> 56; 1 -> 51; 2 -> 41; 3 -> 36; else -> 56
        }
    }

    val pointsDiff: Int get() = pointsMade - threshold(boutsCount)
    val isDeclarerWin: Boolean get() = pointsDiff >= 0

    private fun isSolo(playerCount: Int) =
        playerCount == 5 && (associatedPlayerId == null || associatedPlayerId == declarerId)

    /**
     * Computes per-player scores. Sum is always 0.
     *
     * All bonuses (petit au bout, chelem) are expressed as a per-defender unit:
     *   - each defender receives ±unit
     *   - declarer receives −(total of all defenders + partner)
     * This guarantees zero-sum by construction.
     */
    fun computeScores(playerIds: List<Long>): Map<Long, Int> {
        val playerCount = playerIds.size
        val solo = isSolo(playerCount)

        val defenderIds = playerIds.filter { pid ->
            pid != declarerId &&
                    !(playerCount == 5 && !solo && pid == associatedPlayerId)
        }

        // ── Per-defender components ────────────────────────────────────────────

        // Contract + poignée: go to winning camp
        val contractUnit = (Math.abs(pointsDiff) + 25) * contract.multiplier
        val poigneeUnit = poignees.declarerPoignee.bonus + poignees.defensePoignee.bonus
        val baseUnit = contractUnit + poigneeUnit

        // Petit au bout: 10 × multiplier per defender
        val petitUnit = if (petitAuBout != TarotPetitAuBout.NONE)
            10 * contract.multiplier else 0

        // Chelem: flat per defender
        val chelemUnit = when (chelem) {
            TarotChelem.ANNOUNCED_SUCCESS -> 400
            TarotChelem.UNANNOUNCED_SUCCESS -> 200
            TarotChelem.ANNOUNCED_FAILURE -> 200
            TarotChelem.NONE -> 0
        }

        // ── Defender score (positive = defender gains) ─────────────────────────
        var defenderScore = 0

        // Contract & poignée direction
        defenderScore += if (isDeclarerWin) -baseUnit else baseUnit

        // Petit au bout direction (independent of contract result)
        defenderScore += when (petitAuBout) {
            TarotPetitAuBout.DEFENSE -> petitUnit    // defense gains → defender +
            TarotPetitAuBout.DECLARER -> -petitUnit  // declarer gains → defender −
            TarotPetitAuBout.NONE -> 0
        }

        // Chelem direction
        defenderScore += when (chelem) {
            TarotChelem.ANNOUNCED_SUCCESS -> -chelemUnit   // declarer wins chelem → defender pays
            TarotChelem.UNANNOUNCED_SUCCESS -> -chelemUnit
            TarotChelem.ANNOUNCED_FAILURE -> chelemUnit    // declarer fails → defender gains
            TarotChelem.NONE -> 0
        }

        // ── Assign scores (zero-sum by construction) ──────────────────────────
        val result = mutableMapOf<Long, Int>()

        for (pid in defenderIds) {
            result[pid] = defenderScore
        }

        if (playerCount == 5 && !solo) {
            // Partner mirrors one defender (same direction, 1× weight)
            val partnerScore = -defenderScore
            result[associatedPlayerId!!] = partnerScore
            // Declarer negates the sum of all other players
            val othersSum = defenderIds.size * defenderScore + partnerScore
            result[declarerId] = -othersSum
        } else {
            // Declarer negates sum of all defenders
            result[declarerId] = -(defenderIds.size * defenderScore)
        }

        return result
    }
}

data class TarotPlayerState(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int
) {
    fun getTotal(rounds: List<TarotRound>, playerIds: List<Long>): Int =
        rounds.sumOf { it.computeScores(playerIds)[playerId] ?: 0 }
}

enum class TarotCellRole {
    DECLARER_WIN, DECLARER_LOSS,
    PARTNER_WIN, PARTNER_LOSS,
    DEFENDER_WIN, DEFENDER_LOSS
}

fun TarotRound.getCellRole(playerId: Long, playerIds: List<Long>): TarotCellRole {
    val solo = playerIds.size == 5 &&
            (associatedPlayerId == null || associatedPlayerId == declarerId)
    return when {
        playerId == declarerId ->
            if (isDeclarerWin) TarotCellRole.DECLARER_WIN else TarotCellRole.DECLARER_LOSS
        playerIds.size == 5 && !solo && playerId == associatedPlayerId ->
            if (isDeclarerWin) TarotCellRole.PARTNER_WIN else TarotCellRole.PARTNER_LOSS
        else ->
            if (isDeclarerWin) TarotCellRole.DEFENDER_LOSS else TarotCellRole.DEFENDER_WIN
    }
}

// ─── Display symbols ─────────────────────────────────────────────────────────

/** Naval chevrons for contract (shown inline, using Unicode). */
fun TarotContract.symbol(): String = when (this) {
    TarotContract.PRISE -> "∧"
    TarotContract.GARDE -> "∧∧"
    TarotContract.GARDE_SANS -> "∧∧∧"
    TarotContract.GARDE_CONTRE -> "∧∧∧∧"
}

/** 4-leaf clovers for bouts count (☘ = U+2618). */
fun boutsSymbol(count: Int): String = if (count == 0) "○" else "☘".repeat(count)

/** Handshakes for poignée level. */
fun TarotPoigneeLevel.symbol(): String = when (this) {
    TarotPoigneeLevel.NONE -> ""
    TarotPoigneeLevel.SIMPLE -> "🤝"
    TarotPoigneeLevel.DOUBLE -> "🤝🤝"
    TarotPoigneeLevel.TRIPLE -> "🤝🤝🤝"
}

/** Framed 1 for petit au bout. */
const val PETIT_AU_BOUT_SYMBOL = "🃏1"

/** Laurel for chelem result. */
fun TarotChelem.symbol(): String = when (this) {
    TarotChelem.NONE -> ""
    TarotChelem.ANNOUNCED_SUCCESS -> "🌿"    // green laurel: announced + achieved
    TarotChelem.UNANNOUNCED_SUCCESS -> "🍃"  // neutral leaf: achieved unannounced
    TarotChelem.ANNOUNCED_FAILURE -> "🍂"    // red/dead leaf: announced + failed
}
