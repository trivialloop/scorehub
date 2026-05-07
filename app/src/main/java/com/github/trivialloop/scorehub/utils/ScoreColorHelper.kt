package com.github.trivialloop.scorehub.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.trivialloop.scorehub.R

/**
 * Semantic colour role for a score cell within a row.
 *
 * BEST    → the best score in the row (displayed green)
 * WORST   → the worst score in the row (displayed red)
 * NEUTRAL → neither best nor worst (displayed in the default text colour)
 *
 * What "best" means depends on the game:
 *  - Higher is better (Yahtzee, Cactus, Escoba, Cribbage, Wingspan, Tarot): highest score = BEST
 *  - Lower is better  (Skyjo): lowest score = BEST
 *
 * This is controlled by [higherIsBetter] in the companion [invoke] factory.
 *
 * Usage:
 *   val role = ScoreColorRole(value, allRowValues)                        // higher = best (default)
 *   val role = ScoreColorRole(value, allRowValues, higherIsBetter = false) // lower  = best
 *
 *   // Resolve to an Android color resource directly:
 *   cell.setTextColor(role.toColor(context))
 *
 * Rules:
 *  - All values identical → NEUTRAL for everyone.
 *  - null value           → NEUTRAL.
 *  - Less than 2 non-null values → NEUTRAL.
 */
enum class ScoreColorRole {
    BEST, WORST, NEUTRAL;

    /** Resolves this role to the matching Android color resource. */
    fun toColor(context: Context): Int = when (this) {
        BEST    -> ContextCompat.getColor(context, R.color.score_text_best)
        WORST   -> ContextCompat.getColor(context, R.color.score_text_worst)
        NEUTRAL -> ContextCompat.getColor(context, R.color.score_cell_text)
    }

    companion object {
        /**
         * Computes the [ScoreColorRole] for [value] within [allValues].
         *
         * @param value          The score to classify.
         * @param allValues      All scores in the row (nulls ignored).
         * @param higherIsBetter True (default) when a higher score is better (Yahtzee, Cactus…).
         *                       False when a lower score is better (Skyjo).
         */
        operator fun invoke(
            value: Int?,
            allValues: List<Int?>,
            higherIsBetter: Boolean = true
        ): ScoreColorRole {
            if (value == null) return NEUTRAL
            val nonNull = allValues.filterNotNull()
            if (nonNull.size < 2) return NEUTRAL
            val min = nonNull.min()
            val max = nonNull.max()
            if (min == max) return NEUTRAL
            return when (value) {
                max -> if (higherIsBetter) BEST  else WORST
                min -> if (higherIsBetter) WORST else BEST
                else -> NEUTRAL
            }
        }
    }
}