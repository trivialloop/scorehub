package com.github.trivialloop.scorehub.games.qwixx

import android.content.Context
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
import android.widget.TextView
import android.widget.Toast
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
import com.github.trivialloop.scorehub.databinding.ActivityQwixxGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class QwixxGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQwixxGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray

    private lateinit var gameState: QwixxGameState

    companion object {
        const val GAME_TYPE = "qwixx"

        // Color row accent colors (ARGB)
        private val ROW_COLOR_RED     = 0xFFE53935.toInt()
        private val ROW_COLOR_YELLOW  = 0xFFFDD835.toInt()
        private val ROW_COLOR_GREEN   = 0xFF43A047.toInt()
        private val ROW_COLOR_BLUE    = 0xFF1E88E5.toInt()
        private val ROW_COLOR_PENALTY = 0xFF9E9E9E.toInt()

        // Grid layout: 4 columns × 3 rows (then last row has lock)
        // Ascending  (RED/YELLOW):  2 6 10 / 3 7 11 / 4 8 12 / 5 9 🔒
        // Descending (GREEN/BLUE): 12 8 4 / 11 7 3 / 10 6 2 /  9 5 🔒
        private val ASCENDING_GRID = listOf(
            listOf(2, 6, 10),
            listOf(3, 7, 11),
            listOf(4, 8, 12),
            listOf(5, 9, null)   // null = lock
        )
        private val DESCENDING_GRID = listOf(
            listOf(12, 8, 4),
            listOf(11, 7, 3),
            listOf(10, 6, 2),
            listOf(9, 5, null)   // null = lock
        )

        // Penalty grid: 2 columns × 2 rows
        private val PENALTY_GRID = listOf(
            listOf(0, 1),
            listOf(2, 3)
        )
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQwixxGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.appBarLayout.setPadding(0, sb.top, 0, 0)
            insets
        }

        database     = AppDatabase.getDatabase(this)
        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()

        val players = playerIds.indices.map { i ->
            QwixxPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }
        gameState = QwixxGameState(players)
        gameState.currentRound = QwixxRound(1, 0)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.qwixx_game)

        buildTable()
    }

    // ─── Table construction ───────────────────────────────────────────────────

    private fun buildTable() {
        val container = binding.scoreTableContainer
        container.removeAllViews()

        val totalPlayers = gameState.players.size
        val activeIdx    = gameState.activePlayerIndex

        // Determine visible players (same logic as Yahtzee for 6+, but max is 5)
        val visibleIndices = when {
            totalPlayers <= 5 -> (0 until totalPlayers).toList()
            else -> {
                val p2 = (activeIdx - 2 + totalPlayers) % totalPlayers
                val p1 = (activeIdx - 1 + totalPlayers) % totalPlayers
                val n1 = (activeIdx + 1) % totalPlayers
                val n2 = (activeIdx + 2) % totalPlayers
                listOf(p2, p1, activeIdx, n1, n2)
            }
        }

        // Build main horizontal scroll container
        val scrollH = android.widget.HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Label column
        mainRow.addView(buildLabelColumn())

        // Player columns
        for (playerIdx in visibleIndices) {
            val isActive = playerIdx == activeIdx
            mainRow.addView(buildPlayerColumn(playerIdx, isActive))
        }

        scrollH.addView(mainRow)
        container.addView(scrollH)
    }

    // ─── Label column ────────────────────────────────────────────────────────

    private fun buildLabelColumn(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(60), LinearLayout.LayoutParams.MATCH_PARENT)
        }

        // Header spacer
        col.addView(makeHeaderSpacerCell(dpToPx(48)))

        // Color rows
        col.addView(makeColorLabelCell(ROW_COLOR_RED,     dpToPx(colorRowHeight(QwixxColor.RED))))
        col.addView(makeColorLabelCell(ROW_COLOR_YELLOW,  dpToPx(colorRowHeight(QwixxColor.YELLOW))))
        col.addView(makeColorLabelCell(ROW_COLOR_GREEN,   dpToPx(colorRowHeight(QwixxColor.GREEN))))
        col.addView(makeColorLabelCell(ROW_COLOR_BLUE,    dpToPx(colorRowHeight(QwixxColor.BLUE))))
        col.addView(makeColorLabelCell(ROW_COLOR_PENALTY, dpToPx(colorRowHeight(null))))

        // Total label
        col.addView(makeTotalLabelCell())

        return col
    }

    private fun makeHeaderSpacerCell(height: Int): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        background = cellDrawable(ContextCompat.getColor(this@QwixxGameActivity, R.color.header_cell_background))
    }

    private fun makeColorLabelCell(color: Int, height: Int): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        background = cellDrawable(color)
    }

    private fun makeTotalLabelCell(): TextView = TextView(this).apply {
        text = getString(R.string.qwixx_total)
        gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44))
        background = cellDrawable(ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
    }

    // ─── Player column ────────────────────────────────────────────────────────

    private fun buildPlayerColumn(playerIdx: Int, isActive: Boolean): LinearLayout {
        val player = gameState.players[playerIdx]
        val weight = columnWeight(isActive)

        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }

        col.addView(makePlayerNameCell(player, isActive, playerIdx))
        col.addView(buildColorRow(playerIdx, QwixxColor.RED))
        col.addView(buildColorRow(playerIdx, QwixxColor.YELLOW))
        col.addView(buildColorRow(playerIdx, QwixxColor.GREEN))
        col.addView(buildColorRow(playerIdx, QwixxColor.BLUE))
        col.addView(buildPenaltyRow(playerIdx))
        col.addView(buildTotalCell(player))

        return col
    }

    private fun makePlayerNameCell(player: QwixxPlayerState, isActive: Boolean, playerIdx: Int): TextView =
        TextView(this).apply {
            text = player.playerName
            gravity = Gravity.CENTER; textSize = if (isActive) 14f else 11f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48))
            background = cellDrawable(player.playerColor); setTextColor(Color.WHITE)
            // Tap to switch active player view (non-active player's turn in OTHERS phase)
            setOnClickListener { onPlayerNameTapped(playerIdx) }
        }

    // ─── Color row ────────────────────────────────────────────────────────────

    private fun buildColorRow(playerIdx: Int, color: QwixxColor): LinearLayout {
        val player   = gameState.players[playerIdx]
        val rowState = player.rowState(color)
        val rowColor = rowAccentColor(color)
        val isGloballyLocked = gameState.lockState.isLocked(color)
        val grid     = if (color == QwixxColor.RED || color == QwixxColor.YELLOW) ASCENDING_GRID else DESCENDING_GRID

        val rowContainer = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(colorRowHeight(color))
            )
            background = cellDrawable(adjustAlpha(rowColor, 0.15f))
        }

        for (gridRow in grid) {
            val lineLayout = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (number in gridRow) {
                if (number == null) {
                    // Lock checkbox
                    lineLayout.addView(buildLockCell(playerIdx, color, rowState, isGloballyLocked, rowColor))
                } else {
                    lineLayout.addView(buildNumberCell(playerIdx, color, number, rowState, isGloballyLocked, rowColor))
                }
            }
            rowContainer.addView(lineLayout)
        }
        return rowContainer
    }

    private fun buildNumberCell(
        playerIdx: Int, color: QwixxColor, number: Int,
        rowState: QwixxRowState, isGloballyLocked: Boolean, accentColor: Int
    ): LinearLayout {
        val isChecked  = rowState.checked.contains(number)
        val canCheck   = !isGloballyLocked && rowState.canCheck(number) && canPlayerCheckColor(playerIdx)
        val isSkipped  = !isChecked && !rowState.canCheck(number) && !isGloballyLocked

        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val bg = when {
                isChecked   -> accentColor
                isSkipped   -> adjustAlpha(accentColor, 0.1f)
                canCheck    -> ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_editable_bg)
                else        -> ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_background)
            }
            background = cellDrawable(bg)
        }

        val cb = CheckBox(this).apply {
            isEnabled       = canCheck && !isChecked
            isClickable     = canCheck && !isChecked
            buttonTintList  = android.content.res.ColorStateList.valueOf(accentColor)
            layoutParams    = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            if (isSkipped) alpha = 0.2f
            if (canCheck && !isChecked) {
                setOnClickListener {
                    onNumberChecked(playerIdx, color, number)
                }
            }
        }

        val tv = TextView(this).apply {
            text     = number.toString()
            textSize = 10f
            gravity  = Gravity.CENTER
            setTextColor(if (isChecked) Color.WHITE else ContextCompat.getColor(
                this@QwixxGameActivity, R.color.score_cell_text))
            if (isSkipped) alpha = 0.2f
        }

        cell.addView(cb)
        cell.addView(tv)
        return cell
    }

    private fun buildLockCell(
        playerIdx: Int, color: QwixxColor,
        rowState: QwixxRowState, isGloballyLocked: Boolean, accentColor: Int
    ): LinearLayout {
        val isChecked = rowState.locked

        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            background = cellDrawable(
                if (isChecked) accentColor
                else ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_never_bg)
            )
        }

        val cb = CheckBox(this).apply {
            this.isChecked = isChecked
            isEnabled      = false
            isClickable    = false
            buttonTintList = android.content.res.ColorStateList.valueOf(accentColor)
            layoutParams   = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        val tv = TextView(this).apply {
            text     = "🔒"
            textSize = 10f
            gravity  = Gravity.CENTER
        }

        cell.addView(cb)
        cell.addView(tv)
        return cell
    }

    // ─── Penalty row ──────────────────────────────────────────────────────────

    private fun buildPenaltyRow(playerIdx: Int): LinearLayout {
        val player  = gameState.players[playerIdx]
        val penaltyCount = player.penalties
        val canAddPenalty = canPlayerAddPenalty(playerIdx)

        val rowContainer = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(colorRowHeight(null))
            )
            background = cellDrawable(adjustAlpha(ROW_COLOR_PENALTY, 0.15f))
        }

        for (gridRow in PENALTY_GRID) {
            val lineLayout = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (penaltyIdx in gridRow) {
                val isChecked = penaltyIdx < penaltyCount
                val canCheck  = canAddPenalty && penaltyIdx == penaltyCount

                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity     = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    background  = cellDrawable(
                        if (isChecked) ROW_COLOR_PENALTY
                        else if (canCheck) ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_editable_bg)
                        else ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_background)
                    )
                }

                val cb = CheckBox(this).apply {
                    this.isChecked = isChecked
                    isEnabled      = canCheck
                    isClickable    = canCheck
                    buttonTintList = android.content.res.ColorStateList.valueOf(ROW_COLOR_PENALTY)
                    layoutParams   = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.CENTER }
                    if (canCheck) setOnClickListener {
                        onPenaltyChecked(playerIdx)
                    }
                }

                val tv = TextView(this).apply {
                    text     = getString(R.string.qwixx_penalty_symbol)
                    textSize = 10f
                    gravity  = Gravity.CENTER
                    setTextColor(if (isChecked) Color.WHITE
                    else ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text))
                }

                cell.addView(cb)
                cell.addView(tv)
                lineLayout.addView(cell)
            }
            rowContainer.addView(lineLayout)
        }
        return rowContainer
    }

    // ─── Total cell ───────────────────────────────────────────────────────────

    private fun buildTotalCell(player: QwixxPlayerState): TextView {
        val total = player.totalScore()
        return TextView(this).apply {
            text = total.toString()
            gravity = Gravity.CENTER; textSize = 15f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44))
            background = cellDrawable(ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg))
            setTextColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
        }
    }

    // ─── Game logic ───────────────────────────────────────────────────────────

    /**
     * Returns true if [playerIdx] is currently allowed to interact with color checkboxes.
     */
    private fun canPlayerCheckColor(playerIdx: Int): Boolean {
        if (gameState.isOver) return false
        val round = gameState.currentRound ?: return false
        return when (round.phase) {
            QwixxRoundPhase.ACTIVE_FIRST  -> playerIdx == round.activePlayerIndex
            QwixxRoundPhase.OTHERS        -> {
                // Any non-active player who hasn't finished yet
                playerIdx != round.activePlayerIndex && !round.othersFinished.contains(playerIdx)
            }
            QwixxRoundPhase.ACTIVE_SECOND -> playerIdx == round.activePlayerIndex
        }
    }

    /**
     * Returns true if [playerIdx] is currently allowed to check a penalty.
     * Only the active player in ACTIVE_SECOND phase (mandatory if no color was checked).
     */
    private fun canPlayerAddPenalty(playerIdx: Int): Boolean {
        if (gameState.isOver) return false
        val round = gameState.currentRound ?: return false
        if (playerIdx != round.activePlayerIndex) return false
        return round.phase == QwixxRoundPhase.ACTIVE_SECOND && !round.activeCheckedFirst
    }

    private fun onPlayerNameTapped(playerIdx: Int) {
        val round = gameState.currentRound ?: return
        if (gameState.isOver) return

        when (round.phase) {
            QwixxRoundPhase.OTHERS -> {
                // Tapping a non-active player name = that player passes
                if (playerIdx != round.activePlayerIndex && !round.othersFinished.contains(playerIdx)) {
                    round.othersFinished.add(playerIdx)
                    advanceOthersOrNextPhase()
                }
            }
            QwixxRoundPhase.ACTIVE_SECOND -> {
                // Tapping active player in ACTIVE_SECOND = they pass (only if they already checked in phase 1)
                if (playerIdx == round.activePlayerIndex && round.activeCheckedFirst) {
                    advanceToNextRound()
                }
            }
            else -> {}
        }
        buildTable()
    }

    private fun onNumberChecked(playerIdx: Int, color: QwixxColor, number: Int) {
        val round = gameState.currentRound ?: return
        val player = gameState.players[playerIdx]

        val success = player.rowState(color).check(number)
        if (!success) {
            buildTable()
            return
        }

        // Sync global lock state
        gameState.syncLocks()

        when (round.phase) {
            QwixxRoundPhase.ACTIVE_FIRST -> {
                round.activeCheckedFirst = true
                round.phase = QwixxRoundPhase.OTHERS
                // If only 1 player, skip OTHERS phase
                if (gameState.players.size == 1) round.phase = QwixxRoundPhase.ACTIVE_SECOND
            }
            QwixxRoundPhase.OTHERS -> {
                round.othersFinished.add(playerIdx)
                advanceOthersOrNextPhase()
            }
            QwixxRoundPhase.ACTIVE_SECOND -> {
                // Active player's second check (or first if they skipped phase 1)
                advanceToNextRound()
                buildTable()
                return
            }
        }

        if (gameState.checkEndCondition()) {
            endGame()
            return
        }
        buildTable()
    }

    private fun onPenaltyChecked(playerIdx: Int) {
        val round = gameState.currentRound ?: return
        if (round.phase != QwixxRoundPhase.ACTIVE_SECOND) return
        if (playerIdx != round.activePlayerIndex) return
        if (round.activeCheckedFirst) return   // penalty only mandatory when no color was checked

        val player = gameState.players[playerIdx]
        player.penalties++

        advanceToNextRound()

        if (gameState.checkEndCondition()) {
            endGame()
            return
        }
        buildTable()
    }

    private fun advanceOthersOrNextPhase() {
        val round = gameState.currentRound ?: return
        val allNonActive = gameState.players.indices.filter { it != round.activePlayerIndex }
        val allFinished  = allNonActive.all { round.othersFinished.contains(it) }
        if (allFinished) {
            round.phase = QwixxRoundPhase.ACTIVE_SECOND
        }
    }

    private fun advanceToNextRound() {
        val round = gameState.currentRound ?: return
        val nextActiveIdx = (round.activePlayerIndex + 1) % gameState.players.size
        gameState.activePlayerIndex = nextActiveIdx
        gameState.currentRound = QwixxRound(round.roundNumber + 1, nextActiveIdx)
    }

    private fun endGame() {
        gameState.isOver = true
        buildTable()
        saveResultsAndShowSummary()
    }

    private fun saveResultsAndShowSummary() {
        val totals   = gameState.players.associate { it to it.totalScore() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners  = totals.filter { it.value == maxScore }.keys
        val isDraw   = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(gameState.players.map { player ->
                GameResult(
                    gameType   = GAME_TYPE,
                    playerId   = player.playerId,
                    playerName = player.playerName,
                    score      = player.totalScore(),
                    isWinner   = !isDraw && player in winners,
                    isDraw     = isDraw && player in winners
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@QwixxGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun colorRowHeight(color: QwixxColor?): Int = when (color) {
        null -> 88   // penalty: 2 rows × ~44dp
        else -> 176  // 4 grid rows × ~44dp
    }

    private fun columnWeight(isActive: Boolean): Float = when {
        gameState.players.size == 1 -> 1f
        gameState.players.size == 2 -> if (isActive) 0.75f else 0.25f
        gameState.players.size <= 5 -> if (isActive) 0.5f  else 0.15f
        else                        -> if (isActive) 0.4f  else 0.15f
    }

    private fun rowAccentColor(color: QwixxColor) = when (color) {
        QwixxColor.RED    -> ROW_COLOR_RED
        QwixxColor.YELLOW -> ROW_COLOR_YELLOW
        QwixxColor.GREEN  -> ROW_COLOR_GREEN
        QwixxColor.BLUE   -> ROW_COLOR_BLUE
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val a = (255 * alpha).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_qwixx_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.qwixx_quit_game)
                    .setMessage(R.string.qwixx_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null).show()
                true
            }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
