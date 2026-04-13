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

    // players[0] is dealer in round 1, alternates each round
    private val rounds = mutableListOf<CribbageRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE      = "cribbage"
        private const val WIN_SCORE      = 121
        private const val MAX_HAND_SCORE = 29
        private const val MAX_CRIB_SCORE = 29

        // Fixed row height for all cells — ensures uniform height across the row
        private const val ROW_HEIGHT_DP  = 48
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
     * Fixed column layout (7 columns total):
     *
     *  [#] │ players[0]: In play │ End of round │ Crib │ players[1]: In play │ End of round │ Crib
     *
     * - The Crib column for the non-dealer shows a strongly-dimmed permanent placeholder.
     * - ALL cells in a row share the same fixed height (ROW_HEIGHT_DP) for visual consistency.
     */
    private fun buildTable() {
        val headerRow = buildHeaderRow()
        val roundRows = rounds.mapIndexed { index, round -> buildRoundRow(round, index) }
        val totalRow  = buildTotalRow()

        val screenHeight       = resources.displayMetrics.heightPixels
        val appBarHeight       = binding.toolbar.layoutParams?.height?.takeIf { it > 0 } ?: dpToPx(56)
        val rowHeightPx        = dpToPx(ROW_HEIGHT_DP)
        // 2 header rows + round rows + 1 total row
        val totalNaturalHeight = rowHeightPx * (roundRows.size + 3)
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
     *  Row 1 (ROW_HEIGHT_DP): [empty] │ [Player 0 name — weight 3] │ [Player 1 name — weight 3]
     *  Row 2 (ROW_HEIGHT_DP): [#]     │ [In play][End][Crib]        │ [In play][End][Crib]
     */
    private fun buildHeaderRow(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Row 1 — player names
        val nameRow = makeFixedRow()
        nameRow.addView(makeRoundLabelCell(""))
        for (player in players) {
            nameRow.addView(makePlayerNameHeaderCell(player.playerName, player.playerColor, weight = 3f))
        }
        container.addView(nameRow)

        // Row 2 — column sub-labels
        val subRow = makeFixedRow()
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
     * One row per round:
     *  [roundNum] │ [p0: In play] [p0: End] [p0: Crib] │ [p1: In play] [p1: End] [p1: Crib]
     *
     * Entry order (dealer = player whose colour appears on the round label):
     *   1. In play: both players simultaneously (+/−)
     *      AND dealer End of round: can also be filled during the In play phase.
     *   2. When dealer End is entered → Pone End becomes editable.
     *   3. When Pone End is entered   → Crib becomes editable.
     *   4. When Crib is entered       → round complete, new round appended.
     *
     * Previous round locking:
     *   - A previous round stays editable (End + Crib) until the current round has
     *     ANY pegging activity (any +/− press). Once locked, cells show LOCKED_PREV style.
     */
    private fun buildRoundRow(round: CribbageRound, roundIndex: Int): LinearLayout {
        val isLastRound  = roundIndex == rounds.lastIndex
        val isPrevRound  = roundIndex == rounds.lastIndex - 1
        val currentRound = rounds.last()

        // Previous round remains editable until the new round has pegging activity
        val prevRoundEditable = isPrevRound && !gameOver && !currentRound.hasPeggingActivity()

        val row = makeFixedRow()

        // Round label — tinted with dealer's colour
        val dealerPlayer = players.first { it.playerId == round.dealerId }
        val roundLabel   = makeRoundLabelCell(round.roundNumber.toString())
        roundLabel.background = solidDrawable(dealerPlayer.playerColor)
        roundLabel.setTextColor(Color.WHITE)
        row.addView(roundLabel)

        val p0     = players[0]
        val p1     = players[1]
        val peg0   = round.peggingScores[p0.playerId] ?: 0
        val peg1   = round.peggingScores[p1.playerId] ?: 0
        val hand0  = round.handScores[p0.playerId]
        val hand1  = round.handScores[p1.playerId]

        // ── For each player, determine cell states ─────────────────────────────
        listOf(p0, p1).forEach { player ->
            val isDealer  = player.playerId == round.dealerId
            val myPeg     = round.peggingScores[player.playerId] ?: 0
            val oppPeg    = if (player == p0) peg1 else peg0
            val myHand    = round.handScores[player.playerId]
            val oppHand   = if (player == p0) hand1 else hand0

            // ── In play (Pegging) ───────────────────────────────────────────────
            //
            // Editable on: last round while pegging is open, OR prev round while no new-round pegging yet.
            // On prev round: only +/− if prevRoundEditable AND round's own pegging is still open.
            val peggingCanEdit = !gameOver && round.isPeggingEditable() &&
                    (isLastRound || (prevRoundEditable && !round.isDealerHandEntered()))

            val pegColor = when {
                myPeg > oppPeg -> CribbageCellColor.GREEN
                myPeg < oppPeg -> CribbageCellColor.RED
                else           -> CribbageCellColor.DEFAULT
            }

            // Pegging state for visual: if the round is locked (dealer hand entered), show LOCKED_PREV
            val peggingState = when {
                peggingCanEdit                   -> CellState.EDITABLE
                !round.isPeggingEditable()       -> CellState.LOCKED_PREV   // pegging locked by hand entry
                isLastRound && !gameOver         -> CellState.LOCKED_SOON   // round open but not last
                else                             -> CellState.LOCKED_PREV
            }

            row.addView(makePeggingCell(
                score       = myPeg,
                scoreColor  = pegColor,
                state       = peggingState,
                onDecrement = {
                    val cur = round.peggingScores[player.playerId] ?: 0
                    if (cur > 0) {
                        round.peggingScores[player.playerId] = cur - 1
                        buildTable(); checkGameOver()
                    }
                },
                onIncrement = {
                    val cur = round.peggingScores[player.playerId] ?: 0
                    round.peggingScores[player.playerId] = cur + 1
                    buildTable(); checkGameOver()
                }
            ))

            // ── End of round (Hand) ───────────────────────────────────────────
            //
            // Entry order: dealer first, then pone (after dealer entered).
            //
            // Editable conditions:
            //   Last round, no gameOver, AND:
            //     - Dealer: always editable until crib is entered (even during pegging phase)
            //     - Pone:   only after dealer hand entered, until crib entered
            //   Prev round: editable as long as prevRoundEditable
            val handEditable = !gameOver && when {
                isLastRound && isDealer  -> myHand == null || round.cribScore == null
                isLastRound && !isDealer -> round.isDealerHandEntered() && (myHand == null || round.cribScore == null)
                prevRoundEditable        -> true   // previous round still open
                else                     -> false
            }

            // Visual state for hand cell
            val handState = when {
                handEditable                               -> CellState.EDITABLE
                isLastRound && !isDealer
                        && !round.isDealerHandEntered()
                        && !gameOver                       -> CellState.LOCKED_SOON  // waiting for dealer
                isLastRound && round.cribScore != null     -> CellState.LOCKED_PREV  // round locked
                isLastRound && !gameOver                   -> CellState.LOCKED_SOON
                else                                       -> CellState.LOCKED_PREV
            }

            val handColor = when {
                myHand != null && oppHand != null && myHand > oppHand -> CribbageCellColor.GREEN
                myHand != null && oppHand != null && myHand < oppHand -> CribbageCellColor.RED
                else -> CribbageCellColor.DEFAULT
            }

            row.addView(makeHandCell(
                score    = myHand,
                color    = handColor,
                state    = handState,
                onClick  = { showHandScoreInput(round, player.playerId) }
            ))

            // ── Crib ───────────────────────────────────────────────────────────
            if (isDealer) {
                val cribEditable = !gameOver &&
                        (isLastRound && round.isPoneHandEntered() && round.cribScore == null
                                || prevRoundEditable)

                val cribState = when {
                    cribEditable                                     -> CellState.EDITABLE
                    isLastRound && !round.isPoneHandEntered()
                            && !gameOver                            -> CellState.LOCKED_SOON
                    isLastRound && round.cribScore != null          -> CellState.LOCKED_PREV
                    isLastRound && !gameOver                        -> CellState.LOCKED_SOON
                    else                                            -> CellState.LOCKED_PREV
                }

                row.addView(makeCribCell(
                    score   = round.cribScore,
                    state   = cribState,
                    onClick = { showCribScoreInput(round) }
                ))
            } else {
                // Non-dealer: permanent placeholder — no crib ever
                row.addView(makeNeverCribCell())
            }
        }

        return row
    }

    /** Total row — each player spans 3 sub-columns (weight=3). */
    private fun buildTotalRow(): LinearLayout {
        val row    = makeFixedRow()
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

    /**
     * Hand score input.
     * - Dealer can enter at any time (even during pegging phase).
     * - Pone can only enter after dealer has entered.
     */
    private fun showHandScoreInput(round: CribbageRound, playerId: Long) {
        val isDealer = playerId == round.dealerId
        // Guard: pone cannot enter before dealer
        if (!isDealer && !round.isDealerHandEntered()) return

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
                    showHandScoreInput(round, playerId)
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

    /**
     * Creates a horizontal row with a FIXED height (ROW_HEIGHT_DP).
     * This ensures all cells in the row — including the pegging LinearLayout —
     * share exactly the same height.
     */
    private fun makeFixedRow(): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(ROW_HEIGHT_DP)
        )
    }

    private fun makeRoundLabelCell(text: String): TextView = TextView(this).apply {
        this.text    = text
        gravity      = Gravity.CENTER
        textSize     = 12f
        setTypeface(null, Typeface.BOLD)
        // Fixed width, MATCH_PARENT height so it fills the row
        layoutParams = LinearLayout.LayoutParams(dpToPx(32), LinearLayout.LayoutParams.MATCH_PARENT)
        background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameHeaderCell(name: String, color: Int, weight: Float): TextView =
        TextView(this).apply {
            text         = name
            gravity      = Gravity.CENTER
            textSize     = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines     = 1
            ellipsize    = TextUtils.TruncateAt.END
            // weight-based width, MATCH_PARENT height
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            background   = playerCellDrawable(color)
            setTextColor(Color.WHITE)
        }

    private fun makeSubHeaderCell(label: String): TextView = TextView(this).apply {
        text         = label
        gravity      = Gravity.CENTER
        textSize     = 9f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_text))
    }

    /**
     * Pegging cell: [−] [score] [+] all inside a horizontal LinearLayout.
     * Uses MATCH_PARENT height to fill the fixed-height row completely.
     */
    private fun makePeggingCell(
        score: Int,
        scoreColor: CribbageCellColor,
        state: CellState,
        onDecrement: () -> Unit,
        onIncrement: () -> Unit
    ): LinearLayout {
        val resolvedScoreColor = resolveScoreColor(scoreColor)
        val neutralColor       = ContextCompat.getColor(this, R.color.score_cell_text)

        val bgColor = when (state) {
            CellState.EDITABLE    -> ContextCompat.getColor(this, R.color.score_cell_background)
            CellState.LOCKED_SOON -> ContextCompat.getColor(this, R.color.cribbage_locked_soon_bg)
            CellState.LOCKED_PREV,
            CellState.LOCKED_NEVER -> ContextCompat.getColor(this, R.color.cribbage_locked_prev_bg)
        }

        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            // weight=1, MATCH_PARENT height — fills the row
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            background   = cellDrawable(bgColor)
        }

        val canEdit = state == CellState.EDITABLE

        val btnMinus = TextView(this).apply {
            text         = "−"
            gravity      = Gravity.CENTER
            textSize     = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setTextColor(if (canEdit) resolvedScoreColor else neutralColor)
            alpha        = if (canEdit) 1f else 0.25f
            if (canEdit) setOnClickListener { onDecrement() }
        }

        val scoreText = TextView(this).apply {
            text         = score.toString()
            gravity      = Gravity.CENTER
            textSize     = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.2f)
            setTextColor(if (canEdit) resolvedScoreColor else neutralColor)
            alpha        = if (canEdit) 1f else if (state == CellState.LOCKED_SOON) 0.4f else 0.3f
        }

        val btnPlus = TextView(this).apply {
            text         = "+"
            gravity      = Gravity.CENTER
            textSize     = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setTextColor(if (canEdit) resolvedScoreColor else neutralColor)
            alpha        = if (canEdit) 1f else 0.25f
            if (canEdit) setOnClickListener { onIncrement() }
        }

        container.addView(btnMinus)
        container.addView(scoreText)
        container.addView(btnPlus)
        return container
    }

    /** Hand / End of round cell — tappable, MATCH_PARENT height. */
    private fun makeHandCell(
        score: Int?,
        color: CribbageCellColor,
        state: CellState,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text         = score?.toString() ?: ""
        gravity      = Gravity.CENTER
        textSize     = 14f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

        val textColor = resolveScoreColor(color)
        setTextColor(textColor)
        if (color != CribbageCellColor.DEFAULT && score != null) setTypeface(null, Typeface.BOLD)

        val bgColor = when (state) {
            CellState.EDITABLE    ->
                if (score == null)
                    ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_editable_hint)
                else
                    ContextCompat.getColor(this@CribbageGameActivity, R.color.score_cell_background)
            CellState.LOCKED_SOON  -> ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_locked_soon_bg)
            CellState.LOCKED_PREV,
            CellState.LOCKED_NEVER -> ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_locked_prev_bg)
        }
        background = cellDrawable(bgColor)

        alpha = when (state) {
            CellState.EDITABLE    -> 1f
            CellState.LOCKED_SOON -> 0.5f
            CellState.LOCKED_PREV -> if (score != null) 0.7f else 0.35f
            CellState.LOCKED_NEVER -> 0.2f
        }

        if (state == CellState.EDITABLE) setOnClickListener { onClick() }
    }

    /** Crib cell — dealer only, tappable, MATCH_PARENT height. */
    private fun makeCribCell(
        score: Int?,
        state: CellState,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text         = score?.toString() ?: ""
        gravity      = Gravity.CENTER
        textSize     = 14f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.score_cell_text))

        val bgColor = when (state) {
            CellState.EDITABLE    ->
                if (score == null)
                    ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_editable_hint)
                else
                    ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_crib_background)
            CellState.LOCKED_SOON  -> ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_locked_soon_bg)
            CellState.LOCKED_PREV,
            CellState.LOCKED_NEVER -> ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_locked_prev_bg)
        }
        background = cellDrawable(bgColor)

        alpha = when (state) {
            CellState.EDITABLE    -> 1f
            CellState.LOCKED_SOON -> 0.5f
            CellState.LOCKED_PREV -> if (score != null) 0.7f else 0.35f
            CellState.LOCKED_NEVER -> 0.2f
        }

        if (state == CellState.EDITABLE) setOnClickListener { onClick() }
    }

    /**
     * Permanent empty crib placeholder for the non-dealer player.
     * Very strongly dimmed — this player never has a crib this round.
     */
    private fun makeNeverCribCell(): TextView = TextView(this).apply {
        text         = ""
        gravity      = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_never_crib_bg))
        alpha        = 0.35f
    }

    /** Total cell with configurable weight, MATCH_PARENT height. */
    private fun makeTotalCell(text: String, weight: Float = 1f): TextView = TextView(this).apply {
        this.text    = text
        gravity      = Gravity.CENTER
        textSize     = 15f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.yahtzee_calculated_cell_background))
        setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.yahtzee_calculated_cell_text))
    }

    // ─── Drawing helpers ───────────────────────────────────────────────────────

    private fun resolveScoreColor(color: CribbageCellColor): Int = when (color) {
        CribbageCellColor.GREEN   -> ContextCompat.getColor(this, R.color.cribbage_score_green)
        CribbageCellColor.RED     -> ContextCompat.getColor(this, R.color.cribbage_score_red)
        CribbageCellColor.DEFAULT -> ContextCompat.getColor(this, R.color.score_cell_text)
    }

    /** Cell background drawable with a thin border. */
    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_cell_border))
    }

    /** Player name header cell — border only, no stroke needed at the bottom edge. */
    private fun playerCellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@CribbageGameActivity, R.color.cribbage_cell_border))
    }

    /** Solid background without a border stroke (for the round label when tinted with player colour). */
    private fun solidDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
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
