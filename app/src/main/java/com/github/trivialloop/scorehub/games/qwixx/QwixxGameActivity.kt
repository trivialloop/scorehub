package com.github.trivialloop.scorehub.games.qwixx

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
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

    // Cell dimensions in px — computed from screen size
    private var cellW: Int = 0   // width of one cell  (varies with player count)
    private var cellH: Int = 0   // height of one cell (fills screen vertically)

    companion object {
        const val GAME_TYPE = "qwixx"

        private val ROW_COLOR_RED     = 0xFFE53935.toInt()
        private val ROW_COLOR_YELLOW  = 0xFFFDD835.toInt()
        private val ROW_COLOR_GREEN   = 0xFF43A047.toInt()
        private val ROW_COLOR_BLUE    = 0xFF1E88E5.toInt()
        private val ROW_COLOR_PENALTY = 0xFF757575.toInt()

        private val ASCENDING_GRID = listOf(
            listOf(2,  6,  10),
            listOf(3,  7,  11),
            listOf(4,  8,  12),
            listOf(5,  9,  null)
        )
        private val DESCENDING_GRID = listOf(
            listOf(12, 8,  4),
            listOf(11, 7,  3),
            listOf(10, 6,  2),
            listOf(9,  5,  null)
        )
        private val PENALTY_ROW = listOf(0, 1, 2, 3)

        // Thick border between players (dp)
        private const val PLAYER_BORDER_DP  = 3
        // Thin divider between color sections within a player (dp)
        private const val SECTION_BORDER_DP = 2

        // Grid rows count (used for height calculation):
        // 1 name + 4 color rows × 4 lines + 1 penalty + 1 total = 19
        private const val GRID_ROWS = 19
        // Each player column is 3 cells wide
        private const val CELLS_PER_PLAYER = 3
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

        binding.scoreTableContainer.post { computeCellSize(); buildTable() }
    }

    // ─── Cell size ────────────────────────────────────────────────────────────

    private fun computeCellSize() {
        val w = binding.scoreTableContainer.width
        val h = binding.scoreTableContainer.height

        val nPlayers   = gameState.players.size
        // Total cell-columns = 1 label + nPlayers * CELLS_PER_PLAYER
        val totalCols  = 1 + nPlayers * CELLS_PER_PLAYER
        // Account for thick player borders (between players, and between label and first player)
        val playerBorderTotal = dpToPx(PLAYER_BORDER_DP) * (nPlayers + 1)
        val availW     = w - playerBorderTotal

        // Height: GRID_ROWS rows + 6 section dividers (negligible)
        val sectionDividerTotal = dpToPx(SECTION_BORDER_DP) * 6
        val availH     = h - sectionDividerTotal

        cellW = availW / totalCols
        cellH = availH / GRID_ROWS
    }

    // ─── Table ────────────────────────────────────────────────────────────────

    private fun buildTable() {
        if (cellW == 0 || cellH == 0) return
        val container = binding.scoreTableContainer
        container.removeAllViews()

        val mainRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Thick left border before label column
        mainRow.addView(playerBorderView())
        mainRow.addView(buildLabelColumn())

        for (idx in gameState.players.indices) {
            // Thick border before each player column
            mainRow.addView(playerBorderView())
            mainRow.addView(buildPlayerColumn(idx))
        }

        // Thick right border after last player
        mainRow.addView(playerBorderView())

        container.addView(mainRow)
    }

    private fun playerBorderView() = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            dpToPx(PLAYER_BORDER_DP),
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.header_cell_background))
    }

    // ─── Label column ─────────────────────────────────────────────────────────

    private fun buildLabelColumn(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(cellW, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        col.addView(blankCell())   // name row

        listOf(
            ROW_COLOR_RED     to 4,
            ROW_COLOR_YELLOW  to 4,
            ROW_COLOR_GREEN   to 4,
            ROW_COLOR_BLUE    to 4,
            ROW_COLOR_PENALTY to 1
        ).forEachIndexed { i, (color, rows) ->
            if (i > 0) col.addView(sectionDivider())
            col.addView(colorSwatchCell(color, rows * cellH))
        }

        col.addView(sectionDivider())
        col.addView(totalLabelCell())
        return col
    }

    private fun blankCell() = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellH)
        setBackgroundColor(
            ContextCompat.getColor(this@QwixxGameActivity, R.color.header_cell_background))
    }

    private fun colorSwatchCell(color: Int, height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        setBackgroundColor(color)
    }

    private fun totalLabelCell() = TextView(this).apply {
        text = getString(R.string.qwixx_total)
        gravity = Gravity.CENTER
        textSize = labelTextSize()
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellH)
        setBackgroundColor(
            ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg))
        setTextColor(
            ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
    }

    private fun sectionDivider() = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(SECTION_BORDER_DP))
        setBackgroundColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_border))
    }

    // ─── Player column ────────────────────────────────────────────────────────

    private fun buildPlayerColumn(playerIdx: Int): LinearLayout {
        val player   = gameState.players[playerIdx]
        val isActive = playerIdx == gameState.activePlayerIndex

        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                CELLS_PER_PLAYER * cellW, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        col.addView(nameCell(player, isActive, playerIdx))

        listOf(
            Triple(QwixxColor.RED,    ASCENDING_GRID,  ROW_COLOR_RED),
            Triple(QwixxColor.YELLOW, ASCENDING_GRID,  ROW_COLOR_YELLOW),
            Triple(QwixxColor.GREEN,  DESCENDING_GRID, ROW_COLOR_GREEN),
            Triple(QwixxColor.BLUE,   DESCENDING_GRID, ROW_COLOR_BLUE)
        ).forEachIndexed { i, (color, grid, accent) ->
            if (i > 0) col.addView(sectionDivider())
            col.addView(buildColorRowGrid(playerIdx, color, grid, accent))
        }

        col.addView(sectionDivider())
        col.addView(buildPenaltyRow(playerIdx))
        col.addView(sectionDivider())
        col.addView(totalCell(player))
        return col
    }

    private fun nameCell(player: QwixxPlayerState, isActive: Boolean, playerIdx: Int) =
        TextView(this).apply {
            text = player.playerName
            gravity = Gravity.CENTER
            textSize = labelTextSize()
            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, cellH)
            alpha = if (isActive) 1f else 0.75f
            setBackgroundColor(player.playerColor)
            setTextColor(Color.WHITE)
            setOnClickListener { onPlayerNameTapped(playerIdx) }
        }

    // ─── Color row grid ───────────────────────────────────────────────────────

    private fun buildColorRowGrid(
        playerIdx: Int,
        color: QwixxColor,
        grid: List<List<Int?>>,
        accentColor: Int
    ): LinearLayout {
        val player         = gameState.players[playerIdx]
        val rowState       = player.rowState(color)
        val globallyLocked = isColorLockedForPlayer(playerIdx, color)

        val gridLayout = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 4 * cellH)
        }

        for (gridRow in grid) {
            val rowLayout = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, cellH)
            }
            for (number in gridRow) {
                if (number == null) rowLayout.addView(lockCell(rowState, accentColor))
                else rowLayout.addView(numberCell(
                    playerIdx, color, number, rowState, globallyLocked, accentColor))
            }
            gridLayout.addView(rowLayout)
        }
        return gridLayout
    }

    /**
     * A color is locked for a player when:
     * - their own row is locked (they checked the last number), OR
     * - the global lock was set in a PREVIOUS turn, OR
     * - the global lock was set THIS turn but it's no longer this player's turn to act.
     *
     * During the ALL phase: if a player just locked a color this turn,
     * other players who haven't acted yet can still check the last number.
     */
    private fun isColorLockedForPlayer(playerIdx: Int, color: QwixxColor): Boolean {
        val player = gameState.players[playerIdx]
        if (player.rowState(color).locked) return true
        if (!gameState.lockState.isLocked(color)) return false
        val round = gameState.currentRound ?: return true
        // If locked this turn, allow the player to still act if it's their turn
        if (round.colorsLockedThisTurn.contains(color)) {
            return !canPlayerInteract(playerIdx, color)
        }
        return true  // locked in a previous turn
    }

    private fun numberCell(
        playerIdx: Int,
        color: QwixxColor,
        number: Int,
        rowState: QwixxRowState,
        globallyLocked: Boolean,
        accentColor: Int
    ): TextView {
        val isChecked = rowState.checked.contains(number)
        val canCheck  = !globallyLocked && rowState.canCheck(number) &&
                canPlayerInteract(playerIdx, color)
        val isSkipped = !isChecked && !globallyLocked && !rowState.locked && run {
            val seq     = rowState.numbers
            val lastIdx = rowState.checked
                .mapNotNull { seq.indexOf(it).takeIf { i -> i >= 0 } }
                .maxOrNull() ?: -1
            val myIdx = seq.indexOf(number)
            myIdx in 0 until lastIdx
        }

        val bgColor = when {
            isChecked -> accentColor
            isSkipped -> blendColor(accentColor, 0.12f)
            canCheck  -> ContextCompat.getColor(this, R.color.cell_editable_bg)
            else      -> ContextCompat.getColor(this, R.color.score_cell_background)
        }

        return TextView(this).apply {
            text = number.toString()
            gravity = Gravity.CENTER
            textSize = cellTextSize()
            setTypeface(null, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(cellW, cellH)
            background = bottomBorderOnly(bgColor)
            setTextColor(when {
                isChecked -> Color.WHITE
                isSkipped -> blendColor(
                    ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text), 0.3f)
                else      -> ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text)
            })
            alpha = if (isSkipped) 0.4f else 1f
            if (canCheck) setOnClickListener { onNumberChecked(playerIdx, color, number) }
        }
    }

    private fun lockCell(rowState: QwixxRowState, accentColor: Int) = TextView(this).apply {
        text = "🔒"
        gravity = Gravity.CENTER
        textSize = cellTextSize()
        layoutParams = LinearLayout.LayoutParams(cellW, cellH)
        background = bottomBorderOnly(
            if (rowState.locked) accentColor
            else ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_never_bg))
        alpha = if (rowState.locked) 1f else 0.35f
    }

    // ─── Penalty row ──────────────────────────────────────────────────────────

    private fun buildPenaltyRow(playerIdx: Int): LinearLayout {
        val player       = gameState.players[playerIdx]
        val penaltyCount = player.penalties
        val canAdd       = canPlayerAddPenalty(playerIdx)

        // 4 cells evenly filling 3*cellW
        val penaltyW = (CELLS_PER_PLAYER * cellW) / 4

        val rowLayout = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, cellH)
        }

        for (penaltyIdx in PENALTY_ROW) {
            val isChecked = penaltyIdx < penaltyCount
            val canCheck  = canAdd && penaltyIdx == penaltyCount
            val bgColor   = when {
                isChecked -> ROW_COLOR_PENALTY
                canCheck  -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                else      -> ContextCompat.getColor(this, R.color.score_cell_background)
            }
            rowLayout.addView(TextView(this).apply {
                text = "✗"
                gravity = Gravity.CENTER
                textSize = cellTextSize()
                setTypeface(null, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(penaltyW, cellH)
                background = bottomBorderOnly(bgColor)
                setTextColor(
                    if (isChecked) Color.WHITE
                    else ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text))
                if (canCheck) setOnClickListener { onPenaltyChecked(playerIdx) }
            })
        }
        return rowLayout
    }

    // ─── Total cell ───────────────────────────────────────────────────────────

    private fun totalCell(player: QwixxPlayerState) = TextView(this).apply {
        text = player.totalScore().toString()
        gravity = Gravity.CENTER
        textSize = cellTextSize() + 1f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, cellH)
        setBackgroundColor(
            ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg))
        setTextColor(
            ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
    }

    // ─── Interaction helpers ──────────────────────────────────────────────────

    private fun canPlayerInteract(playerIdx: Int,
                                  @Suppress("UNUSED_PARAMETER") color: QwixxColor): Boolean {
        if (gameState.isOver) return false
        val round = gameState.currentRound ?: return false
        return when (round.phase) {
            QwixxRoundPhase.ALL           -> !round.playersFinished.contains(playerIdx)
            QwixxRoundPhase.ACTIVE_SECOND -> playerIdx == round.activePlayerIndex
        }
    }

    private fun canPlayerAddPenalty(playerIdx: Int): Boolean {
        if (gameState.isOver) return false
        val round = gameState.currentRound ?: return false
        if (playerIdx != round.activePlayerIndex) return false
        return round.phase == QwixxRoundPhase.ACTIVE_SECOND && !round.activeCheckedFirst
    }

    // ─── Event handlers ───────────────────────────────────────────────────────

    private fun onPlayerNameTapped(playerIdx: Int) {
        if (gameState.isOver) return
        val round = gameState.currentRound ?: return
        when (round.phase) {
            QwixxRoundPhase.ALL -> {
                if (!round.playersFinished.contains(playerIdx)) {
                    round.playersFinished.add(playerIdx)
                    advanceAllOrNextPhase()
                    buildTable()
                }
            }
            QwixxRoundPhase.ACTIVE_SECOND -> {
                if (playerIdx == round.activePlayerIndex && round.activeCheckedFirst) {
                    finishActiveSecondPhase()
                }
            }
        }
    }

    private fun onNumberChecked(playerIdx: Int, color: QwixxColor, number: Int) {
        if (gameState.isOver) return
        val round  = gameState.currentRound ?: return
        val player = gameState.players[playerIdx]

        val success = player.rowState(color).check(number)
        if (!success) { buildTable(); return }

        // If this player just locked their row, set the global lock for this turn.
        // Other players who haven't acted yet can still check the last number.
        if (player.rowState(color).locked) {
            gameState.lockState.lock(color)
            round.colorsLockedThisTurn.add(color)
        }

        when (round.phase) {
            QwixxRoundPhase.ALL -> {
                if (playerIdx == round.activePlayerIndex) {
                    round.activeCheckedFirst = true
                }
                round.playersFinished.add(playerIdx)
                advanceAllOrNextPhase()
                buildTable()
            }
            QwixxRoundPhase.ACTIVE_SECOND -> {
                finishActiveSecondPhase()
            }
        }
    }

    private fun onPenaltyChecked(playerIdx: Int) {
        if (gameState.isOver) return
        val round = gameState.currentRound ?: return
        if (round.phase != QwixxRoundPhase.ACTIVE_SECOND) return
        if (playerIdx != round.activePlayerIndex) return
        if (round.activeCheckedFirst) return

        gameState.players[playerIdx].penalties++
        finishActiveSecondPhase()
    }

    /**
     * Called when the active player finishes their second action (checked a number,
     * checked a penalty, or tapped their name to pass).
     *
     * The end condition is only evaluated HERE — after the active player's full turn
     * is complete. This ensures every player has had a chance to act before the game ends.
     */
    private fun finishActiveSecondPhase() {
        advanceToNextRound()
        if (gameState.checkEndCondition()) {
            endGame()
        } else {
            buildTable()
        }
    }

    private fun advanceAllOrNextPhase() {
        val round = gameState.currentRound ?: return
        if (gameState.players.indices.all { round.playersFinished.contains(it) }) {
            round.phase = QwixxRoundPhase.ACTIVE_SECOND
        }
    }

    private fun advanceToNextRound() {
        val round   = gameState.currentRound ?: return
        val nextIdx = (round.activePlayerIndex + 1) % gameState.players.size
        gameState.activePlayerIndex = nextIdx
        gameState.currentRound = QwixxRound(round.roundNumber + 1, nextIdx)
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
                    gameType   = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.totalScore(),
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

    // ─── Drawing helpers ──────────────────────────────────────────────────────

    /** Bottom hairline only — no left/right/top borders, so siblings look seamless. */
    private fun bottomBorderOnly(bgColor: Int): LayerDrawable {
        val bg     = GradientDrawable().apply { setColor(bgColor) }
        val border = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(1, ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_border))
        }
        return LayerDrawable(arrayOf(bg, border)).also {
            it.setLayerInset(1, 0, -dpToPx(4), 0, 0)
        }
    }

    private fun blendColor(color: Int, alpha: Float): Int {
        val bg = ContextCompat.getColor(this, R.color.score_cell_background)
        val r  = (Color.red(color)   * alpha + Color.red(bg)   * (1 - alpha)).toInt()
        val g  = (Color.green(color) * alpha + Color.green(bg) * (1 - alpha)).toInt()
        val b  = (Color.blue(color)  * alpha + Color.blue(bg)  * (1 - alpha)).toInt()
        return Color.argb(255, r, g, b)
    }

    private fun cellTextSize(): Float =
        (cellW / resources.displayMetrics.density * 0.30f).coerceIn(7f, 14f)

    private fun labelTextSize(): Float =
        (cellW / resources.displayMetrics.density * 0.27f).coerceIn(6f, 12f)

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ─── Menu ─────────────────────────────────────────────────────────────────

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
