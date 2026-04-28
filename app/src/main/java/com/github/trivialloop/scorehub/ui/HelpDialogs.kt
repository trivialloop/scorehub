package com.github.trivialloop.scorehub.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.github.trivialloop.scorehub.R

/**
 * Centralised help content for all games.
 *
 * Every game provides:
 *  - [GameHelp] for the Player Selection screen (rules summary + Wikipedia link)
 *  - [AppHelp] for the Game screen (how to use the app for that game)
 *
 * Wikipedia URLs are stored in string resources so that each locale can define
 * a different URL. If a localized Wikipedia article does not exist, simply use
 * the same URL in both `values/strings.xml` and `values-fr/strings.xml`.
 * Naming convention: `help_<game>_wikipedia_url`
 */
object HelpDialogs {

    // ─── Data classes ──────────────────────────────────────────────────────────

    data class GameHelp(
        val players: String,
        val objective: String,
        val scoring: String,
        val endCondition: String,
        val wikipediaUrl: String
    )

    data class AppHelp(
        val steps: List<String>
    )

    // ─── Game content ──────────────────────────────────────────────────────────

    private fun getGameHelp(gameType: String, context: Context): GameHelp? = when (gameType) {

        "cactus" -> GameHelp(
            players      = context.getString(R.string.help_cactus_players),
            objective    = context.getString(R.string.help_cactus_objective),
            scoring      = context.getString(R.string.help_cactus_scoring),
            endCondition = context.getString(R.string.help_cactus_end),
            wikipediaUrl = context.getString(R.string.help_cactus_wikipedia_url)
        )

        "cribbage" -> GameHelp(
            players      = context.getString(R.string.help_cribbage_players),
            objective    = context.getString(R.string.help_cribbage_objective),
            scoring      = context.getString(R.string.help_cribbage_scoring),
            endCondition = context.getString(R.string.help_cribbage_end),
            wikipediaUrl = context.getString(R.string.help_cribbage_wikipedia_url)
        )

        "escoba" -> GameHelp(
            players      = context.getString(R.string.help_escoba_players),
            objective    = context.getString(R.string.help_escoba_objective),
            scoring      = context.getString(R.string.help_escoba_scoring),
            endCondition = context.getString(R.string.help_escoba_end),
            wikipediaUrl = context.getString(R.string.help_escoba_wikipedia_url)
        )

        "skyjo" -> GameHelp(
            players      = context.getString(R.string.help_skyjo_players),
            objective    = context.getString(R.string.help_skyjo_objective),
            scoring      = context.getString(R.string.help_skyjo_scoring),
            endCondition = context.getString(R.string.help_skyjo_end),
            wikipediaUrl = context.getString(R.string.help_skyjo_wikipedia_url)
        )

        "tarot" -> GameHelp(
            players      = context.getString(R.string.help_tarot_players),
            objective    = context.getString(R.string.help_tarot_objective),
            scoring      = context.getString(R.string.help_tarot_scoring),
            endCondition = context.getString(R.string.help_tarot_end),
            wikipediaUrl = context.getString(R.string.help_tarot_wikipedia_url)
        )

        "wingspan" -> GameHelp(
            players      = context.getString(R.string.help_wingspan_players),
            objective    = context.getString(R.string.help_wingspan_objective),
            scoring      = context.getString(R.string.help_wingspan_scoring),
            endCondition = context.getString(R.string.help_wingspan_end),
            wikipediaUrl = context.getString(R.string.help_wingspan_wikipedia_url)
        )

        "yahtzee" -> GameHelp(
            players      = context.getString(R.string.help_yahtzee_players),
            objective    = context.getString(R.string.help_yahtzee_objective),
            scoring      = context.getString(R.string.help_yahtzee_scoring),
            endCondition = context.getString(R.string.help_yahtzee_end),
            wikipediaUrl = context.getString(R.string.help_yahtzee_wikipedia_url)
        )

        else -> null
    }

