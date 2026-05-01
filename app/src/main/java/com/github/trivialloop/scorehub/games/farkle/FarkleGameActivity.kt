package com.github.trivialloop.scorehub.games.farkle

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityFarkleGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

/**
 * Farkle game screen.
 *
 * Table layout — each horizontal row has WRAP_CONTENT height so the active-turn
 * cell (which grows with each Add entry) automatically pushes all other cells in
 * the same row to the same height, giving perfect cross-column alignment.
 *
 * The "round slot" concept:
 *   Round slot N represents the N-th turn for every player.
 *   - If a player has completed their N-th turn → show the banked/farkled score.
 *   - If it is their active turn (in progress) → show the live Add/Bank/Farkle UI.
 *   - If they haven't reached that slot yet → show an empty placeholder.
 *
 * This guarantees that row N is always the same logical turn for every player
 * and that heights stay aligned even as the active cell grows.
 */
class FarkleGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFarkleGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<FarklePlayerState>

    private val rounds = mutableListOf<FarkleRound>()
    private var currentPlayerIndex = 0
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "farkle"
        private const val SCORE_LIMIT = 10_000

        private const val HEADER_ROW_DP = 52
        private const val ROUND_ROW_DP  = 48  // height of a completed round cell
        private const val ENTRY_ROW_DP  = 36  // height of one roll-entry line
        private const val SUM_ROW_DP    = 28  // height of the "= xxx" subtotal line
        private const val BTN_ROW_DP    = 44  // height of the Add/Bank/Farkle buttons
        private const val TOTAL_ROW_DP  = 52
        private const val LABEL_COL_DP  = 48
    }

    // ─── Visible players ──────────────────────────────────────────────────────

    private val totalPlayers get() = players.size

    private fun getVisiblePlayers(): List<Pair<Int, FarklePlayerState>> {
        return when {
            totalPlayers <= 3 -> players.indices.map { it to players[it] }
            else -> {
                val prev = if (currentPlayerIndex == 0) totalPlayers - 1 else currentPlayerIndex - 1
                val next = if (currentPlayerIndex == totalPlayers - 1) 0 else currentPlayerIndex + 1
                listOf(prev to players[prev], currentPlayerIndex to players[currentPlayerIndex], next to players[next])
            }
        }
    }

    private fun columnWeight(isActive: Boolean): Float = when {
        totalPlayers == 1 -> 1f
        totalPlayers == 2 -> if (isActive) 0.8f else 0.2f
        else              -> if (isActive) 0.6f else 0.2f
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFarkleGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())

            binding.appBarLayout.setPadding(
                0,
                statusBarInsets.top,
                0,
                0
            )

            insets
        }

        database     = AppDatabase.getDatabase(this)
        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()

        players = playerIds.indices.map { i ->
            FarklePlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.farkle_game)

        startTurnFor(currentPlayerIndex)
        buildTable()
    }

    // ─── Turn management ──────────────────────────────────────────────────────

    private fun startTurnFor(playerIndex: Int) {
        val player = players[playerIndex]
        val roundNumber = rounds.count { it.playerId == player.playerId && it.isComplete } + 1
        rounds.add(FarkleRound(roundNumber = roundNumber, playerId = player.playerId))
    }

    // ─── Table construction ───────────────────────────────────────────────────

    private fun buildTable() {
        binding.scoreTableContainer.removeAllViews()

        val visible = getVisiblePlayers()

        // Completed rounds per visible player
        val completedByPlayer: Map<Long, List<FarkleRound>> = visible.associate { (_, p) ->
            p.playerId to rounds.filter { it.playerId == p.playerId && it.isComplete }
        }

        // Active (in-progress) turn: only the current player has one
        val activeTurn: FarkleRound? =
            rounds.lastOrNull { it.playerId == players[currentPlayerIndex].playerId && !it.isComplete }

        // Number of the active round slot = completed rounds of current player + 1
        // (the slot that is currently being played)
        val activeSlot: Int = completedByPlayer[players[currentPlayerIndex].playerId]?.size ?: 0

        // Total number of round-slot rows to display:
        // max completed across all visible players + 1 for the active slot (always show it)
        val maxCompleted = completedByPlayer.values.maxOfOrNull { it.size } ?: 0
        val totalSlots = maxOf(maxCompleted, activeSlot + 1)

        // Totals for color comparison
        val allTotals = visible.map { (_, p) -> p.getTotal(rounds) }

        // ── Header row ────────────────────────────────────────────────────────
        binding.scoreTableContainer.addView(buildHeaderRow(visible))

        // ── Round-slot rows ───────────────────────────────────────────────────
        // Each slot is ONE horizontal row with WRAP_CONTENT height.
        // The active-turn cell grows vertically; all other cells in the row
        // inherit the same height automatically via MATCH_PARENT.
        for (slotIdx in 0 until totalSlots) {
            val isActiveSlot = slotIdx == activeSlot

            // Scores for this slot, per visible player (null = not yet played)
            val slotScores: List<Int?> = visible.map { (_, p) ->
                completedByPlayer[p.playerId]?.getOrNull(slotIdx)?.score
            }

            binding.scoreTableContainer.addView(
                buildSlotRow(visible, completedByPlayer, slotIdx, isActiveSlot, activeTurn, slotScores)
            )
        }

        // ── Total row ─────────────────────────────────────────────────────────
        binding.scoreTableContainer.addView(buildTotalRow(visible, allTotals))

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ─── Row builders ─────────────────────────────────────────────────────────

    private fun buildHeaderRow(visible: List<Pair<Int, FarklePlayerState>>): LinearLayout {
        // Fixed height for the header
        val row = makeRow(HEADER_ROW_DP)
        row.addView(makeLabelCell("", HEADER_ROW_DP, isTotal = false))
        for ((idx, player) in visible) {
            val isActive = idx == currentPlayerIndex
            row.addView(makePlayerNameCell(player, isActive, columnWeight(isActive)))
        }
        return row
    }

    /**
     * One row per round slot.
     * Height is WRAP_CONTENT so it expands with the active-turn cell.
     * All other cells use MATCH_PARENT so they fill the same height.
     */
    private fun buildSlotRow(
        visible: List<Pair<Int, FarklePlayerState>>,
        completedByPlayer: Map<Long, List<FarkleRound>>,
        slotIdx: Int,
        isActiveSlot: Boolean,
        activeTurn: FarkleRound?,
        slotScores: List<Int?>
    ): LinearLayout {
        // WRAP_CONTENT height so active cell can grow
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Label cell: round number (1-based)
        row.addView(makeLabelCellMatchParent((slotIdx + 1).toString(), isTotal = false))

        for ((colIdx, pair) in visible.withIndex()) {
            val (idx, player) = pair
            val isActive = idx == currentPlayerIndex
            val w = columnWeight(isActive)

            when {
                // Active slot for the active player → show live Add/Bank/Farkle UI
                isActiveSlot && isActive && activeTurn != null -> {
                    row.addView(makeActiveTurnCell(activeTurn, w))
                }

                // Completed round for this player at this slot
                completedByPlayer[player.playerId]?.getOrNull(slotIdx) != null -> {
                    val round = completedByPlayer[player.playerId]!!.getOrNull(slotIdx)!!
                    // Color: highest score in slot → green, lowest → red
                    val role = ScoreColorRole(slotScores[colIdx], slotScores)
                    val textColor = when (role) {
                        ScoreColorRole.WORST -> ContextCompat.getColor(this, R.color.score_text_best)
                        ScoreColorRole.BEST  -> ContextCompat.getColor(this, R.color.score_text_worst)
                        else                 -> ContextCompat.getColor(this, R.color.score_cell_text)
                    }
                    row.addView(makeCompletedRoundCell(round, textColor, isActive, w))
                }

                // Placeholder: player hasn't reached this slot yet
                else -> {
                    row.addView(makePlaceholderCell(w))
                }
            }
        }

        return row
    }

    private fun buildTotalRow(
        visible: List<Pair<Int, FarklePlayerState>>,
        allTotals: List<Int>
    ): LinearLayout {
        val row = makeRow(TOTAL_ROW_DP)
        row.addView(makeLabelCell(getString(R.string.farkle_total), TOTAL_ROW_DP, isTotal = true))
        for ((colIdx, pair) in visible.withIndex()) {
            val (idx, _) = pair
            val isActive = idx == currentPlayerIndex
            val total = allTotals[colIdx]
            // Higher total = better → WORST (=min) → red, BEST (=max) → green
            val role = ScoreColorRole(total, allTotals)
            val textColor = ContextCompat.getColor(this, R.color.yahtzee_calculated_cell_text)
            row.addView(makeTotalCell(total, textColor, isActive, columnWeight(isActive)))
        }
        return row
    }

    // ─── Cell builders ────────────────────────────────────────────────────────

    /** Fixed-height row (used for header and total). */
    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp)
        )
    }

    private fun colLp(weight: Float) =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)

    private fun fixedColLp(weight: Float, heightDp: Int) =
        LinearLayout.LayoutParams(0, dpToPx(heightDp), weight)

    /** Label cell with fixed width and fixed height (for header/total rows). */
    private fun makeLabelCell(text: String, heightDp: Int, isTotal: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(heightDp))
            val bg = if (isTotal) R.color.yahtzee_calculated_cell_background else R.color.header_cell_background
            val fg = if (isTotal) R.color.yahtzee_calculated_cell_text else R.color.header_cell_text
            background = cellDrawable(ContextCompat.getColor(this@FarkleGameActivity, bg))
            setTextColor(ContextCompat.getColor(this@FarkleGameActivity, fg))
        }

    /** Label cell with fixed width and MATCH_PARENT height (for slot rows). */
    private fun makeLabelCellMatchParent(text: String, isTotal: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT
            )
            val bg = if (isTotal) R.color.yahtzee_calculated_cell_background else R.color.header_cell_background
            val fg = if (isTotal) R.color.yahtzee_calculated_cell_text else R.color.header_cell_text
            background = cellDrawable(ContextCompat.getColor(this@FarkleGameActivity, bg))
            setTextColor(ContextCompat.getColor(this@FarkleGameActivity, fg))
        }

    private fun makePlayerNameCell(player: FarklePlayerState, isActive: Boolean, weight: Float): TextView =
        TextView(this).apply {
            text = player.playerName
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = fixedColLp(weight, HEADER_ROW_DP)
            background = cellDrawable(player.playerColor)
            setTextColor(Color.WHITE)
            alpha = if (isActive) 1f else 0.55f
        }

    /**
     * Completed turn cell — MATCH_PARENT height so it fills the slot row.
     * Always shows the numeric score (0 for farkle).
     */
    private fun makeCompletedRoundCell(
        round: FarkleRound,
        textColor: Int,
        isActive: Boolean,
        weight: Float
    ): TextView = TextView(this).apply {
        text = round.score.toString()
        gravity = Gravity.CENTER
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        layoutParams = colLp(weight)
        background = cellDrawable(ContextCompat.getColor(this@FarkleGameActivity, R.color.score_cell_background))
        setTextColor(textColor)
        alpha = if (isActive) 1f else 0.65f
        // Minimum height so a 0-entry completed row doesn't collapse
        minimumHeight = dpToPx(ROUND_ROW_DP)
    }

    /**
     * Placeholder cell for a slot a player hasn't reached yet.
     * MATCH_PARENT height, minimum height = ROUND_ROW_DP so it stays aligned
     * with completed cells in the same row.
     */
    private fun makePlaceholderCell(weight: Float): TextView = TextView(this).apply {
        text = ""
        layoutParams = colLp(weight)
        background = cellDrawable(ContextCompat.getColor(this@FarkleGameActivity, R.color.score_cell_background))
        minimumHeight = dpToPx(ROUND_ROW_DP)
    }

    /**
     * Active turn cell — vertical LinearLayout with WRAP_CONTENT height.
     * This is what makes the slot row grow when the player adds entries.
     *
     * Contents:
     *   - One TextView per roll entry (last one is re-editable)
     *   - "= sum" line if at least one entry
     *   - Add / Bank / Farkle button row
     */
    private fun makeActiveTurnCell(turn: FarkleRound, weight: Float): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            background = cellDrawable(ContextCompat.getColor(this@FarkleGameActivity, R.color.cell_editable_bg))

            // Roll entries
            for ((index, entry) in turn.rollEntries.withIndex()) {
                val isLast = index == turn.rollEntries.lastIndex
                addView(TextView(this@FarkleGameActivity).apply {
                    text = entry.toString()
                    gravity = Gravity.CENTER
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ENTRY_ROW_DP)
                    )
                    if (isLast) {
                        background = cellDrawable(ContextCompat.getColor(this@FarkleGameActivity, R.color.cell_editable_filled_bg))
                        setTypeface(null, Typeface.BOLD)
                        setOnClickListener { showEditEntryDialog(turn, index) }
                    } else {
                        background = cellDrawable(ContextCompat.getColor(this@FarkleGameActivity, R.color.score_cell_background))
                    }
                    setTextColor(ContextCompat.getColor(this@FarkleGameActivity, R.color.score_cell_text))
                })
            }

            // Running sum
            if (turn.rollEntries.isNotEmpty()) {
                addView(TextView(this@FarkleGameActivity).apply {
                    text = "= ${turn.entrySum}"
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(SUM_ROW_DP)
                    )
                    background = cellDrawable(ContextCompat.getColor(this@FarkleGameActivity, R.color.header_cell_background))
                    setTextColor(ContextCompat.getColor(this@FarkleGameActivity, R.color.header_cell_text))
                })
            }

            // Add / Bank / Farkle buttons
            addView(LinearLayout(this@FarkleGameActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(BTN_ROW_DP)
                )
                addView(makeActionButton(getString(R.string.farkle_btn_add), isFarkle = false, isAdd = true) {
                    showAddScoreDialog(turn)
                })
                addView(makeActionButton(getString(R.string.farkle_btn_bank), isFarkle = false, isAdd = false) {
                    if (turn.rollEntries.isEmpty()) performFarkle(turn) else performBank(turn)
                })
                addView(makeActionButton(getString(R.string.farkle_btn_farkle), isFarkle = true, isAdd = false) {
                    performFarkle(turn)
                })
            })
        }

    private fun makeActionButton(label: String, isFarkle: Boolean, isAdd: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val bgColor = when {
                isFarkle -> ContextCompat.getColor(this@FarkleGameActivity, R.color.score_text_worst)
                isAdd    -> ContextCompat.getColor(this@FarkleGameActivity, R.color.cell_editable_bg)
                else     -> ContextCompat.getColor(this@FarkleGameActivity, R.color.score_text_best)
            }
            background = cellDrawable(bgColor)
            setTextColor(
                if (isAdd) ContextCompat.getColor(this@FarkleGameActivity, R.color.score_cell_text)
                else Color.WHITE
            )
            setOnClickListener { onClick() }
        }

    private fun makeTotalCell(total: Int, textColor: Int, isActive: Boolean, weight: Float): TextView =
        TextView(this).apply {
            text = total.toString()
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = fixedColLp(weight, TOTAL_ROW_DP)
            background = cellDrawable(ContextCompat.getColor(this@FarkleGameActivity, R.color.yahtzee_calculated_cell_background))
            setTextColor(textColor)
            alpha = if (isActive) 1f else 0.75f
        }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@FarkleGameActivity, R.color.cell_border))
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private fun showAddScoreDialog(turn: FarkleRound) {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.farkle_score_hint)
            gravity = Gravity.CENTER
            textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.farkle_btn_add))
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value <= 0) { showAddScoreDialog(turn); return@setPositiveButton }
                turn.rollEntries.add(value)
                buildTable()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        editText.requestFocus()
    }

    private fun showEditEntryDialog(turn: FarkleRound, entryIndex: Int) {
        val current = turn.rollEntries[entryIndex]
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.farkle_score_hint)
            gravity = Gravity.CENTER
            textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(6))
            setText(current.toString())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("✏️ ${getString(R.string.farkle_btn_add)}")
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value <= 0) { showEditEntryDialog(turn, entryIndex); return@setPositiveButton }
                turn.rollEntries[entryIndex] = value
                buildTable()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        editText.requestFocus()
    }

    // ─── Game actions ─────────────────────────────────────────────────────────

    private fun performBank(turn: FarkleRound) {
        turn.banked = true
        checkEndOfGameAndAdvance()
    }

    private fun performFarkle(turn: FarkleRound) {
        turn.farkled = true
        turn.rollEntries.clear()
        checkEndOfGameAndAdvance()
    }

    private fun checkEndOfGameAndAdvance() {
        val player = players[currentPlayerIndex]
        val total = player.getTotal(rounds)
        if (total >= SCORE_LIMIT && !gameOver) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.farkle_game_over_title))
                .setMessage(getString(R.string.farkle_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true
                    buildTable()
                    saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    advanceToNextPlayer()
                }
                .show()
        } else {
            advanceToNextPlayer()
        }
    }

    private fun advanceToNextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        startTurnFor(currentPlayerIndex)
        buildTable()
    }

    // ─── Save results ─────────────────────────────────────────────────────────

    private fun saveResultsAndShowSummary() {
        // Use playerId as key (not FarklePlayerState) to avoid data-class equality issues.
        val totals   = players.associate { it.playerId to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winnerIds = totals.filter { it.value == maxScore }.keys
        val isDraw   = winnerIds.size > 1
        // Single timestamp so all GameResults share the same playedAt — required for
        // getCountedGamesPlayedByPlayer to recognise them as one multiplayer session.
        val playedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                val score = totals[player.playerId] ?: 0
                GameResult(
                    gameType   = GAME_TYPE,
                    playerId   = player.playerId,
                    playerName = player.playerName,
                    score      = score,
                    isWinner   = !isDraw && player.playerId in winnerIds,
                    isDraw     = isDraw && player.playerId in winnerIds,
                    playedAt   = playedAt
                )
            })
            val sortedPlayers = players.sortedByDescending { totals[it.playerId] ?: 0 }
            var rank = 1
            val entries = sortedPlayers.mapIndexed { i, p ->
                val s = totals[p.playerId] ?: 0
                val prevScore = if (i > 0) totals[sortedPlayers[i - 1].playerId] ?: 0 else s
                val r = if (i > 0 && s == prevScore) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@FarkleGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_farkle_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.farkle_quit_game)
                    .setMessage(R.string.farkle_quit_game_message)
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