package com.github.trivialloop.scorehub.games.free_game

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.databinding.ActivityFreegameGameBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole

class FreeGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFreegameGameBinding
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<FreeGamePlayerState>

    // All completed rounds + the current in-progress round (if any)
    private val rounds = mutableListOf<FreeGameRound>()

    // Index of the currently selected/active player
    private var currentPlayerIndex = 0

    // Handler + Runnable for the 2-second auto-commit timer
    private val commitHandler = Handler(Looper.getMainLooper())
    private val commitRunnable = Runnable { commitCurrentRound() }

    companion object {
        const val GAME_TYPE        = "free_game"
        private const val COMMIT_DELAY_MS  = 2000L
        private const val HEADER_ROW_DP    = 52
        private const val ROUND_ROW_DP     = 40
        private const val ACTIVE_ROW_DP    = 44
        private const val BTN_ROW_DP       = 48
        private const val TOTAL_ROW_DP     = 52
        private const val LABEL_COL_DP     = 65
    }

    private val totalPlayers get() = players.size

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFreegameGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.appBarLayout.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()

        players = playerIds.indices.map { i ->
            FreeGamePlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.free_game_game)

        buildTable()
    }

    override fun onDestroy() {
        commitHandler.removeCallbacks(commitRunnable)
        super.onDestroy()
    }

    // ─── Visible players (max 5, centred on currentPlayerIndex) ──────────────

    private fun getVisiblePlayers(): List<Pair<Int, FreeGamePlayerState>> = when {
        totalPlayers <= 5 -> players.indices.map { it to players[it] }
        else -> {
            val prev2 = (currentPlayerIndex - 2 + totalPlayers) % totalPlayers
            val prev1 = (currentPlayerIndex - 1 + totalPlayers) % totalPlayers
            val next1 = (currentPlayerIndex + 1) % totalPlayers
            val next2 = (currentPlayerIndex + 2) % totalPlayers
            listOf(
                prev2 to players[prev2],
                prev1 to players[prev1],
                currentPlayerIndex to players[currentPlayerIndex],
                next1 to players[next1],
                next2 to players[next2]
            )
        }
    }

    private fun columnWeight(isActive: Boolean): Float = when {
        totalPlayers == 1 -> 1f
        totalPlayers == 2 -> if (isActive) 0.8f else 0.2f
        totalPlayers <= 5 -> if (isActive) 0.6f else 0.2f
        else -> if (isActive) 0.4f else 0.15f
    }

    // ─── Round / timer logic ──────────────────────────────────────────────────

    /** The current in-progress (uncommitted) round, if any. */
    private fun currentRound(): FreeGameRound? =
        rounds.lastOrNull { !it.isComplete }

    /**
     * Add a score delta to the active player's current round.
     * Resets the 2-second commit timer.
     */
    private fun addScore(delta: Int) {
        val active = players[currentPlayerIndex]

        // If there is no in-progress round for the active player, create one.
        val round = currentRound()
            ?.takeIf { it.playerId == active.playerId }
            ?: run {
                // Commit any lingering round from a different player first.
                currentRound()?.let { it.isComplete = true }
                val newRound = FreeGameRound(
                    roundNumber = rounds.count { it.isComplete } + 1,
                    playerId    = active.playerId
                )
                rounds.add(newRound)
                newRound
            }

        round.score += delta

        // Reset the 2-second timer
        commitHandler.removeCallbacks(commitRunnable)
        commitHandler.postDelayed(commitRunnable, COMMIT_DELAY_MS)

        buildTable()
    }

    /** Commit the current in-progress round (called after 2s timeout). */
    private fun commitCurrentRound() {
        currentRound()?.isComplete = true
        buildTable()
    }

    /**
     * Delete the last completed round (only if there is no active in-progress round)
     * and start a new editable 2-second window for the same player.
     * Used when the user taps the last round cell of the active player.
     */
    private fun undoLastRound() {
        // Only allow undo when there is no active in-progress round
        if (currentRound() != null) return

        val lastCompleted = rounds.lastOrNull { it.isComplete } ?: return
        rounds.remove(lastCompleted)

        // Re-create the round as in-progress
        currentPlayerIndex = players.indexOfFirst { it.playerId == lastCompleted.playerId }
            .takeIf { it >= 0 } ?: currentPlayerIndex
        val restored = FreeGameRound(
            roundNumber = rounds.count { it.isComplete } + 1,
            playerId    = lastCompleted.playerId,
            score       = lastCompleted.score
        )
        rounds.add(restored)

        // Start the 2-second window
        commitHandler.removeCallbacks(commitRunnable)
        commitHandler.postDelayed(commitRunnable, COMMIT_DELAY_MS)

        buildTable()
    }

    // ─── Table construction ───────────────────────────────────────────────────

    private fun buildTable() {
        val visible      = getVisiblePlayers()
        val completedByPlayer: Map<Long, List<FreeGameRound>> = visible.associate { (_, p) ->
            p.playerId to rounds.filter { it.playerId == p.playerId && it.isComplete }
        }
        val activeRound  = currentRound()
        val allTotals    = visible.map { (_, p) -> p.getTotal(rounds) }

        val maxCompleted = completedByPlayer.values.maxOfOrNull { it.size } ?: 0
        // If there is an active round being entered, we need one extra slot
        val activeSlot   = if (activeRound != null) maxCompleted else -1
        val totalSlots   = if (activeRound != null) maxCompleted + 1 else maxCompleted

        val headerRow    = buildHeaderRow(visible)
        val slotRows     = (0 until totalSlots).map { slotIdx ->
            val isActiveSlot = activeRound != null && slotIdx == maxCompleted
            val slotScores: List<Int?> = visible.map { (_, p) ->
                completedByPlayer[p.playerId]?.getOrNull(slotIdx)?.score
            }
            buildSlotRow(visible, completedByPlayer, slotIdx, isActiveSlot, activeRound, slotScores)
        }
        val totalRow     = buildTotalRow(visible, allTotals)
        val controlRow   = buildControlRow()

        // Fixed header
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(headerRow)
        binding.headerContainer.addView(controlRow)

        // Scrollable: slot rows + total
        binding.tableContainer.removeAllViews()
        slotRows.forEach { binding.tableContainer.addView(it) }
        binding.tableContainer.addView(totalRow)

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ─── Header row ───────────────────────────────────────────────────────────

    private fun buildHeaderRow(visible: List<Pair<Int, FreeGamePlayerState>>): LinearLayout {
        val row = makeRow(HEADER_ROW_DP)
        row.addView(makeLabelCell(getString(R.string.free_game_round_label), HEADER_ROW_DP))
        for ((idx, player) in visible) {
            val cell = makePlayerNameCell(player, idx == currentPlayerIndex, columnWeight(idx == currentPlayerIndex))
            cell.setOnClickListener {
                val realIdx = players.indexOfFirst { it.playerId == player.playerId }
                if (realIdx >= 0 && realIdx != currentPlayerIndex) {
                    // Commit any in-progress round before switching player
                    commitHandler.removeCallbacks(commitRunnable)
                    commitCurrentRound()
                    currentPlayerIndex = realIdx
                    buildTable()
                }
            }
            row.addView(cell)
        }
        return row
    }

    // ─── Control row (+1 +2 +5 / -1 -2 -5) ──────────────────────────────────

    private fun buildControlRow(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Current round score display
        val activeRound   = currentRound()
        val activePlayer  = players[currentPlayerIndex]
        val currentScore  = activeRound
            ?.takeIf { it.playerId == activePlayer.playerId }
            ?.score ?: 0

        val scoreRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ACTIVE_ROW_DP))
            background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity,
                if (activeRound != null && activeRound.playerId == activePlayer.playerId)
                    R.color.cell_editable_bg else R.color.score_cell_background))
        }

        val scoreLabelCell = TextView(this).apply {
            text = getString(R.string.free_game_current_round)
            textSize = 12f; setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
            setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_text))
            background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_background))
        }

        val scoreValueCell = TextView(this).apply {
            text = if (activeRound != null && activeRound.playerId == activePlayer.playerId)
                currentScore.toString() else ""
            textSize = 20f; setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.score_cell_text))
            background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity,
                if (activeRound != null && activeRound.playerId == activePlayer.playerId)
                    R.color.cell_editable_filled_bg else R.color.score_cell_background))
        }
        scoreRow.addView(scoreLabelCell)
        scoreRow.addView(scoreValueCell)
        container.addView(scoreRow)

        // Row 1: + buttons
        val plusRow = makeRow(BTN_ROW_DP)
        val labelPlus = TextView(this).apply {
            text = ""; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
            background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_background))
        }
        plusRow.addView(labelPlus)
        for (delta in listOf(1, 2, 5)) {
            plusRow.addView(makeScoreButton("+$delta", isPositive = true) { addScore(delta) })
        }
        container.addView(plusRow)

        // Row 2: - buttons
        val minusRow = makeRow(BTN_ROW_DP)
        val labelMinus = TextView(this).apply {
            text = ""; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
            background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_background))
        }
        minusRow.addView(labelMinus)
        for (delta in listOf(1, 2, 5)) {
            minusRow.addView(makeScoreButton("-$delta", isPositive = false) { addScore(-delta) })
        }
        container.addView(minusRow)

        return container
    }

    // ─── Slot row ─────────────────────────────────────────────────────────────

    private fun buildSlotRow(
        visible: List<Pair<Int, FreeGamePlayerState>>,
        completedByPlayer: Map<Long, List<FreeGameRound>>,
        slotIdx: Int,
        isActiveSlot: Boolean,
        activeRound: FreeGameRound?,
        slotScores: List<Int?>
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val roundNum = slotIdx + 1
        row.addView(makeLabelCellMatchParent(roundNum.toString()))

        for ((colIdx, pair) in visible.withIndex()) {
            val (idx, player) = pair
            val isActive = idx == currentPlayerIndex
            val w        = columnWeight(isActive)

            when {
                isActiveSlot && isActive && activeRound != null && activeRound.playerId == player.playerId -> {
                    // Active in-progress cell for this player
                    val cell = makeScoringCell(activeRound.score.toString(), weight = w)
                    row.addView(cell)
                }
                completedByPlayer[player.playerId]?.getOrNull(slotIdx) != null -> {
                    val roundScore = completedByPlayer[player.playerId]!![slotIdx].score
                    val role       = ScoreColorRole(slotScores[colIdx], slotScores, higherIsBetter = true)
                    val textColor  = when (role) {
                        ScoreColorRole.BEST  -> ContextCompat.getColor(this, R.color.score_text_best)
                        ScoreColorRole.WORST -> ContextCompat.getColor(this, R.color.score_text_worst)
                        else                 -> ContextCompat.getColor(this, R.color.score_cell_text)
                    }
                    val cell = makeCompletedRoundCell(roundScore.toString(), textColor, isActive, w)

                    // Allow undo only on the last completed round of the active player
                    // when there is no active round in progress
                    val isLastCompletedForActive = isActive &&
                            activeRound == null &&
                            completedByPlayer[player.playerId]?.lastIndex == slotIdx
                    if (isLastCompletedForActive) {
                        cell.background = cellDrawable(
                            ContextCompat.getColor(this, R.color.cell_editable_filled_bg))
                        cell.setOnClickListener { undoLastRound() }
                    }
                    row.addView(cell)
                }
                isActiveSlot && activeRound != null && activeRound.playerId != player.playerId -> {
                    // Another player's column during an active round — show empty editable-like cell
                    row.addView(makePlaceholderCell(w))
                }
                else -> row.addView(makePlaceholderCell(w))
            }
        }
        return row
    }

    // ─── Total row ────────────────────────────────────────────────────────────

    private fun buildTotalRow(visible: List<Pair<Int, FreeGamePlayerState>>, allTotals: List<Int>): LinearLayout {
        val row = makeRow(TOTAL_ROW_DP)
        row.addView(makeLabelCell(getString(R.string.free_game_total), TOTAL_ROW_DP))
        for ((colIdx, pair) in visible.withIndex()) {
            val (idx, _) = pair
            val isActive = idx == currentPlayerIndex
            val total    = allTotals[colIdx]
            val role     = ScoreColorRole(total, allTotals, higherIsBetter = true)
            val textColor = when (role) {
                ScoreColorRole.BEST  -> ContextCompat.getColor(this, R.color.score_text_best)
                ScoreColorRole.WORST -> ContextCompat.getColor(this, R.color.score_text_worst)
                else                 -> ContextCompat.getColor(this, R.color.score_calculated_cell_text)
            }
            val cell = makeTotalCell(total.toString(), isActive, columnWeight(isActive))
            cell.setTextColor(textColor)
            row.addView(cell)
        }
        return row
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
    }

    private fun makeLabelCell(text: String, heightDp: Int): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(heightDp))
        background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_text))
    }

    private fun makeLabelCellMatchParent(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
        background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameCell(player: FreeGamePlayerState, isActive: Boolean, weight: Float): TextView =
        TextView(this).apply {
            text = player.playerName; gravity = Gravity.CENTER; textSize = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(HEADER_ROW_DP), weight)
            background = cellDrawable(player.playerColor); setTextColor(Color.WHITE)
            alpha = if (isActive) 1f else 0.7f
        }

    private fun makeScoreButton(label: String, isPositive: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label; gravity = Gravity.CENTER; textSize = 16f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val bgColor = if (isPositive)
                ContextCompat.getColor(this@FreeGameActivity, R.color.score_text_best)
            else
                ContextCompat.getColor(this@FreeGameActivity, R.color.score_text_worst)
            background = cellDrawable(bgColor)
            setTextColor(Color.WHITE)
            setOnClickListener { onClick() }
        }

    private fun makeScoringCell(text: String, weight: Float): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 14f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity, R.color.cell_editable_filled_bg))
        setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.score_cell_text))
        minimumHeight = dpToPx(ROUND_ROW_DP)
    }

    private fun makeCompletedRoundCell(text: String, textColor: Int, isActive: Boolean, weight: Float): TextView =
        TextView(this).apply {
            this.text = text; gravity = Gravity.CENTER; textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity, R.color.score_cell_background))
            setTextColor(textColor); alpha = if (isActive) 1f else 0.65f
            minimumHeight = dpToPx(ROUND_ROW_DP)
        }

    private fun makePlaceholderCell(weight: Float): TextView = TextView(this).apply {
        text = ""; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity, R.color.score_cell_background))
        minimumHeight = dpToPx(ROUND_ROW_DP)
    }

    private fun makeTotalCell(text: String, isActive: Boolean, weight: Float): TextView =
        TextView(this).apply {
            this.text = text; gravity = Gravity.CENTER; textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(TOTAL_ROW_DP), weight)
            background = cellDrawable(ContextCompat.getColor(this@FreeGameActivity, R.color.cell_calculated_bg))
            setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.score_calculated_cell_text))
            alpha = if (isActive) 1f else 0.75f
        }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@FreeGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ─── Options menu ─────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_free_game_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.free_game_quit_game)
                    .setMessage(R.string.free_game_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        commitHandler.removeCallbacks(commitRunnable)
                        finish()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
