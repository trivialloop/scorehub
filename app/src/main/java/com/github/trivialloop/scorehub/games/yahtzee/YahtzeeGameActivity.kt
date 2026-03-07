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
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityYahtzeeGameBinding
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
        }
    }

    private fun buildScoreTable() {
        binding.scoreTableContainer.removeAllViews()

        val visiblePlayers = getVisiblePlayers()

        // Create category column
        val categoryColumn = createCategoryColumn()
        binding.scoreTableContainer.addView(categoryColumn)

        // Create player columns
        for (visiblePlayer in visiblePlayers) {
            val isActive = visiblePlayer.first == currentPlayerIndex
            val playerColumn = createPlayerColumn(visiblePlayer.second, isActive)
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

    private fun createCategoryColumn(): LinearLayout {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )

        val categories = listOf(
            "", // Empty line for the players names
            getString(R.string.yahtzee_ones),
            getString(R.string.yahtzee_twos),
            getString(R.string.yahtzee_threes),
            getString(R.string.yahtzee_fours),
            getString(R.string.yahtzee_fives),
            getString(R.string.yahtzee_sixes),
            getString(R.string.upper_total),
            getString(R.string.bonus),
            getString(R.string.yahtzee_chance),
            getString(R.string.yahtzee_three_kind),
            getString(R.string.yahtzee_four_kind),
            getString(R.string.yahtzee_full_house),
            getString(R.string.yahtzee_small_straight),
            getString(R.string.yahtzee_large_straight),
            getString(R.string.yahtzee_yahtzee),
            getString(R.string.lower_total),
            getString(R.string.total)
        )

        for (category in categories) {
            val textView = createCellTextView(category, true)
            textView.setTypeface(null, Typeface.BOLD)
            column.addView(textView)
        }

        return column
    }

    private fun createPlayerColumn(playerScore: YahtzeePlayerScore, isActive: Boolean): LinearLayout {
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
        val categories = listOf(
            YahtzeeCategory.ONES,
            YahtzeeCategory.TWOS,
            YahtzeeCategory.THREES,
            YahtzeeCategory.FOURS,
            YahtzeeCategory.FIVES,
            YahtzeeCategory.SIXES
        )

        for (category in categories) {
            val cell = createScoreCell(playerScore, category, isActive)
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
            bonusCell.setTextColor(Color.LTGRAY)
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
            val cell = createScoreCell(playerScore, category, isActive)
            column.addView(cell)
        }

        // Lower total
        column.addView(createCalculatedCell(playerScore.getLowerTotal().toString()))

        // Grand total
        val grandTotalCell = createCalculatedCell(playerScore.getGrandTotal().toString())
        grandTotalCell.setTypeface(null, Typeface.BOLD)
        column.addView(grandTotalCell)

        return column
    }

    private fun createCellTextView(text: String, isHeader: Boolean): TextView {
        val textView = TextView(this)
        textView.text = text
        textView.setPadding(16, 16, 16, 16)
        textView.gravity = Gravity.CENTER
        textView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        textView.setBackgroundResource(android.R.drawable.editbox_background)
        if (isHeader) {
            textView.setBackgroundColor(Color.LTGRAY)
        }
        return textView
    }

    private fun createCalculatedCell(text: String): TextView {
        val textView = TextView(this)
        textView.text = text
        textView.setPadding(16, 16, 16, 16)
        textView.gravity = Gravity.CENTER
        textView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        textView.setBackgroundResource(android.R.drawable.editbox_background)
        textView.setBackgroundColor(Color.parseColor("#F0F0F0"))
        return textView
    }

    private fun createScoreCell(playerScore: YahtzeePlayerScore, category: YahtzeeCategory, isActive: Boolean): TextView {
        val textView = TextView(this)
        val score = playerScore.scores[category]
        textView.text = score?.toString() ?: ""
        textView.setPadding(16, 16, 16, 16)
        textView.gravity = Gravity.CENTER
        textView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        textView.setBackgroundResource(android.R.drawable.editbox_background)

        if (isActive) {
            textView.setOnClickListener {
                showScoreSelectionDialog(playerScore, category)
            }
        }

        return textView
    }

    private fun showScoreSelectionDialog(playerScore: YahtzeePlayerScore, category: YahtzeeCategory) {
        val possibleValues = category.getPossibleValues()
        val items = mutableListOf("")
        items.addAll(possibleValues.map { it.toString() })

        AlertDialog.Builder(this)
            .setTitle(getCategoryName(category))
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    // Clear score (option vide)
                    playerScore.scores[category] = null
                } else {
                    playerScore.scores[category] = possibleValues[which - 1]
                }
                nextPlayer()
                buildScoreTable()
                checkGameCompletion()
            }
            .show()
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
            .setTitle(R.string.game_complete)
            .setMessage(R.string.game_complete_message)
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
                // For the solo games, no winner or draw
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
        val message = StringBuilder()
        message.append(getString(R.string.final_scores)).append("\n\n")

        totals.entries.sortedByDescending { it.value }.forEach { (name, score) ->
            message.append("$name: $score")
            if (!isSoloGame && name in winners) {
                message.append(" ★")
            }
            message.append("\n")
        }

        message.append("\n")
        if (isDraw) {
            message.append(getString(R.string.draw_message))
        } else if (!isSoloGame) {
            message.append(getString(R.string.winner_message, winners.first()))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.game_results)
            .setMessage(message.toString())
            .setPositiveButton(R.string.ok) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.quit_game)
                    .setMessage(R.string.quit_game_message)
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
