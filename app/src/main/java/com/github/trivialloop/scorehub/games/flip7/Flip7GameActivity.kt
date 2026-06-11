package com.github.trivialloop.scorehub.games.flip7

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
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
import com.github.trivialloop.scorehub.databinding.ActivityFlip7GameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class Flip7GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFlip7GameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<Flip7PlayerState>

    private val turns = mutableListOf<Flip7Turn>()
    private var currentPlayerIndex = 0
    private var currentRound = 1
    private var gameOver = false

    companion object {
        const val GAME_TYPE     = "flip7"
        private const val SCORE_LIMIT   = FLIP7_SCORE_LIMIT
        private const val HEADER_ROW_DP = 52
        private const val ROUND_ROW_DP  = 48
        private const val BTN_ROW_DP    = 44
        private const val TOTAL_ROW_DP  = 52
        private const val LABEL_COL_DP  = 65
    }

    private val totalPlayers get() = players.size

    private fun getVisiblePlayers(): List<Pair<Int, Flip7PlayerState>> = when {
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

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlip7GameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)

        val darkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        controller.isAppearanceLightStatusBars = !darkMode

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.appBarLayout.setPadding(
                0,
                systemBars.top,
                0,
                0
            )

            binding.root.setPadding(
                systemBars.left,
                0,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }
        database     = AppDatabase.getDatabase(this)
        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()

        players = playerIds.indices.map { i ->
            Flip7PlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.flip7_game)

        startTurnFor(currentPlayerIndex)
        buildTable()
    }

    // ─── Turn management ──────────────────────────────────────────────────────

    /**
     * The first player of each round rotates: round 1 starts at index 0,
     * round 2 starts at index 1, etc.
     */
    private fun firstPlayerIndexForRound(round: Int): Int =
        (round - 1) % totalPlayers

    private fun startTurnFor(playerIndex: Int) {
        val player = players[playerIndex]
        val roundNumber = turns.count { it.playerId == player.playerId && it.isComplete } + 1
        turns.add(Flip7Turn(roundNumber = currentRound, playerId = player.playerId))
    }

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        val visible = getVisiblePlayers()

        // Group completed turns by player, in round order
        val completedByPlayer: Map<Long, List<Flip7Turn>> = visible.associate { (_, p) ->
            p.playerId to turns.filter { it.playerId == p.playerId && it.isComplete }
                .sortedBy { it.roundNumber }
        }
        val activeTurn: Flip7Turn? =
            turns.lastOrNull { it.playerId == players[currentPlayerIndex].playerId && !it.isComplete }
        val activeSlot   = completedByPlayer[players[currentPlayerIndex].playerId]?.size ?: 0
        val maxCompleted = completedByPlayer.values.maxOfOrNull { it.size } ?: 0
        val totalSlots   = maxOf(maxCompleted, activeSlot + 1)
        val allTotals    = visible.map { (_, p) -> p.getTotal(turns) }

        val headerRow = buildHeaderRow(visible)
        val slotRows  = (0 until totalSlots).map { slotIdx ->
            val isActiveSlot = slotIdx == activeSlot
            val slotScores: List<Int?> = visible.map { (_, p) ->
                completedByPlayer[p.playerId]?.getOrNull(slotIdx)?.score
            }
            buildSlotRow(visible, completedByPlayer, slotIdx, isActiveSlot, activeTurn, slotScores)
        }
        val totalRow = buildTotalRow(visible, allTotals)

        // Fixed header
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(headerRow)

        // Scrollable: slot rows + total
        binding.tableContainer.removeAllViews()
        slotRows.forEach { binding.tableContainer.addView(it) }
        binding.tableContainer.addView(totalRow)

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun buildHeaderRow(visible: List<Pair<Int, Flip7PlayerState>>): LinearLayout {
        val row = makeRow(HEADER_ROW_DP)
        row.addView(makeLabelCell(getString(R.string.flip7_round_label), HEADER_ROW_DP))
        for ((idx, player) in visible) {
            val cell = makePlayerNameCell(player, idx == currentPlayerIndex, columnWeight(idx == currentPlayerIndex))
            // Allow switching active player by tapping their name
            cell.setOnClickListener {
                val realIdx = players.indexOfFirst { it.playerId == player.playerId }
                if (realIdx != -1 && realIdx != currentPlayerIndex) {
                    currentPlayerIndex = realIdx
                    buildTable()
                }
            }
            row.addView(cell)
        }
        return row
    }

    private fun buildSlotRow(
        visible: List<Pair<Int, Flip7PlayerState>>,
        completedByPlayer: Map<Long, List<Flip7Turn>>,
        slotIdx: Int,
        isActiveSlot: Boolean,
        activeTurn: Flip7Turn?,
        slotScores: List<Int?>
    ): LinearLayout {
        // Round label = slot index + 1 (round numbers start at 1)
        val roundNum = slotIdx + 1
        val row = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Tint label cell with the color of the first player for this round
        val labelCell = makeLabelCellMatchParent(roundNum.toString())
        val firstPlayerForRound = players[firstPlayerIndexForRound(roundNum)]
        labelCell.background = cellDrawable(firstPlayerForRound.playerColor)
        labelCell.setTextColor(android.graphics.Color.WHITE)
        row.addView(labelCell)

        for ((colIdx, pair) in visible.withIndex()) {
            val (idx, player) = pair
            val isActive = idx == currentPlayerIndex
            val w        = columnWeight(isActive)

            when {
                isActiveSlot && isActive && activeTurn != null ->
                    row.addView(makeActiveTurnCell(activeTurn, w))

                completedByPlayer[player.playerId]?.getOrNull(slotIdx) != null -> {
                    val turn      = completedByPlayer[player.playerId]!!.getOrNull(slotIdx)!!
                    val role      = ScoreColorRole(slotScores[colIdx], slotScores, higherIsBetter = true)
                    val textColor = when (role) {
                        ScoreColorRole.BEST  -> ContextCompat.getColor(this, R.color.score_text_best)
                        ScoreColorRole.WORST -> ContextCompat.getColor(this, R.color.score_text_worst)
                        else                 -> ContextCompat.getColor(this, R.color.score_cell_text)
                    }
                    row.addView(makeCompletedTurnCell(turn, textColor, isActive, w))
                }

                else -> row.addView(makePlaceholderCell(w))
            }
        }
        return row
    }

    private fun buildTotalRow(visible: List<Pair<Int, Flip7PlayerState>>, allTotals: List<Int>): LinearLayout {
        val row = makeRow(TOTAL_ROW_DP)
        row.addView(makeLabelCell(getString(R.string.flip7_total), TOTAL_ROW_DP))
        for ((colIdx, pair) in visible.withIndex()) {
            val (idx, _) = pair
            val isActive = idx == currentPlayerIndex
            val total    = allTotals[colIdx]
            row.addView(makeTotalCell(total, isActive, columnWeight(isActive)))
        }
        return row
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
    }

    private fun colLp(weight: Float) =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)

    private fun fixedColLp(weight: Float, h: Int) =
        LinearLayout.LayoutParams(0, dpToPx(h), weight)

    private fun makeLabelCell(text: String, heightDp: Int): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(heightDp))
        background = cellDrawable(ContextCompat.getColor(this@Flip7GameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@Flip7GameActivity, R.color.header_cell_text))
    }

    private fun makeLabelCellMatchParent(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
        background = cellDrawable(ContextCompat.getColor(this@Flip7GameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@Flip7GameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameCell(player: Flip7PlayerState, isActive: Boolean, weight: Float): TextView =
        TextView(this).apply {
            text = player.playerName; gravity = Gravity.CENTER; textSize = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = fixedColLp(weight, HEADER_ROW_DP)
            background = cellDrawable(player.playerColor); setTextColor(Color.WHITE)
        }

    private fun makeCompletedTurnCell(turn: Flip7Turn, textColor: Int, isActive: Boolean, weight: Float): TextView =
        TextView(this).apply {
            text = turn.score.toString(); gravity = Gravity.CENTER; textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = colLp(weight)
            background = cellDrawable(ContextCompat.getColor(this@Flip7GameActivity, R.color.score_cell_background))
            setTextColor(textColor); alpha = if (isActive) 1f else 0.65f
            minimumHeight = dpToPx(ROUND_ROW_DP)
        }

    private fun makePlaceholderCell(weight: Float): TextView = TextView(this).apply {
        text = ""; layoutParams = colLp(weight)
        background = cellDrawable(ContextCompat.getColor(this@Flip7GameActivity, R.color.score_cell_background))
        minimumHeight = dpToPx(ROUND_ROW_DP)
    }

    /** Active turn cell: shows "0" and "Flip" buttons */
    private fun makeActiveTurnCell(turn: Flip7Turn, weight: Float): LinearLayout =
        LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            background   = cellDrawable(ContextCompat.getColor(this@Flip7GameActivity, R.color.cell_editable_bg))

            addView(LinearLayout(this@Flip7GameActivity).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(BTN_ROW_DP))
                addView(makeActionButton(getString(R.string.flip7_btn_add),  isZero = false) { showCardSelectionDialog(turn) })
                addView(makeActionButton(getString(R.string.flip7_btn_zero), isZero = true) { performZero(turn) })
            })
        }

    private fun makeActionButton(label: String, isZero: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val bgColor = if (isZero)
                ContextCompat.getColor(this@Flip7GameActivity, R.color.score_text_worst)
            else
                ContextCompat.getColor(this@Flip7GameActivity, R.color.score_text_best)
            background = cellDrawable(bgColor)
            setTextColor(Color.WHITE)
            setOnClickListener { onClick() }
        }

    private fun makeTotalCell(total: Int, isActive: Boolean, weight: Float): TextView =
        TextView(this).apply {
            text = total.toString(); gravity = Gravity.CENTER; textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = fixedColLp(weight, TOTAL_ROW_DP)
            background = cellDrawable(
                ContextCompat.getColor(this@Flip7GameActivity, R.color.cell_calculated_bg))
            setTextColor(ContextCompat.getColor(this@Flip7GameActivity, R.color.score_calculated_cell_text))
            alpha = if (isActive) 1f else 0.75f
        }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@Flip7GameActivity, R.color.cell_border))
    }

    // ─── Card selection dialog ─────────────────────────────────────────────────

    private fun showCardSelectionDialog(turn: Flip7Turn) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_flip7_card_selection, null)

        // Card checkboxes (0–6 left column, 7–12 right column)
        val cardCheckboxes = mutableMapOf<Int, CheckBox>()
        for (v in 0..6) {
            val id = resources.getIdentifier("cbCard$v", "id", packageName)
            (dialogView.findViewById<CheckBox>(id))?.let { cardCheckboxes[v] = it }
        }
        for (v in 7..12) {
            val id = resources.getIdentifier("cbCard$v", "id", packageName)
            (dialogView.findViewById<CheckBox>(id))?.let { cardCheckboxes[v] = it }
        }

        // Bonus + checkboxes
        val bonusPlusCheckboxes = mutableListOf<CheckBox>()
        val bonusPlusIds = listOf(
            R.id.cbBonus2, R.id.cbBonus4, R.id.cbBonus6,
            R.id.cbBonus8a, R.id.cbBonus8b, R.id.cbBonus10
        )
        val bonusPlusValues = listOf(2, 4, 6, 8, 8, 10)
        for (id in bonusPlusIds) {
            (dialogView.findViewById<CheckBox>(id))?.let { bonusPlusCheckboxes.add(it) }
        }

        // x2 bonus
        val cbX2 = dialogView.findViewById<CheckBox>(R.id.cbBonusX2)

        val scorePreview = dialogView.findViewById<TextView>(R.id.textScorePreview)

        /** Recomputes and shows the score preview. */
        fun updatePreview() {
            val selectedCards = cardCheckboxes.filter { it.value.isChecked }.keys.toList()
            val selectedBonusPlus = bonusPlusCheckboxes.zip(bonusPlusValues)
                .filter { it.first.isChecked }.map { it.second }
            val x2 = cbX2?.isChecked ?: false

            var base = selectedCards.sum()
            if (x2) base *= 2
            if (selectedCards.size == 7) base += 15
            base += selectedBonusPlus.sum()

            val cardCount = selectedCards.size
            val suffix = if (cardCount == 7) " (+15 ${getString(R.string.flip7_bonus_flip7)})" else ""
            scorePreview?.text = getString(R.string.flip7_score_preview, base, cardCount, FLIP7_MAX_CARDS, suffix)

            // Disable remaining card checkboxes when 7 are selected
            if (cardCount >= FLIP7_MAX_CARDS) {
                cardCheckboxes.filter { !it.value.isChecked }.forEach { it.value.isEnabled = false }
            } else {
                cardCheckboxes.forEach { it.value.isEnabled = true }
            }
        }

        // Attach listeners
        cardCheckboxes.values.forEach { cb -> cb.setOnCheckedChangeListener { _, _ -> updatePreview() } }
        bonusPlusCheckboxes.forEach { cb -> cb.setOnCheckedChangeListener { _, _ -> updatePreview() } }
        cbX2?.setOnCheckedChangeListener { _, _ -> updatePreview() }
        updatePreview()

        AlertDialog.Builder(this)
            .setTitle("${players[currentPlayerIndex].playerName} — ${getString(R.string.flip7_btn_add)}")
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val selectedCards = cardCheckboxes.filter { it.value.isChecked }.keys.sorted()
                val selectedBonusPlus = bonusPlusCheckboxes.zip(bonusPlusValues)
                    .filter { it.first.isChecked }.map { it.second }
                val x2 = cbX2?.isChecked ?: false

                val completedTurn = turn.copy(
                    selectedCards = selectedCards,
                    bonusPlus = selectedBonusPlus,
                    bonusX2 = x2,
                    isComplete = true
                )
                replaceTurn(turn, completedTurn)
                advanceToNextPlayer()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ─── Game actions ─────────────────────────────────────────────────────────

    private fun performZero(turn: Flip7Turn) {
        val completedTurn = turn.copy(choseZero = true, isComplete = true)
        replaceTurn(turn, completedTurn)
        advanceToNextPlayer()
    }

    private fun replaceTurn(old: Flip7Turn, new: Flip7Turn) {
        val idx = turns.indexOf(old)
        if (idx >= 0) turns[idx] = new else turns.add(new)
    }

    private fun advanceToNextPlayer() {
        // Check if all players have completed the current round
        val playersInRound = players.indices.toList()
        val completedThisRound = playersInRound.all { idx ->
            turns.any { t -> t.playerId == players[idx].playerId && t.roundNumber == currentRound && t.isComplete }
        }

        if (completedThisRound) {
            // Check end-of-game condition
            val maxTotal = players.maxOf { it.getTotal(turns) }
            if (maxTotal >= SCORE_LIMIT) {
                gameOver = true
                buildTable()
                saveResultsAndShowSummary()
                return
            }
            // Start new round
            currentRound++
            currentPlayerIndex = firstPlayerIndexForRound(currentRound)
        } else {
            // Advance to next player in this round (skip players who already played)
            var next = (currentPlayerIndex + 1) % totalPlayers
            var safety = 0
            while (safety < totalPlayers) {
                val alreadyPlayed = turns.any { t ->
                    t.playerId == players[next].playerId &&
                            t.roundNumber == currentRound && t.isComplete
                }
                if (!alreadyPlayed) break
                next = (next + 1) % totalPlayers
                safety++
            }
            currentPlayerIndex = next
        }

        startTurnFor(currentPlayerIndex)
        buildTable()
    }

    // ─── Save results ──────────────────────────────────────────────────────────

    private fun saveResultsAndShowSummary() {
        val totals    = players.associate { it.playerId to it.getTotal(turns) }
        val maxScore  = totals.values.maxOrNull() ?: 0
        val winnerIds = totals.filter { it.value == maxScore }.keys
        val isDraw    = winnerIds.size > 1
        val playedAt  = System.currentTimeMillis()
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType = GAME_TYPE, playerId = player.playerId, playerName = player.playerName,
                    score    = totals[player.playerId] ?: 0,
                    isWinner = !isDraw && player.playerId in winnerIds,
                    isDraw   = isDraw  && player.playerId in winnerIds,
                    playedAt = playedAt
                )
            })
            val sorted = players.sortedByDescending { totals[it.playerId] ?: 0 }
            var rank = 1
            val entries = sorted.mapIndexed { i, p ->
                val s    = totals[p.playerId] ?: 0
                val prev = if (i > 0) totals[sorted[i - 1].playerId] ?: 0 else s
                val r    = if (i > 0 && s == prev) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@Flip7GameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_flip7_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.flip7_quit_game)
                    .setMessage(R.string.flip7_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
