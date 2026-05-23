package com.github.trivialloop.scorehub.games.farkle

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
import android.widget.ScrollView
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
import com.github.trivialloop.scorehub.databinding.ActivityFarkleGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class FarkleGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFarkleGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<FarklePlayerState>

    private val rounds = mutableListOf<FarkleRound>()

    private var currentPlayerIndex = 0
    private var gameOver = false
    private var finalRoundStarted = false

    companion object {

        const val GAME_TYPE = "farkle"

        private const val SCORE_LIMIT   = 10_000
        private const val HEADER_ROW_DP = 52
        private const val ROUND_ROW_DP  = 48
        private const val ENTRY_ROW_DP  = 36
        private const val SUM_ROW_DP    = 28
        private const val BTN_ROW_DP    = 44
        private const val TOTAL_ROW_DP  = 52
        private const val LABEL_COL_DP  = 48

        val ALL_COMBINATIONS = listOf(

            // 1 die
            RollEntry(100, 1, "⚀"),
            RollEntry(50, 1, "⚄"),

            // 2 dice
            RollEntry(200, 2, "⚀ ⚀"),
            RollEntry(100, 2, "⚄ ⚄"),
            RollEntry(150, 2, "⚀ ⚄"),

            // 3 dice
            RollEntry(1000, 3, "⚀ ⚀ ⚀"),
            RollEntry(200, 3, "⚁ ⚁ ⚁"),
            RollEntry(300, 3, "⚂ ⚂ ⚂"),
            RollEntry(400, 3, "⚃ ⚃ ⚃"),
            RollEntry(500, 3, "⚄ ⚄ ⚄"),
            RollEntry(600, 3, "⚅ ⚅ ⚅"),

            RollEntry(250, 3, "⚀ ⚀ ⚄"),
            RollEntry(200, 3, "⚀ ⚄ ⚄"),

            // 4 dice
            RollEntry(2000, 4, "⚀ ⚀ ⚀ ⚀"),
            RollEntry(400, 4, "⚁ ⚁ ⚁ ⚁"),
            RollEntry(600, 4, "⚂ ⚂ ⚂ ⚂"),
            RollEntry(800, 4, "⚃ ⚃ ⚃ ⚃"),
            RollEntry(1000, 4, "⚄ ⚄ ⚄ ⚄"),
            RollEntry(1200, 4, "⚅ ⚅ ⚅ ⚅"),

            // 5 dice
            RollEntry(3000, 5, "⚀ ⚀ ⚀ ⚀ ⚀"),
            RollEntry(600, 5, "⚁ ⚁ ⚁ ⚁ ⚁"),
            RollEntry(900, 5, "⚂ ⚂ ⚂ ⚂ ⚂"),
            RollEntry(1200, 5, "⚃ ⚃ ⚃ ⚃ ⚃"),
            RollEntry(1500, 5, "⚄ ⚄ ⚄ ⚄ ⚄"),
            RollEntry(1800, 5, "⚅ ⚅ ⚅ ⚅ ⚅"),

            // 6 dice
            RollEntry(4000, 6, "⚀ ⚀ ⚀ ⚀ ⚀ ⚀"),
            RollEntry(800, 6, "⚁ ⚁ ⚁ ⚁ ⚁ ⚁"),
            RollEntry(1200, 6, "⚂ ⚂ ⚂ ⚂ ⚂ ⚂"),
            RollEntry(1600, 6, "⚃ ⚃ ⚃ ⚃ ⚃ ⚃"),
            RollEntry(2000, 6, "⚄ ⚄ ⚄ ⚄ ⚄ ⚄"),
            RollEntry(2400, 6, "⚅ ⚅ ⚅ ⚅ ⚅ ⚅"),

            // Special
            RollEntry(750, 6, "⚀ ⚀ ⚁ ⚁ ⚂ ⚂"),
            RollEntry(1500, 6, "⚀ ⚁ ⚂ ⚃ ⚄ ⚅")
        )
    }

    private val totalPlayers get() = players.size

    private fun getVisiblePlayers(): List<Pair<Int, FarklePlayerState>> = when {
        totalPlayers <= 3 -> players.indices.map { it to players[it] }

        else -> {

            val prev =
                if (currentPlayerIndex == 0)
                    totalPlayers - 1
                else
                    currentPlayerIndex - 1

            val next =
                if (currentPlayerIndex == totalPlayers - 1)
                    0
                else
                    currentPlayerIndex + 1

            listOf(
                prev to players[prev],
                currentPlayerIndex to players[currentPlayerIndex],
                next to players[next]
            )
        }
    }

    private fun columnWeight(isActive: Boolean): Float = when {
        totalPlayers == 1 -> 1f
        totalPlayers == 2 -> if (isActive) 0.8f else 0.2f
        else -> if (isActive) 0.6f else 0.2f
    }

    override fun attachBaseContext(newBase: Context) {

        val language = LocaleHelper.getPersistedLocale(newBase)

        super.attachBaseContext(
            LocaleHelper.setLocale(newBase, language)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityFarkleGameBinding.inflate(layoutInflater)

        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val statusBarInsets =
                insets.getInsets(WindowInsetsCompat.Type.statusBars())

            binding.appBarLayout.setPadding(
                0,
                statusBarInsets.top,
                0,
                0
            )

            insets
        }

        database = AppDatabase.getDatabase(this)

        playerIds =
            intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()

        playerNames =
            intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()

        playerColors =
            intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        players = playerIds.indices.map { i ->

            FarklePlayerState(
                playerIds[i],
                playerNames[i],
                playerColors[i]
            )
        }

        setSupportActionBar(binding.toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportActionBar?.title =
            getString(R.string.farkle_game)

        startTurnFor(currentPlayerIndex)

        buildTable()
    }

    private fun startTurnFor(playerIndex: Int) {

        val player = players[playerIndex]

        val roundNumber =
            rounds.count {
                it.playerId == player.playerId && it.isComplete
            } + 1

        rounds.add(
            FarkleRound(
                roundNumber = roundNumber,
                playerId = player.playerId
            )
        )
    }

    private fun getRemainingDice(turn: FarkleRound): Int {

        val usedDice =
            turn.rollEntries.sumOf {
                it.usedDice
            }

        val remaining = 6 - usedDice

        return if (remaining <= 0) 6 else remaining
    }

    private fun buildTable() {

        binding.scoreTableContainer.removeAllViews()

        val visible = getVisiblePlayers()

        val completedByPlayer: Map<Long, List<FarkleRound>> =
            visible.associate { (_, p) ->

                p.playerId to rounds.filter {
                    it.playerId == p.playerId && it.isComplete
                }
            }

        val activeTurn: FarkleRound? =
            rounds.lastOrNull {
                it.playerId == players[currentPlayerIndex].playerId &&
                        !it.isComplete
            }

        val activeSlot =
            completedByPlayer[players[currentPlayerIndex].playerId]?.size ?: 0

        val maxCompleted =
            completedByPlayer.values.maxOfOrNull {
                it.size
            } ?: 0

        val totalSlots =
            maxOf(maxCompleted, activeSlot + 1)

        val allTotals =
            visible.map { (_, p) ->
                p.getTotal(rounds)
            }

        binding.scoreTableContainer.addView(
            buildHeaderRow(visible)
        )

        for (slotIdx in 0 until totalSlots) {

            val isActiveSlot = slotIdx == activeSlot

            val slotScores: List<Int?> =
                visible.map { (_, p) ->
                    completedByPlayer[p.playerId]
                        ?.getOrNull(slotIdx)
                        ?.score
                }

            binding.scoreTableContainer.addView(
                buildSlotRow(
                    visible,
                    completedByPlayer,
                    slotIdx,
                    isActiveSlot,
                    activeTurn,
                    slotScores
                )
            )
        }

        binding.scoreTableContainer.addView(
            buildTotalRow(
                visible,
                allTotals
            )
        )

        binding.scrollView.post {
            binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun buildHeaderRow(
        visible: List<Pair<Int, FarklePlayerState>>
    ): LinearLayout {

        val row = makeRow(HEADER_ROW_DP)

        row.addView(
            makeLabelCell(
                "",
                HEADER_ROW_DP,
                false
            )
        )

        for ((idx, player) in visible) {

            row.addView(
                makePlayerNameCell(
                    player,
                    idx == currentPlayerIndex,
                    columnWeight(idx == currentPlayerIndex)
                )
            )
        }

        return row
    }

    private fun buildSlotRow(
        visible: List<Pair<Int, FarklePlayerState>>,
        completedByPlayer: Map<Long, List<FarkleRound>>,
        slotIdx: Int,
        isActiveSlot: Boolean,
        activeTurn: FarkleRound?,
        slotScores: List<Int?>
    ): LinearLayout {

        val row = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
        }

        row.addView(
            makeLabelCellMatchParent(
                (slotIdx + 1).toString(),
                false
            )
        )

        for ((colIdx, pair) in visible.withIndex()) {

            val (idx, player) = pair

            val isActive = idx == currentPlayerIndex

            val w = columnWeight(isActive)

            when {

                isActiveSlot &&
                        isActive &&
                        activeTurn != null -> {

                    row.addView(
                        makeActiveTurnCell(activeTurn, w)
                    )
                }

                completedByPlayer[player.playerId]
                    ?.getOrNull(slotIdx) != null -> {

                    val round =
                        completedByPlayer[player.playerId]
                            ?.getOrNull(slotIdx)!!

                    val role =
                        ScoreColorRole(
                            slotScores[colIdx],
                            slotScores,
                            higherIsBetter = true
                        )

                    val textColor =
                        when (role) {

                            ScoreColorRole.BEST ->
                                ContextCompat.getColor(
                                    this,
                                    R.color.score_text_best
                                )

                            ScoreColorRole.WORST ->
                                ContextCompat.getColor(
                                    this,
                                    R.color.score_text_worst
                                )

                            else ->
                                ContextCompat.getColor(
                                    this,
                                    R.color.score_cell_text
                                )
                        }

                    row.addView(
                        makeCompletedRoundCell(
                            round,
                            textColor,
                            isActive,
                            w
                        )
                    )
                }

                else -> {

                    row.addView(
                        makePlaceholderCell(w)
                    )
                }
            }
        }

        return row
    }

    private fun buildTotalRow(
        visible: List<Pair<Int, FarklePlayerState>>,
        allTotals: List<Int>
    ): LinearLayout {

        val row = makeRow(TOTAL_ROW_DP)

        row.addView(
            makeLabelCell(
                getString(R.string.farkle_total),
                TOTAL_ROW_DP,
                true
            )
        )

        for ((colIdx, pair) in visible.withIndex()) {

            val (idx, _) = pair

            val isActive = idx == currentPlayerIndex

            val total = allTotals[colIdx]

            val textColor =
                ContextCompat.getColor(
                    this,
                    R.color.score_calculated_cell_text
                )

            row.addView(
                makeTotalCell(
                    total,
                    textColor,
                    isActive,
                    columnWeight(isActive)
                )
            )
        }

        return row
    }

    private fun makeRow(heightDp: Int): LinearLayout =
        LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(heightDp)
                )
        }

    private fun colLp(weight: Float) =
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            weight
        )

    private fun fixedColLp(weight: Float, h: Int) =
        LinearLayout.LayoutParams(
            0,
            dpToPx(h),
            weight
        )

    private fun makeLabelCell(
        text: String,
        heightDp: Int,
        isTotal: Boolean
    ): TextView = TextView(this).apply {

        this.text = text

        gravity = Gravity.CENTER

        textSize = 11f

        setTypeface(null, Typeface.BOLD)

        layoutParams =
            LinearLayout.LayoutParams(
                dpToPx(LABEL_COL_DP),
                dpToPx(heightDp)
            )

        val bg =
            if (isTotal)
                R.color.cell_calculated_bg
            else
                R.color.header_cell_background

        val fg =
            if (isTotal)
                R.color.score_calculated_cell_text
            else
                R.color.header_cell_text

        background =
            cellDrawable(
                ContextCompat.getColor(
                    this@FarkleGameActivity,
                    bg
                )
            )

        setTextColor(
            ContextCompat.getColor(
                this@FarkleGameActivity,
                fg
            )
        )
    }

    private fun makeLabelCellMatchParent(
        text: String,
        isTotal: Boolean
    ): TextView = TextView(this).apply {

        this.text = text

        gravity = Gravity.CENTER

        textSize = 11f

        setTypeface(null, Typeface.BOLD)

        layoutParams =
            LinearLayout.LayoutParams(
                dpToPx(LABEL_COL_DP),
                LinearLayout.LayoutParams.MATCH_PARENT
            )

        val bg =
            if (isTotal)
                R.color.cell_calculated_bg
            else
                R.color.header_cell_background

        val fg =
            if (isTotal)
                R.color.score_calculated_cell_text
            else
                R.color.header_cell_text

        background =
            cellDrawable(
                ContextCompat.getColor(
                    this@FarkleGameActivity,
                    bg
                )
            )

        setTextColor(
            ContextCompat.getColor(
                this@FarkleGameActivity,
                fg
            )
        )
    }

    private fun makePlayerNameCell(
        player: FarklePlayerState,
        isActive: Boolean,
        weight: Float
    ): TextView = TextView(this).apply {

        text = player.playerName

        gravity = Gravity.CENTER

        textSize = 13f

        setTypeface(null, Typeface.BOLD)

        maxLines = 1

        ellipsize = TextUtils.TruncateAt.END

        layoutParams = fixedColLp(weight, HEADER_ROW_DP)

        background = cellDrawable(player.playerColor)

        setTextColor(Color.WHITE)
    }

    private fun makeCompletedRoundCell(
        round: FarkleRound,
        textColor: Int,
        isActive: Boolean,
        weight: Float
    ): TextView = TextView(this).apply {

        text = round.score.toString()

        gravity = Gravity.CENTER

        textSize = 14f

        setTypeface(null, Typeface.BOLD)

        layoutParams = colLp(weight)

        background =
            cellDrawable(
                ContextCompat.getColor(
                    this@FarkleGameActivity,
                    R.color.score_cell_background
                )
            )

        setTextColor(textColor)

        alpha = if (isActive) 1f else 0.65f

        minimumHeight = dpToPx(ROUND_ROW_DP)
    }

    private fun makePlaceholderCell(weight: Float): TextView =
        TextView(this).apply {

            text = ""

            layoutParams = colLp(weight)

            background =
                cellDrawable(
                    ContextCompat.getColor(
                        this@FarkleGameActivity,
                        R.color.score_cell_background
                    )
                )

            minimumHeight = dpToPx(ROUND_ROW_DP)
        }

    private fun makeActiveTurnCell(
        turn: FarkleRound,
        weight: Float
    ): LinearLayout =
        LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    weight
                )

            background =
                cellDrawable(
                    ContextCompat.getColor(
                        this@FarkleGameActivity,
                        R.color.cell_editable_bg
                    )
                )

            for ((index, entry) in turn.rollEntries.withIndex()) {

                val isLast =
                    index == turn.rollEntries.lastIndex

                addView(
                    TextView(this@FarkleGameActivity).apply {

                        text = entry.score.toString()

                        gravity = Gravity.CENTER

                        textSize = 14f

                        layoutParams =
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dpToPx(ENTRY_ROW_DP)
                            )

                        if (isLast) {

                            background =
                                cellDrawable(
                                    ContextCompat.getColor(
                                        this@FarkleGameActivity,
                                        R.color.cell_editable_filled_bg
                                    )
                                )

                            setTypeface(null, Typeface.BOLD)

                            setOnClickListener {
                                showEditEntryDialog(
                                    turn,
                                    index
                                )
                            }

                        } else {

                            background =
                                cellDrawable(
                                    ContextCompat.getColor(
                                        this@FarkleGameActivity,
                                        R.color.score_cell_background
                                    )
                                )
                        }

                        setTextColor(
                            ContextCompat.getColor(
                                this@FarkleGameActivity,
                                R.color.score_cell_text
                            )
                        )
                    }
                )
            }

            if (turn.rollEntries.isNotEmpty()) {

                addView(
                    TextView(this@FarkleGameActivity).apply {

                        text = "= ${turn.entrySum}"

                        gravity = Gravity.CENTER

                        textSize = 12f

                        setTypeface(null, Typeface.BOLD)

                        layoutParams =
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dpToPx(SUM_ROW_DP)
                            )

                        background =
                            cellDrawable(
                                ContextCompat.getColor(
                                    this@FarkleGameActivity,
                                    R.color.header_cell_background
                                )
                            )

                        setTextColor(
                            ContextCompat.getColor(
                                this@FarkleGameActivity,
                                R.color.header_cell_text
                            )
                        )
                    }
                )
            }

            addView(
                LinearLayout(this@FarkleGameActivity).apply {

                    orientation = LinearLayout.HORIZONTAL

                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dpToPx(BTN_ROW_DP)
                        )

                    addView(
                        makeActionButton(
                            getString(R.string.farkle_btn_add),
                            false,
                            true
                        ) {
                            showAddScoreDialog(turn)
                        }
                    )

                    addView(
                        makeActionButton(
                            getString(R.string.farkle_btn_bank),
                            false,
                            false
                        ) {
                            if (turn.rollEntries.isEmpty())
                                performFarkle(turn)
                            else
                                performBank(turn)
                        }
                    )

                    addView(
                        makeActionButton(
                            getString(R.string.farkle_btn_farkle),
                            true,
                            false
                        ) {
                            performFarkle(turn)
                        }
                    )
                }
            )
        }

    private fun makeActionButton(
        label: String,
        isFarkle: Boolean,
        isAdd: Boolean,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {

        text = label

        gravity = Gravity.CENTER

        textSize = 11f

        setTypeface(null, Typeface.BOLD)

        layoutParams =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )

        val bgColor =
            when {

                isFarkle ->
                    ContextCompat.getColor(
                        this@FarkleGameActivity,
                        R.color.score_text_worst
                    )

                isAdd ->
                    ContextCompat.getColor(
                        this@FarkleGameActivity,
                        R.color.cell_editable_bg
                    )

                else ->
                    ContextCompat.getColor(
                        this@FarkleGameActivity,
                        R.color.score_text_best
                    )
            }

        background = cellDrawable(bgColor)

        setTextColor(
            if (isAdd)
                ContextCompat.getColor(
                    this@FarkleGameActivity,
                    R.color.score_cell_text
                )
            else
                Color.WHITE
        )

        setOnClickListener {
            onClick()
        }
    }

    private fun makeTotalCell(
        total: Int,
        textColor: Int,
        isActive: Boolean,
        weight: Float
    ): TextView = TextView(this).apply {

        text = total.toString()

        gravity = Gravity.CENTER

        textSize = 16f

        setTypeface(null, Typeface.BOLD)

        layoutParams =
            fixedColLp(weight, TOTAL_ROW_DP)

        background =
            cellDrawable(
                ContextCompat.getColor(
                    this@FarkleGameActivity,
                    R.color.cell_calculated_bg
                )
            )

        setTextColor(textColor)

        alpha = if (isActive) 1f else 0.75f
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable =
        GradientDrawable().apply {

            setColor(bgColor)

            setStroke(
                1,
                ContextCompat.getColor(
                    this@FarkleGameActivity,
                    R.color.cell_border
                )
            )
        }

    private fun showAddScoreDialog(turn: FarkleRound) {

        val remainingDice = getRemainingDice(turn)

        val available =
            ALL_COMBINATIONS.filter {
                it.usedDice <= remainingDice
            }

        val items =
            available.map {
                "${it.label}   ${it.score}"
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(
                "${players[currentPlayerIndex].playerName} - " +
                "${getString(R.string.farkle_btn_add)} " +
                "($remainingDice 🎲)")
            .setItems(items) { _, which ->

                turn.rollEntries.add(
                    available[which]
                )

                buildTable()
            }
            .setNegativeButton(
                getString(R.string.cancel),
                null
            )
            .show()
    }

    private fun showEditEntryDialog(
        turn: FarkleRound,
        entryIndex: Int
    ) {

        val items =
            ALL_COMBINATIONS.map {
                "${it.label}   ${it.score}"
            }.toTypedArray()

        val current =
            turn.rollEntries[entryIndex]

        val selectedIdx =
            ALL_COMBINATIONS.indexOfFirst {

                it.score == current.score &&
                        it.usedDice == current.usedDice &&
                        it.label == current.label
            }.takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("✏️ ${getString(R.string.farkle_btn_add)}")
            .setSingleChoiceItems(
                items,
                selectedIdx
            ) { dialog, which ->

                turn.rollEntries[entryIndex] =
                    ALL_COMBINATIONS[which]

                buildTable()

                dialog.dismiss()
            }
            .setNegativeButton(
                getString(R.string.cancel),
                null
            )
            .show()
    }

    private fun performBank(turn: FarkleRound) {
        turn.banked = true
        checkEndOfGameAndAdvance()
    }

    private fun performFarkle(turn: FarkleRound) {
        turn.farkled = true
        turn.rollEntries.clear()
        applyTripleFarklePenaltyIfNeeded(turn)
        checkEndOfGameAndAdvance()
    }

    private fun applyTripleFarklePenaltyIfNeeded(
        currentTurn: FarkleRound
    ) {

        val player =
            players.first {
                it.playerId == currentTurn.playerId
            }

        val playerRounds =
            rounds.filter {
                it.playerId == currentTurn.playerId
            }

        if (player.hasTripleFarkle(playerRounds)) {

            val totalBeforePenalty =
                player.getTotal(playerRounds.dropLast(1))

            currentTurn.tripleFarklePenalty = true

            currentTurn.penaltyPointsLost =
                totalBeforePenalty
        }
    }

    private fun checkEndOfGameAndAdvance() {

        if (!finalRoundStarted &&
            players.any {
                it.getTotal(rounds) >= SCORE_LIMIT
            }
        ) {

            finalRoundStarted = true
        }

        if (finalRoundStarted &&
            currentPlayerIndex == totalPlayers - 1
        ) {

            gameOver = true

            buildTable()

            saveResultsAndShowSummary()

            return
        }

        advanceToNextPlayer()
    }

    private fun advanceToNextPlayer() {

        currentPlayerIndex =
            (currentPlayerIndex + 1) % totalPlayers

        startTurnFor(currentPlayerIndex)

        buildTable()
    }

    private fun saveResultsAndShowSummary() {

        val totals =
            players.associate {
                it.playerId to it.getTotal(rounds)
            }

        val maxScore =
            totals.values.maxOrNull() ?: 0

        val winnerIds =
            totals.filter {
                it.value == maxScore
            }.keys

        val isDraw =
            winnerIds.size > 1

        val playedAt =
            System.currentTimeMillis()

        lifecycleScope.launch {

            database.gameResultDao().insertGameResults(
                players.map { player ->

                    GameResult(
                        gameType = GAME_TYPE,
                        playerId = player.playerId,
                        playerName = player.playerName,
                        score = totals[player.playerId] ?: 0,
                        isWinner = !isDraw && player.playerId in winnerIds,
                        isDraw = isDraw && player.playerId in winnerIds,
                        playedAt = playedAt
                    )
                }
            )

            val sortedPlayers =
                players.sortedByDescending {
                    totals[it.playerId] ?: 0
                }

            var rank = 1

            val entries =
                sortedPlayers.mapIndexed { i, p ->

                    val s =
                        totals[p.playerId] ?: 0

                    val prev =
                        if (i > 0)
                            totals[sortedPlayers[i - 1].playerId] ?: 0
                        else
                            s

                    val r =
                        if (i > 0 && s == prev)
                            rank
                        else {
                            rank = i + 1
                            rank
                        }

                    GameResultsDialog.PlayerResult(
                        p.playerName,
                        p.playerColor,
                        s,
                        r
                    )
                }

            GameResultsDialog.show(
                this@FarkleGameActivity,
                entries,
                isDraw,
                " pts"
            ) {
                finish()
            }
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(
            R.menu.menu_farkle_game,
            menu
        )

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {

            android.R.id.home -> {

                AlertDialog.Builder(this)
                    .setTitle(R.string.farkle_quit_game)
                    .setMessage(R.string.farkle_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        finish()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()

                true
            }

            R.id.action_help -> {

                HelpDialogs.showAppHelp(
                    this,
                    GAME_TYPE
                )

                true
            }

            else ->
                super.onOptionsItemSelected(item)
        }
    }
}
