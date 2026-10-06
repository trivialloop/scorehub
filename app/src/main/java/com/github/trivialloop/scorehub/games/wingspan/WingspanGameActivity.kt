package com.github.trivialloop.scorehub.games.wingspan

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
import com.github.trivialloop.scorehub.databinding.ActivityWingspanGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.GameSymbols
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
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
        const val GAME_TYPE    = "wingspan"
        private const val LABEL_COL_DP = GameSymbols.LABEL_COL_DP_ONE_SHOT
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

        playerScores = playerIds.indices.map { i ->
            WingspanPlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.wingspan_game)

        buildScoreTable()

        onBackPressedDispatcher.addCallback(this) {
            showQuitGameDialog()
        }
    }

    private fun iconStyle(c: WingspanCategory) = when (c) {
        WingspanCategory.BIRDS_FOREST    -> GameSymbols.FEATHER.copy(color = 0xFF2E7D32.toInt())  // green
        WingspanCategory.BIRDS_GRASSLAND -> GameSymbols.FEATHER.copy(color = 0xFFF9A825.toInt())  // yellow
        WingspanCategory.BIRDS_WETLAND   -> GameSymbols.FEATHER.copy(color = 0xFF1565C0.toInt())  // blue
        WingspanCategory.BONUS_CARDS     -> GameSymbols.TOTEM_SPARKLES
        WingspanCategory.END_OF_ROUND    -> GameSymbols.DIRECT_HIT
        WingspanCategory.EGGS            -> GameSymbols.EGG
        WingspanCategory.FOOD_ON_CARDS   -> GameSymbols.BUG
        WingspanCategory.TUCKED_CARDS    -> GameSymbols.INBOX_TRAY
    }

    // ─── Table ────────────────────────────────────────────────────────────────

    private fun buildScoreTable() {
        binding.scoreTableContainer.removeAllViews()
        binding.scoreTableContainer.addView(buildLabelColumn())
        for (ps in playerScores) binding.scoreTableContainer.addView(buildPlayerColumn(ps))
    }

    // ─── Label column ─────────────────────────────────────────────────────────

    private fun buildLabelColumn(): LinearLayout {
        val col = makeColumn(weight = 0f, widthDp = LABEL_COL_DP)
        col.addView(makeLabelHeaderCell())     // player name header spacer

        // Birds group: 3 habitat sub-rows + total sub-row
        col.addView(makeBirdsGroupLabel())

        // Remaining categories (no birds)
        for (category in nonBirdCategories()) {
            col.addView(makeCategoryCell(category))
        }
        col.addView(makeLabelTotalCell())
        return col
    }

    private fun makeBirdsGroupLabel(): LinearLayout {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 4f)
        }
        for (c in listOf(WingspanCategory.BIRDS_FOREST, WingspanCategory.BIRDS_GRASSLAND, WingspanCategory.BIRDS_WETLAND)) {
            val s = iconStyle(c); group.addView(makeIconLabelCell(s.emoji, s.color, 18f))
        }
        group.addView(makeIconLabelCell(GameSymbols.SUBTOTAL, GameSymbols.SUBTOTAL_ACCENT, 18f))
        return group
    }

    private fun makeLabelHeaderCell(): TextView = TextView(this).apply {
        layoutParams = cellLayoutParams()
        background = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, R.color.header_cell_background))
    }

    private fun makeCategoryCell(category: WingspanCategory) =
        iconStyle(category).let { makeIconLabelCell(it.emoji, it.color, 22f) }

    private fun makeLabelTotalCell() = TextView(this).apply {
        text = GameSymbols.TOTAL; gravity = Gravity.CENTER; textSize = 18f
        setTypeface(null, Typeface.BOLD); layoutParams = cellLayoutParams()
        background = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@WingspanGameActivity, R.color.score_calculated_cell_text))
    }

    // ─── Player column ─────────────────────────────────────────────────────────

    private fun buildPlayerColumn(ps: WingspanPlayerScore): LinearLayout {
        val col = makeColumn(weight = 1f, widthDp = 0)
        col.addView(makePlayerNameCell(ps))
        col.addView(makeBirdsScoreGroup(ps))
        for (category in nonBirdCategories()) col.addView(makeScoreCell(ps, category))
        col.addView(makeTotalCell(ps))
        return col
    }

    /** Player birds group: forest / grassland / wetland score cells + birds total. */
    private fun makeBirdsScoreGroup(ps: WingspanPlayerScore): LinearLayout {
        val group = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 4f)
        }
        val birdCats = listOf(
            WingspanCategory.BIRDS_FOREST,
            WingspanCategory.BIRDS_GRASSLAND,
            WingspanCategory.BIRDS_WETLAND
        )
        for (cat in birdCats) group.addView(makeHabitatScoreCell(ps, cat))

        // Birds subtotal row
        val birdTotal = birdCats.sumOf { ps.scores[it] ?: 0 }
        val allBirdTotals = playerScores.map { p -> birdCats.sumOf { p.scores[it] ?: 0 } }
        val allEntered = birdCats.all { cat -> playerScores.all { p -> p.scores[cat] != null } }
        val role = if (allEntered) ScoreColorRole(birdTotal, allBirdTotals, higherIsBetter = true)
                   else ScoreColorRole.NEUTRAL
        group.addView(TextView(this).apply {
            text = birdTotal.toString(); gravity = Gravity.CENTER; textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (role != ScoreColorRole.NEUTRAL) role.toColor(this@WingspanGameActivity)
                         else ContextCompat.getColor(this@WingspanGameActivity, R.color.score_calculated_cell_text))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, R.color.cell_calculated_bg))
        })
        return group
    }

    private fun makeHabitatScoreCell(ps: WingspanPlayerScore, category: WingspanCategory): TextView {
        val score     = ps.scores[category]
        val filled    = playerScores.mapNotNull { it.scores[category] }
        val allFilled = filled.size == playerScores.size
        val role      = if (allFilled) ScoreColorRole(score, playerScores.map { it.scores[category] }, higherIsBetter = true)
                        else ScoreColorRole.NEUTRAL
        val textColor = if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
                        else ContextCompat.getColor(this, R.color.score_cell_text)
        val bgRes     = if (!gameOver && score == null) R.color.cell_editable_bg else R.color.score_cell_background

        return TextView(this).apply {
            text = score?.toString() ?: ""; gravity = Gravity.CENTER; textSize = 14f
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, bgRes))
            if (!gameOver) setOnClickListener { showDropdownPicker(ps, category) }
        }
    }

    private fun makeScoreCell(ps: WingspanPlayerScore, category: WingspanCategory): TextView {
        val score     = ps.scores[category]
        val filled    = playerScores.mapNotNull { it.scores[category] }
        val allFilled = filled.size == playerScores.size
        val role      = if (allFilled) ScoreColorRole(score, playerScores.map { it.scores[category] }, higherIsBetter = true)
                        else ScoreColorRole.NEUTRAL
        val textColor = if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
                        else ContextCompat.getColor(this, R.color.score_cell_text)
        val bgRes     = if (!gameOver && score == null) R.color.cell_editable_bg else R.color.score_cell_background

        return TextView(this).apply {
            text = score?.toString() ?: ""; gravity = Gravity.CENTER; textSize = 16f
            setTextColor(textColor); layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, bgRes))
            if (!gameOver) setOnClickListener { showDropdownPicker(ps, category) }
        }
    }

    private fun makePlayerNameCell(ps: WingspanPlayerScore): TextView = TextView(this).apply {
        text = ps.playerName; gravity = Gravity.CENTER
        setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        textSize = 13f; setTypeface(null, Typeface.BOLD); maxLines = 1
        ellipsize = TextUtils.TruncateAt.END; layoutParams = cellLayoutParams()
        background = playerCellDrawable(ps.playerColor); setTextColor(Color.WHITE)
    }

    private fun makeTotalCell(ps: WingspanPlayerScore): TextView {
        val total       = ps.getTotal()
        val allComplete = playerScores.all { it.isComplete() }
        val role        = if (allComplete) ScoreColorRole(total, playerScores.map { it.getTotal() }, higherIsBetter = true)
                          else ScoreColorRole.NEUTRAL
        val textColor   = if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
                          else ContextCompat.getColor(this, R.color.score_calculated_cell_text)
        return TextView(this).apply {
            text = total.toString(); gravity = Gravity.CENTER; textSize = 17f
            setTypeface(null, Typeface.BOLD); setTextColor(textColor); layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@WingspanGameActivity, R.color.cell_calculated_bg))
        }
    }

    // ─── Dropdown picker ──────────────────────────────────────────────────────

    private fun showDropdownPicker(ps: WingspanPlayerScore, category: WingspanCategory) {
        val current = ps.scores[category]
        val title   = if (current != null) "✏️ ${ps.playerName} — ${categoryLabel(category)}"
                      else "${ps.playerName} — ${categoryLabel(category)}"
        val values  = category.getPossibleValues()
        val items   = values.map { it.toString() }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                ps.scores[category] = values[which]
                buildScoreTable()
                checkCompletion()
            }
            .create()

        dialog.show()

        // Scroll to current value (or 0) so the user sees their context immediately
        val scrollTo = if (current != null) values.indexOf(current) else 0
        if (scrollTo >= 0) dialog.listView?.post { dialog.listView?.setSelection(scrollTo) }
    }

    // ─── Completion check ─────────────────────────────────────────────────────

    private fun checkCompletion() {
        if (playerScores.all { it.isComplete() } && !gameOver) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.wingspan_game_complete))
                .setMessage(getString(R.string.wingspan_game_complete_message))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildScoreTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no), null).show()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals   = playerScores.associate { it to it.getTotal() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners  = totals.filter { it.value == maxScore }.keys
        val isDraw   = winners.size > 1
        val playedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(playerScores.map { ps ->
                GameResult(gameType = GAME_TYPE, playerId = ps.playerId, playerName = ps.playerName,
                    score = ps.getTotal(), isWinner = !isDraw && ps in winners, isDraw = isDraw && ps in winners,
                    playedAt = playedAt)
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (ps, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(ps.playerName, ps.playerColor, s, r)
            }
            GameResultsDialog.show(this@WingspanGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Returns non-bird categories in display order. */
    private fun nonBirdCategories() = listOf(
        WingspanCategory.BONUS_CARDS,
        WingspanCategory.END_OF_ROUND,
        WingspanCategory.EGGS,
        WingspanCategory.FOOD_ON_CARDS,
        WingspanCategory.TUCKED_CARDS
    )

    private fun categoryLabel(category: WingspanCategory) = when (category) {
        WingspanCategory.BIRDS_FOREST    -> "🪶🌲"
        WingspanCategory.BIRDS_GRASSLAND -> "🪶🌾"
        WingspanCategory.BIRDS_WETLAND   -> "🪶💧"
        else -> iconStyle(category).emoji
    }

    private fun makeIconLabelCell(icon: String, bgColor: Int, size: Float = 20f, weight: Float = 1f) = TextView(this).apply {
        text = icon; gravity = Gravity.CENTER; textSize = size
        setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, weight)
        background = borderDrawable(bgColor)
    }

    private fun cellLayoutParams() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

    private fun makeColumn(weight: Float, widthDp: Int): LinearLayout {
        val widthPx = if (widthDp == 0) 0 else dpToPx(widthDp)
        return LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }
    }

    private fun borderDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor); setStroke(1, ContextCompat.getColor(this@WingspanGameActivity, R.color.cell_border))
    }

    private fun playerCellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor); setStroke(1, ContextCompat.getColor(this@WingspanGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.wingspan_quit_game)
            .setMessage(R.string.wingspan_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_wingspan_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { showQuitGameDialog(); true }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
