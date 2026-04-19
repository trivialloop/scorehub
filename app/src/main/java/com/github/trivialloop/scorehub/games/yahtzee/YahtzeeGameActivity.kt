package com.github.trivialloop.scorehub.games.yahtzee

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityYahtzeeGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class YahtzeeGameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityYahtzeeGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private val playerScores = mutableListOf<YahtzeePlayerScore>()
    private var currentPlayerIndex = 0

    // Tracks the last category filled by each player (by player index)
    private val lastFilledCategory = mutableMapOf<Int, YahtzeeCategory?>()

    companion object {
        const val GAME_TYPE = "yahtzee"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYahtzeeGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.yahtzee_game)

        initializeScores()
        buildScoreTable()
    }

    private fun initializeScores() {
        for (i in playerIds.indices) {
            playerScores.add(
                YahtzeePlayerScore(
                    playerId = playerIds[i],
                    playerName = playerNames[i],
                    playerColor = playerColors[i]
                )
            )
            lastFilledCategory[i] = null
        }
    }

    private fun buildScoreTable() {
        binding.scoreTableContainer.removeAllViews()

        val visiblePlayers = getVisiblePlayers()

        // Create category column
        val categoryColumn = createCategoryColumn(visiblePlayers)
        binding.scoreTableContainer.addView(categoryColumn)

        // Create player columns
        for (visiblePlayer in visiblePlayers) {
            val isActive = visiblePlayer.first == currentPlayerIndex
            val playerColumn = createPlayerColumn(visiblePlayer.second, visiblePlayer.first, isActive)
            binding.scoreTableContainer.addView(playerColumn)
        }
    }

    private fun getVisiblePlayers(): List<Pair<Int, YahtzeePlayerScore>> {
        val totalPlayers = playerScores.size

        return when {
            totalPlayers == 1 -> listOf(0 to playerScores[0])
            totalPlayers == 2 -> listOf(0 to playerScores[0], 1 to playerScores[1])
            totalPlayers == 3 -> listOf(0 to playerScores[0], 1 to playerScores[1], 2 to playerScores[2])
            else -> {
                val prev = if (currentPlayerIndex == 0) totalPlayers - 1 else currentPlayerIndex - 1
                val next = if (currentPlayerIndex == totalPlayers - 1) 0 else currentPlayerIndex + 1
                listOf(prev to playerScores[prev], currentPlayerIndex to playerScores[currentPlayerIndex], next to playerScores[next])
            }
        }
    }

    /**
     * Creates the category label column.
     * For each scoring category, if the current player has already filled that category,
     * the label text fades toward the background color to match the dimmed cell appearance.
     */
    private fun createCategoryColumn(visiblePlayers: List<Pair<Int, YahtzeePlayerScore>>): LinearLayout {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )

        // Find the active player score among visible players
        val activePlayerScore = visiblePlayers.firstOrNull { it.first == currentPlayerIndex }?.second

        val categoryRows = listOf(
            null to "",                                          // player name row — no category
            YahtzeeCategory.ONES to getString(R.string.yahtzee_ones),
            YahtzeeCategory.TWOS to getString(R.string.yahtzee_twos),
            YahtzeeCategory.THREES to getString(R.string.yahtzee_threes),
            YahtzeeCategory.FOURS to getString(R.string.yahtzee_fours),
            YahtzeeCategory.FIVES to getString(R.string.yahtzee_fives),
            YahtzeeCategory.SIXES to getString(R.string.yahtzee_sixes),
            null to getString(R.string.yahtzee_upper_total),    // calculated — no dimming
            null to getString(R.string.yahtzee_bonus),          // calculated — no dimming
            YahtzeeCategory.CHANCE to getString(R.string.yahtzee_chance),
            YahtzeeCategory.THREE_OF_KIND to getString(R.string.yahtzee_three_kind),
            YahtzeeCategory.FOUR_OF_KIND to getString(R.string.yahtzee_four_kind),
            YahtzeeCategory.FULL_HOUSE to getString(R.string.yahtzee_full_house),
            YahtzeeCategory.SMALL_STRAIGHT to getString(R.string.yahtzee_small_straight),
            YahtzeeCategory.LARGE_STRAIGHT to getString(R.string.yahtzee_large_straight),
            YahtzeeCategory.YAHTZEE to getString(R.string.yahtzee_yahtzee),
            null to getString(R.string.yahtzee_lower_total),    // calculated — no dimming
            null to getString(R.string.yahtzee_total)           // calculated — no dimming
        )

        for ((category, label) in categoryRows) {
            val textView = createCellTextView(label, isHeader = true)
            textView.setTypeface(null, Typeface.BOLD)

            // Dim the category label when the active player has already filled this category
            if (category != null && activePlayerScore != null && activePlayerScore.scores[category] != null) {
                // Blend header text color toward background to match the dimmed score cell
                val dimmedColor = blendWithBackground(
                    ContextCompat.getColor(this, R.color.header_cell_text),
                    alpha = 0.35f
                )
                textView.setTextColor(dimmedColor)
            }

            column.addView(textView)
        }

        return column
    }

    /**
     * Blends a color toward the background at the given opacity (0 = fully transparent, 1 = fully opaque).
     * Used to dim category labels when the corresponding score is already filled.
     */
    private fun blendWithBackground(color: Int, alpha: Float): Int {
        val bg = ContextCompat.getColor(this, android.R.color.transparent)
        // Use the header background as the base to blend into
        val headerBg = ContextCompat.getColor(this, R.color.header_cell_background)
        val r = (Color.red(color) * alpha + Color.red(headerBg) * (1f - alpha)).toInt()
        val g = (Color.green(color) * alpha + Color.green(headerBg) * (1f - alpha)).toInt()
        val b = (Color.blue(color) * alpha + Color.blue(headerBg) * (1f - alpha)).toInt()
        return Color.rgb(r, g, b)
    }

    private fun createPlayerColumn(
        playerScore: YahtzeePlayerScore,
        playerIndex: Int,
        isActive: Boolean
    ): LinearLayout {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL

        val weight = when {
            playerScores.size == 1 -> 1f
            playerScores.size == 2 -> if (isActive) 0.8f else 0.2f
            else -> if (isActive) 0.6f else 0.2f
        }

        column.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            weight
        )

        // Player name with color - clickable to switch players
        val nameCell = createCellTextView(playerScore.playerName, false)
        nameCell.setBackgroundColor(playerScore.playerColor)
        nameCell.setTextColor(Color.WHITE)
        nameCell.setTypeface(null, Typeface.BOLD)
        nameCell.maxLines = 1
        nameCell.ellipsize = android.text.TextUtils.TruncateAt.END

        // Make player name clickable to switch to this player
        nameCell.setOnClickListener {
            val targetIndex = playerScores.indexOf(playerScore)
            if (targetIndex != -1 && targetIndex != currentPlayerIndex) {
                currentPlayerIndex = targetIndex
                buildScoreTable()
            }
        }

        column.addView(nameCell)

        // Score cells
        val upperCategories = listOf(
            YahtzeeCategory.ONES,
            YahtzeeCategory.TWOS,
            YahtzeeCategory.THREES,
            YahtzeeCategory.FOURS,
            YahtzeeCategory.FIVES,
            YahtzeeCategory.SIXES
        )

        for (category in upperCategories) {
            val cell = createScoreCell(playerScore, playerIndex, category, isActive)
            column.addView(cell)
        }

        // Upper total
        column.addView(createCalculatedCell(playerScore.getUpperTotal().toString()))

        // Bonus
        val bonusText = if (playerScore.getBonus() > 0) {
            playerScore.getBonus().toString()
        } else {
            val progress = playerScore.getBonusProgress()
            if (progress > 0) "-$progress" else "0"
        }
        val bonusCell = createCalculatedCell(bonusText)
        if (playerScore.getBonusProgress() > 0) {
            bonusCell.setTextColor(ContextCompat.getColor(this, R.color.yahtzee_bonus_progress_text))
        }
        column.addView(bonusCell)

        // Lower section
        val lowerCategories = listOf(
            YahtzeeCategory.CHANCE,
            YahtzeeCategory.THREE_OF_KIND,
            YahtzeeCategory.FOUR_OF_KIND,
            YahtzeeCategory.FULL_HOUSE,
            YahtzeeCategory.SMALL_STRAIGHT,
            YahtzeeCategory.LARGE_STRAIGHT,
            YahtzeeCategory.YAHTZEE
        )

        for (category in lowerCategories) {
            val cell = createScoreCell(playerScore, playerIndex, category, isActive)
            column.addView(cell)
        }

        // Lower total
        column.addView(createCalculatedCell(playerScore.getLowerTotal().toString()))

        // Grand total
        val grandTotalCell = createCalculatedCell(playerScore.getGrandTotal().toString(), isGrandTotal = true)
        grandTotalCell.setTypeface(null, Typeface.BOLD)
        column.addView(grandTotalCell)

        return column
    }

    private fun createCellTextView(text: String, isHeader: Boolean): TextView {
        val textView = TextView(this)
        textView.text = text
        textView.setPadding(12, 16, 12, 16)
        textView.gravity = Gravity.CENTER
        textView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        if (isHeader) {
            textView.background = cellBorderDrawable(
                ContextCompat.getColor(this, R.color.header_cell_background),
                strong = false
            )
            textView.setTextColor(ContextCompat.getColor(this, R.color.header_cell_text))
        } else {
            textView.background = cellBorderDrawable(
                ContextCompat.getColor(this, R.color.score_cell_background),
                strong = false
            )
            textView.setTextColor(ContextCompat.getColor(this, R.color.score_cell_text))
        }
        return textView
    }

    private fun createCalculatedCell(text: String, isGrandTotal: Boolean = false): TextView {
        val textView = TextView(this)
        textView.text = text
        textView.setPadding(12, 16, 12, 16)
        textView.gravity = Gravity.CENTER
        textView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        textView.background = cellBorderDrawable(
            ContextCompat.getColor(this, R.color.yahtzee_calculated_cell_background),
            strong = isGrandTotal
        )
        textView.setTextColor(ContextCompat.getColor(this, R.color.yahtzee_calculated_cell_text))
        return textView
    }

    private fun createScoreCell(
        playerScore: YahtzeePlayerScore,
        playerIndex: Int,
        category: YahtzeeCategory,
        isActive: Boolean
    ): TextView {
        val textView = TextView(this)
        val score = playerScore.scores[category]
        textView.text = score?.toString() ?: ""
        textView.setPadding(12, 16, 12, 16)
        textView.gravity = Gravity.CENTER
        textView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )

        val alreadyFilled = score != null
        val isLastFilled = isActive && lastFilledCategory[playerIndex] == category

        textView.background = cellBorderDrawable(
            ContextCompat.getColor(this, R.color.score_cell_background),
            strong = false
        )

        when {
            alreadyFilled && isLastFilled && isActive -> {
                // Last filled cell for the active player — re-editable, highlighted
                textView.setTextColor(ContextCompat.getColor(this, R.color.yahtzee_score_filled_text))
                textView.setTypeface(null, Typeface.ITALIC)
                textView.alpha = 0.6f
                // Subtle underline hint via a slightly different border
                textView.background = cellBorderDrawable(
                    ContextCompat.getColor(this, R.color.score_cell_background),
                    strong = true
                )
                textView.setOnClickListener {
                    showScoreSelectionDialog(playerScore, playerIndex, category, isEdit = true)
                }
            }
            alreadyFilled -> {
                // Score already entered — dimmed, not editable
                textView.setTextColor(ContextCompat.getColor(this, R.color.yahtzee_score_filled_text))
                textView.setTypeface(null, Typeface.ITALIC)
                textView.alpha = 0.6f
            }
            isActive -> {
                // Empty cell for active player — normal style, clickable
                textView.setTextColor(ContextCompat.getColor(this, R.color.score_cell_text))
                textView.setOnClickListener {
                    showScoreSelectionDialog(playerScore, playerIndex, category, isEdit = false)
                }
            }
            else -> {
                // Other player's cell — neutral style
                textView.setTextColor(ContextCompat.getColor(this, R.color.score_cell_text))
            }
        }

        return textView
    }

    private fun showScoreSelectionDialog(
        playerScore: YahtzeePlayerScore,
        playerIndex: Int,
        category: YahtzeeCategory,
        isEdit: Boolean
    ) {
        val possibleValues = category.getPossibleValues()

        // When re-editing, offer an extra "keep current" option is implicit — we show the picker again
        val items = mutableListOf("")
        items.addAll(possibleValues.map { it.toString() })

        val title = if (isEdit) {
            "✏️ ${getCategoryName(category)}"
        } else {
            getCategoryName(category)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items.toTypedArray()) { _, which ->
                val previousCategory = lastFilledCategory[playerIndex]

                if (which == 0) {
                    // Clear / cancel the score
                    if (isEdit) {
                        // Remove the score and clear the last filled marker
                        playerScore.scores[category] = null
                        lastFilledCategory[playerIndex] = null
                    } else {
                        playerScore.scores[category] = null
                    }
                } else {
                    playerScore.scores[category] = possibleValues[which - 1]
                    if (!isEdit) {
                        // Only advance turn and track last filled when it is a new entry
                        lastFilledCategory[playerIndex] = category
                        nextPlayer()
                    } else {
                        // Re-edit: update value but keep last filled marker on same category
                        lastFilledCategory[playerIndex] = category
                    }
                }

                buildScoreTable()
                checkGameCompletion()
            }
            .show()
    }

    private fun cellBorderDrawable(bgColor: Int, strong: Boolean): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(bgColor)
            val strokeWidth = if (strong) 2 else 1
            setStroke(strokeWidth, ContextCompat.getColor(this@YahtzeeGameActivity, R.color.yahtzee_cell_border))
        }
    }

    private fun nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % playerScores.size
    }

    private fun getCategoryName(category: YahtzeeCategory): String {
        return when (category) {
            YahtzeeCategory.ONES -> getString(R.string.yahtzee_ones)
            YahtzeeCategory.TWOS -> getString(R.string.yahtzee_twos)
            YahtzeeCategory.THREES -> getString(R.string.yahtzee_threes)
            YahtzeeCategory.FOURS -> getString(R.string.yahtzee_fours)
            YahtzeeCategory.FIVES -> getString(R.string.yahtzee_fives)
            YahtzeeCategory.SIXES -> getString(R.string.yahtzee_sixes)
            YahtzeeCategory.CHANCE -> getString(R.string.yahtzee_chance)
            YahtzeeCategory.THREE_OF_KIND -> getString(R.string.yahtzee_three_kind)
            YahtzeeCategory.FOUR_OF_KIND -> getString(R.string.yahtzee_four_kind)
            YahtzeeCategory.FULL_HOUSE -> getString(R.string.yahtzee_full_house)
            YahtzeeCategory.SMALL_STRAIGHT -> getString(R.string.yahtzee_small_straight)
            YahtzeeCategory.LARGE_STRAIGHT -> getString(R.string.yahtzee_large_straight)
            YahtzeeCategory.YAHTZEE -> getString(R.string.yahtzee_yahtzee)
        }
    }

    private fun checkGameCompletion() {
        if (playerScores.all { it.isComplete() }) {
            showGameCompletionDialog()
        }
    }

    private fun showGameCompletionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.yahtzee_game_complete)
            .setMessage(R.string.yahtzee_game_complete_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                calculateAndSaveResults()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun calculateAndSaveResults() {
        val totals = playerScores.associate { it.playerName to it.getGrandTotal() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1
        val isSoloGame = playerScores.size == 1

        lifecycleScope.launch {
            val results = mutableListOf<GameResult>()

            for (playerScore in playerScores) {
                val isWinner = if (isSoloGame) false else (playerScore.playerName in winners && !isDraw)
                val isDrawResult = if (isSoloGame) false else (isDraw && playerScore.playerName in winners)

                results.add(
                    GameResult(
                        gameType = GAME_TYPE,
                        playerId = playerScore.playerId,
                        playerName = playerScore.playerName,
                        score = playerScore.getGrandTotal(),
                        isWinner = isWinner,
                        isDraw = isDrawResult
                    )
                )
            }

            database.gameResultDao().insertGameResults(results)
            showResultsDialog(totals, winners, isDraw, isSoloGame)
        }
    }

    private fun showResultsDialog(
        totals: Map<String, Int>,
        winners: Set<String>,
        isDraw: Boolean,
        isSoloGame: Boolean
    ) {
        val sorted = totals.entries.sortedByDescending { it.value }
        var currentRank = 1
        val entries = sorted.mapIndexed { index, (name, score) ->
            val rank = if (index > 0 && score == sorted[index - 1].value) currentRank
                       else { currentRank = index + 1; currentRank }
            val player = playerScores.find { it.playerName == name }
            GameResultsDialog.PlayerResult(
                playerName  = name,
                playerColor = player?.playerColor ?: android.graphics.Color.GRAY,
                score       = score,
                rank        = rank
            )
        }

        GameResultsDialog.show(
            context    = this,
            entries    = entries,
            isDraw     = isDraw && !isSoloGame,
            scoreLabel = " pts",
            onDismiss  = { finish() }
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.yahtzee_quit_game)
                    .setMessage(R.string.yahtzee_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
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
