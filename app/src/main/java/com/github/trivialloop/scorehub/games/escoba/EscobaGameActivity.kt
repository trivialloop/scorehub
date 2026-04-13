package com.github.trivialloop.scorehub.games.escoba

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
import com.github.trivialloop.scorehub.databinding.ActivityEscobaGameBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class EscobaGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEscobaGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<EscobaPlayerState>

    private val rounds = mutableListOf<EscobaRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "escoba"
        private const val SCORE_LIMIT = 21
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEscobaGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        players = playerIds.indices.map { i ->
            EscobaPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.escoba_game)

        rounds.add(EscobaRound(1))
        buildTable()
    }

    // ─── Adaptive sizing ───────────────────────────────────────────────────────

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
        val headerRow = buildHeaderRow()
        val roundRows = rounds.mapIndexed { index, round ->
            buildRoundRow(
                round  = round,
                isLast = index == rounds.lastIndex,
                isPrev = index == rounds.lastIndex - 1
            )
        }
        val totalRow = buildTotalRow()

        val screenHeight = resources.displayMetrics.heightPixels
        val appBarHeight = binding.toolbar.layoutParams?.height
            ?.takeIf { it > 0 } ?: dpToPx(56)

        val rowHeight = cellPaddingV * 2 + dpToPx((cellTextSize + 4).toInt())
        val totalNaturalHeight = rowHeight * (roundRows.size + 3)
        val availableHeight = screenHeight - appBarHeight

        if (totalNaturalHeight > availableHeight) {
            binding.headerContainer.removeAllViews()
            binding.headerContainer.addView(headerRow)
            binding.tableContainer.removeAllViews()
            roundRows.forEach { binding.tableContainer.addView(it) }
            binding.totalContainer.removeAllViews()
            binding.totalContainer.addView(totalRow)
            binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        } else {
            binding.headerContainer.removeAllViews()
            binding.tableContainer.removeAllViews()
            binding.totalContainer.removeAllViews()
            binding.tableContainer.addView(headerRow)
            roundRows.forEach { binding.tableContainer.addView(it) }
            binding.tableContainer.addView(totalRow)
        }
    }

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.escoba_round_label)))
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

    private fun buildRoundRow(round: EscobaRound, isLast: Boolean, isPrev: Boolean): LinearLayout {
        val row = makeRow()
        val playerIdList = players.map { it.playerId }
        val roundComplete = round.allScoresEntered(playerIdList)

        // Round number label
        val labelCell = makeLabelCell(round.roundNumber.toString())
        row.addView(labelCell)

        // Determine colors when round is complete
        val rawScores: Map<Long, Int> = if (roundComplete) {
            playerIdList.mapNotNull { id -> round.scores[id]?.let { id to it } }.toMap()
        } else emptyMap()

        val minScore = if (rawScores.size == playerIdList.size) rawScores.values.minOrNull() else null
        val maxScore = if (rawScores.size == playerIdList.size) rawScores.values.maxOrNull() else null
        val allSame = minScore != null && minScore == maxScore

        for (player in players) {
            val score = round.scores[player.playerId]
            val cell = makePlayerCell(score?.toString() ?: "")

            // Color logic: highest = green text, lowest = red text, all same = neutral
            if (roundComplete && !allSame && score != null) {
                when (score) {
                    maxScore -> {
                        cell.setTextColor(ContextCompat.getColor(this, R.color.escoba_score_green))
                        cell.setTypeface(null, Typeface.BOLD)
                    }
                    minScore -> {
                        cell.setTextColor(ContextCompat.getColor(this, R.color.escoba_score_red))
                        cell.setTypeface(null, Typeface.BOLD)
                    }
                }
            }

            // Current round: editable as long as not complete and not game over
            val canEnter = isLast && !roundComplete && !gameOver
            // Previous round: editable only if current round has no scores yet
            val currentRound = rounds.last()
            val canEditPrev = isPrev && !gameOver && currentRound.scores.values.all { it == null }

            when {
                canEnter -> cell.setOnClickListener {
                    showScoreInput(round, player, isEdit = score != null)
                }
                canEditPrev -> cell.setOnClickListener {
                    showScoreInput(round, player, isEdit = true)
                }
            }

            row.addView(cell)
        }

        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.escoba_total)))

        val totals = players.map { it.getTotal(rounds) }
        val maxTotal = if (gameOver) totals.maxOrNull() else null

        for ((index, player) in players.withIndex()) {
            val total = totals[index]
            val cell = makePlayerCell(total.toString(), bold = true)
            if (gameOver && total == maxTotal) {
                cell.setTextColor(ContextCompat.getColor(this, R.color.escoba_score_green))
            }
            row.addView(cell)
        }
        return row
    }

    // ─── Score input dialog ────────────────────────────────────────────────────

    private fun showScoreInput(round: EscobaRound, player: EscobaPlayerState, isEdit: Boolean = false) {
        val playerIdList = players.map { it.playerId }

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.escoba_score_hint)
            gravity = Gravity.CENTER
            textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(2))
            if (isEdit) round.scores[player.playerId]?.let { setText(it.toString()) }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }

        AlertDialog.Builder(this)
            .setTitle("${player.playerName} — ${getString(R.string.escoba_enter_score)}")
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 0) {
                    showScoreInput(round, player, isEdit)
                    return@setPositiveButton
                }
                round.scores[player.playerId] = value
                buildTable()

                // When the round is now complete, check end-of-game condition
                if (!isEdit && round.allScoresEntered(playerIdList)) {
                    checkEndOfGame()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()

        editText.requestFocus()
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    private fun checkEndOfGame() {
        val maxTotal = players.maxOf { it.getTotal(rounds) }
        if (maxTotal >= SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.escoba_game_over_title))
                .setMessage(getString(R.string.escoba_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true
                    buildTable()
                    saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    rounds.add(EscobaRound(rounds.size + 1))
                    buildTable()
                }
                .show()
        } else {
            rounds.add(EscobaRound(rounds.size + 1))
            buildTable()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals = players.associate { it to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1

        lifecycleScope.launch {
            val results = players.map { player ->
                GameResult(
                    gameType   = GAME_TYPE,
                    playerId   = player.playerId,
                    playerName = player.playerName,
                    score      = player.getTotal(rounds),
                    isWinner   = !isDraw && player in winners,
                    isDraw     = isDraw && player in winners
                )
            }
            database.gameResultDao().insertGameResults(results)
            showSummaryDialog(totals, winners, isDraw)
        }
    }

    private fun showSummaryDialog(
        totals: Map<EscobaPlayerState, Int>,
        winners: Set<EscobaPlayerState>,
        isDraw: Boolean
    ) {
        val sorted = totals.entries.sortedByDescending { it.value }
        var currentRank = 1
        val entries = sorted.mapIndexed { index, (player, score) ->
            val rank = if (index > 0 && score == sorted[index - 1].value) currentRank
                       else { currentRank = index + 1; currentRank }
            com.github.trivialloop.scorehub.ui.GameResultsDialog.PlayerResult(
                playerName  = player.playerName,
                playerColor = player.playerColor,
                score       = score,
                rank        = rank
            )
        }

        com.github.trivialloop.scorehub.ui.GameResultsDialog.show(
            context    = this,
            entries    = entries,
            isDraw     = isDraw,
            scoreLabel = " pts",
            onDismiss  = { finish() }
        )
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
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_text))
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
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.score_cell_background))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.score_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@EscobaGameActivity, R.color.escoba_cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ─── Navigation ────────────────────────────────────────────────────────────

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.escoba_quit_game)
                    .setMessage(R.string.escoba_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
