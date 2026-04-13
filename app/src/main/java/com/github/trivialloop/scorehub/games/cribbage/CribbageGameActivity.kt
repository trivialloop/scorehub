package com.github.trivialloop.scorehub.games.cribbage

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
import com.github.trivialloop.scorehub.databinding.ActivityCribbageGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class CribbageGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCribbageGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<CribbagePlayerState>

    // players[0] has the crib in round 1, alternates each round
    private val rounds = mutableListOf<CribbageRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE      = "cribbage"
        private const val WIN_SCORE      = 121
        private const val MAX_HAND_SCORE = 29
        private const val MAX_CRIB_SCORE = 29
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCribbageGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database     = AppDatabase.getDatabase(this)
        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()

        players = playerIds.indices.map { i ->
            CribbagePlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.cribbage_game)

        addNewRound()
        buildTable()
    }

    // ─── Round management ──────────────────────────────────────────────────────

    private fun addNewRound() {
        val roundIndex  = rounds.size
        // Even rounds: players[0] deals; odd rounds: players[1] deals
        val dealerIndex = roundIndex % 2
        val poneIndex   = 1 - dealerIndex
        rounds.add(
            CribbageRound(
                roundNumber = roundIndex + 1,
                dealerId    = playerIds[dealerIndex],
                nonDealerId = playerIds[poneIndex]
            )
        )
    }

    // ─── Table construction ────────────────────────────────────────────────────

    /**
     * Column layout — FIXED across all rounds (players[0] always left, players[1] always right):
     *
     *  [#] | players[0]: Pegging | Hand | Crib | players[1]: Pegging | Hand | Crib
     *
     * The Crib column only shows a score when that player is dealer for that round.
     * Both always have a Crib slot so the column count stays constant (7 total).
     */
    private fun buildTable() {
        val headerRow = buildHeaderRow()
        val roundRows = rounds.mapIndexed { index, round -> buildRoundRow(round, index) }
        val totalRow  = buildTotalRow()

        val screenHeight       = resources.displayMetrics.heightPixels
        val appBarHeight       = binding.toolbar.layoutParams?.height?.takeIf { it > 0 } ?: dpToPx(56)
        val rowHeight          = dpToPx(52)
        val totalNaturalHeight = rowHeight * (roundRows.size + 3)
        val availableHeight    = screenHeight - appBarHeight

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

    /**
     * Two-row header:
     *  Row 1: [empty] [Player 0 name — 3 cols] [Player 1 name — 3 cols]
     *  Row 2: [#]     [Peg][Hand][Crib]         [Peg][Hand][Crib]
     */
    private fun buildHeaderRow(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Row 1 — player names, each spanning 3 sub-columns (weight 3)
        val nameRow = makeRow()
        nameRow.addView(makeRoundLabelCell(""))
        for (player in players) {
            nameRow.addView(makePlayerNameHeaderCell(player.playerName, player.playerColor, weight = 3f))
        }
        container.addView(nameRow)

        // Row 2 — column sub-labels (Pegging / Hand / Crib for each player)
        val subRow = makeRow()
        subRow.addView(makeRoundLabelCell("#"))
        repeat(players.size) {
            subRow.addView(makeSubHeaderCell(getString(R.string.cribbage_pegging)))
            subRow.addView(makeSubHeaderCell(getString(R.string.cribbage_hand)))
            subRow.addView(makeSubHeaderCell(getString(R.string.cribbage_crib)))
        }
        container.addView(subRow)

        return container
    }

    /**
     * One row per round with fixed column order:
     *  [roundNum] [p0:peg][p0:hand][p0:crib] [p1:peg][p1:hand][p1:crib]
     *
     * The crib column for the non-dealer is an empty greyed-out placeholder.
     */
    private fun buildRoundRow(round: CribbageRound, roundIndex: Int): LinearLayout {
        val isLastRound = roundIndex == rounds.lastIndex
        val isPrevRound = roundIndex == rounds.lastIndex - 1
        val currentRound       = rounds.last()
        val canEditPrevPegging = isPrevRound && !gameOver && !currentRound.isPoneHandEntered()

        val row = makeRow()

        // Round number label — tinted with the dealer's colour
        val roundLabel   = makeRoundLabelCell(round.roundNumber.toString())
        val dealerPlayer = players.first { it.playerId == round.dealerId }
        roundLabel.background = cellDrawable(dealerPlayer.playerColor)
        roundLabel.setTextColor(Color.WHITE)
        row.addView(roundLabel)

        val p0 = players[0]
        val p1 = players[1]

        val peg0  = round.peggingScores[p0.playerId] ?: 0
        val peg1  = round.peggingScores[p1.playerId] ?: 0
        val hand0 = round.handScores[p0.playerId]
        val hand1 = round.handScores[p1.playerId]

        val peggingEditable = (isLastRound || canEditPrevPegging) && round.isPeggingEditable() && !gameOver
        val poneId          = round.nonDealerId
        val dealerId        = round.dealerId

        // ── Build cells for each player in fixed order ─────────────────────────
        listOf(p0, p1).forEach { player ->
            val myPeg  = round.peggingScores[player.playerId] ?: 0
            val oppPeg = if (player == p0) peg1 else peg0
            val myHand = round.handScores[player.playerId]
            val oppHand = if (player == p0) hand1 else hand0

            // Pegging
            val pegColor = when {
                myPeg > oppPeg -> CribbageCellColor.GREEN
                myPeg < oppPeg -> CribbageCellColor.RED
                else           -> CribbageCellColor.DEFAULT
            }
            row.addView(makePeggingCell(
                score       = myPeg,
                color       = pegColor,
                canEdit     = peggingEditable,
                onDecrement = {
                    val cur = round.peggingScores[player.playerId] ?: 0
                    if (cur > 0) {
                        round.peggingScores[player.playerId] = cur - 1
                        buildTable()
                        checkGameOver()
                    }
                },
                onIncrement = {
                    val cur = round.peggingScores[player.playerId] ?: 0
                    round.peggingScores[player.playerId] = cur + 1
                    buildTable()
                    checkGameOver()
                }
            ))

            // Hand
            val handColor = when {
                myHand != null && oppHand != null && myHand > oppHand -> CribbageCellColor.GREEN
                myHand != null && oppHand != null && myHand < oppHand -> CribbageCellColor.RED
                else -> CribbageCellColor.DEFAULT
            }
            val isPone          = player.playerId == poneId
            val handEditable    = isLastRound && !gameOver && !round.isPeggingEditable() && when {
                isPone -> myHand == null || round.cribScore == null
                else   -> round.isPoneHandEntered() && (myHand == null || round.cribScore == null)
            }
            row.addView(makeHandCell(myHand, handColor, handEditable) {
                showHandScoreInput(round, player.playerId, poneId)
            })

            // Crib
            val isDealer = player.playerId == dealerId
            if (isDealer) {
                val cribEditable = isLastRound && !gameOver && round.isDealerHandEntered() && round.cribScore == null
                row.addView(makeCribCell(round.cribScore, cribEditable) { showCribScoreInput(round) })
            } else {
                row.addView(makeEmptyCribCell())
            }
        }

        return row
    }

    /**
     * Total row — each player's total spans all 3 of their sub-columns (weight=3).
     */
    private fun buildTotalRow(): LinearLayout {
        val row    = makeRow()
        row.addView(makeRoundLabelCell(getString(R.string.cribbage_total)))

        val totals   = players.associate { it.playerId to it.getTotal(rounds) }
        val maxTotal = totals.values.maxOrNull() ?: 0

        for (player in players) {
            val total = totals[player.playerId] ?: 0
            val cell  = makeTotalCell(total.toString(), weight = 3f)
            if (gameOver && total == maxTotal) {
                cell.setTextColor(ContextCompat.getColor(this, R.color.cribbage_score_green))
            }
            row.addView(cell)
        }

        return row
    }

    // ─── Game over ─────────────────────────────────────────────────────────────

    private fun checkGameOver() {
        if (gameOver) return
        val totals = players.associate { it.playerId to it.getTotal(rounds) }
        if (totals.values.none { it >= WIN_SCORE }) return
        gameOver = true
        buildTable()
        saveResultsAndShowSummary()
    }

    private fun onRoundComplete(round: CribbageRound) {
        checkGameOver()
        if (!gameOver) {
            addNewRound()
            buildTable()
        }
    }

    // ─── Score input dialogs ───────────────────────────────────────────────────

    private fun showHandScoreInput(round: CribbageRound, playerId: Long, poneId: Long) {
        // Dealer cannot enter before pone
        if (playerId != poneId && !round.isPoneHandEntered()) return

        val playerName = players.first { it.playerId == playerId }.playerName
        val current    = round.handScores[playerId]

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint      = "0–$MAX_HAND_SCORE"
            gravity   = Gravity.CENTER
            textSize  = 20f
            filters   = arrayOf(InputFilter.LengthFilter(2))
            current?.let { setText(it.toString()) }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }

        AlertDialog.Builder(this)
            .setTitle("$playerName — ${getString(R.string.cribbage_hand_score)}")
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 0 || value > MAX_HAND_SCORE) {
                    showHandScoreInput(round, playerId, poneId)
                    return@setPositiveButton
                }
                round.handScores[playerId] = value
                buildTable()
                checkGameOver()
                if (round.isComplete()) onRoundComplete(round)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
        editText.requestFocus()
    }

    private fun showCribScoreInput(round: CribbageRound) {
        val dealerName = players.first { it.playerId == round.dealerId }.playerName

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint      = "0–$MAX_CRIB_SCORE"
            gravity   = Gravity.CENTER
            textSize  = 20f
            filters   = arrayOf(InputFilter.LengthFilter(2))
            round.cribScore?.let { setText(it.toString()) }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }

        AlertDialog.Builder(this)
            .setTitle("$dealerName — ${getString(R.string.cribbage_crib_score)}")
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 0 || value > MAX_CRIB_SCORE) {
                    showCribScoreInput(round)
                    return@setPositiveButton
                }
                round.cribScore = value
                buildTable()
                checkGameOver()
                if (round.isComplete()) onRoundComplete(round)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
        editText.requestFocus()
    }

    // ─── Save results ──────────────────────────────────────────────────────────

    private fun saveResultsAndShowSummary() {
        val totals   = players.associate { it to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners  = totals.filter { it.value == maxScore }.keys
        val isDraw   = winners.size > 1

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
            showSummaryDialog(totals, isDraw)
        }
    }

    private fun showSummaryDialog(totals: Map<CribbagePlayerState, Int>, isDraw: Boolean) {
        val sorted = totals.entries.sortedByDescending { it.value }
        var currentRank = 1
        val entries = sorted.mapIndexed { index, (player, score) ->
            val rank = if (index > 0 && score == sorted[index - 1].value) currentRank
            else { currentRank = index + 1; currentRank }
            GameResultsDialog.PlayerResult(player.playerName, player.playerColor, score, rank)
        }
        GameResultsDialog.show(this, entries, isDraw, " pts") { finish() }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow() = LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun makeRoundLabelCell(text: String): TextView = TextView(this).apply {
        this.text    = text
        gravity      = Gravity.CENTER
        setPadding(dpToPx(2), dpToPx(12), dpToPx(2), dpToPx(12))
        textSize     = 12f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(32), LinearLayout.LayoutParams.WRAP_CONTENT)
        background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameHeaderCell(name: String, color: Int, weight: Float): TextView =
        TextView(this).apply {
            text         = name
            gravity      = Gravity.CENTER
            setPadding(dpToPx(4), dpToPx(10), dpToPx(4), dpToPx(10))
            textSize     = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines     = 1
            ellipsize    = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            background   = playerCellDrawable(color)
            setTextColor(Color.WHITE)
        }

    private fun makeSubHeaderCell(label: String): TextView = TextView(this).apply {
        text         = label
        gravity      = Gravity.CENTER
        setPadding(dpToPx(1), dpToPx(5), dpToPx(1), dpToPx(5))
        textSize     = 9f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_text))
    }

    private fun makePeggingCell(
        score: Int,
        color: CribbageCellColor,
        canEdit: Boolean,
        onDecrement: () -> Unit,
        onIncrement: () -> Unit
    ): LinearLayout {
        val scoreColor  = when (color) {
            CribbageCellColor.GREEN   -> ContextCompat.getColor(this, R.color.cribbage_score_green)
            CribbageCellColor.RED     -> ContextCompat.getColor(this, R.color.cribbage_score_red)
            CribbageCellColor.DEFAULT -> ContextCompat.getColor(this, R.color.score_cell_text)
        }
        val neutralColor = ContextCompat.getColor(this, R.color.score_cell_text)

        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.score_cell_background))
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }

        val btnMinus = TextView(this).apply {
            text         = "−"
            gravity      = Gravity.CENTER
            textSize     = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(if (canEdit) scoreColor else neutralColor)
            alpha        = if (canEdit) 1f else 0.3f
            if (canEdit) setOnClickListener { onDecrement() }
        }

        val scoreText = TextView(this).apply {
            text         = score.toString()
            gravity      = Gravity.CENTER
            textSize     = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            setTextColor(scoreColor)
        }

        val btnPlus = TextView(this).apply {
            text         = "+"
            gravity      = Gravity.CENTER
            textSize     = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(if (canEdit) scoreColor else neutralColor)
            alpha        = if (canEdit) 1f else 0.3f
            if (canEdit) setOnClickListener { onIncrement() }
        }

        container.addView(btnMinus)
        container.addView(scoreText)
        container.addView(btnPlus)
        return container
    }

    private fun makeHandCell(
        score: Int?,
        color: CribbageCellColor,
        canEdit: Boolean,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text         = score?.toString() ?: ""
        gravity      = Gravity.CENTER
        setPadding(dpToPx(2), dpToPx(12), dpToPx(2), dpToPx(12))
        textSize     = 14f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val textColor = when (color) {
            CribbageCellColor.GREEN   -> ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_score_green)
            CribbageCellColor.RED     -> ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_score_red)
            CribbageCellColor.DEFAULT -> ContextCompat.getColor(this@CribbageGameActivity, R.color.score_cell_text)
        }
        setTextColor(textColor)
        if (color != CribbageCellColor.DEFAULT && score != null) setTypeface(null, Typeface.BOLD)

        background = if (canEdit && score == null)
            cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_editable_hint))
        else
            cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.score_cell_background))

        if (canEdit) setOnClickListener { onClick() }
    }

    private fun makeCribCell(score: Int?, canEdit: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text         = score?.toString() ?: ""
            gravity      = Gravity.CENTER
            setPadding(dpToPx(2), dpToPx(12), dpToPx(2), dpToPx(12))
            textSize     = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.score_cell_text))
            background   = when {
                canEdit && score == null ->
                    cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_editable_hint))
                else ->
                    cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_crib_background))
            }
            if (canEdit) setOnClickListener { onClick() }
        }

    /** Placeholder crib cell for the non-dealer — neutral and non-interactive. */
    private fun makeEmptyCribCell(): TextView = TextView(this).apply {
        text         = ""
        gravity      = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_background))
        alpha        = 0.4f
    }

    private fun makeTotalCell(text: String, weight: Float = 1f): TextView = TextView(this).apply {
        this.text    = text
        gravity      = Gravity.CENTER
        setPadding(dpToPx(2), dpToPx(12), dpToPx(2), dpToPx(12))
        textSize     = 15f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.yahtzee_calculated_cell_background))
        setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.yahtzee_calculated_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_cell_border))
    }

    private fun playerCellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ─── Navigation ────────────────────────────────────────────────────────────

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.cribbage_quit_game)
                    .setMessage(R.string.cribbage_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}