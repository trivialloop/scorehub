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
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityEscobaGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
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
        private const val SCORE_LIMIT = 21
        private const val MAX_HAND_SCORE = 20
        private const val ROW_HEIGHT_DP = 48
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
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())

            binding.appBarLayout.setPadding(
                0,
                statusBarInsets.top,
                0,
                0
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

        val firstRound = EscobaRound(1).also { r -> players.forEach { r.inPlayScores[it.playerId] = 0 } }
        rounds.add(firstRound)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.escoba_game)

        buildTable()
    }

    // ─── Table ────────────────────────────────────────────────────────────────

    private fun buildTable() {
        val headerRow = buildHeaderRow()
        val roundRows = rounds.mapIndexed { index, round -> buildRoundRow(round, index) }
        val totalRow  = buildTotalRow()

        val screenHeight = resources.displayMetrics.heightPixels
        val appBarHeight = binding.toolbar.layoutParams?.height?.takeIf { it > 0 } ?: dpToPx(56)
        val rowHeightPx  = dpToPx(ROW_HEIGHT_DP)
        val totalNaturalHeight = rowHeightPx * (roundRows.size + 3)

        if (totalNaturalHeight > screenHeight - appBarHeight) {
            binding.headerContainer.removeAllViews(); binding.headerContainer.addView(headerRow)
            binding.tableContainer.removeAllViews(); roundRows.forEach { binding.tableContainer.addView(it) }
            binding.totalContainer.removeAllViews(); binding.totalContainer.addView(totalRow)
            binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        } else {
            binding.headerContainer.removeAllViews()
            binding.tableContainer.removeAllViews(); binding.totalContainer.removeAllViews()
            binding.tableContainer.addView(headerRow)
            roundRows.forEach { binding.tableContainer.addView(it) }
            binding.tableContainer.addView(totalRow)
        }
    }

    private fun buildHeaderRow(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val nameRow = makeFixedRow()
        nameRow.addView(makeRoundLabelCell(""))
        for (player in players) {
            nameRow.addView(makePlayerNameHeaderCell(player.playerName, player.playerColor, weight = 2f))
        }
        container.addView(nameRow)

        val subRow = makeFixedRow()
        subRow.addView(makeRoundLabelCell("#"))
        repeat(players.size) {
            subRow.addView(makeSubHeaderCell(getString(R.string.escoba_in_play)))
            subRow.addView(makeSubHeaderCell(getString(R.string.escoba_hand)))
        }
        container.addView(subRow)

        return container
    }

    private fun buildRoundRow(round: EscobaRound, roundIndex: Int): LinearLayout {
        val isLastRound = roundIndex == rounds.lastIndex
        val isPrevRound = roundIndex == rounds.lastIndex - 1
        val currentRound = rounds.last()
        val prevEditable = isPrevRound && !gameOver && !currentRound.hasInPlayActivity()

        val row = makeFixedRow()
        row.addView(makeRoundLabelCell(round.roundNumber.toString()))

        val playerIdList = players.map { it.playerId }

        for (player in players) {
            val myInPlay  = round.inPlayScores[player.playerId] ?: 0
            val myHand = round.handScores[player.playerId]

            val inPlayCanEdit = !gameOver && round.isInPlayEditable() &&
                    (isLastRound || (prevEditable && !round.handScores.values.any { it != null }))

            val allInPlays  = players.map { round.inPlayScores[it.playerId] }
            val inPlayRole  = ScoreColorRole(myInPlay, allInPlays)
            val inPlayColor = when (inPlayRole) {
                ScoreColorRole.BEST  -> ContextCompat.getColor(this, R.color.score_text_best)
                ScoreColorRole.WORST -> ContextCompat.getColor(this, R.color.score_text_worst)
                else                 -> ContextCompat.getColor(this, R.color.score_cell_text)
            }

            row.addView(makeInPlayCell(
                score    = myInPlay,
                textColor = inPlayColor,
                canEdit  = inPlayCanEdit,
                onDecrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    if (cur > 0) { round.inPlayScores[player.playerId] = cur - 1; buildTable() }
                },
                onIncrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    round.inPlayScores[player.playerId] = cur + 1; buildTable()
                }
            ))

            // ── Fin de manche (hand) ──────────────────────────────────────────
            val handCanEdit  = !gameOver && (isLastRound && !round.isComplete(playerIdList) || prevEditable)

            val bgColor = when {
                handCanEdit && myHand == null -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                handCanEdit && myHand != null -> ContextCompat.getColor(this, R.color.cell_editable_filled_bg)
                else                          -> ContextCompat.getColor(this, R.color.cell_locked_bg)
            }

            val allHands  = players.map { round.handScores[it.playerId] }
            val handRole  = ScoreColorRole(myHand, allHands)
            val handColor = when (handRole) {
                ScoreColorRole.BEST  -> ContextCompat.getColor(this, R.color.score_text_best)
                ScoreColorRole.WORST -> ContextCompat.getColor(this, R.color.score_text_worst)
                else                 -> ContextCompat.getColor(this, R.color.score_cell_text)
            }

            val handCell = makeScoreCell(myHand?.toString() ?: "", bgColor, handColor, bold = handRole != ScoreColorRole.NEUTRAL && myHand != null)
            if (!handCanEdit && myHand != null) handCell.alpha = 0.75f
            if (handCanEdit) handCell.setOnClickListener { showHandScoreInput(round, player) }

            row.addView(handCell)
        }

        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeFixedRow()
        row.addView(makeRoundLabelCell(getString(R.string.escoba_total)))
        val totalValues = players.map { it.getTotal(rounds) }

        for (player in players) {
            val total = player.getTotal(rounds)
            val cell  = makeTotalCell(total.toString(), weight = 2f)
            if (gameOver) {
                when (ScoreColorRole(total, totalValues)) {
                    ScoreColorRole.BEST  -> cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best))
                    ScoreColorRole.WORST -> cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_worst))
                    else -> {}
                }
            }
            row.addView(cell)
        }
        return row
    }

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    private fun showHandScoreInput(round: EscobaRound, player: EscobaPlayerState) {
        val current = round.handScores[player.playerId]

        val title = if (current != null) "✏️ ${player.playerName} — ${getString(R.string.escoba_hand_score)}"
        else "${player.playerName} — ${getString(R.string.escoba_hand_score)}"

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint      = "0–$MAX_HAND_SCORE"
            gravity   = Gravity.CENTER
            textSize  = 20f
            filters   = arrayOf(InputFilter.LengthFilter(2))
            current?.let { setText(it.toString()) }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 0 || value > MAX_HAND_SCORE) {
                    showHandScoreInput(round, player); return@setPositiveButton
                }
                round.handScores[player.playerId] = value
                buildTable()
                val playerIdList = players.map { it.playerId }
                if (round.isComplete(playerIdList)) checkEndOfGame(round)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        editText.requestFocus()
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    /**
     * Called when a round becomes complete.
     *
     * Guard: if [round] is not the last round, it is a re-edit of a previously
     * completed round — a new round already exists, so do NOT add another one.
     */
    private fun checkEndOfGame(round: EscobaRound) {
        // Re-edit guard: only add a new round if this is the current last round
        if (round !== rounds.last()) {
            buildTable()
            return
        }

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
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.getTotal(rounds),
                    isWinner = !isDraw && player in winners,
                    isDraw   = isDraw && player in winners
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

    private fun makeFixedRow(): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ROW_HEIGHT_DP))
    }

    private fun makeRoundLabelCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(32), LinearLayout.LayoutParams.MATCH_PARENT)
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameHeaderCell(name: String, color: Int, weight: Float): TextView = TextView(this).apply {
        text = name; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(color); setTextColor(Color.WHITE)
    }

    private fun makeSubHeaderCell(label: String): TextView = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 9f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_text))
    }

    private fun makeInPlayCell(
        score: Int, textColor: Int, canEdit: Boolean,
        onDecrement: () -> Unit, onIncrement: () -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL
        gravity      = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background   = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.score_cell_background))

        addView(TextView(this@EscobaGameActivity).apply {
            text = "−"; gravity = Gravity.CENTER; textSize = 18f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
            setTextColor(textColor); alpha = if (canEdit) 1f else 0.25f
            if (canEdit) setOnClickListener { onDecrement() }
        })
        addView(TextView(this@EscobaGameActivity).apply {
            text = score.toString(); gravity = Gravity.CENTER; textSize = 14f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setTextColor(textColor); alpha = if (canEdit) 1f else 0.65f
        })
        addView(TextView(this@EscobaGameActivity).apply {
            text = "+"; gravity = Gravity.CENTER; textSize = 18f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
            setTextColor(textColor); alpha = if (canEdit) 1f else 0.25f
            if (canEdit) setOnClickListener { onIncrement() }
        })
    }

    private fun makeScoreCell(text: String, bgColor: Int, textColor: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; gravity = Gravity.CENTER; textSize = 14f
            if (bold) setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            background = cellDrawable(bgColor); setTextColor(textColor)
        }

    private fun makeTotalCell(text: String, weight: Float = 1f): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 15f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.yahtzee_calculated_cell_background))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.yahtzee_calculated_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@EscobaGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_escoba_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.escoba_quit_game)
                    .setMessage(R.string.escoba_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            R.id.action_help -> {
                HelpDialogs.showAppHelp(this, GAME_TYPE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
