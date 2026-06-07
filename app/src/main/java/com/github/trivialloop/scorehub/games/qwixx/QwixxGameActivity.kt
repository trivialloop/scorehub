package com.github.trivialloop.scorehub.games.qwixx

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.DisplayMetrics
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

    // Computed cell size in px — set once in onCreate based on screen width and player count
    private var cellPx: Int = 0

    companion object {
        const val GAME_TYPE = "qwixx"

        private val ROW_COLOR_RED     = 0xFFE53935.toInt()
        private val ROW_COLOR_YELLOW  = 0xFFFDD835.toInt()
        private val ROW_COLOR_GREEN   = 0xFF43A047.toInt()
        private val ROW_COLOR_BLUE    = 0xFF1E88E5.toInt()
        private val ROW_COLOR_PENALTY = 0xFF757575.toInt()

        // Label column: 1 cell wide (same as other cells)
        // Each player column: 3 cells wide
        // Total cells across = 1 (label) + players * 3
        // => cellPx = screenWidth / (1 + players * 3)

        // Ascending grid (RED / YELLOW): 4 rows × 3 cols, null = lock
        private val ASCENDING_GRID = listOf(
            listOf(2,  6,  10),
            listOf(3,  7,  11),
            listOf(4,  8,  12),
            listOf(5,  9,  null)
        )
        // Descending grid (GREEN / BLUE)
        private val DESCENDING_GRID = listOf(
            listOf(12, 8,  4),
            listOf(11, 7,  3),
            listOf(10, 6,  2),
            listOf(9,  5,  null)
        )
        // Penalty grid: 2 rows × 2 cols
        private val PENALTY_GRID = listOf(
            listOf(0, 1),
            listOf(2, 3)
        )

        // Border between color sections (dp)
        private const val SECTION_BORDER_DP = 2
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

        // Compute cell size after layout is measured
        binding.scoreTableContainer.post {
            val screenWidth  = binding.scoreTableContainer.width
            val totalCells   = 1 + gameState.players.size * 3   // 1 label col + 3 cells per player
            cellPx = screenWidth / totalCells
            buildTable()
        }
    }

    // ─── Table construction ───────────────────────────────────────────────────

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
        for (idx in gameState.players.indices) {
            mainRow.addView(buildPlayerColumn(idx))
        }

        container.addView(mainRow)
    }

    // ─── Label column ─────────────────────────────────────────────────────────

    private fun buildLabelColumn(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(cellPx, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        // Name header spacer
        col.addView(spacerCell(cellPx))

        // Color swatches — each section separated by a thick border
        listOf(
            ROW_COLOR_RED     to 4,
            ROW_COLOR_YELLOW  to 4,
            ROW_COLOR_GREEN   to 4,
            ROW_COLOR_BLUE    to 4,
            ROW_COLOR_PENALTY to 2
        ).forEachIndexed { i, (color, rows) ->
            if (i > 0) col.addView(sectionDivider())
            col.addView(colorSwatchCell(color, rows * cellPx))
        }

        col.addView(sectionDivider())
        col.addView(totalLabelCell())

        return col
    }

    private fun spacerCell(height: Int): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        background = solidDrawable(
            ContextCompat.getColor(this@QwixxGameActivity, R.color.header_cell_background),
            withBorder = false)
    }

    private fun colorSwatchCell(color: Int, height: Int): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        setBackgroundColor(color)
    }

    private fun totalLabelCell(): TextView = TextView(this).apply {
        text = getString(R.string.qwixx_total)
        gravity = Gravity.CENTER; textSize = 10f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
        background = solidDrawable(
            ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg),
            withBorder = false)
        setTextColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
    }

    private fun sectionDivider(): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(SECTION_BORDER_DP))
        setBackgroundColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_border))
    }

    // ─── Player column ────────────────────────────────────────────────────────

    private fun buildPlayerColumn(playerIdx: Int): LinearLayout {
        val player   = gameState.players[playerIdx]
        val isActive = playerIdx == gameState.activePlayerIndex
        val colWidth = 3 * cellPx

        val col = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        col.addView(buildNameCell(player, isActive, playerIdx))

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
        col.addView(buildPenaltyGrid(playerIdx))
        col.addView(sectionDivider())
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
                LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
            alpha = if (isActive) 1f else 0.75f
            background = solidDrawable(player.playerColor, withBorder = false)
            setTextColor(Color.WHITE)
            setOnClickListener { onPlayerNameTapped(playerIdx) }
        }

    // ─── Color row grid ───────────────────────────────────────────────────────

    /**
     * 4 rows × 3 cols grid. No border between cells of the same row section.
     * Only a thin bottom border on each cell for vertical rhythm.
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
                LinearLayout.LayoutParams.MATCH_PARENT, 4 * cellPx)
        }

        for (gridRow in grid) {
            val rowLayout = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
            }
            for (number in gridRow) {
                if (number == null) {
                    rowLayout.addView(buildLockCell(rowState, accentColor))
                } else {
                    rowLayout.addView(buildNumberCell(
                        playerIdx, color, number, rowState, globallyLocked, accentColor))
                }
            }
            gridLayout.addView(rowLayout)
        }
        return gridLayout
    }

    private fun buildNumberCell(
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

        // A number is unreachable if it appears before the last-checked in the sequence
        val isSkipped = !isChecked && !globallyLocked && !rowState.locked && run {
            val seq = rowState.numbers
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
            textSize = (cellPx / resources.displayMetrics.density * 0.28f).coerceIn(9f, 14f)
            setTypeface(null, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(cellPx, cellPx)
            // No border between siblings in the same row — only a hairline bottom border
            background = bottomBorderDrawable(bgColor)
            setTextColor(when {
                isChecked -> Color.WHITE
                isSkipped -> blendColor(
                    ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text),
                    0.3f)
                else      -> ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text)
            })
            alpha = if (isSkipped) 0.4f else 1f
            if (canCheck) setOnClickListener { onNumberChecked(playerIdx, color, number) }
        }
    }

    private fun buildLockCell(rowState: QwixxRowState, accentColor: Int): TextView =
        TextView(this).apply {
            text = "🔒"
            gravity = Gravity.CENTER
            textSize = (cellPx / resources.displayMetrics.density * 0.28f).coerceIn(9f, 14f)
            layoutParams = LinearLayout.LayoutParams(cellPx, cellPx)
            background = bottomBorderDrawable(
                if (rowState.locked) accentColor
                else ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_never_bg)
            )
            alpha = if (rowState.locked) 1f else 0.35f
        }

    // ─── Penalty grid ─────────────────────────────────────────────────────────

    private fun buildPenaltyGrid(playerIdx: Int): LinearLayout {
        val player       = gameState.players[playerIdx]
        val penaltyCount = player.penalties
        val canAdd       = canPlayerAddPenalty(playerIdx)

        val gridLayout = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2 * cellPx)
        }

        for (gridRow in PENALTY_GRID) {
            val rowLayout = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
            }
            for (penaltyIdx in gridRow) {
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
                    textSize = (cellPx / resources.displayMetrics.density * 0.28f).coerceIn(9f, 14f)
                    setTypeface(null, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(cellPx, cellPx)
                    background = bottomBorderDrawable(bgColor)
                    setTextColor(
                        if (isChecked) Color.WHITE
                        else ContextCompat.getColor(this@QwixxGameActivity, R.color.score_cell_text)
                    )
                    if (canCheck) setOnClickListener { onPenaltyChecked(playerIdx) }
                }
                rowLayout.addView(cell)
            }
            // 3rd filler cell to match 3-col width
            rowLayout.addView(android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(cellPx, cellPx)
                background = bottomBorderDrawable(
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
            gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, cellPx)
            background = solidDrawable(
                ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_calculated_bg),
                withBorder = false)
            setTextColor(ContextCompat.getColor(this@QwixxGameActivity, R.color.score_calculated_cell_text))
        }

    // ─── Turn / interaction logic ─────────────────────────────────────────────

    private fun canPlayerInteract(playerIdx: Int, @Suppress("UNUSED_PARAMETER") color: QwixxColor): Boolean {
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

        // If THIS player just locked their own row (checked the last number),
        // register the global lock so NO ONE can check that color anymore.
        // Other players who had already checked it this turn keep their check.
        // The global lock prevents NEW checks from this point onward.
        // Important: we do NOT propagate the personal "locked" flag to other players here —
        // only the global lock state is set. Other players' locked flag is set only when
        // they themselves check the last number (handled below for OTHERS phase).
        if (player.rowState(color).locked) {
            gameState.lockState.lock(color)
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
        val round       = gameState.currentRound ?: return
        val allNonActive = gameState.players.indices.filter { it != round.activePlayerIndex }
        if (allNonActive.all { round.othersFinished.contains(it) }) {
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
     * Drawable with only a bottom hairline border (1px) — no left/right/top borders.
     * Used for cells within the same color row so siblings appear seamless.
     */
    private fun bottomBorderDrawable(bgColor: Int): android.graphics.drawable.LayerDrawable {
        val bg = GradientDrawable().apply { setColor(bgColor) }
        val borderColor = ContextCompat.getColor(this, R.color.cell_border)
        val border = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(1, borderColor)
        }
        val layer = android.graphics.drawable.LayerDrawable(arrayOf(bg, border))
        // Clip the border so only the bottom 1px is visible
        layer.setLayerInset(1, 0, -dpToPx(2), 0, 0)
        return layer
    }

    private fun solidDrawable(bgColor: Int, withBorder: Boolean = true): GradientDrawable =
        GradientDrawable().apply {
            setColor(bgColor)
            if (withBorder) setStroke(
                1, ContextCompat.getColor(this@QwixxGameActivity, R.color.cell_border))
        }

    private fun blendColor(color: Int, alpha: Float): Int {
        val bg = ContextCompat.getColor(this, R.color.score_cell_background)
        val r  = (Color.red(color)   * alpha + Color.red(bg)   * (1f - alpha)).toInt()
        val g  = (Color.green(color) * alpha + Color.green(bg) * (1f - alpha)).toInt()
        val b  = (Color.blue(color)  * alpha + Color.blue(bg)  * (1f - alpha)).toInt()
        return Color.argb(255, r, g, b)
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
