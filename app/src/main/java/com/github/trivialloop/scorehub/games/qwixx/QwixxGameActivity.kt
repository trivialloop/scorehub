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

    companion object {
        const val GAME_TYPE = "qwixx"

        private val ROW_COLOR_RED     = 0xFFE53935.toInt()
        private val ROW_COLOR_YELLOW  = 0xFFFDD835.toInt()
        private val ROW_COLOR_GREEN   = 0xFF43A047.toInt()
        private val ROW_COLOR_BLUE    = 0xFF1E88E5.toInt()
        private val ROW_COLOR_PENALTY = 0xFF757575.toInt()

        // Ascending grid layout (RED / YELLOW): 4 rows × 3 cols, null = lock
        private val ASCENDING_GRID = listOf(
            listOf(2, 6, 10),
            listOf(3, 7, 11),
            listOf(4, 8, 12),
            listOf(5, 9, null)
        )
        // Descending grid layout (GREEN / BLUE)
        private val DESCENDING_GRID = listOf(
            listOf(12, 8, 4),
            listOf(11, 7, 3),
            listOf(10, 6, 2),
            listOf(9, 5, null)
        )
        // Penalty grid: 2 rows × 2 cols
        private val PENALTY_GRID = listOf(
            listOf(0, 1),
            listOf(2, 3)
        )

        // Cell size in dp — uniform for every player
        private const val CELL_DP = 44
        // Label column width in dp
        private const val LABEL_COL_DP = 28
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

        // Outer horizontal scroll so all players are always reachable
        val scrollH = android.widget.HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val mainRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Label column (color swatches)
        mainRow.addView(buildLabelColumn())

        // One column per player — all the same fixed width
        for (idx in gameState.players.indices) {
            mainRow.addView(buildPlayerColumn(idx))
        }

        scrollH.addView(mainRow)
        container.addView(scrollH)
    }

    // ─── Label column ─────────────────────────────────────────────────────────

    private fun buildLabelColumn(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(LABEL_COL_DP),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header (player name height)
        col.addView(spacerCell(dpToPx(CELL_DP)))

        // 4 color rows: 4 grid-rows × CELL_DP each
        col.addView(colorSwatchCell(ROW_COLOR_RED,     4 * dpToPx(CELL_DP)))
        col.addView(colorSwatchCell(ROW_COLOR_YELLOW,  4 * dpToPx(CELL_DP)))
        col.addView(colorSwatchCell(ROW_COLOR_GREEN,   4 * dpToPx(CELL_DP)))
        col.addView(colorSwatchCell(ROW_COLOR_BLUE,    4 * dpToPx(CELL_DP)))

        // Penalty row: 2 grid-rows × CELL_DP
        col.addView(colorSwatchCell(ROW_COLOR_PENALTY, 2 * dpToPx(CELL_DP)))

        // Total row
        col.addView(totalLabelCell())

        return col
    }

    private fun spacerCell(height: Int): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        background = solidDrawable(
            ContextCompat.getColor(this@QwixxGameActivity, R.color.header_cell_background))
    }

    private fun colorSwatchCell(color: Int, height: Int): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        background = solidDrawable(color)
    }

    private fun totalLabelCell(): TextView = TextView(this).apply {
        text = getString(R.string.qwixx_total)
        gravity = Gravity.CENTER; textSize = 10f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(CELL_DP))
        background = solidDrawable(
            ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
    }

    // ─── Player column ────────────────────────────────────────────────────────

    private fun buildPlayerColumn(playerIdx: Int): LinearLayout {
        val player   = gameState.players[playerIdx]
        val isActive = playerIdx == gameState.activePlayerIndex

        // Fixed width: 3 cells wide
        val colWidth = 3 * dpToPx(CELL_DP)

        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        col.addView(buildNameCell(player, isActive, playerIdx))
        col.addView(buildColorRowGrid(playerIdx, QwixxColor.RED,     ASCENDING_GRID,  ROW_COLOR_RED))
        col.addView(buildColorRowGrid(playerIdx, QwixxColor.YELLOW,  ASCENDING_GRID,  ROW_COLOR_YELLOW))
        col.addView(buildColorRowGrid(playerIdx, QwixxColor.GREEN,   DESCENDING_GRID, ROW_COLOR_GREEN))
        col.addView(buildColorRowGrid(playerIdx, QwixxColor.BLUE,    DESCENDING_GRID, ROW_COLOR_BLUE))
        col.addView(buildPenaltyGrid(playerIdx))
        col.addView(buildTotalCell(player))

        return col
    }

    private fun buildNameCell(player: QwixxPlayerState, isActive: Boolean, playerIdx: Int): TextView =
        TextView(this).apply {
            text = player.playerName
            gravity = Gravity.CENTER
            textSize = 12f
            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(CELL_DP))
            // Active player: full opacity; others: slightly dimmed
            alpha = if (isActive) 1f else 0.7f
            background = solidDrawable(player.playerColor)
            setTextColor(Color.WHITE)
            // Tap = pass (for non-active in OTHERS phase, or active passing in ACTIVE_SECOND)
            setOnClickListener { onPlayerNameTapped(playerIdx) }
        }

    // ─── Color grid ───────────────────────────────────────────────────────────

    /**
     * Builds a 4-row × 3-col grid of number cells for one color row.
     * No visible separators between cells — each cell is CELL_DP × CELL_DP.
     */
    private fun buildColorRowGrid(
        playerIdx: Int,
        color: QwixxColor,
        grid: List<List<Int?>>,
        accentColor: Int
    ): LinearLayout {
        val player         = gameState.players[playerIdx]
        val rowState       = player.rowState(color)
        val globallyLocked = gameState.lockState.isLocked(color)

        val gridLayout = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                4 * dpToPx(CELL_DP)
            )
        }

        for (gridRow in grid) {
            val rowLayout = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(CELL_DP))
            }
            for (number in gridRow) {
                if (number == null) {
                    rowLayout.addView(buildLockCell(rowState, accentColor))
                } else {
                    rowLayout.addView(buildNumberCell(playerIdx, color, number, rowState, globallyLocked, accentColor))
                }
            }
            gridLayout.addView(rowLayout)
        }
        return gridLayout
    }

    /**
     * A single number cell — CELL_DP × CELL_DP square.
     *
     * Visual states:
     *  - checked   : accent color background, white text, ✓ checkmark
     *  - skipped   : very faint background, faded text (number is behind last checked)
     *  - canCheck  : light yellow (cell_editable_bg) background, normal text — tappable
     *  - default   : white background, normal text — not tappable
     */
    private fun buildNumberCell(
        playerIdx: Int,
        color: QwixxColor,
        number: Int,
        rowState: QwixxRowState,
        globallyLocked: Boolean,
        accentColor: Int
    ): TextView {
        val isChecked  = rowState.checked.contains(number)
        val canCheck   = !globallyLocked && rowState.canCheck(number) && canPlayerInteract(playerIdx, color)
        val isSkipped  = !isChecked && !globallyLocked && !rowState.locked && run {
            // A number is "skipped" (no longer reachable) if it comes before the last checked in sequence
            val seq = rowState.numbers
            val lastCheckedIdx = rowState.checked.mapNotNull { seq.indexOf(it).takeIf { i -> i >= 0 } }.maxOrNull() ?: -1
            val myIdx = seq.indexOf(number)
            myIdx in 0 until lastCheckedIdx
        }

        val bgColor = when {
            isChecked  -> accentColor
            isSkipped  -> blendColor(accentColor, 0.08f)
            canCheck   -> ContextCompat.getColor(this, R.color.cell_editable_bg)
            else       -> ContextCompat.getColor(this, R.color.score_cell_background)
        }

        return TextView(this).apply {
            // Show ✓ when checked, number otherwise
            text = if (isChecked) "✓\n$number" else number.toString()
            gravity = Gravity.CENTER
            textSize = if (isChecked) 11f else 13f
            setTypeface(null, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(dpToPx(CELL_DP), dpToPx(CELL_DP))
            background = solidDrawable(bgColor)
            setTextColor(when {
                isChecked -> Color.WHITE
                isSkipped -> blendColor(
                    ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text), 0.25f)
                else      -> ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text)
            })
            if (canCheck) {
                setOnClickListener { onNumberChecked(playerIdx, color, number) }
            }
        }
    }

    /**
     * Lock cell — shown as 🔒 with accent background when locked, grey otherwise.
     */
    private fun buildLockCell(rowState: QwixxRowState, accentColor: Int): TextView =
        TextView(this).apply {
            text = "🔒"
            gravity = Gravity.CENTER; textSize = 16f
            layoutParams = LinearLayout.LayoutParams(dpToPx(CELL_DP), dpToPx(CELL_DP))
            background = solidDrawable(
                if (rowState.locked) accentColor
                else ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_never_bg)
            )
            alpha = if (rowState.locked) 1f else 0.4f
        }

    // ─── Penalty grid ─────────────────────────────────────────────────────────

    private fun buildPenaltyGrid(playerIdx: Int): LinearLayout {
        val player        = gameState.players[playerIdx]
        val penaltyCount  = player.penalties
        val canAddPenalty = canPlayerAddPenalty(playerIdx)

        val gridLayout = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2 * dpToPx(CELL_DP)
            )
        }

        for (gridRow in PENALTY_GRID) {
            val rowLayout = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(CELL_DP))
            }
            for (penaltyIdx in gridRow) {
                val isChecked = penaltyIdx < penaltyCount
                val canCheck  = canAddPenalty && penaltyIdx == penaltyCount
                val bgColor = when {
                    isChecked -> ROW_COLOR_PENALTY
                    canCheck  -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                    else      -> ContextCompat.getColor(this, R.color.score_cell_background)
                }

                val cell = TextView(this).apply {
                    text = if (isChecked) "✓\n✗" else "✗"
                    gravity = Gravity.CENTER
                    textSize = if (isChecked) 11f else 14f
                    setTypeface(null, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(CELL_DP), dpToPx(CELL_DP))
                    background = solidDrawable(bgColor)
                    setTextColor(
                        if (isChecked) Color.WHITE
                        else ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text)
                    )
                    if (canCheck) setOnClickListener { onPenaltyChecked(playerIdx) }
                }
                rowLayout.addView(cell)
            }
            // Filler cell (3rd column placeholder so width matches color rows)
            rowLayout.addView(android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(CELL_DP), dpToPx(CELL_DP))
                background = solidDrawable(
                    ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_background))
            })
            gridLayout.addView(rowLayout)
        }
        return gridLayout
    }

    // ─── Total cell ───────────────────────────────────────────────────────────

    private fun buildTotalCell(player: QwixxPlayerState): TextView =
        TextView(this).apply {
            text = player.totalScore().toString()
            gravity = Gravity.CENTER; textSize = 14f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(CELL_DP))
            background = solidDrawable(
                ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg))
            setTextColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
        }

    // ─── Turn / interaction logic ─────────────────────────────────────────────

    /**
     * Returns true if [playerIdx] is allowed to tap a color number cell right now.
     *
     * ACTIVE_FIRST  → only the active player
     * OTHERS        → any non-active player who hasn't finished this phase yet
     * ACTIVE_SECOND → only the active player
     *
     * Special: during OTHERS phase the active player can also tap 12/2 (last number)
     * if they already checked it in ACTIVE_FIRST — that is handled inside onNumberChecked.
     * Here we just block color interactions for the active player during OTHERS.
     */
    private fun canPlayerInteract(playerIdx: Int, color: QwixxColor): Boolean {
        if (gameState.isOver) return false
        val round = gameState.currentRound ?: return false
        return when (round.phase) {
            QwixxRoundPhase.ACTIVE_FIRST  -> playerIdx == round.activePlayerIndex
            QwixxRoundPhase.OTHERS        -> playerIdx != round.activePlayerIndex &&
                    !round.othersFinished.contains(playerIdx)
            QwixxRoundPhase.ACTIVE_SECOND -> playerIdx == round.activePlayerIndex
        }
    }

    /**
     * Penalty can only be added by the active player in ACTIVE_SECOND phase
     * when they did NOT check any color in ACTIVE_FIRST.
     */
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
                // Active player taps their own name = pass (skip their first check)
                if (playerIdx == round.activePlayerIndex) {
                    // Move directly to OTHERS (or ACTIVE_SECOND if solo)
                    round.phase = if (gameState.players.size == 1)
                        QwixxRoundPhase.ACTIVE_SECOND
                    else
                        QwixxRoundPhase.OTHERS
                    buildTable()
                }
            }
            QwixxRoundPhase.OTHERS -> {
                // Non-active player taps their name = pass
                if (playerIdx != round.activePlayerIndex &&
                    !round.othersFinished.contains(playerIdx)) {
                    round.othersFinished.add(playerIdx)
                    advanceOthersOrNextPhase()
                    buildTable()
                }
            }
            QwixxRoundPhase.ACTIVE_SECOND -> {
                // Active player taps their name = pass (only allowed if they already checked in phase 1)
                if (playerIdx == round.activePlayerIndex && round.activeCheckedFirst) {
                    advanceToNextRound()
                    if (!gameState.checkEndCondition()) buildTable()
                    else endGame()
                }
            }
        }
    }

    private fun onNumberChecked(playerIdx: Int, color: QwixxColor, number: Int) {
        if (gameState.isOver) return
        val round  = gameState.currentRound ?: return
        val player = gameState.players[playerIdx]

        // Attempt to check the number in this player's row
        val success = player.rowState(color).check(number)
        if (!success) { buildTable(); return }

        // If this player locked their row, register the global lock
        if (player.rowState(color).locked) {
            gameState.lockState.lock(color)
            // Propagate lock to all other players' rows
            for (p in gameState.players) {
                if (p.playerId != player.playerId) {
                    p.rowState(color).locked = true
                }
            }
        }

        when (round.phase) {
            QwixxRoundPhase.ACTIVE_FIRST -> {
                round.activeCheckedFirst = true
                round.phase = if (gameState.players.size == 1)
                    QwixxRoundPhase.ACTIVE_SECOND
                else
                    QwixxRoundPhase.OTHERS
            }
            QwixxRoundPhase.OTHERS -> {
                round.othersFinished.add(playerIdx)
                advanceOthersOrNextPhase()
            }
            QwixxRoundPhase.ACTIVE_SECOND -> {
                // Active player's second check — end of turn
                advanceToNextRound()
                if (gameState.checkEndCondition()) { endGame(); return }
                buildTable()
                return
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
        val allNonActive = gameState.players.indices.filter { it != round.activePlayerIndex }
        if (allNonActive.all { round.othersFinished.contains(it) }) {
            round.phase = QwixxRoundPhase.ACTIVE_SECOND
        }
    }

    private fun advanceToNextRound() {
        val round = gameState.currentRound ?: return
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

    // ─── Drawing helpers ──────────────────────────────────────────────────────

    /**
     * Blends [color] toward white (or black) at the given [alpha].
     * Used to produce faded / ghost variants of accent colors.
     */
    private fun blendColor(color: Int, alpha: Float): Int {
        val bg = ContextCompat.getColor(this, R.color.score_cell_background)
        val r  = (Color.red(color)   * alpha + Color.red(bg)   * (1f - alpha)).toInt()
        val g  = (Color.green(color) * alpha + Color.green(bg) * (1f - alpha)).toInt()
        val b  = (Color.blue(color)  * alpha + Color.blue(bg)  * (1f - alpha)).toInt()
        return Color.argb(255, r, g, b)
    }

    private fun solidDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_border))
    }

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
