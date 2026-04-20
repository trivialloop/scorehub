package com.github.trivialloop.scorehub.utils

/**
 * Score colour role for a single cell within a row.
 *
 * Rules (per row, regardless of game):
 *  - All values identical → NEUTRAL for everyone.
 *  - Value == row minimum  → BEST  (displayed in green)
 *  - Value == row maximum  → WORST (displayed in red)
 *  - Otherwise             → NEUTRAL
 *
 * Pass [lowerIsBetter] = true for games where a lower total is better (e.g. Skyjo):
 * the minimum then maps to WORST and the maximum to BEST.
 *
 * Only the TEXT colour is affected — cell backgrounds are never changed by this helper.
 */
enum class ScoreColorRole { BEST, WORST, NEUTRAL }

fun scoreColorRole(
    value: Int?,
    allValues: List<Int?>,
    lowerIsBetter: Boolean = false
): ScoreColorRole {
    if (value == null) return ScoreColorRole.NEUTRAL
    val nonNull = allValues.filterNotNull()
    if (nonNull.size < 2) return ScoreColorRole.NEUTRAL
    val min = nonNull.min()
    val max = nonNull.max()
    if (min == max) return ScoreColorRole.NEUTRAL
    return when (value) {
        min -> if (lowerIsBetter) ScoreColorRole.WORST else ScoreColorRole.BEST
        max -> if (lowerIsBetter) ScoreColorRole.BEST  else ScoreColorRole.WORST
        else -> ScoreColorRole.NEUTRAL
    }
}
