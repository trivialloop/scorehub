package com.github.trivialloop.scorehub.games.sevenwonders

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
import com.github.trivialloop.scorehub.databinding.ActivitySevenwondersGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

/**
 * One-shot grid for 7 Wonders, same family as WingspanGameActivity / AkropolisGameActivity.
 *
 * The Science row (green — "outils") is the only multi-input category: it is built as a
 * 4-sub-row group (Compass / Gear / Tablet / computed subtotal), exactly mirroring
 * Wingspan's bird-habitat group. Every other category is a single flat row.
 */
class SevenWondersGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySevenwondersGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var playerScores: List<SevenWondersPlayerScore>
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "sevenwonders"
        private const val LABEL_COL_DP = 100

        // Science symbol accent colours (background of the sub-row label cell)
        private val COLOR_COMPASS = 0xFF1565C0.toInt()  // blue
        private val COLOR_GEAR    = 0xFF616161.toInt()  // grey
        private val COLOR_TABLET  = 0xFF2E7D32.toInt()  // green
    }

    private enum class FlatCategory { MILITARY, COINS, WONDER, CIVILIAN, COMMERCE, GUILD }
    private enum class ScienceSymbol { COMPASS, GEAR, TABLET }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySevenwondersGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.root.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        playerScores = playerIds.indices.map { i ->
            SevenWondersPlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.sevenwonders_game)

        buildScoreTable()

        onBackPressedDispatcher.addCallback(this) {
            showQuitGameDialog()
        }
    }

    // ─── Table ────────────────────────────────────────────────────────────────

    private fun buildScoreTable() {
        binding.scoreTableContainer.removeAllViews()
        binding.scoreTableContainer.addView(buildLabelColumn())
        for (ps in playerScores) binding.scoreTableContainer.addView(buildPlayerColumn(ps))
    }

    // ─── Label column ─────────────────────────────────────────────────────────

    private fun buildLabelColumn(): LinearLayout {
        val col = makeColumn(widthDp = LABEL_COL_DP)
        col.addView(makeLabelHeaderCell()) // player name header spacer

        for (category in FlatCategory.entries) {
            col.addView(makeFlatCategoryLabelCell(category))
        }

        col.addView(makeScienceGroupLabel())
        col.addView(makeLabelTotalCell())
        return col
    }

    private fun makeLabelHeaderCell(): TextView = TextView(this).apply {
        layoutParams = cellLayoutParams()
        background = borderDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.header_cell_background))
    }

    private fun makeFlatCategoryLabelCell(category: FlatCategory): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.header_cell_background))
        }
        container.addView(TextView(this).apply {
            text = categoryLabel(category)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.header_cell_text))
            maxLines = 2
            gravity = Gravity.CENTER_VERTICAL
        })
        return container
    }

    /** The science label column: a 4-row block (compass / gear / tablet / science total). */
    private fun makeScienceGroupLabel(): LinearLayout {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 4f)
        }
        group.addView(makeSymbolLabelCell(getString(R.string.sevenwonders_science_compass), COLOR_COMPASS))
        group.addView(makeSymbolLabelCell(getString(R.string.sevenwonders_science_gear), COLOR_GEAR))
        group.addView(makeSymbolLabelCell(getString(R.string.sevenwonders_science_tablet), COLOR_TABLET))
        group.addView(makeScienceTotalLabelCell())
        return group
    }

    private fun makeSymbolLabelCell(label: String, accent: Int): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = borderDrawable(accent)
        }
        container.addView(TextView(this).apply {
            text = label
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 2
            gravity = Gravity.CENTER_VERTICAL
        })
        return container
    }

    private fun makeScienceTotalLabelCell(): TextView = TextView(this).apply {
        text = getString(R.string.sevenwonders_science_total)
        gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        background = borderDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_calculated_cell_text))
    }

    private fun makeLabelTotalCell(): TextView = TextView(this).apply {
        text = getString(R.string.sevenwonders_total); gravity = Gravity.CENTER; textSize = 13f
        setTypeface(null, Typeface.BOLD); layoutParams = cellLayoutParams()
        background = borderDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_calculated_cell_text))
    }

    // ─── Player column ─────────────────────────────────────────────────────────

    private fun buildPlayerColumn(ps: SevenWondersPlayerScore): LinearLayout {
        val col = makeColumn(widthDp = 0, weight = 1f)
        col.addView(makePlayerNameCell(ps))
        for (category in FlatCategory.entries) col.addView(makeFlatScoreCell(ps, category))
        col.addView(makeScienceScoreGroup(ps))
        col.addView(makeTotalCell(ps))
        return col
    }

    private fun makePlayerNameCell(ps: SevenWondersPlayerScore): TextView = TextView(this).apply {
        text = ps.playerName; gravity = Gravity.CENTER
        setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        textSize = 13f; setTypeface(null, Typeface.BOLD); maxLines = 1
        ellipsize = TextUtils.TruncateAt.END; layoutParams = cellLayoutParams()
        background = borderDrawable(ps.playerColor); setTextColor(Color.WHITE)
    }

    private fun makeFlatScoreCell(ps: SevenWondersPlayerScore, category: FlatCategory): TextView {
        val value = categoryValue(ps, category)
        val allValues = playerScores.map { categoryValue(it, category) }
        val allFilled = allValues.all { it != null }
        val role = if (allFilled) ScoreColorRole(value, allValues, higherIsBetter = true) else ScoreColorRole.NEUTRAL
        val textColor = if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
                        else ContextCompat.getColor(this, R.color.score_cell_text)
        val bgRes = if (!gameOver && value == null) R.color.cell_editable_bg else R.color.score_cell_background

        return TextView(this).apply {
            text = value?.toString() ?: ""; gravity = Gravity.CENTER; textSize = 14f
            if (role != ScoreColorRole.NEUTRAL) setTypeface(null, Typeface.BOLD)
            setTextColor(textColor); layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, bgRes))
            if (!gameOver) setOnClickListener { showCategoryPicker(ps, category) }
        }
    }

    /** Science group: 3 tappable symbol sub-cells + 1 calculated subtotal sub-cell. */
    private fun makeScienceScoreGroup(ps: SevenWondersPlayerScore): LinearLayout {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 4f)
        }
        group.addView(makeScienceSymbolCell(ps, ScienceSymbol.COMPASS))
        group.addView(makeScienceSymbolCell(ps, ScienceSymbol.GEAR))
        group.addView(makeScienceSymbolCell(ps, ScienceSymbol.TABLET))

        val scienceValue = if (ps.scienceCompass != null && ps.scienceGear != null && ps.scienceTablet != null)
            ps.getScienceScore() else null
        val allScience = playerScores.map {
            if (it.scienceCompass != null && it.scienceGear != null && it.scienceTablet != null)
                it.getScienceScore() else null
        }
        val allFilled = allScience.all { it != null }
        val role = if (allFilled) ScoreColorRole(scienceValue, allScience, higherIsBetter = true) else ScoreColorRole.NEUTRAL
        group.addView(TextView(this).apply {
            text = scienceValue?.toString() ?: ""; gravity = Gravity.CENTER; textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (role != ScoreColorRole.NEUTRAL) role.toColor(this@SevenWondersGameActivity)
                         else ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_calculated_cell_text))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = borderDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.cell_calculated_bg))
        })
        return group
    }

    private fun makeScienceSymbolCell(ps: SevenWondersPlayerScore, symbol: ScienceSymbol): TextView {
        val value = getScienceSymbolValue(ps, symbol)
        val bgRes = if (!gameOver && value == null) R.color.cell_editable_bg else R.color.score_cell_background
        return TextView(this).apply {
            text = value?.toString() ?: ""; gravity = Gravity.CENTER; textSize = 14f
            setTextColor(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_cell_text))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = borderDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, bgRes))
            if (!gameOver) setOnClickListener { showScienceSymbolPicker(ps, symbol) }
        }
    }

    private fun getScienceSymbolValue(ps: SevenWondersPlayerScore, symbol: ScienceSymbol): Int? = when (symbol) {
        ScienceSymbol.COMPASS -> ps.scienceCompass
        ScienceSymbol.GEAR -> ps.scienceGear
        ScienceSymbol.TABLET -> ps.scienceTablet
    }

    private fun setScienceSymbolValue(ps: SevenWondersPlayerScore, symbol: ScienceSymbol, value: Int) {
        when (symbol) {
            ScienceSymbol.COMPASS -> ps.scienceCompass = value
            ScienceSymbol.GEAR -> ps.scienceGear = value
            ScienceSymbol.TABLET -> ps.scienceTablet = value
        }
    }

    private fun scienceSymbolLabel(symbol: ScienceSymbol): String = when (symbol) {
        ScienceSymbol.COMPASS -> getString(R.string.sevenwonders_science_compass)
        ScienceSymbol.GEAR -> getString(R.string.sevenwonders_science_gear)
        ScienceSymbol.TABLET -> getString(R.string.sevenwonders_science_tablet)
    }

    private fun makeTotalCell(ps: SevenWondersPlayerScore): TextView {
        val allComplete = playerScores.all { it.isComplete() }
        val total = ps.getTotal()
        val role = if (allComplete) ScoreColorRole(total, playerScores.map { it.getTotal() }, higherIsBetter = true)
                   else ScoreColorRole.NEUTRAL
        val textColor = when (role) {
            ScoreColorRole.BEST -> ContextCompat.getColor(this, R.color.score_text_best)
            ScoreColorRole.WORST -> ContextCompat.getColor(this, R.color.score_text_worst)
            else -> ContextCompat.getColor(this, R.color.score_calculated_cell_text)
        }
        return TextView(this).apply {
            text = total.toString(); gravity = Gravity.CENTER; textSize = 15f
            setTypeface(null, Typeface.BOLD); setTextColor(textColor)
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.cell_calculated_bg))
        }
    }

    // ─── Category value / picker plumbing ─────────────────────────────────────

    private fun categoryValue(ps: SevenWondersPlayerScore, category: FlatCategory): Int? = when (category) {
        FlatCategory.MILITARY -> ps.militaryPoints
        FlatCategory.COINS -> ps.coins
        FlatCategory.WONDER -> ps.wonderPoints
        FlatCategory.CIVILIAN -> ps.civilianPoints
        FlatCategory.COMMERCE -> ps.commercePoints
        FlatCategory.GUILD -> ps.guildPoints
    }

    private fun categoryLabel(category: FlatCategory): String = when (category) {
        FlatCategory.MILITARY -> getString(R.string.sevenwonders_military)
        FlatCategory.COINS -> getString(R.string.sevenwonders_coins)
        FlatCategory.WONDER -> getString(R.string.sevenwonders_wonder)
        FlatCategory.CIVILIAN -> getString(R.string.sevenwonders_civilian)
        FlatCategory.COMMERCE -> getString(R.string.sevenwonders_commerce)
        FlatCategory.GUILD -> getString(R.string.sevenwonders_guild)
    }

    private fun categoryValues(category: FlatCategory): List<Int> = when (category) {
        FlatCategory.MILITARY -> SevenWondersValues.MILITARY_VALUES
        FlatCategory.COINS -> SevenWondersValues.COINS_VALUES
        FlatCategory.WONDER -> SevenWondersValues.WONDER_VALUES
        FlatCategory.CIVILIAN -> SevenWondersValues.CIVILIAN_VALUES
        FlatCategory.COMMERCE -> SevenWondersValues.COMMERCE_VALUES
        FlatCategory.GUILD -> SevenWondersValues.GUILD_VALUES
    }

    private fun applyCategoryValue(ps: SevenWondersPlayerScore, category: FlatCategory, value: Int) {
        when (category) {
            FlatCategory.MILITARY -> ps.militaryPoints = value
            FlatCategory.COINS -> ps.coins = value
            FlatCategory.WONDER -> ps.wonderPoints = value
            FlatCategory.CIVILIAN -> ps.civilianPoints = value
            FlatCategory.COMMERCE -> ps.commercePoints = value
            FlatCategory.GUILD -> ps.guildPoints = value
        }
    }

    private fun showCategoryPicker(ps: SevenWondersPlayerScore, category: FlatCategory) {
        val current = categoryValue(ps, category)
        val title = if (current != null) "✏️ ${ps.playerName} — ${categoryLabel(category)}"
                    else "${ps.playerName} — ${categoryLabel(category)}"
        val values = categoryValues(category)
        val items = values.map { it.toString() }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                applyCategoryValue(ps, category, values[which])
                buildScoreTable()
                checkCompletion()
            }
            .create()
        dialog.show()
        val scrollTo = if (current != null) values.indexOf(current) else values.indexOf(0).coerceAtLeast(0)
        if (scrollTo >= 0) dialog.listView?.post { dialog.listView?.setSelection(scrollTo) }
    }

    private fun showScienceSymbolPicker(ps: SevenWondersPlayerScore, symbol: ScienceSymbol) {
        val current = getScienceSymbolValue(ps, symbol)
        val values = SevenWondersValues.SCIENCE_SYMBOL_VALUES
        val items = values.map { it.toString() }.toTypedArray()
        val label = "${ps.playerName} — ${scienceSymbolLabel(symbol)}"
        val title = if (current != null) "✏️ $label" else label

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                setScienceSymbolValue(ps, symbol, values[which])
                buildScoreTable()
                checkCompletion()
            }
            .create()
        dialog.show()
        if (current != null) dialog.listView?.post { dialog.listView?.setSelection(current) }
    }

    // ─── Completion / results ──────────────────────────────────────────────────

    private fun checkCompletion() {
        if (playerScores.all { it.isComplete() } && !gameOver) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.sevenwonders_game_complete))
                .setMessage(getString(R.string.sevenwonders_game_complete_message))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildScoreTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no), null).show()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals = playerScores.associate { it to it.getTotal() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1
        val playedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(playerScores.map { ps ->
                GameResult(
                    gameType = GAME_TYPE, playerId = ps.playerId,
                    playerName = ps.playerName, score = ps.getTotal(),
                    isWinner = !isDraw && ps in winners,
                    isDraw = isDraw && ps in winners,
                    playedAt = playedAt
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (ps, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(ps.playerName, ps.playerColor, s, r)
            }
            GameResultsDialog.show(this@SevenWondersGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Layout helpers ────────────────────────────────────────────────────────

    private fun cellLayoutParams() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

    private fun makeColumn(widthDp: Int, weight: Float = 0f): LinearLayout {
        val widthPx = if (widthDp == 0) 0 else dpToPx(widthDp)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }
    }

    private fun borderDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor); setStroke(1, ContextCompat.getColor(this@SevenWondersGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sevenwonders_quit_game)
            .setMessage(R.string.sevenwonders_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sevenwonders_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { showQuitGameDialog(); true }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
