package com.github.trivialloop.scorehub.games.skyjo

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MenuItem
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
    // Portrait mode: column width = (screen width - label column) / nbPlayers
    // We reduce font size and vertical padding as the number of players grows
    // so everything stays legible without scrolling horizontally.
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
        get() = dpToPx(
            when {
                players.size <= 3 -> 14
                players.size <= 5 -> 10
                else -> 7
            }
        )

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
            // Name always truncated with ellipsis — essential with 6-8 narrow columns
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
            // Visual hint: dimmed when no finisher selected yet
            if (finisher == null) labelCell.alpha = 0.6f
        }
        row.addView(labelCell)

        // ── Score cells ───────────────────────────────────────────────────
        for (player in players) {
            val rawScore = round.scores[player.playerId]
            val finalScore = round.finalScores[player.playerId]
            val cell = makePlayerCell(finalScore?.toString() ?: "")

            if (roundComplete) {
                when (round.getCellColor(player.playerId, playerIdList)) {
                    SkyjoCellColor.GREEN -> {
                        cell.background = cellDrawable(ContextCompat.getColor(this, R.color.skyjo_score_green))
                        cell.setTextColor(Color.WHITE)
                    }
                    SkyjoCellColor.ORANGE -> {
                        cell.background = cellDrawable(ContextCompat.getColor(this, R.color.skyjo_score_orange))
                        cell.setTextColor(Color.WHITE)
                    }
                    SkyjoCellColor.RED -> {
                        cell.background = cellDrawable(ContextCompat.getColor(this, R.color.skyjo_score_red))
                        cell.setTextColor(Color.WHITE)
                    }
                    SkyjoCellColor.DEFAULT -> Unit
                }
            }

            val canEnter = isLast && round.finisherId != null && rawScore == null && !roundComplete && !gameOver
            val canEditPrev = isPrev && !gameOver

            when {
                canEnter -> cell.setOnClickListener { showScoreEntry(round, player) }
                canEditPrev -> cell.setOnClickListener { showScoreEditDialog(round, player) }
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
                cell.background = cellDrawable(ContextCompat.getColor(this, R.color.skyjo_score_green))
                cell.setTextColor(Color.WHITE)
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

    private fun showScoreEntry(round: SkyjoRound, player: SkyjoPlayerState) {
        val playerIdList = players.map { it.playerId }
        val scores = (-2..12).toList()
        AlertDialog.Builder(this)
            .setTitle("${player.playerName} — ${getString(R.string.skyjo_enter_score)}")
            .setItems(scores.map { it.toString() }.toTypedArray()) { _, which ->
                round.scores[player.playerId] = scores[which]
                if (round.allScoresEntered(playerIdList)) {
                    round.computeFinalScores(playerIdList)
                    buildTable()
                    updateTotalsAndCheckEnd()
                } else {
                    buildTable()
                }
            }
            .show()
    }

    private fun showScoreEditDialog(round: SkyjoRound, player: SkyjoPlayerState) {
        val playerIdList = players.map { it.playerId }
        val scores = (-2..12).toList()
        AlertDialog.Builder(this)
            .setTitle("${player.playerName} — ${getString(R.string.skyjo_enter_score)}")
            .setItems(scores.map { it.toString() }.toTypedArray()) { _, which ->
                round.scores[player.playerId] = scores[which]
                if (round.allScoresEntered(playerIdList)) {
                    round.computeFinalScores(playerIdList)
                }
                buildTable()
            }
            .show()
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

    /**
     * Fixed-width label cell for the left column (round number or "Total").
     * Width is fixed at 42dp so player columns share all remaining space equally.
     */
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

    /**
     * Equal-weight player cell. Uses weight=1 so all player columns are identical width
     * regardless of player count. Text is always single-line with ellipsis truncation.
     */
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
