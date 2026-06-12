package com.github.trivialloop.scorehub.games.ohhell

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
import com.github.trivialloop.scorehub.databinding.ActivityOhHellGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class OhHellGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOhHellGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<OhHellPlayerState>

    private val rounds = mutableListOf<OhHellRound>()
    private var totalRounds = 0

    /**
     * Index (in the original players list) of the player whose turn it currently is.
     * During contract phase: the next player to bid.
     * During result phase: the next player to declare their result.
     */
    private var currentPlayerIndex = 0
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "oh_hell"
        private const val LABEL_COL_DP = 65
    }

    private val numPlayers get() = players.size

    // ── Visible players window (active player centred, slightly wider) ────────

    private fun getVisiblePlayers(): List<Pair<Int, OhHellPlayerState>> = when {
        numPlayers <= 5 -> players.indices.map { it to players[it] }
        else -> {
            val prev2 = (currentPlayerIndex - 2 + numPlayers) % numPlayers
            val prev1 = (currentPlayerIndex - 1 + numPlayers) % numPlayers
            val next1 = (currentPlayerIndex + 1) % numPlayers
            val next2 = (currentPlayerIndex + 2) % numPlayers
            listOf(
                prev2 to players[prev2],
                prev1 to players[prev1],
                currentPlayerIndex to players[currentPlayerIndex],
                next1 to players[next1],
                next2 to players[next2]
            )
        }
    }

    private fun columnWeight(isActive: Boolean): Float = when {
        numPlayers == 1 -> 1f
        numPlayers == 2 -> if (isActive) 0.8f else 0.2f
        numPlayers <= 5 -> if (isActive) 0.5f else 0.15f
        else -> if (isActive) 0.4f else 0.13f
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOhHellGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

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

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        players = playerIds.indices.map { i ->
            OhHellPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }
        totalRounds = totalRoundsForPlayers(numPlayers)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.ohhell_game)

        startNextRound()
        buildTable()
    }

    // ── Round management ──────────────────────────────────────────────────────

    private fun startNextRound() {
        val roundNumber = rounds.size + 1
        val maxCards = maxCardsForRound(roundNumber, totalRounds)
        val startIdx = startPlayerIndexForRound(roundNumber, numPlayers)
        rounds.add(OhHellRound(roundNumber, maxCards, startIdx))
        // First to bid is the start player of this round
        currentPlayerIndex = startIdx
    }

    // ── Table: fixed header + scrollable rounds + fixed total ─────────────────

    private fun buildTable() {
        val visible = getVisiblePlayers()

        // ── Header ────────────────────────────────────────────────────────────
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(buildHeaderRow(visible))

        // ── Scrollable rounds + total ─────────────────────────────────────────
        binding.tableContainer.removeAllViews()
        rounds.forEachIndexed { idx, round ->
            binding.tableContainer.addView(buildRoundBlock(round, visible, isLast = idx == rounds.lastIndex))
        }
        binding.tableContainer.addView(buildTotalRow(visible))

        binding.scrollView.post { binding.scrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
    }

    // ── Header row ────────────────────────────────────────────────────────────

    private fun buildHeaderRow(visible: List<Pair<Int, OhHellPlayerState>>): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell("", isTotal = false))
        for ((idx, player) in visible) {
            val isActive = idx == currentPlayerIndex
            row.addView(makePlayerNameCell(player, isActive, columnWeight(isActive)))
        }
        return row
    }

    // ── Round block (contract row + result row) ───────────────────────────────

    private fun buildRoundBlock(
        round: OhHellRound,
        visible: List<Pair<Int, OhHellPlayerState>>,
        isLast: Boolean
    ): LinearLayout {
        val playerIdList = players.map { it.playerId }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // The label cell is tinted with the last bidder's color
        val lastBidderIdx = round.lastBidderIndex(numPlayers)
        val lastBidderColor = players[lastBidderIdx].playerColor

        // ── Contract row ──────────────────────────────────────────────────────
        val contractRow = makeRow()
        contractRow.addView(makeRoundLabelCell(round.roundNumber, round.maxCards, lastBidderColor))

        val contractPhaseActive = isLast && !round.isContractPhaseComplete(playerIdList)
        // Bidding order for this round
        val biddingOrder = biddingOrderForRound(round.roundNumber, numPlayers)
        // Index in biddingOrder of the current player (used to detect "previous" player)
        val currentBidPosition = if (contractPhaseActive)
            biddingOrder.indexOf(currentPlayerIndex)
        else -1
        val prevBidderIdx = if (currentBidPosition > 0)
            biddingOrder[currentBidPosition - 1]
        else -1

        for ((_, pair) in visible.withIndex()) {
            val (idx, player) = pair
            val isActive = idx == currentPlayerIndex
            val w = columnWeight(isActive)
            val contract = round.contracts[player.playerId]
            val playerIndex = players.indexOf(player)

            val isMyContractTurn = contractPhaseActive && idx == currentPlayerIndex && contract == null
            val canEditPrev = contractPhaseActive && idx == prevBidderIdx && contract != null

            val bgColor = when {
                isMyContractTurn -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                canEditPrev -> ContextCompat.getColor(this, R.color.cell_editable_filled_bg)
                else -> ContextCompat.getColor(this, R.color.score_cell_background)
            }

            val cell = makeScoreCell(contract?.toString() ?: "", w, bgColor)
            when {
                isMyContractTurn -> cell.setOnClickListener {
                    showContractPicker(round, player, playerIndex)
                }
                canEditPrev -> cell.setOnClickListener {
                    showContractPicker(round, player, playerIndex, isEdit = true)
                }
            }
            contractRow.addView(cell)
        }
        container.addView(contractRow)

        // ── Result row ────────────────────────────────────────────────────────
        val resultRow = makeRow()
        resultRow.addView(makeRoundLabelCell(round.roundNumber, round.maxCards, lastBidderColor, subRow = true))

        val contractDone = round.isContractPhaseComplete(playerIdList)
        val resultPhaseActive = isLast && contractDone && !round.isComplete(playerIdList)
        val currentResultPosition = if (resultPhaseActive)
            biddingOrder.indexOf(currentPlayerIndex)
        else -1
        val prevResultIdx = if (currentResultPosition > 0)
            biddingOrder[currentResultPosition - 1]
        else -1

        val allScores = visible.map { (_, p) -> round.getScore(p.playerId) }

        for ((colIdx, pair) in visible.withIndex()) {
            val (idx, player) = pair
            val isActive = idx == currentPlayerIndex
            val w = columnWeight(isActive)
            val result = round.results[player.playerId]
            val score = round.getScore(player.playerId)

            val isMyResultTurn = resultPhaseActive && idx == currentPlayerIndex && result == null
            val canEditPrevResult = resultPhaseActive && idx == prevResultIdx && result != null

            val role = ScoreColorRole(score, allScores, higherIsBetter = true)

            val bgColor = when {
                isMyResultTurn -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                canEditPrevResult -> ContextCompat.getColor(this, R.color.cell_editable_filled_bg)
                else -> ContextCompat.getColor(this, R.color.score_cell_background)
            }

            val cell = makeTwoLineResultCell(
                emojiText = round.getResultLabel(player.playerId),
                scoreText = round.getScoreLabel(player.playerId),
                weight = w,
                bgColor = bgColor,
                role = role
            )
            when {
                isMyResultTurn -> cell.setOnClickListener { showResultPicker(round, player) }
                canEditPrevResult -> cell.setOnClickListener {
                    showResultPicker(round, player, isEdit = true)
                }
            }
            resultRow.addView(cell)
        }
        container.addView(resultRow)

        return container
    }

    // ── Total row ─────────────────────────────────────────────────────────────

    private fun buildTotalRow(visible: List<Pair<Int, OhHellPlayerState>>): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.ohhell_total), isTotal = true))

        val allTotals = visible.map { (_, p) -> p.getTotal(rounds) }

        for ((colIdx, pair) in visible.withIndex()) {
            val (idx, player) = pair
            val isActive = idx == currentPlayerIndex
            val w = columnWeight(isActive)
            val total = player.getTotal(rounds)

            val cell = makeScoreCell(
                text = total.toString(),
                weight = w,
                bgColor = ContextCompat.getColor(this, R.color.cell_calculated_bg),
                bold = true
            )
            cell.setTextColor(ContextCompat.getColor(this, R.color.score_calculated_cell_text))

            if (gameOver) {
                val role = ScoreColorRole(total, allTotals, higherIsBetter = true)
                if (role != ScoreColorRole.NEUTRAL) cell.setTextColor(role.toColor(this))
            }
            row.addView(cell)
        }
        return row
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showContractPicker(
        round: OhHellRound,
        player: OhHellPlayerState,
        playerIndex: Int,
        isEdit: Boolean = false
    ) {
        val playerIdList = players.map { it.playerId }
        val allowed = round.allowedContracts(playerIndex, playerIdList)
        val forbidden = if (playerIndex == round.lastBidderIndex(numPlayers))
            round.forbiddenContractForLastBidder(playerIdList)
        else null

        val title = if (isEdit)
            "✏️ ${player.playerName} — ${getString(R.string.ohhell_choose_contract)} (${getString(R.string.ohhell_max_cards, round.maxCards)})"
        else
            "${player.playerName} — ${getString(R.string.ohhell_choose_contract)} (${getString(R.string.ohhell_max_cards, round.maxCards)})"

        val items = allowed.map { v ->
            if (forbidden != null && v == forbidden) "✗ $v" else "$v"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                round.contracts[player.playerId] = allowed[which]
                if (!isEdit) advanceContractPhase(round, playerIndex)
                buildTable()
            }
            .show()
    }

    private fun advanceContractPhase(round: OhHellRound, justBidPlayerIndex: Int) {
        val biddingOrder = biddingOrderForRound(round.roundNumber, numPlayers)
        val posInOrder = biddingOrder.indexOf(justBidPlayerIndex)
        if (posInOrder < biddingOrder.lastIndex) {
            // Move to next player in bidding order
            currentPlayerIndex = biddingOrder[posInOrder + 1]
        } else {
            // All contracts done → start result phase from first bidder
            currentPlayerIndex = biddingOrder[0]
        }
    }

    private fun showResultPicker(
        round: OhHellRound,
        player: OhHellPlayerState,
        isEdit: Boolean = false
    ) {
        val title = if (isEdit)
            "✏️ ${player.playerName} — ${getString(R.string.ohhell_choose_result)}"
        else
            "${player.playerName} — ${getString(R.string.ohhell_choose_result)}"

        // 0 = ✅, 1..maxCards = repeated ❌ (never compact)
        val items = buildList {
            add("✅")
            for (n in 1..round.maxCards) add("❌".repeat(n))
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                round.results[player.playerId] = which
                if (!isEdit) advanceResultPhase(round, players.indexOf(player))
                buildTable()
            }
            .show()
    }

    private fun advanceResultPhase(round: OhHellRound, justDonePlayerIndex: Int) {
        val playerIdList = players.map { it.playerId }
        val biddingOrder = biddingOrderForRound(round.roundNumber, numPlayers)
        val posInOrder = biddingOrder.indexOf(justDonePlayerIndex)
        if (posInOrder < biddingOrder.lastIndex) {
            currentPlayerIndex = biddingOrder[posInOrder + 1]
        } else {
            // Round complete
            if (round.isComplete(playerIdList)) onRoundComplete()
        }
    }

    // ── Game logic ────────────────────────────────────────────────────────────

    private fun onRoundComplete() {
        if (rounds.size >= totalRounds) {
            gameOver = true
            buildTable()
            saveResultsAndShowSummary()
        } else {
            startNextRound()
            buildTable()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals = players.associate { it to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType = GAME_TYPE,
                    playerId = player.playerId,
                    playerName = player.playerName,
                    score = player.getTotal(rounds),
                    isWinner = !isDraw && player in winners,
                    isDraw = isDraw && player in winners
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@OhHellGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * Label cell for the left column.
     * For round rows: shows "N\n(M)" (round number on first line, max cards in parentheses below).
     * For the sub-row (result row): shows only a blank tinted cell.
     * For total: shows the "Total" label.
     */
    private fun makeRoundLabelCell(
        roundNumber: Int,
        maxCards: Int,
        tintColor: Int,
        subRow: Boolean = false
    ): TextView = TextView(this).apply {
        text = if (subRow) "" else "$roundNumber\n($maxCards)"
        gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
        setPadding(dpToPx(2), dpToPx(6), dpToPx(2), dpToPx(6))
        layoutParams = LinearLayout.LayoutParams(
            dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT
        )
        background = cellDrawable(tintColor)
        setTextColor(Color.WHITE)
    }

    private fun makeLabelCell(text: String, isTotal: Boolean): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
        setPadding(dpToPx(2), dpToPx(8), dpToPx(2), dpToPx(8))
        layoutParams = LinearLayout.LayoutParams(
            dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT
        )
        val bgRes = if (isTotal) R.color.cell_calculated_bg else R.color.header_cell_background
        val fgRes = if (isTotal) R.color.score_calculated_cell_text else R.color.header_cell_text
        background = cellDrawable(ContextCompat.getColor(this@OhHellGameActivity, bgRes))
        setTextColor(ContextCompat.getColor(this@OhHellGameActivity, fgRes))
    }

    private fun makePlayerNameCell(
        player: OhHellPlayerState,
        isActive: Boolean,
        weight: Float
    ): TextView = TextView(this).apply {
        text = player.playerName; gravity = Gravity.CENTER
        textSize = if (isActive) 13f else 11f
        setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        setPadding(dpToPx(2), dpToPx(8), dpToPx(2), dpToPx(8))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(player.playerColor)
        setTextColor(Color.WHITE)
    }

    private fun makeScoreCell(
        text: String,
        weight: Float,
        bgColor: Int,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 13f
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(dpToPx(2), dpToPx(8), dpToPx(2), dpToPx(8))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(bgColor)
        setTextColor(ContextCompat.getColor(this@OhHellGameActivity, R.color.score_cell_text))
    }

    /**
     * Two-line result cell:
     *   Line 1: result emoji (✅ / ❌❌❌ …)
     *   Line 2: score delta (+11, −4 …)
     */
    private fun makeTwoLineResultCell(
        emojiText: String,
        scoreText: String,
        weight: Float,
        bgColor: Int,
        role: ScoreColorRole
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(bgColor)

        val scoreColor = when (role) {
            ScoreColorRole.BEST ->
                ContextCompat.getColor(this@OhHellGameActivity, R.color.score_text_best)
            ScoreColorRole.WORST ->
                ContextCompat.getColor(this@OhHellGameActivity, R.color.score_text_worst)
            else ->
                ContextCompat.getColor(this@OhHellGameActivity, R.color.score_cell_text)
        }

        addView(TextView(this@OhHellGameActivity).apply {
            text = emojiText; gravity = Gravity.CENTER; textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(ContextCompat.getColor(this@OhHellGameActivity, R.color.score_cell_text))
            setPadding(dpToPx(1), dpToPx(4), dpToPx(1), 0)
        })
        addView(TextView(this@OhHellGameActivity).apply {
            text = scoreText.ifEmpty { " " }
            gravity = Gravity.CENTER; textSize = 10f
            setTypeface(null, if (scoreText.isNotEmpty()) Typeface.BOLD else Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(if (scoreText.isNotEmpty()) scoreColor
                         else ContextCompat.getColor(this@OhHellGameActivity, R.color.score_cell_text))
            setPadding(dpToPx(1), 0, dpToPx(1), dpToPx(4))
        })
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@OhHellGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_oh_hell_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.ohhell_quit_game)
                    .setMessage(R.string.ohhell_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
