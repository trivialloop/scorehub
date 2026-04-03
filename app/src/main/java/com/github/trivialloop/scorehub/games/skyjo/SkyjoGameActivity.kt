package com.github.trivialloop.scorehub.games.skyjo

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivitySkyjoGameBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class SkyjoGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySkyjoGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<SkyjoPlayerState>

    private val rounds = mutableListOf<SkyjoRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "skyjo"
        private const val SCORE_LIMIT = 100
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySkyjoGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        players = playerIds.indices.map { i ->
            SkyjoPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.skyjo_game)

        rounds.add(SkyjoRound(1))
        buildTable()
    }

    // ─── Adaptive sizing ───────────────────────────────────────────────────────
    //
    //  2–3 players : 14sp, 14dp vertical padding
    //  4–5 players : 13sp, 10dp vertical padding
    //  6–8 players : 11sp,  7dp vertical padding

    private val cellTextSize: Float
        get() = when {
            players.size <= 3 -> 14f
            players.size <= 5 -> 13f
            else -> 11f
        }

    private val cellPaddingV: Int
        get() = dpToPx(when {
            players.size <= 3 -> 14
            players.size <= 5 -> 10
            else -> 7
        })

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        binding.tableContainer.removeAllViews()
        binding.tableContainer.addView(buildHeaderRow())
        for ((index, round) in rounds.withIndex()) {
            val isLast = index == rounds.lastIndex
            val isPrev = index == rounds.lastIndex - 1
            binding.tableContainer.addView(buildRoundRow(round, isLast, isPrev))
        }
        binding.tableContainer.addView(buildTotalRow())
        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.skyjo_round_label)))
        for (player in players) {
            val cell = makePlayerCell(player.playerName, bold = true)
            cell.background = cellDrawable(player.playerColor)
            cell.setTextColor(Color.WHITE)
            cell.maxLines = 1
            cell.ellipsize = TextUtils.TruncateAt.END
            row.addView(cell)
        }
        return row
    }

    private fun buildRoundRow(round: SkyjoRound, isLast: Boolean, isPrev: Boolean): LinearLayout {
        val row = makeRow()
        val playerIdList = players.map { it.playerId }
        val roundComplete = round.isComplete(playerIdList)

        // ── Round number / finisher cell ──────────────────────────────────
        val labelCell = makeLabelCell(round.roundNumber.toString())
        val finisher = players.find { it.playerId == round.finisherId }
        if (finisher != null) {
            labelCell.background = cellDrawable(finisher.playerColor)
            labelCell.setTextColor(Color.WHITE)
        }
        if (isLast && !gameOver) {
            labelCell.setOnClickListener { showFinisherPicker(round) }
            if (finisher == null) labelCell.alpha = 0.6f
        }
        row.addView(labelCell)

        // ── Score cells ───────────────────────────────────────────────────
        for (player in players) {
            val rawScore  = round.scores[player.playerId]
            val finalScore = round.finalScores[player.playerId]

            // Display raw score immediately once entered; switch to final when round complete
            val displayText = when {
                roundComplete  -> finalScore?.toString() ?: ""
                rawScore != null -> rawScore.toString()
                else           -> ""
            }

            val cell = makePlayerCell(displayText)

            // ── Color logic ───────────────────────────────────────────────
            // Finisher cell  : background green if strictly lowest, background red otherwise.
            //                  Text stays neutral (white on colored bg).
            // Other players  : text green if their score equals the global minimum,
            //                  text red if their score equals the global maximum,
            //                  neutral otherwise. Background stays neutral.
            if (roundComplete) {
                val rawScores = playerIdList.mapNotNull { id -> round.scores[id]?.let { id to it } }.toMap()
                if (rawScores.size == playerIdList.size) {
                    val minScore = rawScores.values.min()
                    val maxScore = rawScores.values.max()
                    val finisherIsStrictlyLowest = round.finisherId != null &&
                            rawScores[round.finisherId] == minScore &&
                            rawScores.values.count { it == minScore } == 1

                    if (player.playerId == round.finisherId) {
                        // Finisher: colored background, white text
                        val bgColor = if (finisherIsStrictlyLowest)
                            ContextCompat.getColor(this, R.color.skyjo_score_green)
                        else
                            ContextCompat.getColor(this, R.color.skyjo_score_red)
                        cell.background = cellDrawable(bgColor)
                        cell.setTextColor(ContextCompat.getColor(this, R.color.score_cell_text))
                    } else {
                        // Regular player: colored text, neutral background
                        val raw = rawScores[player.playerId]
                        when {
                            raw == minScore -> {
                                cell.setTextColor(ContextCompat.getColor(this, R.color.skyjo_score_green))
                                cell.setTypeface(null, Typeface.BOLD)
                            }
                            raw == maxScore -> {
                                cell.setTextColor(ContextCompat.getColor(this, R.color.skyjo_score_red))
                                cell.setTypeface(null, Typeface.BOLD)
                            }
                        }
                    }
                }
            }

            // Editable on last round as long as the round isn't complete yet,
            // whether a score is already entered or not (allows correction before last player submits)
            val canEnter    = isLast && round.finisherId != null && !roundComplete && !gameOver
            val canEditPrev = isPrev && !gameOver

            when {
                canEnter    -> cell.setOnClickListener { showScoreInput(round, player, isEdit = rawScore != null) }
                canEditPrev -> cell.setOnClickListener { showScoreInput(round, player, isEdit = true) }
            }

            row.addView(cell)
        }

        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.total)))
        val minTotal = if (gameOver) players.minOf { it.getTotal(rounds) } else Int.MAX_VALUE
        for (player in players) {
            val total = player.getTotal(rounds)
            val cell = makePlayerCell(total.toString(), bold = true)
            if (gameOver && total == minTotal) {
                cell.setTextColor(ContextCompat.getColor(this, R.color.skyjo_score_green))
            }
            row.addView(cell)
        }
        return row
    }

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    private fun showFinisherPicker(round: SkyjoRound) {
        val names = players.map { it.playerName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.skyjo_who_finished))
            .setItems(names) { _, which ->
                round.finisherId = players[which].playerId
                buildTable()
            }
            .show()
    }

    /**
     * Shows a keyboard input dialog for the player to type their score.
     * Accepts integers from -2 to 99 (Skyjo range), validated before confirming.
     * isEdit=true is used when correcting the previous round's score.
     */
    private fun showScoreInput(round: SkyjoRound, player: SkyjoPlayerState, isEdit: Boolean = false) {
        val playerIdList = players.map { it.playerId }

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            hint = getString(R.string.skyjo_score_hint)
            gravity = Gravity.CENTER
            textSize = 20f
            // Max 3 chars: optional minus + up to 2 digits (Skyjo max is 12, min is -2)
            filters = arrayOf(InputFilter.LengthFilter(3))
            // Pre-fill current value when editing
            if (isEdit) round.scores[player.playerId]?.let { setText(it.toString()) }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }

        AlertDialog.Builder(this)
            .setTitle("${player.playerName} — ${getString(R.string.skyjo_enter_score)}")
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val input = editText.text.toString().trim()
                val value = input.toIntOrNull()
                if (value == null) {
                    // Invalid input — reopen dialog
                    showScoreInput(round, player, isEdit)
                    return@setPositiveButton
                }
                round.scores[player.playerId] = value

                if (round.allScoresEntered(playerIdList)) {
                    round.computeFinalScores(playerIdList)
                    buildTable()
                    if (!isEdit) updateTotalsAndCheckEnd()
                } else {
                    buildTable()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()

        // Auto-focus keyboard
        editText.requestFocus()
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    private fun updateTotalsAndCheckEnd() {
        val maxTotal = players.maxOf { it.getTotal(rounds) }
        if (maxTotal >= SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.skyjo_game_over_title))
                .setMessage(getString(R.string.skyjo_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true
                    buildTable()
                    saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    rounds.add(SkyjoRound(rounds.size + 1))
                    buildTable()
                }
                .show()
        } else {
            rounds.add(SkyjoRound(rounds.size + 1))
            buildTable()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals = players.associate { it to it.getTotal(rounds) }
        val minScore = totals.values.minOrNull() ?: 0
        val winners = totals.filter { it.value == minScore }.keys
        val isDraw = winners.size > 1

        lifecycleScope.launch {
            val results = players.map { player ->
                GameResult(
                    gameType = GAME_TYPE,
                    playerId = player.playerId,
                    playerName = player.playerName,
                    score = player.getTotal(rounds),
                    isWinner = !isDraw && player in winners,
                    isDraw = isDraw && player in winners
                )
            }
            database.gameResultDao().insertGameResults(results)
            showSummaryDialog(totals, winners, isDraw)
        }
    }

    private fun showSummaryDialog(
        totals: Map<SkyjoPlayerState, Int>,
        winners: Set<SkyjoPlayerState>,
        isDraw: Boolean
    ) {
        val sb = StringBuilder()
        sb.append(getString(R.string.final_scores)).append("\n\n")
        totals.entries.sortedBy { it.value }.forEach { (player, score) ->
            sb.append("${player.playerName}: $score pts")
            if (player in winners) sb.append(" ★")
            sb.append("\n")
        }
        sb.append("\n")
        if (isDraw) sb.append(getString(R.string.draw_message))
        else sb.append(getString(R.string.winner_message, winners.first().playerName))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.game_results))
            .setMessage(sb.toString())
            .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    // ─── Cell helpers ──────────────────────────────────────────────────────────

    private fun makeRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun makeLabelCell(text: String): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setPadding(dpToPx(4), cellPaddingV, dpToPx(4), cellPaddingV)
        textSize = cellTextSize - 1f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(42), LinearLayout.LayoutParams.WRAP_CONTENT)
        background = cellDrawable(ContextCompat.getColor(this@SkyjoGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@SkyjoGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerCell(text: String, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setPadding(dpToPx(2), cellPaddingV, dpToPx(2), cellPaddingV)
        textSize = cellTextSize
        if (bold) setTypeface(null, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        background = cellDrawable(ContextCompat.getColor(this@SkyjoGameActivity, R.color.score_cell_background))
        setTextColor(ContextCompat.getColor(this@SkyjoGameActivity, R.color.score_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@SkyjoGameActivity, R.color.skyjo_cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ─── Navigation ────────────────────────────────────────────────────────────

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.quit_game)
                    .setMessage(R.string.quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