    private fun getAppHelp(gameType: String, context: Context): AppHelp? = when (gameType) {

        "cactus" -> AppHelp(listOf(
            context.getString(R.string.app_help_cactus_1),
            context.getString(R.string.app_help_cactus_2),
            context.getString(R.string.app_help_cactus_3),
            context.getString(R.string.app_help_cactus_4)
        ))

        "cribbage" -> AppHelp(listOf(
            context.getString(R.string.app_help_cribbage_1),
            context.getString(R.string.app_help_cribbage_2),
            context.getString(R.string.app_help_cribbage_3),
            context.getString(R.string.app_help_cribbage_4)
        ))

        "escoba" -> AppHelp(listOf(
            context.getString(R.string.app_help_escoba_1),
            context.getString(R.string.app_help_escoba_2),
            context.getString(R.string.app_help_escoba_3)
        ))

        "skyjo" -> AppHelp(listOf(
            context.getString(R.string.app_help_skyjo_1),
            context.getString(R.string.app_help_skyjo_2),
            context.getString(R.string.app_help_skyjo_3),
            context.getString(R.string.app_help_skyjo_4)
        ))

        "tarot" -> AppHelp(listOf(
            context.getString(R.string.app_help_tarot_1),
            context.getString(R.string.app_help_tarot_2),
            context.getString(R.string.app_help_tarot_3),
            context.getString(R.string.app_help_tarot_4)
        ))

        "wingspan" -> AppHelp(listOf(
            context.getString(R.string.app_help_wingspan_1),
            context.getString(R.string.app_help_wingspan_2),
            context.getString(R.string.app_help_wingspan_3)
        ))

        "yahtzee" -> AppHelp(listOf(
            context.getString(R.string.app_help_yahtzee_1),
            context.getString(R.string.app_help_yahtzee_2),
            context.getString(R.string.app_help_yahtzee_3),
            context.getString(R.string.app_help_yahtzee_4)
        ))

        else -> null
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Shows the game rules dialog (player selection screen).
     * The Wikipedia URL is resolved automatically from the current locale's string resources.
     */
    fun showGameHelp(context: Context, gameType: String) {
        val help = getGameHelp(gameType, context) ?: return

        val container = buildContainer(context)

        addSection(context, container, context.getString(R.string.help_label_players), help.players)
        addSection(context, container, context.getString(R.string.help_label_objective), help.objective)
        addSection(context, container, context.getString(R.string.help_label_scoring), help.scoring)
        addSection(context, container, context.getString(R.string.help_label_end), help.endCondition)
        addWikiLink(context, container, help.wikipediaUrl)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.help_game_title))
            .setView(container)
            .setPositiveButton(context.getString(R.string.ok), null)
            .show()
    }

    /**
     * Shows the app usage dialog (game screen).
     */
    fun showAppHelp(context: Context, gameType: String) {
        val help = getAppHelp(gameType, context) ?: return

        val container = buildContainer(context)

        help.steps.forEachIndexed { index, step ->
            addStep(context, container, index + 1, step)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.help_app_title))
            .setView(container)
            .setPositiveButton(context.getString(R.string.ok), null)
            .show()
    }

    // ─── View builders ─────────────────────────────────────────────────────────

    private fun buildContainer(context: Context): LinearLayout {
        val density = context.resources.displayMetrics.density
        val paddingPx = (20 * density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2)
        }
    }

    private fun addSection(context: Context, parent: LinearLayout, label: String, value: String) {
        val density = context.resources.displayMetrics.density
        val marginPx = (8 * density).toInt()

        val labelView = TextView(context).apply {
            text = label
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.teal_700))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = marginPx }
        }

        val valueView = TextView(context).apply {
            text = value
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = marginPx / 2 }
        }

        parent.addView(labelView)
        parent.addView(valueView)
    }

    private fun addStep(context: Context, parent: LinearLayout, number: Int, stepText: String) {
        val density = context.resources.displayMetrics.density
        val marginPx = (6 * density).toInt()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = marginPx }
        }

        val numberView = TextView(context).apply {
            text = "$number."
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.teal_700))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (8 * density).toInt() }
        }

        val textView = TextView(context).apply {
            text = stepText
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        row.addView(numberView)
        row.addView(textView)
        parent.addView(row)
    }

    private fun addWikiLink(context: Context, parent: LinearLayout, url: String) {
        val density = context.resources.displayMetrics.density
        val marginPx = (12 * density).toInt()

        val linkLabel = context.getString(R.string.help_wikipedia_link)
        val spannable = SpannableString(linkLabel)
        val clickable = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        }
        spannable.setSpan(clickable, 0, linkLabel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val textView = TextView(context).apply {
            text = spannable
            textSize = 14f
            movementMethod = LinkMovementMethod.getInstance()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = marginPx }
        }

        parent.addView(textView)
    }
}
