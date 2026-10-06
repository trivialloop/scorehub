package com.github.trivialloop.scorehub.utils

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.github.trivialloop.scorehub.R

/**
 * Thin progress row shown under the fixed header of games that end when a score limit is reached.
 * One bar per player (or team), aligned with the score columns.
 */
object ScoreProgressHelper {

    private const val ROW_HEIGHT_DP = 14
    private const val BAR_HEIGHT_DP = 6

    /** [current] is the running total; [color] is the player/team color. */
    data class Entry(val current: Int, val color: Int, val weight: Float? = null)

    /** Progress in 0..1. Negative totals (e.g. Tarot) are clamped to 0. */
    fun ratio(current: Int, limit: Int): Float =
        if (limit <= 0) 0f else (current.toFloat() / limit).coerceIn(0f, 1f)

    /**
     * @param columnWeight 1f for single-column players (Tarot, Cactus, Skyjo),
     *                     2f for players with two sub-columns (Escoba, Cribbage, Ligretto).
     * @param withDividers true when the grid draws 1dp vertical dividers between players.
     * @param dangerMode   true when reaching the limit ENDS the game against you (Skyjo):
     *                     bars use the "worst" color instead of the player color.
     */
    fun buildRow(
        context: Context,
        labelColDp: Int,
        limit: Int,
        entries: List<Entry>,
        columnWeight: Float = 1f,
        withDividers: Boolean = false,
        dangerMode: Boolean = false
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        fun color(res: Int) = ContextCompat.getColor(context, res)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP))
            setBackgroundColor(color(R.color.score_cell_background))
        }

        // Spacer aligned with the label column
        row.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(labelColDp), LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(color(R.color.header_cell_background))
        })

        fun rounded(c: Int) = GradientDrawable().apply {
            cornerRadius = dp(BAR_HEIGHT_DP / 2).toFloat()
            setColor(c)
        }

        entries.forEachIndexed { index, entry ->
            if (withDividers && index > 0) {
                row.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(color(R.color.cell_border))
                })
            }

            val progress = ratio(entry.current, limit)

            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), 0, dp(4), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, entry.weight ?: columnWeight)
            }

            val track = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(BAR_HEIGHT_DP))
                background = rounded(color(R.color.cell_locked_bg))
            }
            track.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, progress)
                background = rounded(if (dangerMode) color(R.color.score_text_worst) else entry.color)
            })
            track.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - progress)
            })

            cell.addView(track)
            row.addView(cell)
        }
        return row
    }
}
