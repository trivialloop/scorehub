package com.github.trivialloop.scorehub.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.github.trivialloop.scorehub.R

/**
 * Shared helper to show a polished game results dialog for both Yahtzee and Skyjo.
 *
 * @param entries       List of (playerName, playerColor, score, rank) sorted by rank (1 = best).
 * @param isDraw        True when multiple players share the top rank.
 * @param scoreLabel    Optional suffix shown after the score (e.g. " pts").
 * @param onDismiss     Called when the user taps OK.
 */
object GameResultsDialog {

    data class PlayerResult(
        val playerName: String,
        val playerColor: Int,
        val score: Int,
        val rank: Int        // 1 = best (highest for Yahtzee, lowest for Skyjo)
    )

    private val RANK_EMOJI = mapOf(1 to "🥇", 2 to "🥈", 3 to "🥉")

    fun show(
        context: Context,
        entries: List<PlayerResult>,
        isDraw: Boolean,
        scoreLabel: String = " pts",
        onDismiss: () -> Unit
    ) {
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_game_results, null)

        val textHeader   = dialogView.findViewById<TextView>(R.id.textResultHeader)
        val textSubtitle = dialogView.findViewById<TextView>(R.id.textResultSubtitle)
        val container    = dialogView.findViewById<LinearLayout>(R.id.scoresContainer)

        // Header
        if (isDraw) {
            textHeader.text = "🤝"
            textSubtitle.text = context.getString(R.string.yahtzee_draw_message)
        } else {
            textHeader.text = "🏆"
            val winner = entries.first { it.rank == 1 }
            textSubtitle.text = context.getString(R.string.yahtzee_winner_message, winner.playerName)
        }

        // Score rows
        for (entry in entries) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(context, 8) }
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // Rank emoji or number
            val rankView = TextView(context).apply {
                text = RANK_EMOJI[entry.rank] ?: "${entry.rank}"
                textSize = if (entry.rank <= 3) 24f else 18f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 40), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            // Color dot
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(context, 18), dpToPx(context, 18)
                ).also {
                    it.marginEnd = dpToPx(context, 10)
                    it.marginStart = dpToPx(context, 4)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(entry.playerColor)
                }
            }

            // Player name
            val nameView = TextView(context).apply {
                text = entry.playerName
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (entry.rank == 1) setTypeface(null, Typeface.BOLD)
            }

            // Score
            val scoreView = TextView(context).apply {
                text = "${entry.score}$scoreLabel"
                textSize = 16f
                if (entry.rank == 1) setTypeface(null, Typeface.BOLD)
                setTextColor(
                    when (entry.rank) {
                        1 -> ContextCompat.getColor(context, R.color.score_text_best)
                        entries.maxOf { it.rank } -> ContextCompat.getColor(context, R.color.score_text_worst)
                        else -> ContextCompat.getColor(context, android.R.color.tab_indicator_text)
                    }
                )
            }

            row.addView(rankView)
            row.addView(dot)
            row.addView(nameView)
            row.addView(scoreView)
            container.addView(row)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.yahtzee_game_results))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.ok)) { _, _ -> onDismiss() }
            .setCancelable(false)
            .show()
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
