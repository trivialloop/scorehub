package com.github.trivialloop.scorehub.utils

/**
 * Unified score colour role.
 *
 * Usage (single import, no separate function needed):
 *   val role = ScoreColorRole(value, allValues)
 *   val role = ScoreColorRole(value, allValues, lowerIsBetter = true)
 *
 * Rules (per row):
 *  - All values identical → NEUTRAL for everyone.
 *  - Value == row minimum → BEST  (displayed green)
 *  - Value == row maximum → WORST (displayed red)
 *  - Otherwise            → NEUTRAL
 *
 * Pass [lowerIsBetter] = true for games where lower is better (e.g. Skyjo totals):
 * minimum → WORST, maximum → BEST.
 *
 * Only TEXT colour is affected — never change cell backgrounds via this helper.
 */
enum class ScoreColorRole {
    BEST, WORST, NEUTRAL;

    companion object {
        operator fun invoke(
            value: Int?,
            allValues: List<Int?>,
            lowerIsBetter: Boolean = false
        ): ScoreColorRole {
            if (value == null) return NEUTRAL
            val nonNull = allValues.filterNotNull()
            if (nonNull.size < 2) return NEUTRAL
            val min = nonNull.min()
            val max = nonNull.max()
            if (min == max) return NEUTRAL
            return when (value) {
                min -> if (lowerIsBetter) WORST else BEST
                max -> if (lowerIsBetter) BEST  else WORST
                else -> NEUTRAL
            }
        }
    }
}
