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
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
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
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class CribbageGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCribbageGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<CribbagePlayerState>

    // players[0] is firstPlayer (plays first) in round 1, alternates each round.
    // The dealer (crib holder) is always the OTHER player.
    private val rounds = mutableListOf<CribbageRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE          = "cribbage"
        private const val WIN_SCORE      = 121
        private const val MAX_HAND_SCORE = 99
        private const val MAX_CRIB_SCORE = 99

        // Fixed row height — ensures every cell in a row has the same height
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
        val roundIndex      = rounds.size
        // Even rounds: players[0] plays first; odd rounds: players[1] plays first
        val firstPlayerIndex = roundIndex % 2
        val dealerIndex      = 1 - firstPlayerIndex
        rounds.add(
            CribbageRound(
                roundNumber   = roundIndex + 1,
                firstPlayerId = playerIds[firstPlayerIndex],
                dealerId      = playerIds[dealerIndex]
            )
        )
    }

    // ─── Table construction ────────────────────────────────────────────────────

    /**
     * Fixed column layout (7 columns total — label + 3×player0 + 3×player1):
     *
     *  [#] │ players[0]: In play (w=1.5) │ End of round (w=1) │ Crib (w=1) │
     *       players[1]: In play (w=1.5)   │ End of round (w=1) │ Crib (w=1)
     *
     * The In play column is wider (weight 1.5) to give the − and + buttons
     * more tap area and reduce accidental taps on the adjacent End of round cell.
     * The Crib column for the first player (non-dealer) is always LOCKED_NEVER.
     * All cells in a row share the same fixed height (ROW_HEIGHT_DP).
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
     * Two-row fixed-height header:
     *  Row 1: [empty] │ [Player 0 name — weight 3] │ [Player 1 name — weight 3]
     *  Row 2: [#]     │ [In play][End][Crib]        │ [In play][End][Crib]
     */
    private fun buildHeaderRow(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Row 1 — player names, each spanning their 3 sub-columns (1.5 + 1 + 1 = 3.5)
        val nameRow = makeFixedRow()
        nameRow.addView(makeRoundLabelCell(""))
        for (player in players) {
            nameRow.addView(makePlayerNameHeaderCell(player.playerName, player.playerColor, weight = 3.5f))
        }
        container.addView(nameRow)

        // Row 2 — column sub-labels
        // Note: the in-play sub-label uses weight 1.5f to match the wider in-play cell
        val subRow = makeFixedRow()
        subRow.addView(makeRoundLabelCell("#"))
        repeat(players.size) {
            subRow.addView(makeSubHeaderCell(getString(R.string.cribbage_in_play), weight = 1.5f))
            subRow.addView(makeSubHeaderCell(getString(R.string.cribbage_hand)))
            subRow.addView(makeSubHeaderCell(getString(R.string.cribbage_crib)))
        }
        container.addView(subRow)

        return container
    }

    /**
     * One row per round with fixed column order (players[0] left, players[1] right).
     *
     * Entry order per round:
     *  1. In-play: both players simultaneously via +/−.
     *  2. First player enters end-of-round score (locks in play for both).
     *  3. Dealer enters end-of-round score.
     *  4. Dealer enters crib score → round complete.
     *
     * The round label is tinted with the FIRST PLAYER's colour.
     * The DEALER's crib column is interactive; the first player's crib is LOCKED_NEVER.
     *
     * Previous round locking:
     *  A completed round's end-of-round + crib cells stay editable until the next
     *  round has any in play activity.
     */
    private fun buildRoundRow(round: CribbageRound, roundIndex: Int): LinearLayout {
        val isLastRound  = roundIndex == rounds.lastIndex
        val isPrevRound  = roundIndex == rounds.lastIndex - 1
        val currentRound = rounds.last()

        // Previous round editable until the new round gets any in play
        val prevRoundEditable = isPrevRound && !gameOver && !currentRound.hasInPlayActivity()

        val row = makeFixedRow()

        // Round label — tinted with the FIRST PLAYER's colour
        val firstPlayer = players.first { it.playerId == round.firstPlayerId }
        val roundLabel  = makeRoundLabelCell(round.roundNumber.toString())
        roundLabel.background = solidDrawable(firstPlayer.playerColor)
        roundLabel.setTextColor(Color.WHITE)
        row.addView(roundLabel)

        val p0    = players[0]
        val p1    = players[1]
        val inPlay0  = round.inPlayScores[p0.playerId] ?: 0
        val inPlay1  = round.inPlayScores[p1.playerId] ?: 0
        val hand0 = round.handScores[p0.playerId]
        val hand1 = round.handScores[p1.playerId]

        // Build cells for each player in fixed order
        listOf(p0, p1).forEach { player ->
            val isFirstPlayer = player.playerId == round.firstPlayerId
            val isDealer      = player.playerId == round.dealerId
            val myInPlay         = round.inPlayScores[player.playerId] ?: 0
            val oppInPlay        = if (player == p0) inPlay1 else inPlay0
            val myHand        = round.handScores[player.playerId]
            val oppHand       = if (player == p0) hand1 else hand0

            // ── En jeu (In play) ──────────────────────────────────────────────
            // Editable while the round is still in the in-play phase AND this is
            // the active round or the still-unlocked previous round.
            val inPlayCanEdit = !gameOver && round.isInPlayEditable() &&
                    (isLastRound || (prevRoundEditable && !round.isFirstPlayerHandEntered()))

            val inPlayColor = when {
                myInPlay > oppInPlay -> CribbageCellColor.GREEN
                myInPlay < oppInPlay -> CribbageCellColor.RED
                else           -> CribbageCellColor.DEFAULT
            }

            val inPlayState = when {
                inPlayCanEdit              -> CellState.EDITABLE
                !round.isInPlayEditable() -> CellState.LOCKED_PREV  // locked once first player enters hand
                isLastRound && !gameOver   -> CellState.LOCKED_SOON  // round open, waiting for something
                else                       -> CellState.LOCKED_PREV
            }

            row.addView(makeInPlayCell(
                score       = myInPlay,
                scoreColor  = inPlayColor,
                state       = inPlayState,
                onDecrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    if (cur > 0) {
                        round.inPlayScores[player.playerId] = cur - 1
                        buildTable()
                        checkGameOver()
                    }
                },
                onIncrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    round.inPlayScores[player.playerId] = cur + 1
                    buildTable()
                    checkGameOver()
                }
            ))

            // ── End of round (Hand) ────────────────────────────────────────────
            // First player enters first (always editable until crib done).
            // Dealer enters second (only after first player has entered).
            // Prev round stays editable until next round has in play.
            val handEditable = !gameOver && when {
                isLastRound && isFirstPlayer -> myHand == null || round.cribScore == null
                isLastRound && isDealer      -> round.isFirstPlayerHandEntered() &&
                        (myHand == null || round.cribScore == null)
                prevRoundEditable            -> true
                else                         -> false
            }

            val handState = when {
                handEditable                                             -> CellState.EDITABLE
                isLastRound && isDealer && !round.isFirstPlayerHandEntered()
                        && !gameOver                                    -> CellState.LOCKED_SOON
                isLastRound && round.cribScore != null                  -> CellState.LOCKED_PREV
                isLastRound && !gameOver                                -> CellState.LOCKED_SOON
                else                                                    -> CellState.LOCKED_PREV
            }

            val handColor = when {
                myHand != null && oppHand != null && myHand > oppHand -> CribbageCellColor.GREEN
                myHand != null && oppHand != null && myHand < oppHand -> CribbageCellColor.RED
                else -> CribbageCellColor.DEFAULT
            }

            row.addView(makeHandCell(
                score   = myHand,
                color   = handColor,
                state   = handState,
                onClick = { showHandScoreInput(round, player.playerId) }
            ))

            // ── Crib ──────────────────────────────────────────────────────────
            // Only the DEALER has a crib. The first player's crib column is LOCKED_NEVER.
            if (isDealer) {
                val cribEditable = !gameOver &&
                        (isLastRound && round.isDealerHandEntered() && round.cribScore == null
                                || prevRoundEditable)

                val cribState = when {
                    cribEditable                                        -> CellState.EDITABLE
                    isLastRound && !round.isDealerHandEntered()
                            && !gameOver                               -> CellState.LOCKED_SOON
                    isLastRound && round.cribScore != null             -> CellState.LOCKED_PREV
                    isLastRound && !gameOver                           -> CellState.LOCKED_SOON
                    else                                               -> CellState.LOCKED_PREV
                }

                row.addView(makeCribCell(
                    score   = round.cribScore,
                    state   = cribState,
                    onClick = { showCribScoreInput(round) }
                ))
            } else {
                // First player never has a crib — permanent placeholder
                row.addView(makeNeverCribCell())
            }
        }

        return row
    }

    /** Total row — each player spans all 3 sub-columns (weight 3.5 = 1.5+1+1). */
    private fun buildTotalRow(): LinearLayout {
        val row    = makeFixedRow()
        row.addView(makeRoundLabelCell(getString(R.string.cribbage_total)))

        val totals   = players.associate { it.playerId to it.getTotal(rounds) }
        val maxTotal = totals.values.maxOrNull() ?: 0

        for (player in players) {
            val total = totals[player.playerId] ?: 0
            val cell  = makeTotalCell(total.toString(), weight = 3.5f)
            if (gameOver && total == maxTotal) {
                cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best))
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
        // Guard: if this round is not the last one, it is a re-edit of a previous round
        // that was already completed. A new round already exists — do not add another.
        if (round !== rounds.last()) {
            buildTable()
            return
        }
        checkGameOver()
        if (!gameOver) {
            addNewRound()
            buildTable()
        }
    }

    // ─── Score input dialogs ───────────────────────────────────────────────────

    /**
     * End-of-round score input dialog.
     * - First player can enter at any time (even during in play phase).
     * - Dealer can only enter after first player has entered.
     */
        private fun showHandScoreInput(round: CribbageRound, playerId: Long) {
        val isFirstPlayer = playerId == round.firstPlayerId
        if (!isFirstPlayer && !round.isFirstPlayerHandEntered()) return
 
        val playerName = players.first { it.playerId == playerId }.playerName
        val current    = round.handScores[playerId]
 
        // Pencil in title when re-editing
        val title = if (current != null) "✏️ $playerName — ${getString(R.string.cribbage_hand_score)}"
                    else "$playerName — ${getString(R.string.cribbage_hand_score)}"
 
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
 
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
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
            .create()
 
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        editText.requestFocus()
    }

    private fun showCribScoreInput(round: CribbageRound) {
        val dealerName = players.first { it.playerId == round.dealerId }.playerName
        val current    = round.cribScore
 
        // Pencil in title when re-editing
        val title = if (current != null) "✏️ $dealerName — ${getString(R.string.cribbage_crib_score)}"
                    else "$dealerName — ${getString(R.string.cribbage_crib_score)}"
 
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint      = "0–$MAX_CRIB_SCORE"
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
 
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
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
            .create()
 
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
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

    /** A horizontal row with a fixed height so all cells align uniformly. */
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            background   = cellDrawable(color)
            setTextColor(Color.WHITE)
        }

    private fun makeSubHeaderCell(label: String, weight: Float = 1f): TextView = TextView(this).apply {
        text         = label
        gravity      = Gravity.CENTER
        textSize     = 9f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background   = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_text))
    }

    /**
     * In-play cell: [−] [score] [+]
     * Uses MATCH_PARENT height to fill the fixed-height row completely.
     *
     * Layout notes:
     *  - The container has weight=1.5f (vs 1f for hand/crib) to give more tap area to − and +.
     *  - The − and + buttons each have weight=1.5f internally so they are clearly larger than
     *    the score label, reducing accidental taps on the adjacent hand cell.
     *  - Score color (green/red/neutral) is ALWAYS applied regardless of lock state, so the
     *    comparison is still visible after the round is locked.
     */
    private fun makeInPlayCell(
        score: Int,
        scoreColor: CribbageCellColor,
        state: CellState,
        onDecrement: () -> Unit,
        onIncrement: () -> Unit
    ): LinearLayout {
        // Score color always applied — preserved even when locked
        val resolvedScoreColor = resolveScoreColor(scoreColor)
        val bgColor            = resolveBgColor(state, score = null)

        // Wider container (weight 1.5) than hand/crib (weight 1) to give + and − more room
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
            background   = cellDrawable(bgColor)
        }

        val canEdit = state == CellState.EDITABLE

        // Alpha for locked state — score text slightly dimmed but colour still visible
        val lockedAlpha = when (state) {
            CellState.LOCKED_SOON  -> 0.55f
            CellState.LOCKED_PREV  -> 0.65f
            else                   -> 0.35f
        }

        val btnMinus = TextView(this).apply {
            text         = "−"
            gravity      = Gravity.CENTER
            textSize     = 18f
            setTypeface(null, Typeface.BOLD)
            // Larger weight (1.5) → bigger tap target
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
            setTextColor(resolvedScoreColor)
            alpha        = if (canEdit) 1f else 0.25f
            if (canEdit) setOnClickListener { onDecrement() }
        }

        val scoreText = TextView(this).apply {
            text         = score.toString()
            gravity      = Gravity.CENTER
            textSize     = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            // Always use the score color so green/red comparison stays visible when locked
            setTextColor(resolvedScoreColor)
            alpha        = if (canEdit) 1f else lockedAlpha
        }

        val btnPlus = TextView(this).apply {
            text         = "+"
            gravity      = Gravity.CENTER
            textSize     = 18f
            setTypeface(null, Typeface.BOLD)
            // Larger weight (1.5) → bigger tap target
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
            setTextColor(resolvedScoreColor)
            alpha        = if (canEdit) 1f else 0.25f
            if (canEdit) setOnClickListener { onIncrement() }
        }

        container.addView(btnMinus)
        container.addView(scoreText)
        container.addView(btnPlus)
        return container
    }

    /** End-of-round (hand) cell — tappable, MATCH_PARENT height. */
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

        setTextColor(resolveScoreColor(color))
        if (color != CribbageCellColor.DEFAULT && score != null) setTypeface(null, Typeface.BOLD)

        background = cellDrawable(resolveBgColor(state, score))
        alpha      = resolveAlpha(state, score)

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

        // When filled and editable, use the crib-specific background colour
        val bgColor = if (state == CellState.EDITABLE && score != null)
            ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_editable_bg)
        else
            resolveBgColor(state, score)

        background = cellDrawable(bgColor)
        alpha      = resolveAlpha(state, score)

        if (state == CellState.EDITABLE) setOnClickListener { onClick() }
    }

    /**
     * Permanent crib placeholder for the first player (never has a crib).
     * Uses the darkest background and strong dimming.
     */
    private fun makeNeverCribCell(): TextView = TextView(this).apply {
        text         = ""
        gravity      = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background   = cellDrawable(
            ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_never_bg)
        )
    }

    /** Total cell spanning [weight] sub-columns, MATCH_PARENT height. */
    private fun makeTotalCell(text: String, weight: Float = 1f): TextView = TextView(this).apply {
        this.text    = text
        gravity      = Gravity.CENTER
        textSize     = 15f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background   = cellDrawable(
            ContextCompat.getColor(this@CribbageGameActivity, R.color.yahtzee_calculated_cell_background)
        )
        setTextColor(
            ContextCompat.getColor(this@CribbageGameActivity, R.color.yahtzee_calculated_cell_text)
        )
    }

    // ─── Visual helpers ────────────────────────────────────────────────────────

    private fun resolveScoreColor(color: CribbageCellColor): Int = when (color) {
        CribbageCellColor.GREEN   -> ContextCompat.getColor(this, R.color.score_text_best)
        CribbageCellColor.RED     -> ContextCompat.getColor(this, R.color.score_text_worst)
        CribbageCellColor.DEFAULT -> ContextCompat.getColor(this, R.color.score_cell_text)
    }

    /**
     * Background colour based on cell state.
     * [score] is used to differentiate editable-filled vs editable-empty.
     */
    private fun resolveBgColor(state: CellState, score: Int?): Int = when (state) {
        CellState.EDITABLE     ->
            if (score == null)
                ContextCompat.getColor(this, R.color.cell_editable_bg)
            else
                ContextCompat.getColor(this, R.color.score_cell_background)
        CellState.LOCKED_SOON  -> ContextCompat.getColor(this, R.color.cell_editable_bg)
        CellState.LOCKED_PREV  -> ContextCompat.getColor(this, R.color.cell_locked_bg)
        CellState.LOCKED_NEVER -> ContextCompat.getColor(this, R.color.cell_never_bg)
    }

    /**
     * Alpha based on cell state — creates clear visual hierarchy:
     *  EDITABLE     → fully visible
     *  LOCKED_SOON  → half-transparent (waiting)
     *  LOCKED_PREV  → slightly dimmed if has value, strongly dimmed if empty
     *  LOCKED_NEVER → invisible (no information to show)
     */
    private fun resolveAlpha(state: CellState, score: Int?): Float = when (state) {
        CellState.EDITABLE     -> 1f
        CellState.LOCKED_SOON  -> 0.55f
        CellState.LOCKED_PREV  -> if (score != null) 0.75f else 0.4f
        CellState.LOCKED_NEVER -> 1f  // background colour already signals unavailability
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_border))
    }

    private fun solidDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_cribbage_game, menu)
        return true
    }

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
            R.id.action_help -> {
                HelpDialogs.showAppHelp(this, GAME_TYPE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
