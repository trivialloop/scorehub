package com.github.trivialloop.scorehub.games.yahtzee

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import com.github.trivialloop.scorehub.databinding.ActivityYahtzeeGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class YahtzeeGameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityYahtzeeGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private val playerScores = mutableListOf<YahtzeePlayerScore>()
    private var currentPlayerIndex = 0
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.appBarLayout.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        database     = AppDatabase.getDatabase(this)
        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.yahtzee_game)

        initializeScores()
        buildScoreTable()
    }

    private fun initializeScores() {
        for (i in playerIds.indices) {
            playerScores.add(YahtzeePlayerScore(playerIds[i], playerNames[i], playerColors[i]))
            lastFilledCategory[i] = null
        }
    }

    private fun buildScoreTable() {
        binding.scoreTableContainer.removeAllViews()
        val visiblePlayers = getVisiblePlayers()
        binding.scoreTableContainer.addView(createCategoryColumn(visiblePlayers))
        for (vp in visiblePlayers) {
            binding.scoreTableContainer.addView(createPlayerColumn(vp.second, vp.first, vp.first == currentPlayerIndex))
        }
    }

    private fun getVisiblePlayers(): List<Pair<Int, YahtzeePlayerScore>> {
        val total = playerScores.size
        return when {
            total <= 3 -> playerScores.indices.map { it to playerScores[it] }
            else -> {
                val prev = if (currentPlayerIndex == 0) total - 1 else currentPlayerIndex - 1
                val next = if (currentPlayerIndex == total - 1) 0 else currentPlayerIndex + 1
                listOf(prev to playerScores[prev], currentPlayerIndex to playerScores[currentPlayerIndex], next to playerScores[next])
            }
        }
    }

    private fun createCategoryColumn(visiblePlayers: List<Pair<Int, YahtzeePlayerScore>>): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val activePlayerScore = visiblePlayers.firstOrNull { it.first == currentPlayerIndex }?.second
        val categoryRows = listOf(
            null to "",
            YahtzeeCategory.ONES          to getString(R.string.yahtzee_ones),
            YahtzeeCategory.TWOS          to getString(R.string.yahtzee_twos),
            YahtzeeCategory.THREES        to getString(R.string.yahtzee_threes),
            YahtzeeCategory.FOURS         to getString(R.string.yahtzee_fours),
            YahtzeeCategory.FIVES         to getString(R.string.yahtzee_fives),
            YahtzeeCategory.SIXES         to getString(R.string.yahtzee_sixes),
            null to getString(R.string.yahtzee_upper_total),
            null to getString(R.string.yahtzee_bonus),
            YahtzeeCategory.CHANCE        to getString(R.string.yahtzee_chance),
            YahtzeeCategory.THREE_OF_KIND to getString(R.string.yahtzee_three_kind),
            YahtzeeCategory.FOUR_OF_KIND  to getString(R.string.yahtzee_four_kind),
            YahtzeeCategory.FULL_HOUSE    to getString(R.string.yahtzee_full_house),
            YahtzeeCategory.SMALL_STRAIGHT to getString(R.string.yahtzee_small_straight),
            YahtzeeCategory.LARGE_STRAIGHT to getString(R.string.yahtzee_large_straight),
            YahtzeeCategory.YAHTZEE       to getString(R.string.yahtzee_yahtzee),
            null to getString(R.string.yahtzee_lower_total),
            null to getString(R.string.yahtzee_total)
        )
        for ((category, label) in categoryRows) {
            val tv = createCellTextView(label, isHeader = true)
            tv.setTypeface(null, Typeface.BOLD)
            if (category != null && activePlayerScore?.scores?.get(category) != null) {
                tv.setTextColor(blendWithBackground(ContextCompat.getColor(this, R.color.header_cell_text), alpha = 0.35f))
            }
            column.addView(tv)
        }
        return column
    }

    private fun blendWithBackground(color: Int, alpha: Float): Int {
        val bg = ContextCompat.getColor(this, R.color.header_cell_background)
        val r  = (Color.red(color)   * alpha + Color.red(bg)   * (1f - alpha)).toInt()
        val g  = (Color.green(color) * alpha + Color.green(bg) * (1f - alpha)).toInt()
        val b  = (Color.blue(color)  * alpha + Color.blue(bg)  * (1f - alpha)).toInt()
        return Color.rgb(r, g, b)
    }

    private fun createPlayerColumn(playerScore: YahtzeePlayerScore, playerIndex: Int, isActive: Boolean): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val weight = when {
                playerScores.size == 1 -> 1f
                playerScores.size == 2 -> if (isActive) 0.8f else 0.2f
                else -> if (isActive) 0.6f else 0.2f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }

        // Player name cell
        val nameCell = createCellTextView(playerScore.playerName, false)
        nameCell.setBackgroundColor(playerScore.playerColor)
        nameCell.setTextColor(Color.WHITE)
        nameCell.setTypeface(null, Typeface.BOLD)
        nameCell.maxLines = 1
        nameCell.ellipsize = android.text.TextUtils.TruncateAt.END
        nameCell.setOnClickListener {
            val idx = playerScores.indexOf(playerScore)
            if (idx != -1 && idx != currentPlayerIndex) { currentPlayerIndex = idx; buildScoreTable() }
        }
        column.addView(nameCell)

        val upperCats = listOf(YahtzeeCategory.ONES, YahtzeeCategory.TWOS, YahtzeeCategory.THREES,
            YahtzeeCategory.FOURS, YahtzeeCategory.FIVES, YahtzeeCategory.SIXES)
        val lowerCats = listOf(YahtzeeCategory.CHANCE, YahtzeeCategory.THREE_OF_KIND,
            YahtzeeCategory.FOUR_OF_KIND, YahtzeeCategory.FULL_HOUSE,
            YahtzeeCategory.SMALL_STRAIGHT, YahtzeeCategory.LARGE_STRAIGHT, YahtzeeCategory.YAHTZEE)

        for (cat in upperCats) column.addView(createScoreCell(playerScore, playerIndex, cat, isActive))

        column.addView(createCalculatedCell(playerScore.getUpperTotal().toString()))

        val bonusText = if (playerScore.getBonus() > 0) playerScore.getBonus().toString()
        else { val p = playerScore.getBonusProgress(); if (p > 0) "-$p" else "0" }
        val bonusCell = createCalculatedCell(bonusText)
        if (playerScore.getBonusProgress() > 0)
            bonusCell.setTextColor(ContextCompat.getColor(this, R.color.score_progress_cell_text))
        column.addView(bonusCell)

        for (cat in lowerCats) column.addView(createScoreCell(playerScore, playerIndex, cat, isActive))

        column.addView(createCalculatedCell(playerScore.getLowerTotal().toString()))

        // Grand total — Yahtzee: higher = better, so BEST=green, WORST=red
        val grandTotals  = playerScores.map { it.getGrandTotal() }
        val grandTotal   = playerScore.getGrandTotal()
        val grandCell    = createCalculatedCell(grandTotal.toString(), isGrandTotal = true)
        grandCell.setTypeface(null, Typeface.BOLD)
        if (playerScores.all { it.isComplete() }) {
            val role = ScoreColorRole(grandTotal, grandTotals, higherIsBetter = true)
            if (role != ScoreColorRole.NEUTRAL) grandCell.setTextColor(role.toColor(this))
        }
        column.addView(grandCell)
        return column
    }

    private fun createCellTextView(text: String, isHeader: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(12, 16, 12, 16)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            if (isHeader) {
                background = cellBorderDrawable(ContextCompat.getColor(this@YahtzeeGameActivity, R.color.header_cell_background))
                setTextColor(ContextCompat.getColor(this@YahtzeeGameActivity, R.color.header_cell_text))
            } else {
                background = cellBorderDrawable(ContextCompat.getColor(this@YahtzeeGameActivity, R.color.score_cell_background))
                setTextColor(ContextCompat.getColor(this@YahtzeeGameActivity, R.color.score_cell_text))
            }
        }
    }

    private fun createCalculatedCell(text: String, isGrandTotal: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(12, 16, 12, 16)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = cellBorderDrawable(
                ContextCompat.getColor(this@YahtzeeGameActivity, R.color.cell_calculated_bg),
                strong = isGrandTotal
            )
            setTextColor(ContextCompat.getColor(this@YahtzeeGameActivity, R.color.score_calculated_cell_text))
        }
    }

    private fun createScoreCell(
        playerScore: YahtzeePlayerScore, playerIndex: Int,
        category: YahtzeeCategory, isActive: Boolean
    ): TextView {
        val score        = playerScore.scores[category]
        val isLastFilled = isActive && lastFilledCategory[playerIndex] == category
        val alreadyFilled = score != null

        // Per-row color: Yahtzee higher = better
        val role = ScoreColorRole(score, playerScores.map { it.scores[category] }, higherIsBetter = true)
        val rowTextColor = if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
        else ContextCompat.getColor(this, R.color.score_filled_category_text)

        return TextView(this).apply {
            setPadding(12, 16, 12, 16)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

            when {
                alreadyFilled && isLastFilled && isActive -> {
                    // Re-editable last-filled cell — NO pencil in cell text, pencil in dialog title only
                    text = score.toString()
                    background = cellBorderDrawable(
                        ContextCompat.getColor(this@YahtzeeGameActivity, R.color.cell_editable_filled_bg),
                        strong = true
                    )
                    setTextColor(rowTextColor)
                    setTypeface(null, Typeface.BOLD)
                    setOnClickListener { showScoreSelectionDialog(playerScore, playerIndex, category, isEdit = true) }
                }
                alreadyFilled -> {
                    text = score.toString()
                    background = cellBorderDrawable(ContextCompat.getColor(this@YahtzeeGameActivity, R.color.score_cell_background))
                    setTextColor(rowTextColor)
                    setTypeface(null, Typeface.ITALIC)
                    alpha = 0.6f
                }
                isActive -> {
                    text = ""
                    background = cellBorderDrawable(ContextCompat.getColor(this@YahtzeeGameActivity, R.color.score_cell_background))
                    setTextColor(ContextCompat.getColor(this@YahtzeeGameActivity, R.color.score_cell_text))
                    setOnClickListener { showScoreSelectionDialog(playerScore, playerIndex, category, isEdit = false) }
                }
                else -> {
                    text = score?.toString() ?: ""
                    background = cellBorderDrawable(ContextCompat.getColor(this@YahtzeeGameActivity, R.color.score_cell_background))
                    setTextColor(rowTextColor)
                    if (alreadyFilled) { setTypeface(null, Typeface.ITALIC); alpha = 0.6f }
                }
            }
        }
    }

    private fun showScoreSelectionDialog(
        playerScore: YahtzeePlayerScore, playerIndex: Int,
        category: YahtzeeCategory, isEdit: Boolean
    ) {
        val possibleValues = category.getPossibleValues()
        val items = mutableListOf("") // first item = clear/cancel
        items.addAll(possibleValues.map { it.toString() })

        // Pencil in dialog title when re-editing
        val categoryName = getCategoryName(category)
        val title = if (isEdit) "✏️ $categoryName" else categoryName

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    if (isEdit) { playerScore.scores[category] = null; lastFilledCategory[playerIndex] = null }
                } else {
                    playerScore.scores[category] = possibleValues[which - 1]
                    lastFilledCategory[playerIndex] = category
                    if (!isEdit) nextPlayer()
                }
                buildScoreTable()
                checkGameCompletion()
            }
            .create()

        dialog.show()

        if (category.scrollToBottom()) {
            dialog.listView?.post { dialog.listView?.setSelection(items.size - 1) }
        }
    }

    private fun cellBorderDrawable(bgColor: Int, strong: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(bgColor)
            setStroke(
                if (strong) 2 else 1,
                ContextCompat.getColor(this@YahtzeeGameActivity, R.color.cell_border)
            )
        }

    private fun nextPlayer() { currentPlayerIndex = (currentPlayerIndex + 1) % playerScores.size }

    private fun getCategoryName(category: YahtzeeCategory): String = when (category) {
        YahtzeeCategory.ONES          -> getString(R.string.yahtzee_ones)
        YahtzeeCategory.TWOS          -> getString(R.string.yahtzee_twos)
        YahtzeeCategory.THREES        -> getString(R.string.yahtzee_threes)
        YahtzeeCategory.FOURS         -> getString(R.string.yahtzee_fours)
        YahtzeeCategory.FIVES         -> getString(R.string.yahtzee_fives)
        YahtzeeCategory.SIXES         -> getString(R.string.yahtzee_sixes)
        YahtzeeCategory.CHANCE        -> getString(R.string.yahtzee_chance)
        YahtzeeCategory.THREE_OF_KIND -> getString(R.string.yahtzee_three_kind)
        YahtzeeCategory.FOUR_OF_KIND  -> getString(R.string.yahtzee_four_kind)
        YahtzeeCategory.FULL_HOUSE    -> getString(R.string.yahtzee_full_house)
        YahtzeeCategory.SMALL_STRAIGHT -> getString(R.string.yahtzee_small_straight)
        YahtzeeCategory.LARGE_STRAIGHT -> getString(R.string.yahtzee_large_straight)
        YahtzeeCategory.YAHTZEE       -> getString(R.string.yahtzee_yahtzee)
    }

    private fun checkGameCompletion() {
        if (playerScores.all { it.isComplete() }) {
            AlertDialog.Builder(this)
                .setTitle(R.string.yahtzee_game_complete)
                .setMessage(R.string.yahtzee_game_complete_message)
                .setPositiveButton(R.string.yes) { _, _ -> calculateAndSaveResults() }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun calculateAndSaveResults() {
        val totals     = playerScores.associate { it.playerName to it.getGrandTotal() }
        val maxScore   = totals.values.maxOrNull() ?: 0
        val winners    = totals.filter { it.value == maxScore }.keys
        val isDraw     = winners.size > 1
        val isSoloGame = playerScores.size == 1

        lifecycleScope.launch {
            val results = playerScores.map { ps ->
                GameResult(
                    gameType   = GAME_TYPE, playerId = ps.playerId, playerName = ps.playerName,
                    score      = ps.getGrandTotal(),
                    isWinner   = if (isSoloGame) false else (ps.playerName in winners && !isDraw),
                    isDraw     = if (isSoloGame) false else (isDraw && ps.playerName in winners)
                )
            }
            database.gameResultDao().insertGameResults(results)
            showResultsDialog(totals, winners, isDraw, isSoloGame)
        }
    }

    private fun showResultsDialog(totals: Map<String, Int>, winners: Set<String>, isDraw: Boolean, isSoloGame: Boolean) {
        val sorted = totals.entries.sortedByDescending { it.value }
        var currentRank = 1
        val entries = sorted.mapIndexed { i, (name, score) ->
            val rank = if (i > 0 && score == sorted[i - 1].value) currentRank else { currentRank = i + 1; currentRank }
            val player = playerScores.find { it.playerName == name }
            GameResultsDialog.PlayerResult(name, player?.playerColor ?: android.graphics.Color.GRAY, score, rank)
        }
        GameResultsDialog.show(this, entries, isDraw && !isSoloGame, " pts") { finish() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_yahtzee_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this).setTitle(R.string.yahtzee_quit_game).setMessage(R.string.yahtzee_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }.setNegativeButton(R.string.no, null).show()
                true
            }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}