package com.github.trivialloop.scorehub.games.scrabble

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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityScrabbleGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch
import kotlin.math.abs

class ScrabbleGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScrabbleGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<ScrabblePlayerState>

    private val turns = mutableListOf<ScrabbleTurn>()
    private var currentPlayerIndex = 0
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "scrabble"
        private const val HEADER_ROW_DP = 52
        private const val MIN_CELL_DP   = 48
        private const val ENTRY_ROW_DP  = 36
        private const val SUM_ROW_DP    = 30
        private const val BTN_ROW_DP    = 48
        private const val TOTAL_ROW_DP  = 52
        private const val LABEL_COL_DP  = 65
        private const val MAX_ABS_SCORE = 999
    }

    private fun columnWeight(isActive: Boolean): Float = if (isActive && !gameOver) 1.4f else 1f

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScrabbleGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

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
            ScrabblePlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.scrabble_game)

        binding.btnFinishGame.setOnClickListener { showFinishGameDialog() }

        startTurnFor(currentPlayerIndex)
        buildTable()

        onBackPressedDispatcher.addCallback(this) { showQuitGameDialog() }
    }

    // ─── Turn management ──────────────────────────────────────────────────────

    private fun startTurnFor(playerIndex: Int) {
        val player = players[playerIndex]
        val roundNumber = turns.count { it.playerId == player.playerId } + 1
        turns.add(ScrabbleTurn(roundNumber, player.playerId))
    }

    private fun endTurn() {
        val player = players[currentPlayerIndex]
        player.currentTurn(turns)?.isComplete = true
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
        startTurnFor(currentPlayerIndex)
        buildTable()
    }

    // ─── Table construction ───────────────────────────────────────────────────

    private fun buildTable() {
        val turnsByPlayer = players.map { p -> turns.filter { it.playerId == p.playerId } }
        val totalSlots = turnsByPlayer.maxOfOrNull { it.size } ?: 0
        val totals = players.map { it.getTotal(turns) }

        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(buildHeaderRow())

        binding.tableContainer.removeAllViews()
        for (slot in 0 until totalSlots) {
            binding.tableContainer.addView(buildSlotRow(turnsByPlayer.map { it.getOrNull(slot) }, slot))
        }
        binding.tableContainer.addView(buildTotalRow(totals))

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow(HEADER_ROW_DP)
        row.addView(makeLabelCell("", LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(HEADER_ROW_DP))))
        players.forEachIndexed { idx, p ->
            row.addView(TextView(this).apply {
                text = p.playerName; gravity = Gravity.CENTER; textSize = 13f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(HEADER_ROW_DP), columnWeight(idx == currentPlayerIndex))
                background = cellDrawable(p.playerColor)
                setTextColor(Color.WHITE)
            })
        }
        return row
    }

    private fun buildSlotRow(slotTurns: List<ScrabbleTurn?>, slot: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(makeLabelCell(
            (slot + 1).toString(),
            LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
        ))

        // Best / worst coloring only once every player has finished this round
        val sums: List<Int?> = slotTurns.map { t -> if (t != null && t.isComplete) t.score else null }
        val roundDone = sums.all { it != null }

        slotTurns.forEachIndexed { i, turn ->
            val w = columnWeight(i == currentPlayerIndex)
            val cell = when {
                turn == null -> makePlaceholderCell(w)
                !turn.isComplete && !gameOver -> makeActiveTurnCell(turn, w)
                else -> {
                    val role = if (roundDone) ScoreColorRole(turn.score, sums) else ScoreColorRole.NEUTRAL
                    makeCompletedTurnCell(turn, role, w)
                }
            }
            row.addView(cell)
        }
        return row
    }

    private fun buildTotalRow(totals: List<Int>): LinearLayout {
        val row = makeRow(TOTAL_ROW_DP)
        row.addView(makeLabelCell(
            getString(R.string.scrabble_total),
            LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(TOTAL_ROW_DP)),
            isTotal = true
        ))
        totals.forEachIndexed { idx, total ->
            val role = ScoreColorRole(total, totals)
            row.addView(TextView(this).apply {
                text = total.toString(); gravity = Gravity.CENTER; textSize = 16f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(TOTAL_ROW_DP), columnWeight(idx == currentPlayerIndex))
                background = cellDrawable(ContextCompat.getColor(this@ScrabbleGameActivity, R.color.cell_calculated_bg))
                setTextColor(
                    if (role == ScoreColorRole.NEUTRAL)
                        ContextCompat.getColor(this@ScrabbleGameActivity, R.color.score_calculated_cell_text)
                    else role.toColor(this@ScrabbleGameActivity)
                )
            })
        }
        return row
    }

    // ─── Cells ────────────────────────────────────────────────────────────────

    /** Turn in progress: tappable entries, running sum, then [+] and [✅] buttons. */
    private fun makeActiveTurnCell(turn: ScrabbleTurn, weight: Float): LinearLayout =
        LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            background   = cellDrawable(ContextCompat.getColor(this@ScrabbleGameActivity, R.color.cell_editable_bg))

            turn.entries.forEachIndexed { index, value ->
                addView(makeEntryText(
                    value.toString(), R.color.cell_editable_filled_bg,
                    ContextCompat.getColor(this@ScrabbleGameActivity, R.color.score_cell_text), bold = false
                ) { showScoreDialog(turn, index) })
            }
            if (turn.entries.size > 1) addView(makeSumText(turn.score, null))

            addView(LinearLayout(this@ScrabbleGameActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(BTN_ROW_DP))
                addView(makeActionButton("+", null) { showScoreDialog(turn, null) })
                addView(makeActionButton("✅", R.color.score_text_best) { endTurn() })
            })
        }

    /** Closed turn. Entries stay editable until the game is over. */
    private fun makeCompletedTurnCell(turn: ScrabbleTurn, role: ScoreColorRole, weight: Float): LinearLayout =
        LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            minimumHeight = dpToPx(MIN_CELL_DP)
            background = cellDrawable(ContextCompat.getColor(this@ScrabbleGameActivity, R.color.score_cell_background))
            gravity = Gravity.CENTER_VERTICAL

            val neutral = ContextCompat.getColor(this@ScrabbleGameActivity, R.color.score_cell_text)
            val roleColor = role.toColor(this@ScrabbleGameActivity)
            val colored = role != ScoreColorRole.NEUTRAL

            when (turn.entries.size) {
                0 -> addView(makeEntryText("–", R.color.score_cell_background, neutral, false, MIN_CELL_DP, null))
                1 -> addView(makeEntryText(
                    turn.entries[0].toString(), R.color.score_cell_background,
                    roleColor, colored, MIN_CELL_DP,
                    if (gameOver) null else ({ showScoreDialog(turn, 0) })
                ))
                else -> {
                    turn.entries.forEachIndexed { index, value ->
                        addView(makeEntryText(
                            value.toString(), R.color.score_cell_background, neutral, false, ENTRY_ROW_DP,
                            if (gameOver) null else ({ showScoreDialog(turn, index) })
                        ))
                    }
                    addView(makeSumText(turn.score, if (colored) roleColor else null))
                }
            }
        }

    private fun makePlaceholderCell(weight: Float): TextView = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(ContextCompat.getColor(this@ScrabbleGameActivity, R.color.score_cell_background))
        minimumHeight = dpToPx(MIN_CELL_DP)
    }

    private fun makeEntryText(
        text: String, bgRes: Int, textColor: Int, bold: Boolean,
        heightDp: Int = ENTRY_ROW_DP, onClick: (() -> Unit)?
    ): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 14f
        if (bold) setTypeface(null, Typeface.BOLD)
        setTextColor(textColor)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
        background = cellDrawable(ContextCompat.getColor(this@ScrabbleGameActivity, bgRes))
        if (onClick != null) setOnClickListener { onClick() }
    }

    private fun makeSumText(sum: Int, color: Int?): TextView = TextView(this).apply {
        text = "= $sum"; gravity = Gravity.CENTER; textSize = 12f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(SUM_ROW_DP))
        background = cellDrawable(ContextCompat.getColor(this@ScrabbleGameActivity, R.color.header_cell_background))
        setTextColor(color ?: ContextCompat.getColor(this@ScrabbleGameActivity, R.color.header_cell_text))
    }

    private fun makeActionButton(label: String, tintColorRes: Int?, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label; gravity = Gravity.CENTER; textSize = 22f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val bgColor = if (tintColorRes != null)
                ColorUtils.setAlphaComponent(ContextCompat.getColor(this@ScrabbleGameActivity, tintColorRes), 64)
            else ContextCompat.getColor(this@ScrabbleGameActivity, R.color.cell_editable_bg)
            background = cellDrawable(bgColor)
            setTextColor(ContextCompat.getColor(this@ScrabbleGameActivity, R.color.score_cell_text))
            setOnClickListener { onClick() }
        }

    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
    }

    private fun makeLabelCell(text: String, lp: LinearLayout.LayoutParams, isTotal: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; gravity = Gravity.CENTER; textSize = 11f
            setTypeface(null, Typeface.BOLD)
            layoutParams = lp
            val bg = if (isTotal) R.color.cell_calculated_bg else R.color.header_cell_background
            val fg = if (isTotal) R.color.score_calculated_cell_text else R.color.header_cell_text
            background = cellDrawable(ContextCompat.getColor(this@ScrabbleGameActivity, bg))
            setTextColor(ContextCompat.getColor(this@ScrabbleGameActivity, fg))
        }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@ScrabbleGameActivity, R.color.cell_border))
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    /** Adds a score ([entryIndex] = null) or edits / deletes an existing one. */
    private fun showScoreDialog(turn: ScrabbleTurn, entryIndex: Int?) {
        val player = players.first { it.playerId == turn.playerId }
        val isEdit = entryIndex != null
        val baseTitle = "${player.playerName} — ${getString(R.string.scrabble_enter_score)}"

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            filters = arrayOf(InputFilter.LengthFilter(4))
            hint = getString(R.string.scrabble_score_hint)
            gravity = Gravity.CENTER
            textSize = 20f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            if (isEdit) { setText(turn.entries[entryIndex!!].toString()); selectAll() }
        }
        val container = FrameLayout(this).apply {
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), 0)
            addView(input)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (isEdit) "✏️ $baseTitle" else baseTitle)
            .setView(container)
            .setPositiveButton(getString(R.string.ok), null)   // overridden below to validate
            .setNegativeButton(getString(R.string.cancel), null)
        if (isEdit) {
            builder.setNeutralButton(getString(R.string.scrabble_delete_entry)) { _, _ ->
                turn.entries.removeAt(entryIndex!!)
                buildTable()
            }
        }

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim().toIntOrNull()
                if (value == null || abs(value) > MAX_ABS_SCORE) {
                    Toast.makeText(this, R.string.scrabble_score_invalid, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (isEdit) turn.entries[entryIndex!!] = value else turn.entries.add(value)
                dialog.dismiss()
                buildTable()
            }
        }
        dialog.show()
    }

    private fun showFinishGameDialog() {
        if (gameOver) return
        AlertDialog.Builder(this)
            .setTitle(R.string.scrabble_finish_game_title)
            .setMessage(R.string.scrabble_finish_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finishGame() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.scrabble_quit_game)
            .setMessage(R.string.scrabble_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    // ─── End of game ──────────────────────────────────────────────────────────

    private fun finishGame() {
        // Drop the untouched in-progress turn, close the one that has entries
        turns.removeAll { !it.isComplete && it.entries.isEmpty() }
        turns.forEach { it.isComplete = true }
        gameOver = true
        binding.btnFinishGame.isEnabled = false
        buildTable()
        saveResultsAndShowSummary()
    }

    private fun saveResultsAndShowSummary() {
        val totals    = players.associate { it.playerId to it.getTotal(turns) }
        val maxScore  = totals.values.maxOrNull() ?: 0
        val winnerIds = totals.filter { it.value == maxScore }.keys
        val isDraw    = winnerIds.size > 1
        val playedAt  = System.currentTimeMillis()   // computed ONCE for the whole session
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType   = GAME_TYPE,
                    playerId   = player.playerId,
                    playerName = player.playerName,
                    score      = totals[player.playerId] ?: 0,
                    isWinner   = !isDraw && player.playerId in winnerIds,
                    isDraw     = isDraw && player.playerId in winnerIds,
                    playedAt   = playedAt
                )
            })
            val sorted = players.sortedByDescending { totals[it.playerId] ?: 0 }
            var rank = 1
            val entries = sorted.mapIndexed { i, p ->
                val s = totals[p.playerId] ?: 0
                val prev = if (i > 0) totals[sorted[i - 1].playerId] ?: 0 else s
                val r = if (i > 0 && s == prev) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@ScrabbleGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ─── Menu ─────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scrabble_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { showQuitGameDialog(); true }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
