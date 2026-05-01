package com.github.trivialloop.scorehub.games.wingspan

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityWingspanGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class WingspanGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWingspanGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var playerScores: List<WingspanPlayerScore>

    private var gameOver = false

    companion object {
        const val GAME_TYPE = "wingspan"

        private const val LABEL_COL_DP = 100
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWingspanGameBinding.inflate(layoutInflater)
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

        playerScores = playerIds.indices.map { i ->
            WingspanPlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.wingspan_game)

        buildScoreTable()
    }

    private fun buildScoreTable() {
        binding.scoreTableContainer.removeAllViews()

        binding.scoreTableContainer.addView(buildLabelColumn())

        for (ps in playerScores) {
            binding.scoreTableContainer.addView(buildPlayerColumn(ps))
        }
    }

    private fun buildLabelColumn(): LinearLayout {
        val col = makeColumn(weight = 0f, widthDp = LABEL_COL_DP)

        col.addView(makeLabelHeaderCell())

        for (category in WingspanCategory.entries) {
            col.addView(makeCategoryCell(category))
        }

        col.addView(makeLabelTotalCell())

        return col
    }

    private fun buildPlayerColumn(ps: WingspanPlayerScore): LinearLayout {
        val col = makeColumn(weight = 1f, widthDp = 0)

        col.addView(makePlayerNameCell(ps))

        for (category in WingspanCategory.entries) {
            col.addView(makeScoreCell(ps, category))
        }

        col.addView(makeTotalCell(ps))

        return col
    }


    private fun makeColumn(weight: Float, widthDp: Int): LinearLayout {
        val widthPx = if (widthDp == 0) 0 else dpToPx(widthDp)
        return LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }
    }

    private fun cellLayoutParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
    )

    private fun makeLabelHeaderCell(): TextView = TextView(this).apply {
        layoutParams = cellLayoutParams()
        background   = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, R.color.header_cell_background))
    }

    private fun makeCategoryCell(category: WingspanCategory): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
            layoutParams = cellLayoutParams()
            background   = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, R.color.header_cell_background))
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(22), dpToPx(22)).also { it.marginEnd = dpToPx(4) }
            setImageResource(categoryIcon(category))
            imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@WingspanGameActivity, R.color.header_cell_text)
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val label = TextView(this).apply {
            text      = categoryLabel(category)
            textSize  = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@WingspanGameActivity, R.color.header_cell_text))
            maxLines  = 2
            gravity   = Gravity.CENTER_VERTICAL
        }

        container.addView(icon)
        container.addView(label)
        return container
    }

    private fun makeLabelTotalCell(): TextView = TextView(this).apply {
        text         = getString(R.string.wingspan_total)
        gravity      = Gravity.CENTER
        textSize     = 13f
        setTypeface(null, Typeface.BOLD)
        layoutParams = cellLayoutParams()
        background   = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, R.color.yahtzee_calculated_cell_background))
        setTextColor(ContextCompat.getColor(this@WingspanGameActivity, R.color.yahtzee_calculated_cell_text))
    }

    private fun makePlayerNameCell(ps: WingspanPlayerScore): TextView = TextView(this).apply {
        text         = ps.playerName
        gravity      = Gravity.CENTER
        setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        textSize     = 13f
        setTypeface(null, Typeface.BOLD)
        maxLines     = 1
        ellipsize    = android.text.TextUtils.TruncateAt.END
        layoutParams = cellLayoutParams()
        background   = playerCellDrawable(ps.playerColor)
        setTextColor(Color.WHITE)
    }

    private fun makeScoreCell(ps: WingspanPlayerScore, category: WingspanCategory): TextView {
        val filled    = playerScores.mapNotNull { it.scores[category] }
        val allFilled = filled.size == playerScores.size
        val maxVal    = if (allFilled) filled.maxOrNull() else null
        val minVal    = if (allFilled) filled.minOrNull() else null
        val allSame   = allFilled && maxVal == minVal
        val score     = ps.scores[category]

        val textColor = if (allFilled && !allSame && score != null) {
            when (score) {
                maxVal -> ContextCompat.getColor(this, R.color.score_text_best)
                minVal -> ContextCompat.getColor(this, R.color.score_text_worst)
                else   -> ContextCompat.getColor(this, R.color.score_cell_text)
            }
        } else {
            ContextCompat.getColor(this, R.color.score_cell_text)
        }

        return TextView(this).apply {
            text         = score?.toString() ?: ""
            gravity      = Gravity.CENTER
            textSize     = 16f
            setTextColor(textColor)
            layoutParams = cellLayoutParams()
            background   = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, R.color.score_cell_background))

            if (!gameOver) setOnClickListener { showScoreInput(ps, category) }
        }
    }

    private fun makeTotalCell(ps: WingspanPlayerScore): TextView {
        val total       = ps.getTotal()
        val allComplete = playerScores.all { it.isComplete() }
        val totals      = playerScores.map { it.getTotal() }
        val maxTotal    = if (allComplete) totals.maxOrNull() else null
        val minTotal    = if (allComplete) totals.minOrNull() else null
        val allSame     = allComplete && maxTotal == minTotal

        val textColor = if (allComplete && !allSame) {
            when (total) {
                maxTotal -> ContextCompat.getColor(this, R.color.score_text_best)
                minTotal -> ContextCompat.getColor(this, R.color.score_text_worst)
                else     -> ContextCompat.getColor(this, R.color.yahtzee_calculated_cell_text)
            }
        } else {
            ContextCompat.getColor(this, R.color.yahtzee_calculated_cell_text)
        }

        return TextView(this).apply {
            text         = total.toString()
            gravity      = Gravity.CENTER
            textSize     = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            layoutParams = cellLayoutParams()
            background   = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, R.color.yahtzee_calculated_cell_background))
        }
    }

    private fun borderDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@WingspanGameActivity, R.color.cell_border))
    }

    private fun playerCellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@WingspanGameActivity, R.color.cell_border))
    }

        private fun showScoreInput(ps: WingspanPlayerScore, category: WingspanCategory) {
        val current = ps.scores[category]
 
        // Pencil in title when re-editing a cell that already has a value
        val title = if (current != null) "✏️ ${ps.playerName} — ${categoryLabel(category)}"
                    else "${ps.playerName} — ${categoryLabel(category)}"
 
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint      = "0–99"
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
                if (value == null || value < 0) { showScoreInput(ps, category); return@setPositiveButton }
                ps.scores[category] = value
                buildScoreTable()
                checkCompletion()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
 
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        editText.requestFocus()
    }

    private fun checkCompletion() {
        if (playerScores.all { it.isComplete() } && !gameOver) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.wingspan_game_complete))
                .setMessage(getString(R.string.wingspan_game_complete_message))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true
                    buildScoreTable()
                    saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals   = playerScores.associate { it to it.getTotal() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners  = totals.filter { it.value == maxScore }.keys
        val isDraw   = winners.size > 1
        lifecycleScope.launch {
            val results = playerScores.map { ps ->
                GameResult(
                    gameType   = GAME_TYPE,
                    playerId   = ps.playerId,
                    playerName = ps.playerName,
                    score      = ps.getTotal(),
                    isWinner   = !isDraw && ps in winners,
                    isDraw     = isDraw && ps in winners
                )
            }
            database.gameResultDao().insertGameResults(results)
            showSummaryDialog(totals, isDraw)
        }
    }

    private fun showSummaryDialog(totals: Map<WingspanPlayerScore, Int>, isDraw: Boolean) {
        val sorted = totals.entries.sortedByDescending { it.value }
        var currentRank = 1
        val entries = sorted.mapIndexed { index, (ps, score) ->
            val rank = if (index > 0 && score == sorted[index - 1].value) currentRank
            else { currentRank = index + 1; currentRank }
            GameResultsDialog.PlayerResult(ps.playerName, ps.playerColor, score, rank)
        }
        GameResultsDialog.show(this, entries, isDraw, " pts") { finish() }
    }

    private fun categoryLabel(category: WingspanCategory): String = when (category) {
        WingspanCategory.BIRDS         -> getString(R.string.wingspan_birds)
        WingspanCategory.BONUS_CARDS   -> getString(R.string.wingspan_bonus_cards)
        WingspanCategory.END_OF_ROUND  -> getString(R.string.wingspan_end_of_round)
        WingspanCategory.EGGS          -> getString(R.string.wingspan_eggs)
        WingspanCategory.FOOD_ON_CARDS -> getString(R.string.wingspan_food_on_cards)
        WingspanCategory.TUCKED_CARDS  -> getString(R.string.wingspan_tucked_cards)
    }

    private fun categoryIcon(category: WingspanCategory): Int = when (category) {
        WingspanCategory.BIRDS         -> R.drawable.ic_ws_birds
        WingspanCategory.BONUS_CARDS   -> R.drawable.ic_ws_bonus
        WingspanCategory.END_OF_ROUND  -> R.drawable.ic_ws_end_of_round
        WingspanCategory.EGGS          -> R.drawable.ic_ws_eggs
        WingspanCategory.FOOD_ON_CARDS -> R.drawable.ic_ws_food
        WingspanCategory.TUCKED_CARDS  -> R.drawable.ic_ws_tucked
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_wingspan_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.wingspan_quit_game)
                    .setMessage(R.string.wingspan_quit_game_message)
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
