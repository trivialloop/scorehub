package com.github.trivialloop.scorehub.games.cribbage

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.addCallback
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
import com.github.trivialloop.scorehub.databinding.ActivityCribbageGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class CribbageGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCribbageGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<CribbagePlayerState>

    private val rounds = mutableListOf<CribbageRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "cribbage"
        private const val WIN_SCORE            = 121
        private const val MAX_HAND_SCORE       = 29
        private const val MAX_CRIB_SCORE       = 29
        private const val ROW_HEIGHT_DP        = 48
        private const val SUB_HEADER_HEIGHT_DP = 28
        private const val LABEL_COL_DP         = 65
        private const val SYMBOL_IN_PLAY       = "🏃"
        private const val SYMBOL_HAND          = "🏁"
        private const val SYMBOL_CRIB          = "📥"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCribbageGameBinding.inflate(layoutInflater)
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
            CribbagePlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.cribbage_game)

        addNewRound()
        buildTable()

        onBackPressedDispatcher.addCallback(this) {
            showQuitGameDialog()
        }
    }

    // ─── Round management ──────────────────────────────────────────────────────

    private fun addNewRound() {
        val roundIndex       = rounds.size
        val firstPlayerIndex = roundIndex % 2
        val dealerIndex      = 1 - firstPlayerIndex
        rounds.add(CribbageRound(roundIndex + 1, playerIds[firstPlayerIndex], playerIds[dealerIndex]))
    }

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(buildHeaderRow())

        binding.tableContainer.removeAllViews()
        rounds.forEachIndexed { index, round ->
            binding.tableContainer.addView(buildRoundBlock(round, index))
        }
        binding.tableContainer.addView(buildTotalRow())

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun buildHeaderRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation       = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams      = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(ROW_HEIGHT_DP + SUB_HEADER_HEIGHT_DP))
        }

        // One single label cell spanning both header lines
        row.addView(makeRoundLabelCell(""))

        players.forEachIndexed { index, player ->
            if (index > 0) row.addView(makeVerticalDivider())
            row.addView(buildPlayerHeaderBlock(player))
        }
        return row
    }

    /** Name on top, then the two symbols below, in the player's color. */
    private fun buildPlayerHeaderBlock(player: CribbagePlayerState): LinearLayout =
        LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)

            addView(makePlayerNameHeaderCell(player.playerName, player.playerColor))

            addView(LinearLayout(this@CribbageGameActivity).apply {
                orientation       = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                layoutParams      = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(SUB_HEADER_HEIGHT_DP))
                addView(makeSubHeaderCell(SYMBOL_IN_PLAY, player.playerColor))
                addView(makeVerticalDivider())
                addView(makeSubHeaderCell(SYMBOL_HAND, player.playerColor))
            })
        }

    /**
    * One round = 2 lines:
    *  - top line    : in play | end of round (per player)
    *  - bottom line : crib (dealer only, the other player gets a "never" cell)
    * The label cell spans both lines.
    */
    private fun buildRoundBlock(round: CribbageRound, roundIndex: Int): LinearLayout {
        val isLastRound       = roundIndex == rounds.lastIndex
        val isPrevRound       = roundIndex == rounds.lastIndex - 1
        val currentRound      = rounds.last()
        val prevRoundEditable = isPrevRound && !gameOver && !currentRound.hasInPlayActivity()

        val block = LinearLayout(this).apply {
            orientation       = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams      = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ROW_HEIGHT_DP * 2))
        }

        val firstPlayer = players.first { it.playerId == round.firstPlayerId }
        block.addView(makeRoundLabelCell(round.roundNumber.toString(), tint = firstPlayer.playerColor))

        val inPlay0 = round.inPlayScores[players[0].playerId] ?: 0
        val inPlay1 = round.inPlayScores[players[1].playerId] ?: 0
        val hand0   = round.handScores[players[0].playerId]
        val hand1   = round.handScores[players[1].playerId]

        players.forEachIndexed { index, player ->
            if (index > 0) block.addView(makeVerticalDivider())

            val isFirstPlayer = player.playerId == round.firstPlayerId
            val isDealer      = player.playerId == round.dealerId
            val myInPlay      = round.inPlayScores[player.playerId] ?: 0
            val myHand        = round.handScores[player.playerId]

            // ── In play ───────────────────────────────────────────────────────
            val inPlayCanEdit = !gameOver && round.isInPlayEditable() &&
                    (isLastRound || (prevRoundEditable && !round.isFirstPlayerHandEntered()))

            val inPlayRole  = ScoreColorRole(myInPlay, listOf(inPlay0, inPlay1), higherIsBetter = true)
            val inPlayColor = inPlayRole.toColor(this)

            val inPlayState = when {
                inPlayCanEdit             -> CellState.EDITABLE
                !round.isInPlayEditable() -> CellState.LOCKED_PREV
                isLastRound && !gameOver  -> CellState.LOCKED_SOON
                else                      -> CellState.LOCKED_PREV
            }

            val inPlayCell = makeInPlayCell(
                score       = myInPlay,
                scoreColor  = inPlayColor,
                state       = inPlayState,
                onDecrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    if (cur > 0) { round.inPlayScores[player.playerId] = cur - 1; buildTable(); checkGameOver() }
                },
                onIncrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    round.inPlayScores[player.playerId] = cur + 1; buildTable(); checkGameOver()
                }
            )

            // ── Hand ──────────────────────────────────────────────────────────
            val handEditable = !gameOver && when {
                isLastRound && isFirstPlayer -> myHand == null || round.cribScore == null
                isLastRound && isDealer      -> round.isFirstPlayerHandEntered() && (myHand == null || round.cribScore == null)
                prevRoundEditable            -> true
                else                         -> false
            }
            val handState = when {
                handEditable                                                              -> CellState.EDITABLE
                isLastRound && isDealer && !round.isFirstPlayerHandEntered() && !gameOver -> CellState.LOCKED_SOON
                isLastRound && round.cribScore != null                                   -> CellState.LOCKED_PREV
                isLastRound && !gameOver                                                 -> CellState.LOCKED_SOON
                else                                                                     -> CellState.LOCKED_PREV
            }
            val handRole  = ScoreColorRole(myHand, listOf(hand0, hand1), higherIsBetter = true)
            val handColor = if (handRole != ScoreColorRole.NEUTRAL && myHand != null) handRole.toColor(this)
                            else ContextCompat.getColor(this, R.color.score_cell_text)

            val handCell = makeHandCell(
                score     = myHand,
                textColor = handColor,
                bold      = handRole != ScoreColorRole.NEUTRAL && myHand != null,
                state     = handState,
                onClick   = { showHandScoreInput(round, player.playerId) }
            )

            // ── Crib (bottom line) ────────────────────────────────────────────
            val cribCell = if (isDealer) {
                val cribEditable = !gameOver &&
                        (isLastRound && round.isDealerHandEntered() && round.cribScore == null || prevRoundEditable)
                val cribState = when {
                    cribEditable                                             -> CellState.EDITABLE
                    isLastRound && !round.isDealerHandEntered() && !gameOver -> CellState.LOCKED_SOON
                    isLastRound && round.cribScore != null                  -> CellState.LOCKED_PREV
                    isLastRound && !gameOver                                -> CellState.LOCKED_SOON
                    else                                                    -> CellState.LOCKED_PREV
                }
                makeCribCell(score = round.cribScore, state = cribState, onClick = { showCribScoreInput(round) })
            } else {
                makeNeverCribCell()
            }

            // ── Player column: top line + crib line ───────────────────────────
            val topRow = makeRow()
            topRow.addView(inPlayCell)
            topRow.addView(makeVerticalDivider())
            topRow.addView(handCell)

            block.addView(LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)
                addView(topRow)
                addView(cribCell)
            })
        }
        return block
    }

    private fun buildTotalRow(): LinearLayout {
        val row       = makeRow()
        val totals    = players.associate { it.playerId to it.getTotal(rounds) }
        val allTotals = players.map { totals[it.playerId] }
        row.addView(makeRoundLabelCell(getString(R.string.cribbage_total)))

        players.forEachIndexed { index, player ->
            if (index > 0) row.addView(makeVerticalDivider())

            val total = totals[player.playerId] ?: 0
            val cell  = makeTotalCell(total.toString())
            if (gameOver) {
                val role = ScoreColorRole(total, allTotals, higherIsBetter = true)
                if (role != ScoreColorRole.NEUTRAL) cell.setTextColor(role.toColor(this))
            }
            row.addView(cell)
        }
        return row
    }

    // ─── Game over ─────────────────────────────────────────────────────────────

    private fun checkGameOver() {
        if (gameOver) return
        val totals = players.associate { it.playerId to it.getTotal(rounds) }
        if (totals.values.none { it >= WIN_SCORE }) return
        gameOver = true; buildTable(); saveResultsAndShowSummary()
    }

    private fun onRoundComplete(round: CribbageRound) {
        if (round !== rounds.last()) { buildTable(); return }
        checkGameOver()
        if (!gameOver) { addNewRound(); buildTable() }
    }

    // ─── Score input dialogs ───────────────────────────────────────────────────

    private fun showHandScoreInput(round: CribbageRound, playerId: Long) {
        val isFirstPlayer = playerId == round.firstPlayerId
        if (!isFirstPlayer && !round.isFirstPlayerHandEntered()) return

        val playerName = players.first { it.playerId == playerId }.playerName
        val current    = round.handScores[playerId]
        val title = if (current != null) "✏️ $playerName — ${getString(R.string.cribbage_hand_score)}"
                    else "$playerName — ${getString(R.string.cribbage_hand_score)}"

        showScorePicker(title, MAX_HAND_SCORE, current) { value ->
            round.handScores[playerId] = value
            buildTable(); checkGameOver()
            if (round.isComplete()) onRoundComplete(round)
        }
    }

    private fun showCribScoreInput(round: CribbageRound) {
        val dealerName = players.first { it.playerId == round.dealerId }.playerName
        val current    = round.cribScore
        val title = if (current != null) "✏️ $dealerName — ${getString(R.string.cribbage_crib_score)}"
                    else "$dealerName — ${getString(R.string.cribbage_crib_score)}"

        showScorePicker(title, MAX_CRIB_SCORE, current) { value ->
            round.cribScore = value
            buildTable(); checkGameOver()
            if (round.isComplete()) onRoundComplete(round)
        }
    }

    /** List picker 0..[max]; the current value is preselected when editing. */
    private fun showScorePicker(title: String, max: Int, current: Int?, onPicked: (Int) -> Unit) {
        val values = (0..max).toList()
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(values.map { it.toString() }.toTypedArray()) { _, which -> onPicked(values[which]) }
            .create()
        dialog.show()
        if (current != null) dialog.listView?.setSelection(current)
    }

    // ─── Save results ──────────────────────────────────────────────────────────

    private fun saveResultsAndShowSummary() {
        val totals   = players.associate { it to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners  = totals.filter { it.value == maxScore }.keys
        val isDraw   = winners.size > 1
        val playedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(gameType = GAME_TYPE, playerId = player.playerId, playerName = player.playerName,
                    score = player.getTotal(rounds), isWinner = !isDraw && player in winners, isDraw = isDraw && player in winners,
                    playedAt = playedAt)
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@CribbageGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(heightDp: Int = ROW_HEIGHT_DP): LinearLayout = LinearLayout(this).apply {
        orientation       = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        layoutParams      = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
    }

    /** Label cell; [tint] (player color) marks who plays first this round. */
    private fun makeRoundLabelCell(text: String, tint: Int? = null): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
        background = cellDrawable(
            tint ?: ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_background))
        setTextColor(
            if (tint != null) Color.WHITE
            else ContextCompat.getColor(this@CribbageGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameHeaderCell(name: String, color: Int): TextView = TextView(this).apply {
        text = name; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ROW_HEIGHT_DP))
        background = cellDrawable(color); setTextColor(Color.WHITE)
    }

    private fun makeSubHeaderCell(symbol: String, color: Int): TextView = TextView(this).apply {
        text = symbol; gravity = Gravity.CENTER; textSize = 14f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background = GradientDrawable().apply { setColor(color) }
        setTextColor(Color.WHITE)
    }

    /** 1dp vertical line, same color as the cell borders. */
    private fun makeVerticalDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dpToPx(1), LinearLayout.LayoutParams.MATCH_PARENT)
        setBackgroundColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_border))
    }

    private fun makeInPlayCell(
        score: Int, scoreColor: Int, state: CellState,
        onDecrement: () -> Unit, onIncrement: () -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation       = LinearLayout.HORIZONTAL
        gravity           = Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        layoutParams      = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background        = cellDrawable(resolveBgColor(state, score = null))

        val canEdit     = state == CellState.EDITABLE
        val lockedAlpha = when (state) { CellState.LOCKED_SOON -> 0.55f; CellState.LOCKED_PREV -> 0.65f; else -> 0.35f }

        addView(makeStepButton("−", canEdit, onDecrement))
        addView(TextView(this@CribbageGameActivity).apply {
            text = score.toString(); gravity = Gravity.CENTER; textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setTextColor(scoreColor); alpha = if (canEdit) 1f else lockedAlpha
        })
        addView(makeStepButton("+", canEdit, onIncrement))
    }

    /** Small rounded button; invisible (but still taking space) once the in-play phase is locked. */
    private fun makeStepButton(symbol: String, visible: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = symbol; gravity = Gravity.CENTER; textSize = 18f; setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.score_cell_text))
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(34), 1f).also {
                it.marginStart = dpToPx(2); it.marginEnd = dpToPx(2)
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.score_cell_background))
                setStroke(1, ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_border))
            }
            if (visible) setOnClickListener { onClick() } else visibility = View.INVISIBLE
        }

    private fun makeHandCell(score: Int?, textColor: Int, bold: Boolean, state: CellState, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = score?.toString() ?: ""; gravity = Gravity.CENTER; textSize = 16f
            if (bold) setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setTextColor(textColor)
            background = cellDrawable(resolveBgColor(state, score))
            alpha = resolveAlpha(state, score)
            if (state == CellState.EDITABLE) setOnClickListener { onClick() }
        }

    /** Crib cell: full width of the player block, on the second line of the round. */
    private fun makeCribCell(score: Int?, state: CellState, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = score?.toString() ?: SYMBOL_CRIB
            gravity = Gravity.CENTER; textSize = if (score != null) 16f else 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ROW_HEIGHT_DP))
            setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.score_cell_text))
            val bgColor = if (state == CellState.EDITABLE && score != null)
                ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_editable_bg)
            else resolveBgColor(state, score)
            background = cellDrawable(bgColor)
            alpha = resolveAlpha(state, score)
            if (state == CellState.EDITABLE) setOnClickListener { onClick() }
        }

    private fun makeNeverCribCell(): TextView = TextView(this).apply {
        text = ""
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ROW_HEIGHT_DP))
        background = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_never_bg))
    }

    private fun makeTotalCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 18f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)
        background = cellDrawable(ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@CribbageGameActivity, R.color.score_calculated_cell_text))
    }

    private fun resolveBgColor(state: CellState, score: Int?): Int = when (state) {
        CellState.EDITABLE     -> if (score == null) ContextCompat.getColor(this, R.color.cell_editable_bg)
                                  else ContextCompat.getColor(this, R.color.score_cell_background)
        CellState.LOCKED_SOON  -> ContextCompat.getColor(this, R.color.cell_editable_bg)
        CellState.LOCKED_PREV  -> ContextCompat.getColor(this, R.color.cell_locked_bg)
        CellState.LOCKED_NEVER -> ContextCompat.getColor(this, R.color.cell_never_bg)
    }

    private fun resolveAlpha(state: CellState, score: Int?): Float = when (state) {
        CellState.EDITABLE     -> 1f
        CellState.LOCKED_SOON  -> 0.55f
        CellState.LOCKED_PREV  -> if (score != null) 0.75f else 0.4f
        CellState.LOCKED_NEVER -> 1f
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor); setStroke(1, ContextCompat.getColor(this@CribbageGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.cribbage_quit_game)
            .setMessage(R.string.cribbage_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_cribbage_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { showQuitGameDialog(); true }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
