package com.github.trivialloop.scorehub.games.escoba

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
import android.view.View
import android.view.WindowManager
import android.widget.EditText
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
import com.github.trivialloop.scorehub.databinding.ActivityEscobaGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import com.github.trivialloop.scorehub.utils.ScoreProgressHelper
import kotlinx.coroutines.launch

class EscobaGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEscobaGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<EscobaPlayerState>

    private val rounds = mutableListOf<EscobaRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "escoba"
        private const val SCORE_LIMIT          = 21
        private const val MAX_HAND_SCORE       = 20
        private const val ROW_HEIGHT_DP        = 48
        private const val SUB_HEADER_HEIGHT_DP = 28
        private const val LABEL_COL_DP         = 65
        private const val SYMBOL_IN_PLAY       = "🏃"
        private const val SYMBOL_HAND          = "🏁"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEscobaGameBinding.inflate(layoutInflater)
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
            EscobaPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        val firstRound = EscobaRound(1).also { r ->
            players.forEach { r.inPlayScores[it.playerId] = 0 }
        }
        rounds.add(firstRound)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.escoba_game)

        buildTable()

        onBackPressedDispatcher.addCallback(this) {
            showQuitGameDialog()
        }
    }

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(buildHeaderRow())
        binding.headerContainer.addView(buildProgressRow())

        binding.tableContainer.removeAllViews()
        rounds.forEachIndexed { index, round ->
            binding.tableContainer.addView(
                buildRoundRow(round, isLast = index == rounds.lastIndex, isPrev = index == rounds.lastIndex - 1)
            )
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

        // One single label cell spanning both header lines (no inner border)
        row.addView(makeRoundLabelCell(""))

        players.forEachIndexed { index, player ->
            if (index > 0) row.addView(makeVerticalDivider())
            row.addView(buildPlayerHeaderBlock(player))
        }
        return row
    }

    /** Name on top, then the two symbols below, in the player's color. */
    private fun buildPlayerHeaderBlock(player: EscobaPlayerState): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)

        addView(makePlayerNameHeaderCell(player.playerName, player.playerColor))

        addView(LinearLayout(this@EscobaGameActivity).apply {
            orientation       = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams      = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(SUB_HEADER_HEIGHT_DP))
            addView(makeSubHeaderCell(SYMBOL_IN_PLAY, player.playerColor))
            addView(makeVerticalDivider())
            addView(makeSubHeaderCell(SYMBOL_HAND, player.playerColor))
        })
    }

    private fun buildProgressRow() = ScoreProgressHelper.buildRow(
        context      = this,
        labelColDp   = LABEL_COL_DP,
        limit        = SCORE_LIMIT,
        entries      = players.map { ScoreProgressHelper.Entry(it.getTotal(rounds), it.playerColor) },
        columnWeight = 2f,
        withDividers = true
    )

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

    private fun buildRoundRow(round: EscobaRound, isLast: Boolean, isPrev: Boolean): LinearLayout {
        val row          = makeRow()
        val playerIdList = players.map { it.playerId }
        val currentRound = rounds.last()
        val prevEditable = isPrev && !gameOver && !currentRound.hasInPlayActivity()

        // Player 1 starts odd rounds, player 2 starts even rounds
        val starter = players[(round.roundNumber - 1) % players.size]
        row.addView(makeRoundLabelCell(round.roundNumber.toString(), tint = starter.playerColor))

        players.forEachIndexed { index, player ->
            if (index > 0) row.addView(makeVerticalDivider())

            val myInPlay = round.inPlayScores[player.playerId] ?: 0
            val myHand   = round.handScores[player.playerId]

            // ── In play (−/score/+) ───────────────────────────────────────────
            val inPlayCanEdit = !gameOver && round.isInPlayEditable() &&
                    (isLast || (prevEditable && !round.handScores.values.any { it != null }))

            val allInPlays = players.map { round.inPlayScores[it.playerId] }
            val inPlayRole = ScoreColorRole(myInPlay, allInPlays, higherIsBetter = true)

            row.addView(makeInPlayCell(
                score       = myInPlay,
                textColor   = inPlayRole.toColor(this),
                canEdit     = inPlayCanEdit,
                onDecrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    if (cur > 0) { round.inPlayScores[player.playerId] = cur - 1; buildTable() }
                },
                onIncrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    round.inPlayScores[player.playerId] = cur + 1; buildTable()
                }
            ))

            // ── End-of-round hand score ───────────────────────────────────────
            val handCanEdit = !gameOver &&
                    (isLast && !round.isComplete(playerIdList) || prevEditable)

            val bgColor = when {
                handCanEdit && myHand == null -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                handCanEdit && myHand != null -> ContextCompat.getColor(this, R.color.cell_editable_filled_bg)
                else                          -> ContextCompat.getColor(this, R.color.cell_locked_bg)
            }

            val allHands = players.map { round.handScores[it.playerId] }
            val handRole = ScoreColorRole(myHand, allHands, higherIsBetter = true)

            val handCell = makeHandCell(
                hand      = myHand,
                bgColor   = bgColor,
                textColor = handRole.toColor(this),
                bold      = handRole != ScoreColorRole.NEUTRAL && myHand != null,
                locked    = !handCanEdit
            )
            if (handCanEdit) handCell.setOnClickListener { showHandScorePicker(round, player) }
            row.addView(handCell)
        }
        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row         = makeRow()
        val totalValues = players.map { it.getTotal(rounds) }
        row.addView(makeRoundLabelCell(getString(R.string.escoba_total)))

        players.forEachIndexed { index, player ->
            if (index > 0) row.addView(makeVerticalDivider())

            val total = player.getTotal(rounds)
            val role  = ScoreColorRole(total, totalValues, higherIsBetter = true)
            val cell  = makeTotalCell(total.toString())
            if (gameOver && role != ScoreColorRole.NEUTRAL) cell.setTextColor(role.toColor(this))
            row.addView(cell)
        }
        return row
    }

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    private fun showHandScorePicker(round: EscobaRound, player: EscobaPlayerState) {
        val current = round.handScores[player.playerId]
        val title = if (current != null)
            "✏️ ${player.playerName} — ${getString(R.string.escoba_hand_score)}"
        else
            "${player.playerName} — ${getString(R.string.escoba_hand_score)}"

        val values = (0..MAX_HAND_SCORE).toList()
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(values.map { it.toString() }.toTypedArray()) { _, which ->
                round.handScores[player.playerId] = values[which]
                buildTable()
                if (round.isComplete(players.map { it.playerId })) checkEndOfGame(round)
            }
            .create()

        dialog.show()
        if (current != null) dialog.listView?.setSelection(current)
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    private fun checkEndOfGame(round: EscobaRound) {
        if (round !== rounds.last()) { buildTable(); return }

        val maxTotal = players.maxOf { it.getTotal(rounds) }
        if (maxTotal >= SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.escoba_game_over_title))
                .setMessage(getString(R.string.escoba_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    val newRound = EscobaRound(rounds.size + 1).also { r ->
                        players.forEach { r.inPlayScores[it.playerId] = 0 }
                    }
                    rounds.add(newRound); buildTable()
                }
                .show()
        } else {
            val newRound = EscobaRound(rounds.size + 1).also { r ->
                players.forEach { r.inPlayScores[it.playerId] = 0 }
            }
            rounds.add(newRound); buildTable()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals   = players.associate { it to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners  = totals.filter { it.value == maxScore }.keys
        val isDraw   = winners.size > 1
        val playedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType   = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.getTotal(rounds),
                    isWinner   = !isDraw && player in winners,
                    isDraw     = isDraw && player in winners,
                    playedAt   = playedAt
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@EscobaGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(heightDp: Int = ROW_HEIGHT_DP): LinearLayout = LinearLayout(this).apply {
        orientation       = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        layoutParams      = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
    }

    /** Label cell; [tint] (player color) marks who starts the round. */
    private fun makeRoundLabelCell(text: String, tint: Int? = null): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
        background = cellDrawable(
            tint ?: ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_background))
        setTextColor(
            if (tint != null) Color.WHITE
            else ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_text))
    }

    private fun makeInPlayCell(
        score: Int, textColor: Int, canEdit: Boolean,
        onDecrement: () -> Unit, onIncrement: () -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation       = LinearLayout.HORIZONTAL
        gravity           = Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        layoutParams      = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background = cellDrawable(ContextCompat.getColor(
            this@EscobaGameActivity,
            if (canEdit) R.color.cell_editable_bg else R.color.cell_locked_bg))

        addView(makeStepButton("−", canEdit, onDecrement))
        addView(TextView(this@EscobaGameActivity).apply {
            text = score.toString(); gravity = Gravity.CENTER; textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setTextColor(textColor); alpha = if (canEdit) 1f else 0.65f
        })
        addView(makeStepButton("+", canEdit, onIncrement))
    }

    /** Small rounded button; invisible (but still taking space) once the in-play phase is locked. */
    private fun makeStepButton(symbol: String, visible: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = symbol; gravity = Gravity.CENTER; textSize = 18f; setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.score_cell_text))
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(34), 1f).also {
                it.marginStart = dpToPx(2); it.marginEnd = dpToPx(2)
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.score_cell_background))
                setStroke(1, ContextCompat.getColor(this@EscobaGameActivity, R.color.cell_border))
            }
            if (visible) setOnClickListener { onClick() } else visibility = View.INVISIBLE
        }

    /** Hand score centered, round total (in play + hand) in the bottom-right corner. */
    private fun makeHandCell(
        hand: Int?, bgColor: Int, textColor: Int, bold: Boolean, locked: Boolean
    ): TextView = TextView(this).apply {
        text = hand?.toString() ?: ""; gravity = Gravity.CENTER; textSize = 16f
        if (bold) setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background = cellDrawable(bgColor)
        setTextColor(textColor)
        if (locked && hand != null) alpha = 0.75f
    }

    private fun makeTotalCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 18f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.score_calculated_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@EscobaGameActivity, R.color.cell_border))
    }

    /** 1dp vertical line, same color as the cell borders. */
    private fun makeVerticalDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dpToPx(1), LinearLayout.LayoutParams.MATCH_PARENT)
        setBackgroundColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.escoba_quit_game)
            .setMessage(R.string.escoba_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_escoba_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { showQuitGameDialog(); true }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
