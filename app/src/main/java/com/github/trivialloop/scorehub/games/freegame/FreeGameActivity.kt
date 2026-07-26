package com.github.trivialloop.scorehub.games.freegame

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

    // ─── State ────────────────────────────────────────────────────────────────
    // Committed (closed) rounds in global order.
    private val rounds = mutableListOf<FreeGameRound>()

    // Pending round: score accumulating for activePlayerId, not yet committed.
    private var pendingScore: Int  = 0
    private var activePlayerId: Long = -1L
    private val isRoundActive get() = activePlayerId != -1L

    // Global round counter: incremented each time a round is committed.
    // The pending round will occupy slot (rounds.size + 1) when committed.
    private val nextRoundNumber get() = rounds.size + 1

    // 5-second commit timer
    private val commitHandler = Handler(Looper.getMainLooper())
    private val commitRunnable = Runnable { commitPending() }

    companion object {
        const val GAME_TYPE         = "freegame"
        private const val COMMIT_DELAY_MS = 3000L   // 3 seconds
        private const val HEADER_ROW_DP   = 52
        private const val BTN_ROW_DP      = 52
        private const val ROUND_ROW_DP    = 40
        private const val TOTAL_ROW_DP    = 52
        private const val LABEL_COL_DP    = 65
    }

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
        supportActionBar?.title = getString(R.string.freegame_game)

        buildTable()
    }

    override fun onDestroy() {
        commitHandler.removeCallbacks(commitRunnable)
        super.onDestroy()
    }

    // ─── Score / timer logic ──────────────────────────────────────────────────

    /**
     * Called when a ±button is pressed for [player].
     *
     * - Same player as active → accumulate, reset timer.
     * - Different player (or no active round) → commit current pending first,
     *   then start a new round for this player.
     */
    private fun addScore(player: FreeGamePlayerState, delta: Int) {
        if (isRoundActive && activePlayerId != player.playerId) {
            commitPending()   // closes the previous player's round
        }

        activePlayerId = player.playerId
        pendingScore  += delta

        commitHandler.removeCallbacks(commitRunnable)
        commitHandler.postDelayed(commitRunnable, COMMIT_DELAY_MS)

        buildTable()
    }

    /**
     * Close the pending round and push it into [rounds].
     * The round gets the roundNumber it was assigned when it started
     * (i.e. rounds.size + 1 at commit time, which equals nextRoundNumber
     * because nothing else was added in between).
     */
    private fun commitPending() {
        if (!isRoundActive) return
        commitHandler.removeCallbacks(commitRunnable)
        rounds.add(
            FreeGameRound(
                roundNumber = rounds.size + 1,
                playerId    = activePlayerId,
                score       = pendingScore
            )
        )
        pendingScore   = 0
        activePlayerId = -1L
        buildTable()
    }

    /**
     * Undo the last committed round: removes it from [rounds] and restores
     * it as the pending round with a fresh 5-second window.
     * Only allowed when no round is currently active.
     */
    private fun undoLast() {
        if (isRoundActive) return
        val last = rounds.removeLastOrNull() ?: return
        activePlayerId = last.playerId
        pendingScore   = last.score
        commitHandler.removeCallbacks(commitRunnable)
        commitHandler.postDelayed(commitRunnable, COMMIT_DELAY_MS)
        buildTable()
    }

    // ─── Table construction ───────────────────────────────────────────────────

    private fun buildTable() {
        // Map: roundNumber → FreeGameRound (only committed rounds)
        val roundByNumber: Map<Int, FreeGameRound> = rounds.associateBy { it.roundNumber }

        // The pending round will occupy slot (rounds.size + 1) visually
        val pendingSlot   = if (isRoundActive) rounds.size + 1 else -1

        // Total visual rows = committed count + 1 if a round is pending
        val totalSlots    = if (isRoundActive) rounds.size + 1 else rounds.size

        // Totals include the pending score for the active player
        val allTotals = players.map { p ->
            p.getTotal(rounds) +
                    if (isRoundActive && p.playerId == activePlayerId) pendingScore else 0
        }

        // Fixed header
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(buildHeaderRow())
        binding.headerContainer.addView(buildAllButtonsRow())

        // Scrollable content
        binding.tableContainer.removeAllViews()
        for (slotIdx in 1..totalSlots) {
            binding.tableContainer.addView(
                buildDataRow(roundByNumber, slotIdx, pendingSlot)
            )
        }
        binding.tableContainer.addView(buildTotalRow(allTotals))

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ─── Header: player names ─────────────────────────────────────────────────

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow(HEADER_ROW_DP)
        row.addView(makeLabelCell(getString(R.string.freegame_round_label), HEADER_ROW_DP))
        for (player in players) {
            val isActive = isRoundActive && player.playerId == activePlayerId
            row.addView(makePlayerNameCell(player, isActive))
        }
        return row
    }

    // ─── Header: all players' ± buttons (2 columns: -v | +v) ─────────────────

    private fun buildAllButtonsRow(): LinearLayout {
        val outerRow = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Empty label cell spanning all three button rows
        outerRow.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(BTN_ROW_DP * 3))
            background = cellDrawable(
                ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_background))
        })

        // Per-player: three rows of [−v | +v]
        for (player in players) {
            val group = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            for (v in listOf(1, 2, 5)) {
                val btnRow = LinearLayout(this).apply {
                    orientation  = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(BTN_ROW_DP))
                }
                btnRow.addView(makeScoreButton("-$v", isPositive = false) { addScore(player, -v) })
                btnRow.addView(makeScoreButton("+$v", isPositive = true)  { addScore(player, v)  })
                group.addView(btnRow)
            }
            outerRow.addView(group)
        }
        return outerRow
    }

    // ─── Data row ─────────────────────────────────────────────────────────────

    /**
     * Build one display row for [slotIdx] (1-based global round number).
     *
     * For each player we look up whether there is a committed round with that
     * roundNumber, or whether this slot is the pending slot for the active player.
     */
    private fun buildDataRow(
        roundByNumber: Map<Int, FreeGameRound>,
        slotIdx: Int,
        pendingSlot: Int
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ROUND_ROW_DP))
        }

        row.addView(makeLabelCellFixed(slotIdx.toString()))

        for (player in players) {
            when {
                // This slot is the pending slot AND it belongs to this player
                slotIdx == pendingSlot && player.playerId == activePlayerId -> {
                    row.addView(makePendingCell(pendingScore.toString()))
                }

                // There is a committed round with this global number for this player
                roundByNumber[slotIdx]?.playerId == player.playerId -> {
                    val entry      = roundByNumber[slotIdx]!!
                    val isUndoable = !isRoundActive && entry == rounds.lastOrNull()
                    val cell       = makeCommittedCell(entry.score.toString(), isUndoable)
                    if (isUndoable) cell.setOnClickListener { undoLast() }
                    row.addView(cell)
                }

                // Empty
                else -> row.addView(makePlaceholderCell())
            }
        }
        return row
    }

    // ─── Total row ────────────────────────────────────────────────────────────

    private fun buildTotalRow(allTotals: List<Int>): LinearLayout {
        val row = makeRow(TOTAL_ROW_DP)
        row.addView(makeLabelCell(getString(R.string.freegame_total), TOTAL_ROW_DP))
        for ((_, total) in allTotals.withIndex()) {
            val role = ScoreColorRole(total, allTotals, higherIsBetter = true)
            val textColor = when (role) {
                ScoreColorRole.BEST  -> ContextCompat.getColor(this, R.color.score_text_best)
                ScoreColorRole.WORST -> ContextCompat.getColor(this, R.color.score_text_worst)
                else                 -> ContextCompat.getColor(this, R.color.score_calculated_cell_text)
            }
            val cell = makeTotalCell(total.toString())
            cell.setTextColor(textColor)
            row.addView(cell)
        }
        return row
    }

    // ─── Cell / row builders ──────────────────────────────────────────────────

    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
    }

    private fun makeLabelCell(text: String, heightDp: Int): TextView = TextView(this).apply {
        this.text = text
        gravity   = Gravity.CENTER
        textSize  = 11f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(heightDp))
        background = cellDrawable(
            ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_text))
    }

    private fun makeLabelCellFixed(text: String): TextView = TextView(this).apply {
        this.text = text
        gravity   = Gravity.CENTER
        textSize  = 11f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(ROUND_ROW_DP))
        background = cellDrawable(
            ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameCell(player: FreeGamePlayerState, isActive: Boolean): TextView =
        TextView(this).apply {
            text      = player.playerName
            gravity   = Gravity.CENTER
            textSize  = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines  = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(HEADER_ROW_DP), 1f)
            background = cellDrawable(player.playerColor)
            setTextColor(Color.WHITE)
            alpha = if (isActive) 1f else if (this@FreeGameActivity.isRoundActive) 0.55f else 1f
        }

    private fun makeScoreButton(label: String, isPositive: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text     = label
            gravity  = Gravity.CENTER
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val bgColor = if (isPositive)
                ContextCompat.getColor(this@FreeGameActivity, R.color.score_text_best)
            else
                ContextCompat.getColor(this@FreeGameActivity, R.color.score_text_worst)
            background = cellDrawable(bgColor)
            setTextColor(Color.WHITE)
            setOnClickListener { onClick() }
        }

    private fun makePendingCell(text: String): TextView = TextView(this).apply {
        this.text = text
        gravity   = Gravity.CENTER
        textSize  = 16f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, dpToPx(ROUND_ROW_DP), 1f)
        background = cellDrawable(
            ContextCompat.getColor(this@FreeGameActivity, R.color.cell_editable_filled_bg))
        setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.score_cell_text))
    }

    private fun makeCommittedCell(text: String, isUndoable: Boolean): TextView = TextView(this).apply {
        this.text = text
        gravity   = Gravity.CENTER
        textSize  = 14f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, dpToPx(ROUND_ROW_DP), 1f)
        background = cellDrawable(
            ContextCompat.getColor(this@FreeGameActivity,
                if (isUndoable) R.color.cell_editable_filled_bg
                else R.color.score_cell_background))
        setTextColor(ContextCompat.getColor(this@FreeGameActivity, R.color.score_cell_text))
    }

    private fun makePlaceholderCell(): TextView = TextView(this).apply {
        text = ""
        layoutParams = LinearLayout.LayoutParams(0, dpToPx(ROUND_ROW_DP), 1f)
        background = cellDrawable(
            ContextCompat.getColor(this@FreeGameActivity, R.color.score_cell_background))
    }

    private fun makeTotalCell(text: String): TextView = TextView(this).apply {
        this.text = text
        gravity   = Gravity.CENTER
        textSize  = 16f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, dpToPx(TOTAL_ROW_DP), 1f)
        background = cellDrawable(
            ContextCompat.getColor(this@FreeGameActivity, R.color.cell_calculated_bg))
        setTextColor(
            ContextCompat.getColor(this@FreeGameActivity, R.color.score_calculated_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@FreeGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ─── Menu ─────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_freegame_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.freegame_quit_game)
                    .setMessage(R.string.freegame_quit_game_message)
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
