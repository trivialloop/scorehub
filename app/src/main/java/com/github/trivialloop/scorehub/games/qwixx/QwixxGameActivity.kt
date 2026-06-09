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

    // Cell size in px — computed once from screen dimensions
    private var cellPx: Int = 0

    companion object {
        const val GAME_TYPE = "qwixx"

        private val ROW_COLOR_RED     = 0xFFE53935.toInt()
        private val ROW_COLOR_YELLOW  = 0xFFFDD835.toInt()
        private val ROW_COLOR_GREEN   = 0xFF43A047.toInt()
        private val ROW_COLOR_BLUE    = 0xFF1E88E5.toInt()
        private val ROW_COLOR_PENALTY = 0xFF757575.toInt()

        // Grid layouts — null = lock cell
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

        // Penalty: 1 row × 4 cols (indices 0-3)
        private val PENALTY_ROW = listOf(0, 1, 2, 3)

        // Thick separator between color sections (dp)
        private const val SECTION_BORDER_DP = 2

        // Layout:
        //   label col  = 1 cell wide
        //   player col = 3 cells wide
        //   total cols = 1 + players*3
        //
        // Rows (in cells):
        //   name header  = 1
        //   RED          = 4
        //   YELLOW       = 4
        //   GREEN        = 4
        //   BLUE         = 4
        //   PENALTY      = 1
        //   TOTAL        = 1
        //   total rows   = 19
        //   + 6 dividers (2dp each, negligible)
        //
        // cellPx = min(screenW / totalCols, screenH / 19)
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

        binding.scoreTableContainer.post {
            val w = binding.scoreTableContainer.width
            val h = binding.scoreTableContainer.height
            val totalCols = 1 + gameState.players.size * 3
            val totalRows = 19  // 1 name + 4*4 color + 1 penalty + 1 total
            val cellByW = w / totalCols
            val cellByH = h / totalRows
            cellPx = minOf(cellByW, cellByH)
            buildTable()
        }
    }

    // ─── Table ────────────────────────────────────────────────────────────────

    private fun buildTable() {
        if (cellPx == 0) return
        val container = binding.scoreTableContainer
        container.removeAllViews()

        val mainRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        mainRow.addView(buildLabelColumn())
        for (idx in gameState.players.indices) mainRow.addView(buildPlayerColumn(idx))
        container.addView(mainRow)
    }

    // ─── Label column ─────────────────────────────────────────────────────────

    private fun buildLabelColumn(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(cellPx, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        col.addView(blankCell(cellPx))  // name row
        listOf(
            ROW_COLOR_RED     to 4,
            ROW_COLOR_YELLOW  to 4,
            ROW_COLOR_GREEN   to 4,
            ROW_COLOR_BLUE    to 4,
            ROW_COLOR_PENALTY to 1
        ).forEachIndexed { i, (color, rows) ->
            if (i > 0) col.addView(divider())
            col.addView(colorSwatchCell(color, rows * cellPx))
        }
        col.addView(divider())
        col.addView(totalLabelCell())
        return col
    }

    private fun blankCell(height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        setBackgroundColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.header_cell_background))
    }

    private fun colorSwatchCell(color: Int, height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        setBackgroundColor(color)
    }

    private fun totalLabelCell() = TextView(this).apply {
        text = getString(R.string.qwixx_total)
        gravity = Gravity.CENTER; textSize = labelTextSize(); setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
        setBackgroundColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
    }

    private fun divider() = android.view.View(this).apply {
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
                3 * cellPx, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        col.addView(nameCell(player, isActive, playerIdx))

        listOf(
            Triple(QwixxColor.RED,    ASCENDING_GRID,  ROW_COLOR_RED),
            Triple(QwixxColor.YELLOW, ASCENDING_GRID,  ROW_COLOR_YELLOW),
            Triple(QwixxColor.GREEN,  DESCENDING_GRID, ROW_COLOR_GREEN),
            Triple(QwixxColor.BLUE,   DESCENDING_GRID, ROW_COLOR_BLUE)
        ).forEachIndexed { i, (color, grid, accent) ->
            if (i > 0) col.addView(divider())
            col.addView(buildColorRowGrid(playerIdx, color, grid, accent))
        }

        col.addView(divider())
        col.addView(buildPenaltyRow(playerIdx))
        col.addView(divider())
        col.addView(totalCell(player))
        return col
    }

    private fun nameCell(player: QwixxPlayerState, isActive: Boolean, playerIdx: Int) =
        TextView(this).apply {
            text = player.playerName
            gravity = Gravity.CENTER; textSize = labelTextSize()
            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
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
        // "globallyLocked" means no new checks allowed at all —
        // but during OTHERS phase a player can still check the last number
        // if the global lock was just set this same turn.
        val globallyLocked = isColorLockedForPlayer(playerIdx, color)

        val gridLayout = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 4 * cellPx)
        }

        for (gridRow in grid) {
            val rowLayout = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
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
     * Determines whether a player can still check numbers in [color].
     *
     * A color is locked for a player when:
     *  - the global lock was set in a PREVIOUS turn (i.e. not the current round), OR
     *  - the player has already checked the last number themselves (their row is locked), OR
     *  - the global lock was set this turn AND that player is not currently eligible to act.
     *
     * Key rule: during OTHERS phase, the global lock may have just been set by the active
     * player checking the last number. Other non-active players who still haven't taken their
     * turn CAN still check that last number too (and earn their own lock), as long as they
     * have >= 5 checks in that row.
     */
    private fun isColorLockedForPlayer(playerIdx: Int, color: QwixxColor): Boolean {
        val player = gameState.players[playerIdx]
        // If this player's own row is locked, they can't check anything
        if (player.rowState(color).locked) return true
        // If no global lock, nothing to do
        if (!gameState.lockState.isLocked(color)) return false
        // Global lock exists. Was it set this turn?
        val round = gameState.currentRound ?: return true
        if (!round.colorsLockedThisTurn.contains(color)) return true  // locked in a previous turn
        // It was locked this turn. The player can still check IF they haven't acted yet
        // and it's currently their turn to act.
        return !canPlayerInteract(playerIdx, color)
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
            val seq      = rowState.numbers
            val lastIdx  = rowState.checked
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
            layoutParams = LinearLayout.LayoutParams(cellPx, cellPx)
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
        gravity = Gravity.CENTER; textSize = cellTextSize()
        layoutParams = LinearLayout.LayoutParams(cellPx, cellPx)
        background = bottomBorderOnly(
            if (rowState.locked) accentColor
            else ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_never_bg))
        alpha = if (rowState.locked) 1f else 0.35f
    }

    // ─── Penalty row ─────────────────────────────────────────────────────────

    /**
     * Single row of 4 cells (indices 0-3), each CELL_DP wide.
     * The row is 3 cells wide (same as player column) — 3rd cell is used for the 3rd penalty,
     * 4th penalty goes on top of the 4th cell... but we only have 3 cells width.
     * Solution: 4 penalty cells each at 0.75× width so all 4 fit in 3×cellPx total.
     */
    private fun buildPenaltyRow(playerIdx: Int): LinearLayout {
        val player       = gameState.players[playerIdx]
        val penaltyCount = player.penalties
        val canAdd       = canPlayerAddPenalty(playerIdx)

        val rowLayout = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
        }

        // 4 penalty cells, each 3/4 of cellPx wide so they fill exactly 3*cellPx
        val penaltyW = (3 * cellPx) / 4

        for (penaltyIdx in PENALTY_ROW) {
            val isChecked = penaltyIdx < penaltyCount
            val canCheck  = canAdd && penaltyIdx == penaltyCount
            val bgColor   = when {
                isChecked -> ROW_COLOR_PENALTY
                canCheck  -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                else      -> ContextCompat.getColor(this, R.color.score_cell_background)
            }
            val cell = TextView(this).apply {
                text = "✗"
                gravity = Gravity.CENTER
                textSize = cellTextSize()
                setTypeface(null, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(penaltyW, cellPx)
                background = bottomBorderOnly(bgColor)
                setTextColor(
                    if (isChecked) Color.WHITE
                    else ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text))
                if (canCheck) setOnClickListener { onPenaltyChecked(playerIdx) }
            }
            rowLayout.addView(cell)
        }
        return rowLayout
    }

    // ─── Total cell ───────────────────────────────────────────────────────────

    private fun totalCell(player: QwixxPlayerState) = TextView(this).apply {
        text = player.totalScore().toString()
        gravity = Gravity.CENTER; textSize = cellTextSize() + 1f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
        setBackgroundColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
    }

    // ─── Interaction helpers ──────────────────────────────────────────────────

    private fun canPlayerInteract(playerIdx: Int,
                                  @Suppress("UNUSED_PARAMETER") color: QwixxColor): Boolean {
        if (gameState.isOver) return false
        val round = gameState.currentRound ?: return false
        return when (round.phase) {
            QwixxRoundPhase.ACTIVE_FIRST  -> playerIdx == round.activePlayerIndex
            QwixxRoundPhase.OTHERS        -> playerIdx != round.activePlayerIndex &&
                    !round.othersFinished.contains(playerIdx)
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
            QwixxRoundPhase.ACTIVE_FIRST -> {
                if (playerIdx == round.activePlayerIndex) {
                    round.phase = if (gameState.players.size == 1)
                        QwixxRoundPhase.ACTIVE_SECOND else QwixxRoundPhase.OTHERS
                    buildTable()
                }
            }
            QwixxRoundPhase.OTHERS -> {
                if (playerIdx != round.activePlayerIndex &&
                    !round.othersFinished.contains(playerIdx)) {
                    round.othersFinished.add(playerIdx)
                    advanceOthersOrNextPhase()
                    buildTable()
                }
            }
            QwixxRoundPhase.ACTIVE_SECOND -> {
                if (playerIdx == round.activePlayerIndex && round.activeCheckedFirst) {
                    advanceToNextRound()
                    if (gameState.checkEndCondition()) endGame() else buildTable()
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

        // If this player just locked their own row (checked last number),
        // record the global lock for this turn.
        // Other players who haven't acted yet can still check the last number themselves
        // (isColorLockedForPlayer handles this via colorsLockedThisTurn).
        if (player.rowState(color).locked) {
            gameState.lockState.lock(color)
            round.colorsLockedThisTurn.add(color)
        }

        when (round.phase) {
            QwixxRoundPhase.ACTIVE_FIRST -> {
                round.activeCheckedFirst = true
                round.phase = if (gameState.players.size == 1)
                    QwixxRoundPhase.ACTIVE_SECOND else QwixxRoundPhase.OTHERS
            }
            QwixxRoundPhase.OTHERS -> {
                round.othersFinished.add(playerIdx)
                advanceOthersOrNextPhase()
            }
            QwixxRoundPhase.ACTIVE_SECOND -> {
                advanceToNextRound()
                if (gameState.checkEndCondition()) { endGame(); return }
                buildTable(); return
            }
        }

        if (gameState.checkEndCondition()) { endGame(); return }
        buildTable()
    }

    private fun onPenaltyChecked(playerIdx: Int) {
        if (gameState.isOver) return
        val round = gameState.currentRound ?: return
        if (round.phase != QwixxRoundPhase.ACTIVE_SECOND) return
        if (playerIdx != round.activePlayerIndex) return
        if (round.activeCheckedFirst) return

        gameState.players[playerIdx].penalties++
        advanceToNextRound()
        if (gameState.checkEndCondition()) { endGame(); return }
        buildTable()
    }

    private fun advanceOthersOrNextPhase() {
        val round = gameState.currentRound ?: return
        val nonActive = gameState.players.indices.filter { it != round.activePlayerIndex }
        if (nonActive.all { round.othersFinished.contains(it) }) {
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

    /** Only a bottom hairline — no borders between sibling cells in the same row. */
    private fun bottomBorderOnly(bgColor: Int): LayerDrawable {
        val bg     = GradientDrawable().apply { setColor(bgColor) }
        val border = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(1, ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_border))
        }
        return LayerDrawable(arrayOf(bg, border)).also {
            it.setLayerInset(1, 0, -dpToPx(4), 0, 0)   // clip top/left/right, keep bottom
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
        (cellPx / resources.displayMetrics.density * 0.30f).coerceIn(8f, 14f)

    private fun labelTextSize(): Float =
        (cellPx / resources.displayMetrics.density * 0.28f).coerceIn(7f, 13f)

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
