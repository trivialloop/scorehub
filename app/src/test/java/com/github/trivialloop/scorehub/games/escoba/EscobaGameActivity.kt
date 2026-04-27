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
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class EscobaGameActivity : BaseActivity() {

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

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEscobaGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        players = playerIds.indices.map { i ->
            EscobaPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        // Initialise in play scores for round 1
        val firstRound = EscobaRound(1)
        players.forEach { firstRound.inPlayScores[it.playerId] = 0 }
        rounds.add(firstRound)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.escoba_game)

        buildTable()
    }

    // ─── Table construction ────────────────────────────────────────────────────

    /**
     * Column layout (2 sub-columns per player):
     *  [#] │ Player 0: [En jeu (w=1.5)] [Fin de manche (w=1)] │ Player 1: [...] │ ...
     */
    private fun buildTable() {
        val headerRow = buildHeaderRow()
        val roundRows = rounds.mapIndexed { index, round ->
            buildRoundRow(round, index)
        }
        val totalRow = buildTotalRow()

        val screenHeight = resources.displayMetrics.heightPixels
        val appBarHeight = binding.toolbar.layoutParams?.height?.takeIf { it > 0 } ?: dpToPx(56)
        val rowHeightPx = dpToPx(ROW_HEIGHT_DP)
        val totalNaturalHeight = rowHeightPx * (roundRows.size + 3)
        val availableHeight = screenHeight - appBarHeight

        if (totalNaturalHeight > availableHeight) {
            binding.headerContainer.removeAllViews()
            binding.headerContainer.addView(headerRow)
            binding.tableContainer.removeAllViews()
            roundRows.forEach { binding.tableContainer.addView(it) }
            binding.totalContainer.removeAllViews()
            binding.totalContainer.addView(totalRow)
            binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        } else {
            binding.headerContainer.removeAllViews()
            binding.tableContainer.removeAllViews()
            binding.totalContainer.removeAllViews()
            binding.tableContainer.addView(headerRow)
            roundRows.forEach { binding.tableContainer.addView(it) }
            binding.tableContainer.addView(totalRow)
        }
    }

    /**
     * Two-row header:
     *  Row 1: [empty] │ [Player 0 name — spans 2.5] │ [Player 1 name — spans 2.5] │ ...
     *  Row 2: [#]     │ [En jeu][Fin de manche]      │ [En jeu][Fin de manche]      │ ...
     */
    private fun buildHeaderRow(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Row 1 — player names
        val nameRow = makeFixedRow()
        nameRow.addView(makeRoundLabelCell(""))
        for (player in players) {
            nameRow.addView(makePlayerNameHeaderCell(player.playerName, player.playerColor, weight = 2.5f))
        }
        container.addView(nameRow)

        // Row 2 — sub-labels (En jeu + Fin de manche)
        val subRow = makeFixedRow()
        subRow.addView(makeRoundLabelCell("#"))
        repeat(players.size) {
            subRow.addView(makeSubHeaderCell(getString(R.string.escoba_in_play), weight = 1.5f))
            subRow.addView(makeSubHeaderCell(getString(R.string.escoba_hand)))
        }
        container.addView(subRow)

        return container
    }

    private fun buildRoundRow(round: EscobaRound, roundIndex: Int): LinearLayout {
        val isLastRound = roundIndex == rounds.lastIndex
        val isPrevRound = roundIndex == rounds.lastIndex - 1
        val currentRound = rounds.last()

        // Previous round stays editable until the new round has any in play
        val prevRoundEditable = isPrevRound && !gameOver && !currentRound.hasInPlayActivity()

        val row = makeFixedRow()
        row.addView(makeRoundLabelCell(round.roundNumber.toString()))

        for (player in players) {
            val myInPlay = round.inPlayScores[player.playerId] ?: 0
            val myHand = round.handScores[player.playerId]
            val allHands = players.mapNotNull { round.handScores[it.playerId] }
            val oppInPlays = players.filter { it.playerId != player.playerId }
                .map { round.inPlayScores[it.playerId] ?: 0 }

            // ── En jeu (In play) ──────────────────────────────────────────────
            val inPlayCanEdit = !gameOver && round.isInPlayEditable() &&
                    (isLastRound || (prevRoundEditable && !round.handScores.values.any { it != null }))

            val inPlayColor = when {
                oppInPlays.isEmpty() -> EscobaScoreColor.NEUTRAL
                myInPlay > oppInPlays.max() -> EscobaScoreColor.BEST
                myInPlay < oppInPlays.min() -> EscobaScoreColor.WORST
                else -> EscobaScoreColor.NEUTRAL
            }

            row.addView(makeInPlayCell(
                score = myInPlay,
                scoreColor = inPlayColor,
                canEdit = inPlayCanEdit,
                onDecrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    if (cur > 0) {
                        round.inPlayScores[player.playerId] = cur - 1
                        buildTable()
                        checkGameOver()
                    }
                },
                onIncrement = {
                    val cur = round.inPlayScores[player.playerId] ?: 0
                    round.inPlayScores[player.playerId] = cur + 1
                    buildTable()
                    checkGameOver()
                }
            ))

            // ── Fin de manche (Hand) ───────────────────────────────────────────
            val handCanEdit = !gameOver &&
                    (isLastRound && !round.isComplete(players.map { it.playerId }) ||
                            prevRoundEditable)

            // Score color based on all entered hand scores
            val handColor = when {
                myHand == null -> EscobaScoreColor.NEUTRAL
                allHands.size < players.size -> EscobaScoreColor.NEUTRAL // not all entered yet
                allHands.toSet().size == 1 -> EscobaScoreColor.NEUTRAL   // all same
                myHand == allHands.max() -> EscobaScoreColor.BEST
                myHand == allHands.min() -> EscobaScoreColor.WORST
                else -> EscobaScoreColor.NEUTRAL
            }

            // Pencil icon when re-editing a filled cell
            val displayText = when {
                myHand == null && handCanEdit -> ""
                myHand != null && handCanEdit -> "✏ $myHand"
                else -> myHand?.toString() ?: ""
            }

            val handBgColor = when {
                myHand == null && handCanEdit ->
                    ContextCompat.getColor(this, R.color.cell_editable_bg)
                myHand != null && handCanEdit ->
                    ContextCompat.getColor(this, R.color.cell_editable_filled_bg)
                else ->
                    ContextCompat.getColor(this, R.color.cell_locked_bg)
            }

            val handCell = makeHandCell(
                text = displayText,
                textColor = resolveScoreColor(handColor),
                bgColor = handBgColor,
                canEdit = handCanEdit
            )

            if (handCanEdit) {
                handCell.setOnClickListener { showHandScoreInput(round, player) }
            }
            row.addView(handCell)
        }

        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeFixedRow()
        row.addView(makeRoundLabelCell(getString(R.string.escoba_total)))

        val totals = players.associate { it.playerId to it.getTotal(rounds) }
        val maxTotal = if (gameOver) totals.values.maxOrNull() else null

        for (player in players) {
            val total = totals[player.playerId] ?: 0
            // Total spans 2.5 sub-columns (1.5 en jeu + 1 fin de manche)
            val cell = makeTotalCell(total.toString(), weight = 2.5f)
            if (gameOver && total == maxTotal) {
                cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best))
            }
            row.addView(cell)
        }
        return row
    }

    // ─── Score input dialog ────────────────────────────────────────────────────

    private fun showHandScoreInput(round: EscobaRound, player: EscobaPlayerState) {
        val current = round.handScores[player.playerId]

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0–$MAX_HAND_SCORE"
            gravity = Gravity.CENTER
            textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(2))
            current?.let { setText(it.toString()) }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("${player.playerName} — ${getString(R.string.escoba_hand_score)}")
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 0 || value > MAX_HAND_SCORE) {
                    showHandScoreInput(round, player)
                    return@setPositiveButton
                }
                round.handScores[player.playerId] = value
                buildTable()
                checkGameOver()

                if (round.isComplete(players.map { it.playerId })) {
                    checkEndOfGame()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        editText.requestFocus()
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    private fun checkGameOver() {
        if (gameOver) return
        val totals = players.associate { it.playerId to it.getTotal(rounds) }
        if (totals.values.none { it >= SCORE_LIMIT }) return
        // Game over only triggered at end of round (after isComplete), not mid-round
    }

    private fun checkEndOfGame() {
        val maxTotal = players.maxOf { it.getTotal(rounds) }
        if (maxTotal >= SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.escoba_game_over_title))
                .setMessage(getString(R.string.escoba_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true
                    buildTable()
                    saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    val newRound = EscobaRound(rounds.size + 1)
                    players.forEach { newRound.inPlayScores[it.playerId] = 0 }
                    rounds.add(newRound)
                    buildTable()
                }
                .show()
        } else {
            val newRound = EscobaRound(rounds.size + 1)
            players.forEach { newRound.inPlayScores[it.playerId] = 0 }
            rounds.add(newRound)
            buildTable()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals = players.associate { it to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1

        lifecycleScope.launch {
            val results = players.map { player ->
                GameResult(
                    gameType = GAME_TYPE,
                    playerId = player.playerId,
                    playerName = player.playerName,
                    score = player.getTotal(rounds),
                    isWinner = !isDraw && player in winners,
                    isDraw = isDraw && player in winners
                )
            }
            database.gameResultDao().insertGameResults(results)
            showSummaryDialog(totals, isDraw)
        }
    }

    private fun showSummaryDialog(totals: Map<EscobaPlayerState, Int>, isDraw: Boolean) {
        val sorted = totals.entries.sortedByDescending { it.value }
        var currentRank = 1
        val entries = sorted.mapIndexed { index, (player, score) ->
            val rank = if (index > 0 && score == sorted[index - 1].value) currentRank
            else { currentRank = index + 1; currentRank }
            GameResultsDialog.PlayerResult(player.playerName, player.playerColor, score, rank)
        }
        GameResultsDialog.show(this, entries, isDraw, " pts") { finish() }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeFixedRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(ROW_HEIGHT_DP)
        )
    }

    private fun makeRoundLabelCell(text: String): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(32), LinearLayout.LayoutParams.MATCH_PARENT)
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameHeaderCell(name: String, color: Int, weight: Float): TextView =
        TextView(this).apply {
            text = name
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            background = cellDrawable(color)
            setTextColor(Color.WHITE)
        }

    private fun makeSubHeaderCell(label: String, weight: Float = 1f): TextView = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 9f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_text))
    }

    /**
     * In-play cell: [−] [score] [+]
     * Wider (weight 1.5) to provide larger tap targets.
     */
    private fun makeInPlayCell(
        score: Int,
        scoreColor: EscobaScoreColor,
        canEdit: Boolean,
        onDecrement: () -> Unit,
        onIncrement: () -> Unit
    ): LinearLayout {
        val resolvedColor = resolveScoreColor(scoreColor)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
            background = cellDrawable(
                ContextCompat.getColor(this@EscobaGameActivity, R.color.score_cell_background)
            )
        }

        val btnMinus = TextView(this).apply {
            text = "−"
            gravity = Gravity.CENTER
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
            setTextColor(resolvedColor)
            alpha = if (canEdit) 1f else 0.25f
            if (canEdit) setOnClickListener { onDecrement() }
        }

        val scoreText = TextView(this).apply {
            text = score.toString()
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setTextColor(resolvedColor)
            alpha = if (canEdit) 1f else 0.65f
        }

        val btnPlus = TextView(this).apply {
            text = "+"
            gravity = Gravity.CENTER
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
            setTextColor(resolvedColor)
            alpha = if (canEdit) 1f else 0.25f
            if (canEdit) setOnClickListener { onIncrement() }
        }

        container.addView(btnMinus)
        container.addView(scoreText)
        container.addView(btnPlus)
        return container
    }

    private fun makeHandCell(
        text: String,
        textColor: Int,
        bgColor: Int,
        canEdit: Boolean
    ): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 14f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        setTextColor(textColor)
        background = cellDrawable(bgColor)
        if (!canEdit && text.isNotEmpty()) alpha = 0.75f
    }

    private fun makeTotalCell(text: String, weight: Float = 1f): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(
            ContextCompat.getColor(this@EscobaGameActivity, R.color.yahtzee_calculated_cell_background)
        )
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.yahtzee_calculated_cell_text))
    }

    private fun resolveScoreColor(color: EscobaScoreColor): Int = when (color) {
        EscobaScoreColor.BEST -> ContextCompat.getColor(this, R.color.score_text_best)
        EscobaScoreColor.WORST -> ContextCompat.getColor(this, R.color.score_text_worst)
        EscobaScoreColor.NEUTRAL -> ContextCompat.getColor(this, R.color.score_cell_text)
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@EscobaGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

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
            else -> super.onOptionsItemSelected(item)
        }
    }
}

enum class EscobaScoreColor { BEST, WORST, NEUTRAL }
